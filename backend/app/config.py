import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    stt_model: str = os.getenv("OPENAI_STT_MODEL", "whisper-1")
    chat_model: str = os.getenv("OPENAI_CHAT_MODEL", "gpt-4o-mini")
    tts_model: str = os.getenv("OPENAI_TTS_MODEL", "gpt-4o-mini-tts")
    tts_voice: str = os.getenv("OPENAI_TTS_VOICE", "alloy")
    jwt_secret: str = os.getenv("JWT_SECRET", "dev-secret-change-me")
    public_base_url: str = os.getenv("PUBLIC_BASE_URL", "http://localhost:8000").rstrip("/")
    session_ttl_minutes: int = int(os.getenv("SESSION_TTL_MINUTES", "30"))
    trial_minutes: int = int(os.getenv("TRIAL_MINUTES", "60"))
    database_path: str = os.getenv("DATABASE_PATH", "hostytalk.db")


settings = Settings()
