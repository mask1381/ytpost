import asyncio
import json
from telethon.sync import TelegramClient
from telethon.sessions import StringSession

def _ensure_event_loop():
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def upload_to_telegram(session_str, api_id, api_hash, file_paths_json, caption, destination):
    """
    آپلود فایل یا لیستی از فایل‌ها (Media Group) به مقصد مشخص شده
    file_paths_json: می‌تواند یک رشته (مسیر فایل) یا یک لیست JSON از مسیرها باشد
    """
    _ensure_event_loop()
    try:
        # پارس کردن مسیر فایل‌ها
        try:
            file_data = json.loads(file_paths_json)
            if isinstance(file_data, list):
                # اگر لیستی از [path, title] باشد، فقط مسیرها را استخراج می‌کنیم
                if len(file_data) > 0 and isinstance(file_data[0], list):
                    files_to_send = [item[0] for item in file_data]
                else:
                    files_to_send = file_data
            else:
                files_to_send = [file_data]
        except:
            files_to_send = [file_paths_json]

        # تشخیص مقصد
        try:
            if destination.startswith('-') or destination.isdigit():
                target = int(destination)
            else:
                target = destination
        except ValueError:
            target = destination

        with TelegramClient(StringSession(session_str), int(api_id), api_hash) as client:
            if len(files_to_send) == 1:
                client.send_file(target, files_to_send[0], caption=caption)
            elif len(files_to_send) > 1:
                # ارسال به صورت Media Group (Carousel)
                # نکته: کپشن فقط روی اولین آیتم قرار می‌گیرد یا جداگانه
                client.send_file(target, files_to_send, caption=caption)
            else:
                return "ERROR: No files to upload"
        return "OK"
    except Exception as e:
        return f"ERROR: {str(e)}"
