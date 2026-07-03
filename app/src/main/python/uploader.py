import asyncio
from telethon.sync import TelegramClient
from telethon.sessions import StringSession

def _ensure_event_loop():
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def upload_to_telegram(session_str, api_id, api_hash, file_path, caption):
    """
    آپلود فایل به تلگرام (Saved Messages)
    """
    _ensure_event_loop()
    try:
        with TelegramClient(StringSession(session_str), int(api_id), api_hash) as client:
            client.send_file('me', file_path, caption=caption)
        return "OK"
    except Exception as e:
        return f"ERROR: {e}"
