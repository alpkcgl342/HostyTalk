import hashlib
import hmac
import os
import time

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import db
from .config import settings

_PBKDF2_ROUNDS = 200_000
_TOKEN_TTL_SECONDS = 30 * 24 * 3600


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _PBKDF2_ROUNDS)
    return f"{salt.hex()}${digest.hex()}"


def verify_password(password: str, stored: str) -> bool:
    salt_hex, digest_hex = stored.split("$", 1)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), _PBKDF2_ROUNDS)
    return hmac.compare_digest(digest.hex(), digest_hex)


def create_token(user) -> str:
    # "ver": şifre değişince artar; eski token'lar (başka cihazlardaki oturumlar) geçersiz olur.
    payload = {"sub": str(user["id"]), "ver": user["token_version"], "exp": int(time.time()) + _TOKEN_TTL_SECONDS}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def user_from_token(token: str):
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        user = db.get_user(int(payload["sub"]))
    except (jwt.PyJWTError, KeyError, ValueError):
        return None
    # "ver" olmayan token'lar bu özellikten önce verildi; sürüm 0 sayılır.
    if user is None or payload.get("ver", 0) != user["token_version"]:
        return None
    return user


_bearer = HTTPBearer(auto_error=False)


def current_user(creds: HTTPAuthorizationCredentials | None = Depends(_bearer)):
    user = user_from_token(creds.credentials) if creds else None
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid_token")
    return user
