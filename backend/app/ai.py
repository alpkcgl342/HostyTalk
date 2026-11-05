"""OpenAI (ChatGPT) köprüsü: konuşmadan metne -> çeviri -> metinden sese zinciri."""

import io
import json
from dataclasses import dataclass

from openai import AsyncOpenAI

from . import languages
from .config import settings

_client: AsyncOpenAI | None = None


def client() -> AsyncOpenAI:
    global _client
    if _client is None:
        if not settings.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY tanımlı değil (.env dosyasını kontrol edin)")
        _client = AsyncOpenAI(api_key=settings.openai_api_key)
    return _client


@dataclass
class Transcript:
    text: str
    language: str | None  # 'tr', 'en'... model dili bildiremezse None
    duration: float       # saniye


async def transcribe(wav: bytes, duration_hint: float) -> Transcript:
    audio = io.BytesIO(wav)
    audio.name = "speech.wav"
    if settings.stt_model == "whisper-1":
        # verbose_json yalnızca whisper-1'de var ve algılanan dili döndürür.
        r = await client().audio.transcriptions.create(
            model=settings.stt_model, file=audio, response_format="verbose_json"
        )
        lang = languages.normalize(r.language, default="") or None
        return Transcript(r.text.strip(), lang, float(r.duration or duration_hint))
    r = await client().audio.transcriptions.create(model=settings.stt_model, file=audio)
    return Transcript(r.text.strip(), None, duration_hint)


_TRANSLATE_PROMPT = """You are HostyTalk, a live interpreter between a shop/hotel/restaurant owner in Türkiye and a tourist.
Translate the user's message from {src} to {tgt}.
Rules:
- Output ONLY the translation, nothing else (no quotes, no notes).
- Keep the speaker's tone and politeness level; sound natural and spoken, not literal.
- Keep numbers, prices, currencies, product and place names exact.
- Short greetings and fillers should stay short."""


async def translate(text: str, src: str, tgt: str) -> str:
    if src == tgt:
        return text
    r = await client().chat.completions.create(
        model=settings.chat_model,
        temperature=0.2,
        messages=[
            {"role": "system", "content": _TRANSLATE_PROMPT.format(
                src=languages.english_name(src), tgt=languages.english_name(tgt))},
            {"role": "user", "content": text},
        ],
    )
    return (r.choices[0].message.content or "").strip()


async def speak(text: str, lang: str) -> bytes:
    kwargs = {}
    if settings.tts_model.startswith("gpt-"):
        kwargs["instructions"] = (
            f"Speak in {languages.english_name(lang)} with a warm, friendly, clear tone, like a helpful host."
        )
    r = await client().audio.speech.create(
        model=settings.tts_model, voice=settings.tts_voice, input=text, response_format="mp3", **kwargs
    )
    return r.content


_SUMMARY_PROMPT = """You summarize a conversation between a merchant and a tourist in Türkiye.
Write a short "what we agreed on" note: items, quantities, prices (with currency), delivery/shipping, times, names.
If nothing concrete was agreed, say so briefly.
Return JSON: {{"{a}": "<summary in {a_name}>", "{b}": "<same summary in {b_name}>"}}"""


async def summarize(lines: list[str], merchant_lang: str, guest_lang: str) -> dict[str, str]:
    keys = [merchant_lang] if merchant_lang == guest_lang else [merchant_lang, guest_lang]
    b = keys[-1]
    r = await client().chat.completions.create(
        model=settings.chat_model,
        temperature=0.2,
        response_format={"type": "json_object"},
        messages=[
            {"role": "system", "content": _SUMMARY_PROMPT.format(
                a=merchant_lang, a_name=languages.english_name(merchant_lang),
                b=b, b_name=languages.english_name(b))},
            {"role": "user", "content": "\n".join(lines) or "(empty conversation)"},
        ],
    )
    data = json.loads(r.choices[0].message.content or "{}")
    return {k: str(data.get(k, "")) for k in keys}
