import json
import sqlite3
import time
from contextlib import contextmanager

from .config import settings

SCHEMA = """
CREATE TABLE IF NOT EXISTS merchants (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    business_name TEXT NOT NULL,
    business_type TEXT NOT NULL DEFAULT 'other',
    plan          TEXT NOT NULL DEFAULT 'trial',
    created_at    INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS usage (
    merchant_id INTEGER NOT NULL REFERENCES merchants(id),
    month       TEXT NOT NULL,          -- 'YYYY-MM'
    seconds     REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (merchant_id, month)
);
-- Esnafın kendi eklediği hazır cümleler
CREATE TABLE IF NOT EXISTS phrases (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    merchant_id INTEGER NOT NULL REFERENCES merchants(id),
    lang        TEXT NOT NULL,
    text        TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);
-- Konuşma geçmişi; share_key ile özet sayfası turistle paylaşılır
CREATE TABLE IF NOT EXISTS conversations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    merchant_id   INTEGER NOT NULL REFERENCES merchants(id),
    share_key     TEXT NOT NULL UNIQUE,
    merchant_lang TEXT NOT NULL,
    guest_lang    TEXT NOT NULL,
    started_at    INTEGER NOT NULL,
    ended_at      INTEGER,
    summary_json  TEXT,                 -- {"tr": "...", "en": "..."}
    offers_json   TEXT NOT NULL DEFAULT '[]'
);
CREATE TABLE IF NOT EXISTS conversation_messages (
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    seq             INTEGER NOT NULL,
    sender          TEXT NOT NULL,      -- 'merchant' | 'guest'
    source_lang     TEXT NOT NULL,
    target_lang     TEXT NOT NULL,
    original        TEXT NOT NULL,
    translated      TEXT NOT NULL,
    ts              INTEGER NOT NULL,
    PRIMARY KEY (conversation_id, seq)
);
"""


@contextmanager
def connect():
    conn = sqlite3.connect(settings.database_path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with connect() as c:
        c.executescript(SCHEMA)
        # Eski veritabanlarına sonradan eklenen sütunlar
        cols = {r["name"] for r in c.execute("PRAGMA table_info(merchants)")}
        if "business_type" not in cols:
            c.execute("ALTER TABLE merchants ADD COLUMN business_type TEXT NOT NULL DEFAULT 'other'")


# --- esnaf --------------------------------------------------------------------

def create_merchant(email: str, password_hash: str, business_name: str, business_type: str = "other") -> int:
    with connect() as c:
        cur = c.execute(
            "INSERT INTO merchants (email, password_hash, business_name, business_type, created_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (email, password_hash, business_name, business_type, int(time.time())),
        )
        return cur.lastrowid


def get_merchant_by_email(email: str) -> sqlite3.Row | None:
    with connect() as c:
        return c.execute("SELECT * FROM merchants WHERE email = ?", (email,)).fetchone()


def get_merchant(merchant_id: int) -> sqlite3.Row | None:
    with connect() as c:
        return c.execute("SELECT * FROM merchants WHERE id = ?", (merchant_id,)).fetchone()


def update_merchant(merchant_id: int, **fields) -> None:
    allowed = {k: v for k, v in fields.items() if k in ("business_name", "business_type") and v is not None}
    if not allowed:
        return
    sets = ", ".join(f"{k} = ?" for k in allowed)
    with connect() as c:
        c.execute(f"UPDATE merchants SET {sets} WHERE id = ?", (*allowed.values(), merchant_id))


# --- kullanım kotası ------------------------------------------------------------

def _month() -> str:
    return time.strftime("%Y-%m")


def add_usage(merchant_id: int, seconds: float) -> None:
    with connect() as c:
        c.execute(
            "INSERT INTO usage (merchant_id, month, seconds) VALUES (?, ?, ?) "
            "ON CONFLICT(merchant_id, month) DO UPDATE SET seconds = seconds + excluded.seconds",
            (merchant_id, _month(), seconds),
        )


def used_seconds(merchant_id: int) -> float:
    with connect() as c:
        row = c.execute(
            "SELECT seconds FROM usage WHERE merchant_id = ? AND month = ?", (merchant_id, _month())
        ).fetchone()
        return row["seconds"] if row else 0.0


# --- hazır cümleler -------------------------------------------------------------

def list_phrases(merchant_id: int, lang: str) -> list[sqlite3.Row]:
    with connect() as c:
        return c.execute(
            "SELECT id, text FROM phrases WHERE merchant_id = ? AND lang = ? ORDER BY id",
            (merchant_id, lang),
        ).fetchall()


def add_phrase(merchant_id: int, lang: str, text: str) -> int:
    with connect() as c:
        cur = c.execute(
            "INSERT INTO phrases (merchant_id, lang, text, created_at) VALUES (?, ?, ?, ?)",
            (merchant_id, lang, text, int(time.time())),
        )
        return cur.lastrowid


def delete_phrase(merchant_id: int, phrase_id: int) -> bool:
    with connect() as c:
        cur = c.execute("DELETE FROM phrases WHERE id = ? AND merchant_id = ?", (phrase_id, merchant_id))
        return cur.rowcount > 0


# --- konuşma geçmişi ------------------------------------------------------------

def create_conversation(merchant_id: int, share_key: str, merchant_lang: str, guest_lang: str) -> int:
    with connect() as c:
        cur = c.execute(
            "INSERT INTO conversations (merchant_id, share_key, merchant_lang, guest_lang, started_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (merchant_id, share_key, merchant_lang, guest_lang, int(time.time())),
        )
        return cur.lastrowid


def add_message(conversation_id: int, m: dict) -> None:
    with connect() as c:
        c.execute(
            "INSERT INTO conversation_messages "
            "(conversation_id, seq, sender, source_lang, target_lang, original, translated, ts) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (conversation_id, m["id"], m["from"], m["source_lang"], m["target_lang"],
             m["original"], m["translated"], m["ts"]),
        )


