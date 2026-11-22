"""Uçtan uca testler. OpenAI çağrıları taklit edilir; gerçek API anahtarı gerekmez."""

import base64
import dataclasses
import io
import os
import sqlite3
import tempfile
import wave

os.environ["DATABASE_PATH"] = os.path.join(tempfile.mkdtemp(), "test.db")
os.environ["PUBLIC_BASE_URL"] = "http://testserver"

import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from app import ai, db
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
    state = {"stt": ("Yarın saat üçte kütüphanede buluşalım.", "tr"), "detect": {}}

    async def detect_language(text):
        return state["detect"].get(text)

    monkeypatch.setattr(ai, "detect_language", detect_language)

    async def transcribe(wav, duration_hint):
        text, lang = state["stt"]
        return ai.Transcript(text, lang, duration_hint)

    async def translate(text, src, tgt):
        return f"[{src}->{tgt}] {text}"

    async def speak(text, lang):
        return b"MP3"

    async def summarize(lines, a, b):
        return {a: "özet", b: "summary"}

    monkeypatch.setattr(ai, "transcribe", transcribe)
    monkeypatch.setattr(ai, "translate", translate)
    monkeypatch.setattr(ai, "speak", speak)
    monkeypatch.setattr(ai, "summarize", summarize)
    return state


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


def register(client, email, name):
    r = client.post("/api/auth/register", json={"email": email, "password": "secret12", "display_name": name})
    assert r.status_code == 200, r.text
    return r.json()["token"]


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def recv_until(ws, kind, **expected):
    """[kind] türünde ve [expected] alanlarını taşıyan ilk mesajı bekler."""
    while True:
        m = ws.receive_json()
        if m["type"] == kind and all(m.get(k) == v for k, v in expected.items()):
            return m


def test_auth_flow(client):
    token = register(client, "a@example.com", "Ayşe")
    assert client.post("/api/auth/register", json={
        "email": "a@example.com", "password": "secret12", "display_name": "x"}).status_code == 409
    assert client.post("/api/auth/login", json={"email": "a@example.com", "password": "wrong"}).status_code == 401
    assert client.post("/api/auth/login", json={"email": "A@example.com", "password": "secret12"}).status_code == 200
    assert client.get("/api/me", headers=auth(token)).json()["display_name"] == "Ayşe"
    assert client.patch("/api/me", json={"display_name": "Ayşe Y."}, headers=auth(token)).json()["display_name"] == "Ayşe Y."
    assert client.get("/api/me").status_code == 401


def test_change_password(client):
    old_token = register(client, "pw@example.com", "Can")
    other_device = client.post("/api/auth/login", json={"email": "pw@example.com", "password": "secret12"}).json()["token"]

    assert client.post("/api/me/password", json={"new_password": "short"}, headers=auth(old_token)).status_code == 422
    assert client.post("/api/me/password", json={"new_password": "yenisifre9"}).status_code == 401
    r = client.post("/api/me/password", json={"new_password": "yenisifre9"}, headers=auth(old_token))
    assert r.status_code == 200
    new_token = r.json()["token"]

    # Yeni şifre çalışır, eskisi çalışmaz
    assert client.post("/api/auth/login", json={"email": "pw@example.com", "password": "yenisifre9"}).status_code == 200
    assert client.post("/api/auth/login", json={"email": "pw@example.com", "password": "secret12"}).status_code == 401
    # Bu cihaz yeni token'la devam eder; diğer cihazlardaki eski oturumlar kapanır
    assert client.get("/api/me", headers=auth(new_token)).status_code == 200
    assert client.get("/api/me", headers=auth(old_token)).status_code == 401
    assert client.get("/api/me", headers=auth(other_device)).status_code == 401


def test_token_issued_before_versioning_still_works(client):
    """Bu özellikten önce verilmiş ("ver" alanı olmayan) token'lar geçerli kalır."""
    import jwt
    import time
    from app.config import settings
    register(client, "legacy@example.com", "Eski")
    uid = db.get_user_by_email("legacy@example.com")["id"]
    legacy = jwt.encode({"sub": str(uid), "exp": int(time.time()) + 60}, settings.jwt_secret, algorithm="HS256")
    assert client.get("/api/me", headers=auth(legacy)).status_code == 200


def test_both_sides_need_accounts(client):
    host = register(client, "h1@example.com", "Ali")
    s = client.post("/api/sessions", json={"lang": "tr"}, headers=auth(host)).json()
    assert len(s["code"]) == 6 and s["join_url"].endswith("/j/" + s["code"])
    assert client.post("/api/sessions", json={}).status_code == 401
    # Hesapsız katılım yok
    assert client.post("/api/sessions/join", json={"code": s["code"]}).status_code == 401
    # Katılmadan bağlanılamaz
    stranger = register(client, "x1@example.com", "Yabancı")
    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect(f"/ws/{s['id']}?role=guest&token={stranger}") as ws:
            ws.receive_json()
    # Kod sayfası, giriş yapmadan kimin davet ettiğini gösterir
    assert client.get(f"/api/sessions/code/{s['code'].lower()}").json()["host_name"] == "Ali"
    assert client.get("/api/sessions/code/ZZZZZZ").status_code == 404


