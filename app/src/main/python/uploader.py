import asyncio
import json
import socks
from telethon.sync import TelegramClient
from telethon.sessions import StringSession

def _ensure_event_loop():
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def _parse_proxy(proxy_str):
    if not proxy_str:
        return None
    try:
        # Expected format: socks5h://127.0.0.1:10808 or http://127.0.0.1:10809
        prefix, rest = proxy_str.split("://")
        addr, port = rest.split(":")
        
        proxy_type = socks.SOCKS5 if "socks5" in prefix else socks.HTTP
        rdns = True if "socks5h" in prefix else False
        
        return {
            'proxy_type': proxy_type,
            'addr': addr,
            'port': int(port),
            'rdns': rdns
        }
    except:
        return None

def upload_to_telegram(session_str, api_id, api_hash, file_paths_json, caption, destination, proxy=None):
    """
    آپلود فایل یا لیستی از فایل‌ها (Media Group) به مقصد مشخص شده
    proxy: رشته پورکسی شناسایی شده (مثلاً socks5h://127.0.0.1:10808)
    """
    _ensure_event_loop()
    try:
        # پارس کردن مسیر فایل‌ها
        try:
            file_data = json.loads(file_paths_json)
            if isinstance(file_data, list):
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

        client_proxy = _parse_proxy(proxy)

        with TelegramClient(StringSession(session_str), int(api_id), api_hash, proxy=client_proxy) as client:
            if len(files_to_send) == 1:
                client.send_file(target, files_to_send[0], caption=caption)
            elif len(files_to_send) > 1:
                client.send_file(target, files_to_send, caption=caption)
            else:
                return "ERROR: No files to upload"
        return "OK"
    except Exception as e:
        return f"ERROR: {str(e)}"
