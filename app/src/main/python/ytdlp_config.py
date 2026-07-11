import os
import certifi

# Set SSL certificate file
os.environ['SSL_CERT_FILE'] = certifi.where()

def get_base_opts(proxy=None, cookie_path=None):
    """
    Returns base yt-dlp options shared across preview and download.
    """
    ua = 'Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36'
    opts = {
        'proxy': proxy,
        'nocheckcertificate': True,
        'quiet': True,
        'no_warnings': True,
        'socket_timeout': 45,
        'retries': 15,
        'fragment_retries': 15,
        'force_ipv4': True,
        'ignoreerrors': True,
        'postprocessors': [],
        'postprocessor_args': {},
        'user_agent': ua,
        'referer': 'https://www.instagram.com/',
        'headers': {
            'User-Agent': ua,
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
            'Accept-Language': 'en-US,en;q=0.5',
            'Connection': 'keep-alive',
            'Upgrade-Insecure-Requests': '1',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Site': 'cross-site',
        },
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'tv'],
            },
            'instagram': {
                'get_video_url': [True],
            }
        }
    }
    if cookie_path and os.path.exists(cookie_path):
        opts['cookiefile'] = cookie_path
    return opts

def get_format_selector(url, quality, audio_only):
    """
    Determines the best format selector based on platform and user preference.
    """
    if audio_only:
        return 'bestaudio/best'
        
    # For Instagram and generic image/video sites, 'best' is more reliable for photos
    if "instagram.com" in url or "facebook.com" in url or "t.co" in url:
        return 'best'
        
    if quality == "medium":
        return 'bv*[height<=720]+ba/b[height<=720]'
    if quality == "worst":
        return 'worst'
        
    return 'bv*+ba/b'
