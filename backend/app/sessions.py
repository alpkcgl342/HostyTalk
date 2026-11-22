"""Canlı çeviri oturumları: bir kullanıcı başlatır, diğeri kodla ya da QR ile katılır.

İki tarafın da hesabı vardır; her kişinin konuşma süresi kendi kotasından düşer.
Canlı oturumlar bellekte tutulur (tek sunucu için yeterli); mesajlar ve özet ayrıca
veritabanına yazılır, böylece iki katılımcı da konuşmayı kendi geçmişinde görür.
"""

import asyncio
import base64
import io
import itertools
import logging
import secrets
import time
import wave
from dataclasses import dataclass, field

from fastapi import WebSocket

from . import ai, db, languages
from .config import settings

log = logging.getLogger(__name__)

HOST, GUEST = "host", "guest"
ROLES = (HOST, GUEST)
MIN_SPEECH_SECONDS = 0.3
MAX_SPEECH_SECONDS = 60
MAX_SESSION_SECONDS = 4 * 3600
# Karışmaması için 0/O ve 1/I gibi benzer karakterler yok.
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 6


def wav_duration(data: bytes) -> float:
    with wave.open(io.BytesIO(data)) as w:
        return w.getnframes() / float(w.getframerate())


def summary_lines(messages: list[dict], names: dict[str, str]) -> list[str]:
    """Özet için konuşmanın düz metin dökümü."""
    return [f"{names.get(m['from']) or m['from']} ({m['source_lang']}): {m['original']}" for m in messages]


