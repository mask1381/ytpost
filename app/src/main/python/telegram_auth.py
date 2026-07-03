import asyncio
import socks
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon.errors import SessionPasswordNeededError

# کلاینت موقت رو تا پایان فرآیند لاگین نگه می‌داریم
_pending = {}

# --- تنظیمات پروکسی ---
# اگر فیلترشکن شما (مثل v2ray) روی گوشی پورت SOCKS5 باز می‌کند، مقادیر زیر را تنظیم کنید.
# معمولاً: (socks.SOCKS5, '127.0.0.1', 10808)
# اگر نمی‌خواهید از پروکسی داخلی استفاده کنید، این را None بگذارید.
PROXY = None 
# مثال برای فعال‌سازی: PROXY = (socks.SOCKS5, '127.0.0.1', 10808)

def _ensure_event_loop():
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def request_code(api_id: str, api_hash: str, phone: str) -> str:
    _ensure_event_loop()
    try:
        if not api_id.strip().isdigit():
            return "ERROR: API ID باید فقط عدد باشد."
            
        client = TelegramClient(
            StringSession(), 
            int(api_id), 
            api_hash,
            proxy=PROXY,
            connection_retries=2,
            timeout=15
        )
        
        client.connect()
        
        if not client.is_user_authorized():
            sent = client.send_code_request(phone)
            _pending['client'] = client
            _pending['phone'] = phone
            _pending['phone_code_hash'] = sent.phone_code_hash
            return "OK"
        else:
            return "ALREADY_AUTHORIZED"
    except ConnectionError:
        return "ERROR: قطع اتصال. فیلترشکن خود را بررسی کنید یا در فایل telegram_auth.py پروکسی ست کنید."
    except Exception as e:
        return f"ERROR: {str(e)}"

def submit_code(code: str) -> str:
    _ensure_event_loop()
    client = _pending.get('client')
    if client is None:
        return "ERROR: جلسه‌ی فعالی یافت نشد."
    try:
        client.sign_in(phone=_pending['phone'], code=code, phone_code_hash=_pending['phone_code_hash'])
        session_str = client.session.save()
        client.disconnect()
        _pending.clear()
        return session_str
    except SessionPasswordNeededError:
        return "NEED_PASSWORD"
    except Exception as e:
        return f"ERROR: {str(e)}"

def submit_password(password: str) -> str:
    _ensure_event_loop()
    client = _pending.get('client')
    if client is None:
        return "ERROR: جلسه‌ی فعالی یافت نشد."
    try:
        client.sign_in(password=password)
        session_str = client.session.save()
        client.disconnect()
        _pending.clear()
        return session_str
    except Exception as e:
        return f"ERROR: {str(e)}"
