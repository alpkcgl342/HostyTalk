"""İki para birimli fiyat gösterimi için kur bilgisi (TL ↔ €, $, £)."""

import logging
import time

import httpx

CURRENCIES = ["TRY", "EUR", "USD", "GBP"]

# Kur servisine ulaşılamazsa kullanılan yaklaşık değerler (1 birim = ? TL).
_FALLBACK_TRY_PER_UNIT = {"TRY": 1.0, "EUR": 48.0, "USD": 42.0, "GBP": 55.0}
_RATES_URL = "https://open.er-api.com/v6/latest/TRY"
_CACHE_SECONDS = 3600

_cache: dict[str, float] = {}
_cache_at = 0.0
log = logging.getLogger(__name__)


async def try_per_unit() -> dict[str, float]:
    global _cache, _cache_at
    if _cache and time.time() - _cache_at < _CACHE_SECONDS:
        return _cache
    try:
        async with httpx.AsyncClient(timeout=5) as http:
            data = (await http.get(_RATES_URL)).json()
        rates = data["rates"]  # 1 TL = rates[X] birim X
        _cache = {c: 1.0 / float(rates[c]) for c in CURRENCIES}
        _cache_at = time.time()
    except Exception as e:  # ağ yoksa uygulama yine çalışsın
        log.warning("Kur bilgisi alınamadı, yedek değerler kullanılıyor: %s", e)
        return _cache or _FALLBACK_TRY_PER_UNIT
    return _cache


async def convert(amount: float, currency: str) -> dict[str, float]:
    rates = await try_per_unit()
    in_try = amount * rates[currency]
    return {c: round(in_try / rates[c], 2) for c in CURRENCIES}