def test_conversation(client, fake_ai):
    host = register(client, "ali@example.com", "Ali")
    guest = register(client, "marie@example.com", "Marie")
    s = client.post("/api/sessions", json={"lang": "tr"}, headers=auth(host)).json()

    j = client.post("/api/sessions/join", json={"code": s["code"].lower(), "lang": "fr-FR"}, headers=auth(guest))
    assert j.status_code == 200 and j.json()["role"] == "guest" and j.json()["host_name"] == "Ali"
    # Oturum iki kişilik
    third = register(client, "third@example.com", "Üçüncü")
    assert client.post("/api/sessions/join", json={"code": s["code"]}, headers=auth(third)).status_code == 409
    # Aynı kişi tekrar katılabilir (bağlantı koparsa)
    assert client.post("/api/sessions/join", json={"code": s["code"], "lang": "fr"}, headers=auth(guest)).status_code == 200

    with client.websocket_connect(f"/ws/{s['id']}?role=host&token={host}") as h, \
            client.websocket_connect(f"/ws/{s['id']}?role=guest&token={guest}") as g:
        st = recv_until(g, "state")
        assert st["guest_lang"] == "fr" and st["guest_name"] == "Marie" and st["host_name"] == "Ali"

        # Ali konuşur -> Marie Fransızca duyar
        h.send_bytes(make_wav(1.0))
        gm = recv_until(g, "message")
        assert gm["translated"] == "[tr->fr] Yarın saat üçte kütüphanede buluşalım."
        assert base64.b64decode(gm["audio_b64"]) == b"MP3"
        assert "audio_b64" not in recv_until(h, "message")  # konuşan kendi çevirisini duymaz

        # Marie'nin telefonu Ali'nin Türkçesini duyarsa yankı sayılır
        g.send_bytes(make_wav(1.0))
        assert recv_until(g, "idle")["type"] == "idle"

        # Marie Rusça konuşursa dili otomatik değişir
        fake_ai["stt"] = ("Хорошо, до завтра.", "ru")
        g.send_bytes(make_wav(1.0))
        assert recv_until(h, "state")["guest_lang"] == "ru"
        assert recv_until(h, "message")["translated"].startswith("[ru->tr]")

        # Yazılı mesaj da gönderilebilir
        h.send_json({"type": "text", "text": "Görüşürüz"})
        recv_until(g, "message")  # Marie'nin kendi mesajının kopyası
        assert recv_until(g, "message")["translated"] == "[tr->ru] Görüşürüz"

        # Kaldırılan özellikler artık bir şey yapmaz
        h.send_json({"type": "offer", "amount": 5, "currency": "EUR"})

        # Katılımcı da bitirebilir
        g.send_json({"type": "end", "summary": True})
        assert recv_until(h, "summary")["texts"] == {"tr": "özet", "ru": "summary"}
        ended = recv_until(h, "ended")
        assert ended["by"] == "guest" and ended["conversation_id"]

    # Kullanım konuşan kişiye yazılır
    assert db.used_seconds(client.get("/api/me", headers=auth(host)).json()["id"]) > 0
    assert db.used_seconds(client.get("/api/me", headers=auth(guest)).json()["id"]) > 0

    # İki taraf da geçmişte kendi gözünden görür
    hl = client.get("/api/conversations", headers=auth(host)).json()
    gl = client.get("/api/conversations", headers=auth(guest)).json()
    assert len(hl) == len(gl) == 1 and hl[0]["message_count"] == 3
    assert hl[0]["other_name"] == "Marie" and hl[0]["my_lang"] == "tr" and hl[0]["other_lang"] == "ru"
    assert gl[0]["other_name"] == "Ali" and gl[0]["my_lang"] == "ru"
    detail = client.get(f"/api/conversations/{gl[0]['id']}", headers=auth(guest)).json()
    assert [m["mine"] for m in detail["messages"]] == [False, True, False]
    assert detail["summary"] == {"tr": "özet", "ru": "summary"}
    assert client.get(f"/api/conversations/{gl[0]['id']}", headers=auth(third)).status_code == 404

    # Sonradan özet
    assert client.post(f"/api/conversations/{hl[0]['id']}/summary", headers=auth(host)).status_code == 200

    # Silme yalnızca silen kişiden gizler; ikisi de silince kayıt gider
    assert client.delete(f"/api/conversations/{hl[0]['id']}", headers=auth(host)).status_code == 200
    assert client.get("/api/conversations", headers=auth(host)).json() == []
    assert len(client.get("/api/conversations", headers=auth(guest)).json()) == 1
    client.delete(f"/api/conversations/{hl[0]['id']}", headers=auth(guest))
    assert db.get_conversation(hl[0]["id"]) is None


