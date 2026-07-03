import asyncio
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon.errors import SessionPasswordNeededError

# کلاینت موقت رو تا پایان فرآیند لاگین نگه می‌داریم
_pending = {}

def _ensure_event_loop():
    """
    اطمینان از اینکه ترد فعلی دارای یک event loop است.
    برای کارکرد صحیح Telethon در تردهای مختلف Chaquopy ضروری است.
    """
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def request_code(api_id: str, api_hash: str, phone: str) -> str:
    """
    مرحله ۱: کد تایید رو به شماره ارسال می‌کنه.
    خروجی: "OK" یا "ERROR: <پیام>"
    """
    _ensure_event_loop()
    try:
        client = TelegramClient(StringSession(), int(api_id), api_hash)
        client.connect()
        sent = client.send_code_request(phone)
        _pending['client'] = client
        _pending['phone'] = phone
        _pending['phone_code_hash'] = sent.phone_code_hash
        return "OK"
    except Exception as e:
        return f"ERROR: {e}"

def submit_code(code: str) -> str:
    """
    مرحله ۲: کد وارد شده توسط کاربر رو تایید می‌کنه.
    خروجی: session_string در صورت موفقیت،
            "NEED_PASSWORD" اگه تایید دومرحله‌ای فعال باشه،
            یا "ERROR: <پیام>"
    """
    _ensure_event_loop()
    client = _pending.get('client')
    if client is None:
        return "ERROR: no pending login, request_code first"
    try:
        client.sign_in(
            phone=_pending['phone'],
            code=code,
            phone_code_hash=_pending['phone_code_hash']
        )
        session_str = client.session.save()
        client.disconnect()
        _pending.clear()
        return session_str
    except SessionPasswordNeededError:
        return "NEED_PASSWORD"
    except Exception as e:
        return f"ERROR: {e}"

def submit_password(password: str) -> str:
    """
    مرحله ۳ (اختیاری): در صورت فعال بودن تایید دومرحله‌ای.
    خروجی: session_string یا "ERROR: <پیام>"
    """
    _ensure_event_loop()
    client = _pending.get('client')
    if client is None:
        return "ERROR: no pending login"
    try:
        client.sign_in(password=password)
        session_str = client.session.save()
        client.disconnect()
        _pending.clear()
        return session_str
    except Exception as e:
        return f"ERROR: {e}"
