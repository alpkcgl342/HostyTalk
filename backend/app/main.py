import asyncio
import contextlib
import json
import logging
import time
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, EmailStr, Field

from . import ai, auth, currency, db, languages, phrases
from .config import settings
from .sessions import GUEST, MERCHANT, manager, share_url, summary_lines

logging.basicConfig(level=logging.INFO)
STATIC = Path(__file__).parent / "static"


@contextlib.asynccontextmanager
async def lifespan(_: FastAPI):
    db.init_db()

    async def janitor():
        while True:
            await asyncio.sleep(60)
            manager.cleanup()

    task = asyncio.create_task(janitor())
    yield
    task.cancel()


app = FastAPI(title="HostyTalk API", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.mount("/static", StaticFiles(directory=STATIC), name="static")


# --- modeller ---------------------------------------------------------------

class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6, max_length=128)
    business_name: str = Field(min_length=1, max_length=100)
    business_type: str = "other"


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class MeIn(BaseModel):
    business_name: str | None = Field(default=None, min_length=1, max_length=100)
    business_type: str | None = None


class SessionIn(BaseModel):
    merchant_lang: str = "tr"


class PhraseIn(BaseModel):
    text: str = Field(min_length=1, max_length=300)
    lang: str = "tr"


def valid_business_type(value: str | None) -> str | None:
    if value is not None and value not in phrases.BUSINESS_TYPES:
        raise HTTPException(422, "invalid_business_type")
    return value


def merchant_out(m) -> dict:
    used = db.used_seconds(m["id"])
    return {
        "id": m["id"],
        "email": m["email"],
        "business_name": m["business_name"],
        "business_type": m["business_type"],
        "plan": m["plan"],
        "used_minutes": round(used / 60, 1),
        "quota_minutes": settings.trial_minutes,
    }


# --- REST -------------------------------------------------------------------

@app.get("/api/health")
def health():
    return {"ok": True, "ai_configured": bool(settings.openai_api_key)}


@app.get("/api/languages")
def list_languages():
    return [{"code": k, "name": v["name"]} for k, v in languages.LANGUAGES.items()]


@app.post("/api/auth/register")
def register(body: RegisterIn):
    email = body.email.lower()
    if db.get_merchant_by_email(email):
        raise HTTPException(409, "email_taken")
    business_type = valid_business_type(body.business_type)
    mid = db.create_merchant(email, auth.hash_password(body.password), body.business_name.strip(), business_type)
    return {"token": auth.create_token(mid), "merchant": merchant_out(db.get_merchant(mid))}


@app.post("/api/auth/login")
def login(body: LoginIn):
    m = db.get_merchant_by_email(body.email.lower())
    if m is None or not auth.verify_password(body.password, m["password_hash"]):
        raise HTTPException(401, "invalid_credentials")
    return {"token": auth.create_token(m["id"]), "merchant": merchant_out(m)}


@app.get("/api/me")
def me(m=Depends(auth.current_merchant)):
    return merchant_out(m)


@app.patch("/api/me")
def update_me(body: MeIn, m=Depends(auth.current_merchant)):
    db.update_merchant(
        m["id"],
        business_name=body.business_name.strip() if body.business_name else None,
        business_type=valid_business_type(body.business_type),
    )
    return merchant_out(db.get_merchant(m["id"]))


# --- hazır cümleler -----------------------------------------------------------

@app.get("/api/phrases")
def list_phrases(lang: str = "tr", m=Depends(auth.current_merchant)):
    lang = languages.normalize(lang, default="tr")
    return {
        "business_type": m["business_type"],
        "custom": [{"id": r["id"], "text": r["text"]} for r in db.list_phrases(m["id"], lang)],
        "defaults": phrases.defaults(m["business_type"], lang),
    }


@app.post("/api/phrases")
def add_phrase(body: PhraseIn, m=Depends(auth.current_merchant)):
    lang = languages.normalize(body.lang, default="tr")
    pid = db.add_phrase(m["id"], lang, body.text.strip())
    return {"id": pid, "text": body.text.strip()}


@app.delete("/api/phrases/{phrase_id}")
def delete_phrase(phrase_id: int, m=Depends(auth.current_merchant)):
    if not db.delete_phrase(m["id"], phrase_id):
        raise HTTPException(404, "not_found")
    return {"ok": True}


# --- konuşma geçmişi ----------------------------------------------------------

def own_conversation(conversation_id: int, m) -> dict:
    conv = db.get_conversation(conversation_id)
    if conv is None or conv["merchant_id"] != m["id"]:
        raise HTTPException(404, "not_found")
    return conv


def conversation_out(conv: dict) -> dict:
    out = {k: v for k, v in conv.items() if k not in ("merchant_id", "share_key")}
    return {**out, "share_url": share_url(conv["share_key"])}


@app.get("/api/conversations")
def list_conversations(m=Depends(auth.current_merchant)):
    return [conversation_out(c) for c in db.list_conversations(m["id"])]


@app.get("/api/conversations/{conversation_id}")
def get_conversation(conversation_id: int, m=Depends(auth.current_merchant)):
    return conversation_out(own_conversation(conversation_id, m))