def test_same_language_on_both_sides_gets_fixed(client, fake_ai):
    """İki taraf da Türkçe seçmişken biri İngilizce yazar ya da konuşursa dili düzelir ve çeviri olur."""
    host = register(client, "tr1@example.com", "Ali")
    guest = register(client, "tr2@example.com", "John")
    s = client.post("/api/sessions", json={"lang": "tr"}, headers=auth(host)).json()
    client.post("/api/sessions/join", json={"code": s["code"], "lang": "tr"}, headers=auth(guest))

    with client.websocket_connect(f"/ws/{s['id']}?role=host&token={host}") as h, \
            client.websocket_connect(f"/ws/{s['id']}?role=guest&token={guest}") as g:
        recv_until(h, "state")

        # Yazılı İngilizce mesaj: katılanın dili en olur, Türkçeye çevrilir
        fake_ai["detect"]["Hello, how are you?"] = "en"
        g.send_json({"type": "text", "text": "Hello, how are you?"})
        recv_until(h, "state", guest_lang="en")
        m = recv_until(h, "message")
        assert m["source_lang"] == "en" and m["translated"] == "[en->tr] Hello, how are you?"

        # Başlatanın Türkçesi artık İngilizceye çevrilir
        h.send_json({"type": "text", "text": "İyiyim, sen?"})
        assert recv_until(g, "message")["translated"] == "[en->tr] Hello, how are you?"  # kendi mesajının kopyası
        assert recv_until(g, "message")["translated"] == "[tr->en] İyiyim, sen?"

    # Sesli mesajda da aynısı: ikisi de Türkçe kayıtlıyken İngilizce konuşulursa dil düzelir
    s2 = client.post("/api/sessions", json={"lang": "tr"}, headers=auth(host)).json()
    client.post("/api/sessions/join", json={"code": s2["code"], "lang": "tr"}, headers=auth(guest))
    fake_ai["stt"] = ("See you tomorrow.", "en")
    with client.websocket_connect(f"/ws/{s2['id']}?role=host&token={host}") as h, \
            client.websocket_connect(f"/ws/{s2['id']}?role=guest&token={guest}") as g:
        recv_until(h, "state")
        g.send_bytes(make_wav(1.0))
        recv_until(h, "state", guest_lang="en")
        assert recv_until(h, "message")["translated"] == "[en->tr] See you tomorrow."


def test_empty_conversation_not_saved(client):
    host = register(client, "empty@example.com", "Boş")
    s = client.post("/api/sessions", json={}, headers=auth(host)).json()
    with client.websocket_connect(f"/ws/{s['id']}?role=host&token={host}") as h:
        recv_until(h, "state")
        h.send_json({"type": "end", "summary": True})
        assert recv_until(h, "ended")["conversation_id"] is None
    assert client.get("/api/conversations", headers=auth(host)).json() == []


def test_migrates_first_version_database(tmp_path, monkeypatch):
    """İlk sürümün (esnaf) veritabanındaki hesaplar korunur."""
    path = tmp_path / "old.db"
    c = sqlite3.connect(path)
    c.executescript("""
        CREATE TABLE merchants (id INTEGER PRIMARY KEY, email TEXT UNIQUE, password_hash TEXT,
            business_name TEXT, business_type TEXT DEFAULT 'other', plan TEXT DEFAULT 'trial', created_at INTEGER);
        CREATE TABLE usage (merchant_id INTEGER, month TEXT, seconds REAL, PRIMARY KEY (merchant_id, month));
        CREATE TABLE conversations (id INTEGER PRIMARY KEY, merchant_id INTEGER, share_key TEXT);
        CREATE TABLE phrases (id INTEGER PRIMARY KEY);
        INSERT INTO merchants (email, password_hash, business_name, created_at) VALUES ('old@example.com', 'x$y', 'Eski', 1);
        INSERT INTO usage VALUES (1, '2026-11', 30);
    """)
    c.close()
    monkeypatch.setattr(db, "settings", dataclasses.replace(db.settings, database_path=str(path)))
    db.init_db()
    assert db.get_user_by_email("old@example.com")["display_name"] == "Eski"
    assert db.create_conversation(1, "tr", "en") > 0


def test_join_page_served(client):
    r = client.get("/j/ABC234")
    assert r.status_code == 200 and "HostyTalk" in r.text
