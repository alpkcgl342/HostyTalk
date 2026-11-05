"""Uçtan uca testler. OpenAI çağrıları taklit edilir; gerçek API anahtarı gerekmez."""

import base64
import io
import os
import tempfile
import wave

os.environ["DATABASE_PATH"] = os.path.join(tempfile.mkdtemp(), "test.db")
os.environ["PUBLIC_BASE_URL"] = "http://testserver"

import pytest
from fastapi.testclient import TestClient

from app import ai, currency
from app.main import app


def make_wav(seconds: float) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(b"\x01\x00" * int(16000 * seconds))
    return buf.getvalue()


@pytest.fixture(autouse=True)
def fake_ai(monkeypatch):
    state = {"stt": ("Merhaba, halı 450 euro.", "tr")}

    async def transcribe(wav, duration_hint):
        text, lang = state["stt"]
        return ai.Transcript(text, lang, duration_hint)

    async def translate(text, src, tgt):
        return f"[{src}->{tgt}] {text}"

    async def speak(text, lang):
        return b"MP3"

    async def summarize(lines, a, b):
        return {a: "özet", b: "summary"}

    async def try_per_unit():
        return {"TRY": 1.0, "EUR": 50.0, "USD": 40.0, "GBP": 60.0}

    monkeypatch.setattr(ai, "transcribe", transcribe)
    monkeypatch.setattr(ai, "translate", translate)
    monkeypatch.setattr(ai, "speak", speak)
    monkeypatch.setattr(ai, "summarize", summarize)
    monkeypatch.setattr(currency, "try_per_unit", try_per_unit)
    return state


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


def register(client, email="esnaf@example.com"):
    r = client.post("/api/auth/register", json={"email": email, "password": "secret12", "business_name": "Halıcı Ali"})
    assert r.status_code == 200, r.text
    return r.json()["token"]


def recv_until(ws, kind):
    while True:
        m = ws.receive_json()
        if m["type"] == kind:
            return m


def test_auth_flow(client):
    token = register(client, "a@example.com")
    assert client.post("/api/auth/register", json={
        "email": "a@example.com", "password": "secret12", "business_name": "x"}).status_code == 409
    assert client.post("/api/auth/login", json={"email": "a@example.com", "password": "wrong"}).status_code == 401
    assert client.post("/api/auth/login", json={"email": "A@example.com", "password": "secret12"}).status_code == 200
    me = client.get("/api/me", headers={"Authorization": f"Bearer {token}"}).json()
    assert me["business_name"] == "Halıcı Ali"
    assert client.get("/api/me").status_code == 401


def test_session_requires_merchant(client):
    assert client.post("/api/sessions", json={}).status_code == 401


