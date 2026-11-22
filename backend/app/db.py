import json
import sqlite3
import time
from contextlib import contextmanager

from .config import settings

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name  TEXT NOT NULL,
    plan          TEXT NOT NULL DEFAULT 'trial',
    created_at    INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS usage (
    user_id INTEGER NOT NULL REFERENCES users(id),
    month   TEXT NOT NULL,          -- 'YYYY-MM'
    seconds REAL NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, month)
);
-- Konuşma geçmişi: iki katılımcı da kendi geçmişinde görür; biri silince yalnızca ondan gizlenir.
CREATE TABLE IF NOT EXISTS conversations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id       INTEGER NOT NULL REFERENCES users(id),
    guest_id      INTEGER REFERENCES users(id),
    host_lang     TEXT NOT NULL,
    guest_lang    TEXT NOT NULL,
    started_at    INTEGER NOT NULL,
    ended_at      INTEGER,
    summary_json  TEXT,                 -- {"tr": "...", "en": "..."}
    host_deleted  INTEGER NOT NULL DEFAULT 0,
    guest_deleted INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS conversation_messages (
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    seq             INTEGER NOT NULL,
    sender          TEXT NOT NULL,      -- 'host' | 'guest'
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


def _tables(c) -> set[str]:
    return {r["name"] for r in c.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}


def _migrate_from_merchant_schema(c) -> None:
    """İlk (esnaf/turist) sürümün veritabanını genel kullanıma taşır.

    Hesaplar ve kullanım korunur; eski konuşma kayıtları yeni yapıya uymadığı için silinir.
    """
    tables = _tables(c)
    if "merchants" in tables and "users" not in tables:
        c.execute("ALTER TABLE merchants RENAME TO users")
        c.execute("ALTER TABLE users RENAME COLUMN business_name TO display_name")
    if "usage" in tables:
        cols = {r["name"] for r in c.execute("PRAGMA table_info(usage)")}
        if "merchant_id" in cols:
            c.execute("ALTER TABLE usage RENAME COLUMN merchant_id TO user_id")
    if "conversations" in tables:
        cols = {r["name"] for r in c.execute("PRAGMA table_info(conversations)")}
        if "merchant_id" in cols:
            c.execute("DROP TABLE IF EXISTS conversation_messages")
            c.execute("DROP TABLE conversations")
    c.execute("DROP TABLE IF EXISTS phrases")


def init_db() -> None:
    with connect() as c:
        c.execute("PRAGMA foreign_keys = OFF")
        _migrate_from_merchant_schema(c)
        c.executescript(SCHEMA)
        c.execute("PRAGMA foreign_keys = ON")


# --- kullanıcılar ---------------------------------------------------------------

def create_user(email: str, password_hash: str, display_name: str) -> int:
    with connect() as c:
        cur = c.execute(
            "INSERT INTO users (email, password_hash, display_name, created_at) VALUES (?, ?, ?, ?)",
            (email, password_hash, display_name, int(time.time())),
        )
        return cur.lastrowid


def get_user_by_email(email: str) -> sqlite3.Row | None:
    with connect() as c:
        return c.execute("SELECT * FROM users WHERE email = ?", (email,)).fetchone()


def get_user(user_id: int) -> sqlite3.Row | None:
    with connect() as c:
        return c.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()


def update_display_name(user_id: int, display_name: str) -> None:
    with connect() as c:
        c.execute("UPDATE users SET display_name = ? WHERE id = ?", (display_name, user_id))


# --- kullanım kotası ------------------------------------------------------------

def _month() -> str:
    return time.strftime("%Y-%m")


def add_usage(user_id: int, seconds: float) -> None:
    with connect() as c:
        c.execute(
            "INSERT INTO usage (user_id, month, seconds) VALUES (?, ?, ?) "
            "ON CONFLICT(user_id, month) DO UPDATE SET seconds = seconds + excluded.seconds",
            (user_id, _month(), seconds),
        )


def used_seconds(user_id: int) -> float:
    with connect() as c:
        row = c.execute(
            "SELECT seconds FROM usage WHERE user_id = ? AND month = ?", (user_id, _month())
        ).fetchone()
        return row["seconds"] if row else 0.0


# --- konuşma geçmişi ------------------------------------------------------------

def create_conversation(host_id: int, host_lang: str, guest_lang: str) -> int:
    with connect() as c:
        cur = c.execute(
            "INSERT INTO conversations (host_id, host_lang, guest_lang, started_at) VALUES (?, ?, ?, ?)",
            (host_id, host_lang, guest_lang, int(time.time())),
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


def update_conversation(conversation_id: int, *, guest_id: int | None = None, guest_lang: str | None = None,
                        ended: bool = False, summary: dict | None = None) -> None:
    sets, args = [], []
    if guest_id is not None:
        sets.append("guest_id = ?"); args.append(guest_id)
    if guest_lang is not None:
        sets.append("guest_lang = ?"); args.append(guest_lang)
    if ended:
        sets.append("ended_at = ?"); args.append(int(time.time()))
    if summary is not None:
        sets.append("summary_json = ?"); args.append(json.dumps(summary, ensure_ascii=False))
    if not sets:
        return
    with connect() as c:
        c.execute(f"UPDATE conversations SET {', '.join(sets)} WHERE id = ?", (*args, conversation_id))


def _names(c, ids) -> dict[int, str]:
    ids = [i for i in ids if i is not None]
    if not ids:
        return {}
    marks = ",".join("?" * len(ids))
    return {r["id"]: r["display_name"] for r in c.execute(f"SELECT id, display_name FROM users WHERE id IN ({marks})", ids)}


def _conversation_out(row: sqlite3.Row, names: dict[int, str], messages=None) -> dict:
    out = {
        "id": row["id"],
        "host_id": row["host_id"],
        "guest_id": row["guest_id"],
        "host_name": names.get(row["host_id"], ""),
        "guest_name": names.get(row["guest_id"], ""),
        "host_lang": row["host_lang"],
        "guest_lang": row["guest_lang"],
        "started_at": row["started_at"],
        "ended_at": row["ended_at"],
        "summary": json.loads(row["summary_json"]) if row["summary_json"] else None,
    }
    if messages is not None:
        out["messages"] = [
            {"id": m["seq"], "from": m["sender"], "source_lang": m["source_lang"], "target_lang": m["target_lang"],
             "original": m["original"], "translated": m["translated"], "ts": m["ts"]}
            for m in messages
        ]
    return out


def list_conversations(user_id: int, limit: int = 100) -> list[dict]:
    with connect() as c:
        rows = c.execute(
            "SELECT conv.*, (SELECT COUNT(*) FROM conversation_messages WHERE conversation_id = conv.id) AS n "
            "FROM conversations conv "
            "WHERE (host_id = ? AND host_deleted = 0) OR (guest_id = ? AND guest_deleted = 0) "
            "ORDER BY started_at DESC, id DESC LIMIT ?",
            (user_id, user_id, limit),
        ).fetchall()
        names = _names(c, {r["host_id"] for r in rows} | {r["guest_id"] for r in rows})
    return [{**_conversation_out(r, names), "message_count": r["n"]} for r in rows]


def get_conversation(conversation_id: int) -> dict | None:
    with connect() as c:
        row = c.execute("SELECT * FROM conversations WHERE id = ?", (conversation_id,)).fetchone()
        if row is None:
            return None
        msgs = c.execute(
            "SELECT * FROM conversation_messages WHERE conversation_id = ? ORDER BY seq", (conversation_id,)
        ).fetchall()
        out = _conversation_out(row, _names(c, [row["host_id"], row["guest_id"]]), msgs)
        out["host_deleted"] = bool(row["host_deleted"])
        out["guest_deleted"] = bool(row["guest_deleted"])
        return out


def hide_conversation(conversation_id: int, user_id: int) -> None:
    """Konuşmayı bu kullanıcının geçmişinden kaldırır; iki taraf da sildiyse kaydı tamamen siler."""
    with connect() as c:
        c.execute("UPDATE conversations SET host_deleted = 1 WHERE id = ? AND host_id = ?", (conversation_id, user_id))
        c.execute("UPDATE conversations SET guest_deleted = 1 WHERE id = ? AND guest_id = ?", (conversation_id, user_id))
        c.execute(
            "DELETE FROM conversations WHERE id = ? AND host_deleted = 1 AND (guest_id IS NULL OR guest_deleted = 1)",
            (conversation_id,),
        )


def delete_conversation(conversation_id: int) -> None:
    with connect() as c:
        c.execute("DELETE FROM conversations WHERE id = ?", (conversation_id,))
