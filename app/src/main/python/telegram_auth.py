import asyncio
import socks
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon.errors import SessionPasswordNeededError, FloodWaitError
from telethon.tl.types import Channel, Chat, ChatAdminRights
import json
import time

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

def fetch_postable_chats(api_id: str, api_hash: str, session_str: str, proxy: str = None) -> str:
    _ensure_consistent_loop()
    client_proxy = _parse_proxy(proxy)
    client = TelegramClient(StringSession(session_str), int(api_id), api_hash, proxy=client_proxy)
    
    try:
        client.connect()
        if not client.is_user_authorized():
            return "ERROR: Unauthorized"
            
        dialogs = client.get_dialogs()
        postable = []
        
        for d in dialogs:
            entity = d.entity
            if not isinstance(entity, (Channel, Chat)):
                continue
                
            can_post = False
            chat_type = "group"
            
            if isinstance(entity, Channel):
                if entity.megagroup:
                    chat_type = "megagroup"
                else:
                    chat_type = "channel"
                
                # Formula: -100 + id
                full_id = int(f"-100{entity.id}")
                
                if entity.creator:
                    can_post = True
                elif entity.admin_rights and entity.admin_rights.post_messages:
                    can_post = True
                elif entity.megagroup and entity.admin_rights and entity.admin_rights.send_messages:
                    # In megagroups, post_messages is for broadcast channels, send_messages is for admins
                    can_post = True
            else:
                # Basic Chat (Group)
                full_id = -entity.id
                if entity.creator or entity.admin_rights:
                    can_post = True
                    
            if can_post:
                postable.append({
                    'id': full_id,
                    'title': d.name,
                    'type': chat_type,
                    'username': getattr(entity, 'username', None),
                    'participants_count': getattr(entity, 'participants_count', None)
                })
                
        return json.dumps(postable)
    except FloodWaitError as e:
        return f"FLOOD_WAIT:{e.seconds}"
    except Exception as e:
        return f"ERROR: {str(e)}"
    finally:
        client.disconnect()

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

def fetch_postable_chats(api_id: str, api_hash: str, session_str: str, proxy: str = None) -> str:
    _ensure_consistent_loop()
    client_proxy = _parse_proxy(proxy)
    client = TelegramClient(StringSession(session_str), int(api_id), api_hash, proxy=client_proxy)
    
    try:
        client.connect()
        if not client.is_user_authorized():
            return "ERROR: Unauthorized"
            
        dialogs = client.get_dialogs()
        postable = []
        
        for d in dialogs:
            entity = d.entity
            if not isinstance(entity, (Channel, Chat)):
                continue
                
            can_post = False
            chat_type = "group"
            
            if isinstance(entity, Channel):
                if entity.megagroup:
                    chat_type = "megagroup"
                else:
                    chat_type = "channel"
                
                # Formula: -100 + id
                full_id = int(f"-100{entity.id}")
                
                if entity.creator:
                    can_post = True
                elif entity.admin_rights and entity.admin_rights.post_messages:
                    can_post = True
                elif entity.megagroup and entity.admin_rights and entity.admin_rights.send_messages:
                    # In megagroups, post_messages is for broadcast channels, send_messages is for admins
                    can_post = True
            else:
                # Basic Chat (Group)
                full_id = -entity.id
                if entity.creator or entity.admin_rights:
                    can_post = True
                    
            if can_post:
                postable.append({
                    'id': full_id,
                    'title': d.name,
                    'type': chat_type,
                    'username': getattr(entity, 'username', None),
                    'participants_count': getattr(entity, 'participants_count', None)
                })
                
        return json.dumps(postable)
    except FloodWaitError as e:
        return f"FLOOD_WAIT:{e.seconds}"
    except Exception as e:
        return f"ERROR: {str(e)}"
    finally:
        client.disconnect()

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

def fetch_postable_chats(api_id: str, api_hash: str, session_str: str, proxy: str = None) -> str:
    _ensure_consistent_loop()
    client_proxy = _parse_proxy(proxy)
    client = TelegramClient(StringSession(session_str), int(api_id), api_hash, proxy=client_proxy)
    
    try:
        client.connect()
        if not client.is_user_authorized():
            return "ERROR: Unauthorized"
            
        dialogs = client.get_dialogs()
        postable = []
        
        for d in dialogs:
            entity = d.entity
            if not isinstance(entity, (Channel, Chat)):
                continue
                
            can_post = False
            chat_type = "group"
            
            if isinstance(entity, Channel):
                if entity.megagroup:
                    chat_type = "megagroup"
                else:
                    chat_type = "channel"
                
                # Formula: -100 + id
                full_id = int(f"-100{entity.id}")
                
                if entity.creator:
                    can_post = True
                elif entity.admin_rights and entity.admin_rights.post_messages:
                    can_post = True
                elif entity.megagroup and entity.admin_rights and entity.admin_rights.send_messages:
                    # In megagroups, post_messages is for broadcast channels, send_messages is for admins
                    can_post = True
            else:
                # Basic Chat (Group)
                full_id = -entity.id
                if entity.creator or entity.admin_rights:
                    can_post = True
                    
            if can_post:
                postable.append({
                    'id': full_id,
                    'title': d.name,
                    'type': chat_type,
                    'username': getattr(entity, 'username', None),
                    'participants_count': getattr(entity, 'participants_count', None)
                })
                
        return json.dumps(postable)
    except FloodWaitError as e:
        return f"FLOOD_WAIT:{e.seconds}"
    except Exception as e:
        return f"ERROR: {str(e)}"
    finally:
        client.disconnect()
