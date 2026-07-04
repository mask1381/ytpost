import asyncio
from telethon.sync import TelegramClient
from telethon.sessions import StringSession

def _ensure_event_loop():
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def upload_to_telegram(session_str, api_id, api_hash, file_path, caption, destination):
    """
    آپلود فایل به مقصد مشخص شده (chat_id عددی یا username متنی)
    """
    _ensure_event_loop()
    try:
        # تشخیص اینکه آیا مقصد ID عددی است یا Username
        try:
            if destination.startswith('-') or destination.isdigit():
                target = int(destination)
            else:
                target = destination
        except ValueError:
            target = destination

        with TelegramClient(StringSession(session_str), int(api_id), api_hash) as client:
            # ارسال فایل؛ Telethon به طور خودکار نوع فایل (ویدیو/عکس) را تشخیص می‌دهد
            client.send_file(target, file_path, caption=caption)
        return "OK"
    except Exception as e:
        return f"ERROR: {str(e)}"
