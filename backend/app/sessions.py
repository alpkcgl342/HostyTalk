"""Tezgâh modu oturumları: esnafın başlattığı, turistin QR ile katıldığı konuşmalar.

Canlı oturumlar bellekte tutulur (tek sunucu için yeterli); mesajlar, teklifler ve özet
ayrıca veritabanına yazılır, böylece esnaf geçmişi görür ve turist özeti saklayabilir.
Maliyet her zaman oturumu başlatan esnaf hesabından düşer; turist yalnızca misafirdir.
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

from . import ai, currency, db, languages
from .config import settings

log = logging.getLogger(__name__)

MERCHANT, GUEST = "merchant", "guest"
MIN_SPEECH_SECONDS = 0.3
MAX_SPEECH_SECONDS = 60
MAX_SESSION_SECONDS = 4 * 3600


def wav_duration(data: bytes) -> float:
    with wave.open(io.BytesIO(data)) as w:
        return w.getnframes() / float(w.getframerate())


def share_url(share_key: str) -> str:
    return f"{settings.public_base_url}/s/{share_key}"


def summary_lines(messages: list[dict], offers: list[dict]) -> list[str]:
    """Özet için konuşmanın düz metin dökümü (mesajlar + pazarlık teklifleri)."""
    lines = [f"{m['from']} ({m['source_lang']}): {m['original']}" for m in messages]
    for o in offers:
        lines.append(f"PRICE OFFER by {o['by']}: {o['amount']:g} {o['currency']} -> {o['status']}")
    return lines


@dataclass
class Session:
    id: str
    join_key: str
    merchant_id: int
    business_name: str
    merchant_lang: str
    conversation_id: int
    share_key: str
    guest_lang: str = "en"
    created_at: float = field(default_factory=time.time)
    merchant_ws: WebSocket | None = None
    guest_ws: set[WebSocket] = field(default_factory=set)
    history: list[dict] = field(default_factory=list)
    offers: list[dict] = field(default_factory=list)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    _ids: itertools.count = field(default_factory=lambda: itertools.count(1))
    _offer_ids: itertools.count = field(default_factory=lambda: itertools.count(1))

    @property
    def join_expires_at(self) -> float:
        return self.created_at + settings.session_ttl_minutes * 60

    @property
    def join_url(self) -> str:
        return f"{settings.public_base_url}/g/{self.id}?k={self.join_key}"

    def lang_of(self, role: str) -> str:
        return self.merchant_lang if role == MERCHANT else self.guest_lang

    def other(self, role: str) -> str:
        return GUEST if role == MERCHANT else MERCHANT

    def sockets(self, role: str) -> list[WebSocket]:
        if role == MERCHANT:
            return [self.merchant_ws] if self.merchant_ws else []
        return list(self.guest_ws)

    def state(self) -> dict:
        return {
            "type": "state",
            "session_id": self.id,
            "business_name": self.business_name,
            "merchant_lang": self.merchant_lang,
            "guest_lang": self.guest_lang,
            "guest_connected": bool(self.guest_ws),
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
        await self.send(MERCHANT, payload)
        await self.send(GUEST, payload)

    # --- konuşma akışı -------------------------------------------------------

    def _quota_left(self) -> bool:
        return db.used_seconds(self.merchant_id) < settings.trial_minutes * 60

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
        if not self._quota_left():
            await self.broadcast({"type": "error", "code": "quota_exceeded"})
            return

        async with self.lock:
            await self.broadcast({"type": "processing", "from": role})
            try:
                t = await ai.transcribe(wav, duration)
                db.add_usage(self.merchant_id, t.duration)
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

        Esnaf ve turist yan yana durduğu için bir telefonun mikrofonu diğer tarafın
        sesini de duyabilir. Turist telefonundan esnafın dili gelirse (ya da tersi)
        bu yankıdır, çevrilmez. Turist yeni bir dilde konuşursa dili otomatik değişir.
        """
        if detected is None or self.merchant_lang == self.guest_lang:
            return True
        other_lang = self.lang_of(self.other(role))
        if detected == other_lang:
            return False
        if role == GUEST and detected != self.guest_lang and languages.is_supported(detected):
            await self.handle_set_guest_lang(detected)
        return True

    async def handle_text(self, role: str, text: str) -> None:
        text = text.strip()[:1000]
        if not text:
            return
        async with self.lock:
            await self.broadcast({"type": "processing", "from": role})
            try:
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

    async def handle_price(self, amount: float, cur: str) -> None:
        if cur not in currency.CURRENCIES or not (0 < amount < 10_000_000):
            return
        conversions = await currency.convert(amount, cur)
        await self.broadcast({"type": "price", "amount": amount, "currency": cur, "conversions": conversions})

    async def handle_set_guest_lang(self, lang: str) -> None:
        if languages.is_supported(lang) and lang != self.guest_lang:
            self.guest_lang = lang
            db.update_conversation(self.conversation_id, guest_lang=lang)
            await self.broadcast(self.state())

    # --- pazarlık modu -------------------------------------------------------

    async def handle_offer(self, role: str, amount: float, cur: str) -> None:
        """Teklif ya da karşı teklif. Yeni teklif, açıktaki teklifi 'countered' yapar."""
        if cur not in currency.CURRENCIES or not (0 < amount < 10_000_000):
            return
        conversions = await currency.convert(amount, cur)
        for o in self.offers:
            if o["status"] == "open":
                o["status"] = "countered"
                await self.broadcast({"type": "offer", **o})
        offer = {
            "id": next(self._offer_ids),
            "by": role,
            "amount": amount,
            "currency": cur,
            "conversions": conversions,
            "status": "open",
            "ts": int(time.time()),
        }
        self.offers.append(offer)
        db.update_conversation(self.conversation_id, offers=self.offers)
        await self.broadcast({"type": "offer", **offer})

    async def handle_offer_reply(self, role: str, offer_id: int, accept: bool) -> None:
        """Teklifi yalnızca karşı taraf kabul ya da reddedebilir."""
        o = next((o for o in self.offers if o["id"] == offer_id), None)
        if o is None or o["status"] != "open" or o["by"] == role:
            return
        o["status"] = "accepted" if accept else "rejected"
        db.update_conversation(self.conversation_id, offers=self.offers)
        await self.broadcast({"type": "offer", **o})

    # --- bitiş -----------------------------------------------------------------

    def finalize(self) -> None:
        """Konuşmayı veritabanında kapatır; hiçbir şey konuşulmadıysa kaydı siler."""
        if not self.history and not self.offers:
            db.delete_conversation(self.merchant_id, self.conversation_id)
        else:
            db.update_conversation(self.conversation_id, guest_lang=self.guest_lang, ended=True)

    async def end(self, with_summary: bool) -> None:
        url = share_url(self.share_key) if (self.history or self.offers) else None
        if with_summary and (self.history or self.offers):
            try:
                summary = await ai.summarize(
                    summary_lines(self.history, self.offers), self.merchant_lang, self.guest_lang
                )
                db.update_conversation(self.conversation_id, summary=summary)
                await self.broadcast({"type": "summary", "texts": summary, "share_url": url})
            except Exception as e:
                log.exception("Özet oluşturulamadı")
                await self.send(MERCHANT, {"type": "error", "code": "summary_failed", "detail": str(e)})
        self.finalize()
        await self.broadcast({"type": "ended", "share_url": url})
        for ws in self.sockets(MERCHANT) + self.sockets(GUEST):
            try:
                await ws.close()
            except Exception:
                pass


class SessionManager:
    def __init__(self) -> None:
        self._sessions: dict[str, Session] = {}

    def create(self, merchant_id: int, business_name: str, merchant_lang: str) -> Session:
        share_key = secrets.token_urlsafe(12)
        guest_lang = "en"
        s = Session(
            id=secrets.token_urlsafe(8),
            join_key=secrets.token_urlsafe(12),
            merchant_id=merchant_id,
            business_name=business_name,
            merchant_lang=merchant_lang,
            conversation_id=db.create_conversation(merchant_id, share_key, merchant_lang, guest_lang),
            share_key=share_key,
            guest_lang=guest_lang,
        )
        self._sessions[s.id] = s
        return s

    def get(self, session_id: str) -> Session | None:
        return self._sessions.get(session_id)

    def remove(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)

    def cleanup(self) -> None:
        now = time.time()
        for sid, s in list(self._sessions.items()):
            idle = s.merchant_ws is None and not s.guest_ws
            if now - s.created_at > MAX_SESSION_SECONDS or (idle and now > s.join_expires_at):
                s.finalize()  # bitir'e basılmadan bırakılan konuşmalar da geçmişe düşsün
                self.remove(sid)


manager = SessionManager()
