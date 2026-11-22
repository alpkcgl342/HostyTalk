import asyncio
import contextlib
import json
import logging
import time
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, EmailStr, Field

from . import ai, auth, db, languages
from .config import settings
from .sessions import GUEST, HOST, ROLES, Session, manager, summary_lines

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
    display_name: str = Field(min_length=1, max_length=60)


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class MeIn(BaseModel):
    display_name: str = Field(min_length=1, max_length=60)


class SessionIn(BaseModel):
    lang: str = "tr"


class JoinIn(BaseModel):
    code: str = Field(min_length=4, max_length=12)
    lang: str = "en"


def user_out(u) -> dict:
    used = db.used_seconds(u["id"])
    return {
        "id": u["id"],
        "email": u["email"],
        "display_name": u["display_name"],
        "plan": u["plan"],
        "used_minutes": round(used / 60, 1),
        "quota_minutes": settings.trial_minutes,
    }


def session_out(s: Session, role: str) -> dict:
    return {
        "id": s.id,
        "role": role,
        "code": s.code,
        "join_url": s.join_url,
        "join_expires_at": int(s.join_expires_at),
        "host_name": s.host_name,
        "host_lang": s.host_lang,
        "guest_name": s.guest_name,
        "guest_lang": s.guest_lang,
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
    if db.get_user_by_email(email):
        raise HTTPException(409, "email_taken")
    uid = db.create_user(email, auth.hash_password(body.password), body.display_name.strip())
    return {"token": auth.create_token(uid), "user": user_out(db.get_user(uid))}


@app.post("/api/auth/login")
def login(body: LoginIn):
    u = db.get_user_by_email(body.email.lower())
    if u is None or not auth.verify_password(body.password, u["password_hash"]):
        raise HTTPException(401, "invalid_credentials")
    return {"token": auth.create_token(u["id"]), "user": user_out(u)}


@app.get("/api/me")
def me(u=Depends(auth.current_user)):
    return user_out(u)


@app.patch("/api/me")
def update_me(body: MeIn, u=Depends(auth.current_user)):
    db.update_display_name(u["id"], body.display_name.strip())
    return user_out(db.get_user(u["id"]))


# --- oturum: başlat / katıl -------------------------------------------------

@app.post("/api/sessions")
def create_session(body: SessionIn, u=Depends(auth.current_user)):
    lang = languages.normalize(body.lang, default="tr")
    s = manager.create(u["id"], u["display_name"], lang)
    return session_out(s, HOST)


@app.get("/api/sessions/code/{code}")
def session_by_code(code: str):
    """Katılım sayfası giriş yapmadan önce kimin davet ettiğini gösterebilsin diye."""
    s = manager.by_code(code)
    if s is None or (s.guest_id is None and s.join_expires_at < time.time()):
        raise HTTPException(404, "session_not_found")
    return {"host_name": s.host_name, "host_lang": s.host_lang}


@app.post("/api/sessions/join")
def join_session(body: JoinIn, u=Depends(auth.current_user)):
    s = manager.by_code(body.code)
    if s is None:
        raise HTTPException(404, "session_not_found")
    if not s.claim_guest(u["id"], u["display_name"], body.lang):
        raise HTTPException(409, "session_full")
    return session_out(s, GUEST)


# --- konuşma geçmişi ----------------------------------------------------------

def my_role(conv: dict, user_id: int) -> str | None:
    if conv["host_id"] == user_id and not conv.get("host_deleted"):
        return HOST
    if conv["guest_id"] == user_id and not conv.get("guest_deleted"):
        return GUEST
    return None


def conversation_out(conv: dict, user_id: int) -> dict:
    """Konuşmayı isteyen kişinin gözünden döndürür: kendi dili, karşı tarafın adı ve dili."""
    role = HOST if conv["host_id"] == user_id else GUEST
    other = GUEST if role == HOST else HOST
    out = {
        "id": conv["id"],
        "my_role": role,
        "my_lang": conv[f"{role}_lang"],
        "other_name": conv[f"{other}_name"],
        "other_lang": conv[f"{other}_lang"],
        "started_at": conv["started_at"],
        "ended_at": conv["ended_at"],
        "summary": conv["summary"],
    }
    if "message_count" in conv:
        out["message_count"] = conv["message_count"]
    if "messages" in conv:
        out["messages"] = [{**m, "mine": m["from"] == role} for m in conv["messages"]]
    return out


def own_conversation(conversation_id: int, u) -> dict:
    conv = db.get_conversation(conversation_id)
    if conv is None or my_role(conv, u["id"]) is None:
        raise HTTPException(404, "not_found")
    return conv


@app.get("/api/conversations")
def list_conversations(u=Depends(auth.current_user)):
    return [conversation_out(c, u["id"]) for c in db.list_conversations(u["id"])]


@app.get("/api/conversations/{conversation_id}")
def get_conversation(conversation_id: int, u=Depends(auth.current_user)):
    return conversation_out(own_conversation(conversation_id, u), u["id"])


@app.post("/api/conversations/{conversation_id}/summary")
async def summarize_conversation(conversation_id: int, u=Depends(auth.current_user)):
    """Özetsiz bitirilen bir konuşmanın özetini sonradan oluşturur."""
    conv = own_conversation(conversation_id, u)
    if not conv["messages"]:
        raise HTTPException(400, "empty_conversation")
    names = {HOST: conv["host_name"], GUEST: conv["guest_name"]}
    summary = await ai.summarize(summary_lines(conv["messages"], names), conv["host_lang"], conv["guest_lang"])
    db.update_conversation(conversation_id, summary=summary)
    return conversation_out(db.get_conversation(conversation_id), u["id"])


@app.delete("/api/conversations/{conversation_id}")
def delete_conversation(conversation_id: int, u=Depends(auth.current_user)):
    """Yalnızca isteyen kişinin geçmişinden kaldırır; karşı tarafın kaydı durur."""
    own_conversation(conversation_id, u)
    db.hide_conversation(conversation_id, u["id"])
    return {"ok": True}


# --- web katılım sayfası (QR ile açılır) --------------------------------------

@app.get("/j/{code}")
def join_page(code: str):
    return FileResponse(STATIC / "guest" / "index.html")


# --- WebSocket --------------------------------------------------------------

async def reject(ws: WebSocket, code: int) -> None:
    # Kabul etmeden kapatmak istemciye yalnızca HTTP 403 gösterir; 44xx kodunun ulaşması
    # için önce kabul edip sonra kapatıyoruz, böylece istemci boşuna yeniden bağlanmaz.
    await ws.accept()
    await ws.close(code=code)


@app.websocket("/ws/{session_id}")
async def conversation_ws(ws: WebSocket, session_id: str, role: str, token: str | None = None):
    s = manager.get(session_id)
    if s is None:
        await reject(ws, 4404)
        return
    if role not in ROLES:
        await reject(ws, 4400)
        return
    # İki taraf da hesabıyla bağlanır; katılımcı önce /api/sessions/join ile yerini almış olmalı.
    user_id = auth.user_id_from_token(token or "")
    if user_id is None or user_id != s.user_of(role):
        await reject(ws, 4401)
        return

    await ws.accept()
    s.sockets_by_role[role].add(ws)
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
            elif kind == "set_lang":
                await s.set_lang(role, str(data.get("lang", "")))
            elif kind == "end":
                # Konuşmayı iki taraftan biri bitirebilir.
                await s.end(by=role, with_summary=bool(data.get("summary", True)))
                manager.remove(s.id)
                return
            elif kind == "ping":
                await ws.send_json({"type": "pong"})
    except WebSocketDisconnect:
        pass
    finally:
        s.sockets_by_role[role].discard(ws)
        if manager.get(s.id):
            await s.broadcast(s.state())