def test_conversation(client, fake_ai):
    token = register(client, "b@example.com")
    s = client.post("/api/sessions", json={"merchant_lang": "tr"},
                    headers={"Authorization": f"Bearer {token}"}).json()
    key = s["join_url"].split("k=")[1]
    assert client.get(f"/api/sessions/{s['id']}/public", params={"k": "wrong"}).status_code == 404
    assert client.get(f"/api/sessions/{s['id']}/public", params={"k": key}).json()["business_name"] == "Halıcı Ali"

    with client.websocket_connect(f"/ws/{s['id']}?role=merchant&token={token}") as m, \
            client.websocket_connect(f"/ws/{s['id']}?role=guest&k={key}&lang=fr-FR") as g:
        assert recv_until(m, "state")["merchant_lang"] == "tr"
        assert recv_until(g, "state")["guest_lang"] == "fr"

        # Esnaf konuşur -> turist Fransızca duyar
        m.send_bytes(make_wav(1.0))
        gm = recv_until(g, "message")
        assert gm["translated"] == "[tr->fr] Merhaba, halı 450 euro."
        assert base64.b64decode(gm["audio_b64"]) == b"MP3"
        mm = recv_until(m, "message")
        assert "audio_b64" not in mm  # konuşan taraf kendi çevirisini duymaz

        # Turistin telefonu esnafın Türkçesini duyarsa yankı sayılır ve çevrilmez
        g.send_bytes(make_wav(1.0))
        assert recv_until(g, "idle")["type"] == "idle"

        # Turist Rusça konuşursa dil otomatik değişir
        fake_ai["stt"] = ("Сколько стоит?", "ru")
        g.send_bytes(make_wav(1.0))
        assert recv_until(m, "state")["guest_lang"] == "ru"
        msg = recv_until(m, "message")
        assert msg["source_lang"] == "ru" and msg["translated"].startswith("[ru->tr]")

        # Hazır kalıp cümle (metin)
        m.send_json({"type": "text", "text": "Hoş geldiniz"})
        recv_until(g, "message")  # turistin kendi Rusça mesajının kopyası
        assert recv_until(g, "message")["translated"] == "[tr->ru] Hoş geldiniz"

        # Fiyat
        m.send_json({"type": "price", "amount": 500, "currency": "TRY"})
        p = recv_until(g, "price")
        assert p["conversions"]["EUR"] == 10.0

        # Pazarlık: esnaf teklif verir, turist karşı teklif verir, esnaf kabul eder
        m.send_json({"type": "offer", "amount": 500, "currency": "EUR"})
        o1 = recv_until(g, "offer")
        assert o1["by"] == "merchant" and o1["status"] == "open"
        m.send_json({"type": "offer_reply", "offer_id": o1["id"], "accept": True})  # kendi teklifini kabul edemez
        g.send_json({"type": "offer", "amount": 400, "currency": "EUR"})
        assert recv_until(g, "offer") == {**o1, "type": "offer", "status": "countered"}
        o2 = recv_until(g, "offer")
        assert o2["by"] == "guest" and o2["status"] == "open"
        m.send_json({"type": "offer_reply", "offer_id": o2["id"], "accept": True})
        assert recv_until(g, "offer")["status"] == "accepted"

        # Bitir + özet
        m.send_json({"type": "end", "summary": True})
        summary = recv_until(g, "summary")
        assert summary["texts"] == {"tr": "özet", "ru": "summary"}
        share = summary["share_url"]
        assert recv_until(g, "ended")["share_url"] == share

    assert client.get(f"/api/sessions/{s['id']}/public", params={"k": key}).status_code == 404

    # Geçmiş
    auth = {"Authorization": f"Bearer {token}"}
    history = client.get("/api/conversations", headers=auth).json()
    assert len(history) == 1 and history[0]["message_count"] == 3 and history[0]["guest_lang"] == "ru"
    detail = client.get(f"/api/conversations/{history[0]['id']}", headers=auth).json()
    assert [o["status"] for o in detail["offers"]] == ["countered", "accepted"]
    assert detail["summary"] == {"tr": "özet", "ru": "summary"} and detail["ended_at"]

    # Turistin özet sayfası (hesapsız, bağlantıyla)
    share_key = share.rsplit("/", 1)[1]
    shared = client.get(f"/api/share/{share_key}").json()
    assert shared["business_name"] == "Halıcı Ali" and len(shared["messages"]) == 3
    assert "merchant_id" not in shared
    assert client.get(f"/s/{share_key}").status_code == 200

    # Başka esnaf göremez
    other = register(client, "other@example.com")
    assert client.get(f"/api/conversations/{history[0]['id']}",
                      headers={"Authorization": f"Bearer {other}"}).status_code == 404

    # Sonradan özet + silme
    assert client.post(f"/api/conversations/{history[0]['id']}/summary", headers=auth).status_code == 200
    assert client.delete(f"/api/conversations/{history[0]['id']}", headers=auth).status_code == 200
    assert client.get("/api/conversations", headers=auth).json() == []


def test_empty_conversation_not_saved(client):
    token = register(client, "empty@example.com")
    auth = {"Authorization": f"Bearer {token}"}
    s = client.post("/api/sessions", json={}, headers=auth).json()
    with client.websocket_connect(f"/ws/{s['id']}?role=merchant&token={token}") as m:
        recv_until(m, "state")
        m.send_json({"type": "end", "summary": True})
        assert recv_until(m, "ended")["share_url"] is None
    assert client.get("/api/conversations", headers=auth).json() == []


def test_phrases(client):
    r = client.post("/api/auth/register", json={
        "email": "otel@example.com", "password": "secret12", "business_name": "Otel", "business_type": "hotel"})
    token = r.json()["token"]
    assert r.json()["merchant"]["business_type"] == "hotel"
    auth = {"Authorization": f"Bearer {token}"}

    p = client.get("/api/phrases", params={"lang": "tr"}, headers=auth).json()
    assert "Kahvaltı 07:00–10:30 arası." in p["defaults"] and p["custom"] == []
    fr = client.get("/api/phrases", params={"lang": "fr"}, headers=auth).json()
    assert "Le départ est à 12h00." in fr["defaults"]

    added = client.post("/api/phrases", json={"text": "Havuz 22:00'de kapanır.", "lang": "tr"}, headers=auth).json()
    assert client.get("/api/phrases", headers=auth).json()["custom"] == [added]
    assert client.get("/api/phrases", params={"lang": "en"}, headers=auth).json()["custom"] == []

    # İşletme türü değişince hazır cümleler de değişir
    me = client.patch("/api/me", json={"business_type": "taxi"}, headers=auth).json()
    assert me["business_type"] == "taxi"
    assert "Taksimetreyle gidiyoruz." in client.get("/api/phrases", headers=auth).json()["defaults"]
    assert client.patch("/api/me", json={"business_type": "spaceship"}, headers=auth).status_code == 422

    assert client.delete(f"/api/phrases/{added['id']}", headers=auth).status_code == 200
    assert client.delete(f"/api/phrases/{added['id']}", headers=auth).status_code == 404


def test_guest_rejected_with_bad_key(client):
    token = register(client, "c@example.com")
    s = client.post("/api/sessions", json={}, headers={"Authorization": f"Bearer {token}"}).json()
    from starlette.websockets import WebSocketDisconnect
    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect(f"/ws/{s['id']}?role=guest&k=nope") as ws:
            ws.receive_json()


def test_guest_page_served(client):
    r = client.get("/g/abc?k=1")
    assert r.status_code == 200 and "HostyTalk" in r.text
