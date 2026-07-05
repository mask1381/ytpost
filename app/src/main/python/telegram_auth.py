import asyncio
import socks
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon.errors import SessionPasswordNeededError

# ذخیره‌ی کلاینت و لوپ برای حفظ وضعیت در تردهای مختلف اندروید
_pending = {
    'client': None,
    'loop': None,
    'phone': None,
    'phone_code_hash': None
}

def _parse_proxy(proxy_str):
    if not proxy_str:
        return None
    try:
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

def _ensure_consistent_loop():
    if _pending['loop'] is None:
        try:
            loop = asyncio.get_event_loop()
        except RuntimeError:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
        _pending['loop'] = loop
    else:
        asyncio.set_event_loop(_pending['loop'])
    return _pending['loop']

def request_code(api_id: str, api_hash: str, phone: str, proxy: str = None) -> str:
    loop = _ensure_consistent_loop()
    try:
        if not api_id.strip().isdigit():
            return "ERROR: API ID باید عدد باشد."
            
        client_proxy = _parse_proxy(proxy)
        
        client = TelegramClient(
            StringSession(), 
            int(api_id), 
            api_hash,
            proxy=client_proxy
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
    except Exception as e:
        return f"ERROR: {str(e)}"

def submit_code(code: str) -> str:
    _ensure_consistent_loop()
    client = _pending.get('client')
    if client is None:
        return "ERROR: جلسه‌ی فعالی یافت نشد. دوباره درخواست کد بدهید."
    try:
        client.sign_in(
            phone=_pending['phone'],
            code=code,
            phone_code_hash=_pending['phone_code_hash']
        )
        session_str = client.session.save()
        client.disconnect()
        _pending['client'] = None
        _pending['loop'] = None
        return session_str
    except SessionPasswordNeededError:
        return "NEED_PASSWORD"
    except Exception as e:
        return f"ERROR: {str(e)}"

def submit_password(password: str) -> str:
    _ensure_consistent_loop()
    client = _pending.get('client')
    if client is None:
        return "ERROR: جلسه‌ی فعالی یافت نشد."
    try:
        client.sign_in(password=password)
        session_str = client.session.save()
        client.disconnect()
        _pending['client'] = None
        _pending['loop'] = None
        return session_str
    except Exception as e:
        return f"ERROR: {str(e)}"