@dataclass
class Session:
    id: str
    code: str
    host_id: int
    host_name: str
    host_lang: str
    conversation_id: int
    guest_id: int | None = None
    guest_name: str = ""
    guest_lang: str = "en"
    created_at: float = field(default_factory=time.time)
    sockets_by_role: dict[str, set[WebSocket]] = field(default_factory=lambda: {HOST: set(), GUEST: set()})
    history: list[dict] = field(default_factory=list)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    _ids: itertools.count = field(default_factory=lambda: itertools.count(1))

    @property
    def join_expires_at(self) -> float:
        return self.created_at + settings.session_ttl_minutes * 60

    @property
    def join_url(self) -> str:
        return f"{settings.public_base_url}/j/{self.code}"

    def user_of(self, role: str) -> int | None:
        return self.host_id if role == HOST else self.guest_id

    def lang_of(self, role: str) -> str:
        return self.host_lang if role == HOST else self.guest_lang

    def name_of(self, role: str) -> str:
        return self.host_name if role == HOST else self.guest_name

    @staticmethod
    def other(role: str) -> str:
        return GUEST if role == HOST else HOST

    def sockets(self, role: str) -> list[WebSocket]:
        return list(self.sockets_by_role[role])

    def state(self) -> dict:
        return {
            "type": "state",
            "session_id": self.id,
            "code": self.code,
            "host_name": self.host_name,
            "guest_name": self.guest_name,
            "host_lang": self.host_lang,
            "guest_lang": self.guest_lang,
            "host_connected": bool(self.sockets_by_role[HOST]),
            "guest_connected": bool(self.sockets_by_role[GUEST]),
            "join_url": self.join_url,
            "join_expires_at": int(self.join_expires_at),
        }

    async def send(self, role: str, payload: dict) -> None:
        for ws in self.sockets(role):
            try:
                await ws.send_json(payload)
            except Exception:  # bağlantı kopmuşsa sessizce geç
                pass

    async def broadcast(self, payload: dict) -> None:
        await self.send(HOST, payload)
        await self.send(GUEST, payload)

    # --- katılım -------------------------------------------------------------

    def claim_guest(self, user_id: int, name: str, lang: str) -> bool:
        """Katılımcı yerini alır. Oturum iki kişiliktir; yer doluysa yalnızca aynı kişi geri dönebilir."""
        if self.guest_id is not None:
            return self.guest_id == user_id
        if time.time() > self.join_expires_at:
            return False
        self.guest_id, self.guest_name = user_id, name
        self.guest_lang = languages.normalize(lang, default="en")
        db.update_conversation(self.conversation_id, guest_id=user_id, guest_lang=self.guest_lang)
        return True

    # --- konuşma akışı -------------------------------------------------------

    def _quota_left(self, role: str) -> bool:
        user_id = self.user_of(role)
        return user_id is not None and db.used_seconds(user_id) < settings.trial_minutes * 60

    async def handle_audio(self, role: str, wav: bytes) -> None:
        try:
            duration = wav_duration(wav)
        except (wave.Error, EOFError):
            await self.send(role, {"type": "error", "code": "bad_audio"})
            return
        log.info("Ses alındı: %s, %.1f sn, oturum %s", role, duration, self.id)
        if duration < MIN_SPEECH_SECONDS or duration > MAX_SPEECH_SECONDS:
            await self.send(role, {"type": "idle"})
            return
        if not self._quota_left(role):
            await self.send(role, {"type": "error", "code": "quota_exceeded"})
            return

        async with self.lock:
            await self.broadcast({"type": "processing", "from": role})
            try:
                t = await ai.transcribe(wav, duration)
                db.add_usage(self.user_of(role), t.duration)  # konuşan kişinin kotasından düşer
                if not t.text:
                    await self.broadcast({"type": "idle"})
                    return
                if not await self._accept_language(role, t.language):
                    await self.broadcast({"type": "idle"})
                    return
                await self._deliver(role, t.text)
            except Exception as e:
                log.exception("Ses işlenemedi")
                await self.broadcast({"type": "error", "code": "ai_failed", "detail": str(e)})

    async def _accept_language(self, role: str, detected: str | None) -> bool:
        """Algılanan dile göre konuşmayı kabul eder ya da eler.

        İki kişi yan yana konuşurken bir telefonun mikrofonu karşı tarafın sesini de
        duyabilir. Bir telefondan karşı tarafın dili gelirse bu yankıdır, çevrilmez.
        Bir taraf başka bir dilde konuşmaya başlarsa o tarafın dili otomatik değişir.
        """
        if detected is None:
            return True
        # Diller farklıyken karşı tarafın dili gelirse yankıdır. Diller aynıysa (ör. ikisi de
        # yanlışlıkla Türkçe seçmiş) yankı ayırt edilemez; konuşma kabul edilir ve dil düzeltilir.
        if self.host_lang != self.guest_lang and detected == self.lang_of(self.other(role)):
            return False
        if detected != self.lang_of(role) and languages.is_supported(detected):
            await self.set_lang(role, detected)
        return True

    async def handle_text(self, role: str, text: str) -> None:
        text = text.strip()[:1000]
        if not text:
            return
        async with self.lock:
            await self.broadcast({"type": "processing", "from": role})
            try:
                # Yazan kişi seçtiğinden farklı bir dilde yazdıysa dilini düzelt (yankı derdi yok).
                detected = await ai.detect_language(text)
                if detected and detected != self.lang_of(role):
                    await self.set_lang(role, detected)
                await self._deliver(role, text)
            except Exception as e:
                log.exception("Metin işlenemedi")
                await self.broadcast({"type": "error", "code": "ai_failed", "detail": str(e)})

    async def _deliver(self, role: str, original: str) -> None:
        src = self.lang_of(role)
        listener = self.other(role)
        tgt = self.lang_of(listener)
        translated = await ai.translate(original, src, tgt)
        audio = await ai.speak(translated, tgt) if translated else b""
        message = {
            "type": "message",
            "id": next(self._ids),
            "from": role,
            "source_lang": src,
            "target_lang": tgt,
            "original": original,
            "translated": translated,
            "ts": int(time.time()),
        }
        self.history.append(message)
        db.add_message(self.conversation_id, message)
        await self.send(role, message)
        await self.send(listener, {**message, "audio_b64": base64.b64encode(audio).decode()})

    async def set_lang(self, role: str, lang: str) -> None:
        if not languages.is_supported(lang) or lang == self.lang_of(role):
            return
        if role == HOST:
            self.host_lang = lang
        else:
            self.guest_lang = lang
            db.update_conversation(self.conversation_id, guest_lang=lang)
        await self.broadcast(self.state())

    # --- bitiş -----------------------------------------------------------------

    def finalize(self) -> None:
        """Konuşmayı veritabanında kapatır; hiçbir şey konuşulmadıysa kaydı siler."""
        if not self.history:
            db.delete_conversation(self.conversation_id)
        else:
            db.update_conversation(self.conversation_id, guest_lang=self.guest_lang, ended=True)

    async def end(self, by: str, with_summary: bool) -> None:
        if with_summary and self.history:
            try:
                names = {HOST: self.host_name, GUEST: self.guest_name}
                summary = await ai.summarize(summary_lines(self.history, names), self.host_lang, self.guest_lang)
                db.update_conversation(self.conversation_id, summary=summary)
                await self.broadcast({"type": "summary", "texts": summary})
            except Exception as e:
                log.exception("Özet oluşturulamadı")
                await self.send(by, {"type": "error", "code": "summary_failed", "detail": str(e)})
        self.finalize()
        await self.broadcast({"type": "ended", "by": by, "conversation_id": self.conversation_id if self.history else None})
        for ws in self.sockets(HOST) + self.sockets(GUEST):
            try:
                await ws.close()
            except Exception:
                pass


class SessionManager:
    def __init__(self) -> None:
        self._sessions: dict[str, Session] = {}

    def _new_code(self) -> str:
        active = {s.code for s in self._sessions.values()}
        while True:
            code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))
            if code not in active:
                return code

    def create(self, host_id: int, host_name: str, host_lang: str) -> Session:
        s = Session(
            id=secrets.token_urlsafe(8),
            code=self._new_code(),
            host_id=host_id,
            host_name=host_name,
            host_lang=host_lang,
            conversation_id=db.create_conversation(host_id, host_lang, "en"),
        )
        self._sessions[s.id] = s
        return s

    def get(self, session_id: str) -> Session | None:
        return self._sessions.get(session_id)

    def by_code(self, code: str) -> Session | None:
        code = code.strip().upper()
        return next((s for s in self._sessions.values() if s.code == code), None)

    def remove(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)

    def cleanup(self) -> None:
        now = time.time()
        for sid, s in list(self._sessions.items()):
            idle = not s.sockets_by_role[HOST] and not s.sockets_by_role[GUEST]
            if now - s.created_at > MAX_SESSION_SECONDS or (idle and now > s.join_expires_at):
                s.finalize()  # bitir'e basılmadan bırakılan konuşmalar da geçmişe düşsün
                self.remove(sid)


manager = SessionManager()