@app.post("/api/conversations/{conversation_id}/summary")
async def summarize_conversation(conversation_id: int, m=Depends(auth.current_merchant)):
    """Özetsiz bitirilen bir konuşmanın özetini sonradan oluşturur."""
    conv = own_conversation(conversation_id, m)
    if not conv["messages"] and not conv["offers"]:
        raise HTTPException(400, "empty_conversation")
    summary = await ai.summarize(
        summary_lines(conv["messages"], conv["offers"]), conv["merchant_lang"], conv["guest_lang"]
    )
    db.update_conversation(conversation_id, summary=summary)
    return conversation_out(db.get_conversation(conversation_id))


@app.delete("/api/conversations/{conversation_id}")
def delete_conversation(conversation_id: int, m=Depends(auth.current_merchant)):
    own_conversation(conversation_id, m)
    db.delete_conversation(m["id"], conversation_id)
    return {"ok": True}


@app.get("/api/share/{share_key}")
def shared_conversation(share_key: str):
    """Turistin saklayabildiği özet sayfasının verisi (bağlantıyı bilen görür)."""
    conv = db.get_conversation_by_share_key(share_key)
    if conv is None:
        raise HTTPException(404, "not_found")
    merchant = db.get_merchant(conv["merchant_id"])
    out = {k: v for k, v in conv.items() if k not in ("merchant_id", "share_key", "id")}
    return {**out, "business_name": merchant["business_name"] if merchant else ""}


@app.post("/api/sessions")
def create_session(body: SessionIn, m=Depends(auth.current_merchant)):
    lang = languages.normalize(body.merchant_lang, default="tr")
    s = manager.create(m["id"], m["business_name"], lang)
    return {"id": s.id, "join_url": s.join_url, "join_expires_at": int(s.join_expires_at), "merchant_lang": lang}


@app.get("/api/sessions/{session_id}/public")
def session_public(session_id: str, k: str):
    s = manager.get(session_id)
    if s is None or s.join_key != k or time.time() > s.join_expires_at:
        raise HTTPException(404, "session_not_found")
    return {"business_name": s.business_name, "merchant_lang": s.merchant_lang, "languages": list_languages()}


@app.get("/api/currency")
async def convert_currency(amount: float, currency_code: str = Query("TRY", alias="currency")):
    if currency_code not in currency.CURRENCIES:
        raise HTTPException(400, "unsupported_currency")
    return {"amount": amount, "currency": currency_code, "conversions": await currency.convert(amount, currency_code)}


@app.get("/g/{session_id}")
def guest_page(session_id: str):
    return FileResponse(STATIC / "guest" / "index.html")


@app.get("/s/{share_key}")
def share_page(share_key: str):
    return FileResponse(STATIC / "guest" / "share.html")


# --- WebSocket --------------------------------------------------------------

async def reject(ws: WebSocket, code: int) -> None:
    # Kabul etmeden kapatmak istemciye yalnızca HTTP 403 gösterir; 44xx kodunun ulaşması
    # için önce kabul edip sonra kapatıyoruz, böylece istemci boşuna yeniden bağlanmaz.
    await ws.accept()
    await ws.close(code=code)


@app.websocket("/ws/{session_id}")
async def conversation_ws(
    ws: WebSocket,
    session_id: str,
    role: str,
    token: str | None = None,
    k: str | None = None,
    lang: str | None = None,
):
    s = manager.get(session_id)
    if s is None:
        await reject(ws, 4404)
        return
    if role == MERCHANT:
        if auth.merchant_id_from_token(token or "") != s.merchant_id:
            await reject(ws, 4401)
            return
    elif role == GUEST:
        # QR yalnızca kendi oturumu için ve kısa süre geçerli.
        if k != s.join_key or time.time() > s.join_expires_at:
            await reject(ws, 4403)
            return
    else:
        await reject(ws, 4400)
        return

    await ws.accept()
    if role == MERCHANT:
        s.merchant_ws = ws
    else:
        s.guest_ws.add(ws)
        if lang:
            s.guest_lang = languages.normalize(lang, default="en")
    await s.broadcast(s.state())

    try:
        while True:
            msg = await ws.receive()
            if msg["type"] == "websocket.disconnect":
                break
            if msg.get("bytes"):
                asyncio.create_task(s.handle_audio(role, msg["bytes"]))
                continue
            try:
                data = json.loads(msg.get("text") or "{}")
            except json.JSONDecodeError:
                continue
            kind = data.get("type")
            if kind == "text":
                asyncio.create_task(s.handle_text(role, str(data.get("text", ""))))
            elif kind == "set_lang" and role == GUEST:
                await s.handle_set_guest_lang(str(data.get("lang", "")))
            elif kind in ("price", "offer"):
                try:
                    amount = float(data.get("amount", 0))
                except (TypeError, ValueError):
                    continue
                cur = str(data.get("currency", "TRY"))
                if kind == "offer":
                    await s.handle_offer(role, amount, cur)  # pazarlık: iki taraf da teklif verebilir
                elif role == MERCHANT:
                    await s.handle_price(amount, cur)
            elif kind == "offer_reply":
                try:
                    offer_id = int(data.get("offer_id", 0))
                except (TypeError, ValueError):
                    continue
                await s.handle_offer_reply(role, offer_id, bool(data.get("accept")))
            elif kind == "end" and role == MERCHANT:
                await s.end(with_summary=bool(data.get("summary", True)))
                manager.remove(s.id)
                return
            elif kind == "ping":
                await ws.send_json({"type": "pong"})
    except WebSocketDisconnect:
        pass
    finally:
        if role == MERCHANT and s.merchant_ws is ws:
            s.merchant_ws = None
        s.guest_ws.discard(ws)
        if manager.get(s.id):
            await s.broadcast(s.state())