def update_conversation(conversation_id: int, *, guest_lang: str | None = None, ended: bool = False,
                        summary: dict | None = None, offers: list | None = None) -> None:
    sets, args = [], []
    if guest_lang is not None:
        sets.append("guest_lang = ?"); args.append(guest_lang)
    if ended:
        sets.append("ended_at = ?"); args.append(int(time.time()))
    if summary is not None:
        sets.append("summary_json = ?"); args.append(json.dumps(summary, ensure_ascii=False))
    if offers is not None:
        sets.append("offers_json = ?"); args.append(json.dumps(offers, ensure_ascii=False))
    if not sets:
        return
    with connect() as c:
        c.execute(f"UPDATE conversations SET {', '.join(sets)} WHERE id = ?", (*args, conversation_id))


def _conversation_out(row: sqlite3.Row, messages: list[sqlite3.Row] | None = None) -> dict:
    out = {
        "id": row["id"],
        "share_key": row["share_key"],
        "merchant_lang": row["merchant_lang"],
        "guest_lang": row["guest_lang"],
        "started_at": row["started_at"],
        "ended_at": row["ended_at"],
        "summary": json.loads(row["summary_json"]) if row["summary_json"] else None,
        "offers": json.loads(row["offers_json"] or "[]"),
    }
    if messages is not None:
        out["messages"] = [
            {"id": m["seq"], "from": m["sender"], "source_lang": m["source_lang"], "target_lang": m["target_lang"],
             "original": m["original"], "translated": m["translated"], "ts": m["ts"]}
            for m in messages
        ]
    return out


def list_conversations(merchant_id: int, limit: int = 100) -> list[dict]:
    with connect() as c:
        rows = c.execute(
            "SELECT conv.*, (SELECT COUNT(*) FROM conversation_messages WHERE conversation_id = conv.id) AS n "
            "FROM conversations conv WHERE merchant_id = ? ORDER BY started_at DESC, id DESC LIMIT ?",
            (merchant_id, limit),
        ).fetchall()
    return [{**_conversation_out(r), "message_count": r["n"]} for r in rows]


def _get_conversation(where: str, value) -> dict | None:
    with connect() as c:
        row = c.execute(f"SELECT * FROM conversations WHERE {where} = ?", (value,)).fetchone()
        if row is None:
            return None
        msgs = c.execute(
            "SELECT * FROM conversation_messages WHERE conversation_id = ? ORDER BY seq", (row["id"],)
        ).fetchall()
        return {**_conversation_out(row, msgs), "merchant_id": row["merchant_id"]}


def get_conversation(conversation_id: int) -> dict | None:
    return _get_conversation("id", conversation_id)


def get_conversation_by_share_key(share_key: str) -> dict | None:
    return _get_conversation("share_key", share_key)


def delete_conversation(merchant_id: int, conversation_id: int) -> bool:
    with connect() as c:
        cur = c.execute("DELETE FROM conversations WHERE id = ? AND merchant_id = ?", (conversation_id, merchant_id))
        return cur.rowcount > 0
