"""Desteklenen diller. Yeni dil eklemek için bu tabloya bir satır eklemek yeterli."""

LANGUAGES: dict[str, dict[str, str]] = {
    "tr": {"name": "Türkçe", "english": "Turkish", "whisper": "turkish"},
    "en": {"name": "English", "english": "English", "whisper": "english"},
    "ar": {"name": "العربية", "english": "Arabic", "whisper": "arabic"},
    "fr": {"name": "Français", "english": "French", "whisper": "french"},
    "ru": {"name": "Русский", "english": "Russian", "whisper": "russian"},
}

_WHISPER_TO_CODE = {v["whisper"]: k for k, v in LANGUAGES.items()}


def is_supported(code: str | None) -> bool:
    return code in LANGUAGES


def normalize(code: str | None, default: str = "en") -> str:
    """'fr-FR', 'FR', 'french' gibi değerleri 'fr' koduna indirger."""
    if not code:
        return default
    c = code.strip().lower()
    if c in _WHISPER_TO_CODE:
        return _WHISPER_TO_CODE[c]
    c = c.split("-")[0].split("_")[0]
    return c if c in LANGUAGES else default


def english_name(code: str) -> str:
    return LANGUAGES[code]["english"]
