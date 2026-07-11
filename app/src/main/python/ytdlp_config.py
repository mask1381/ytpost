import os
import certifi

# Set SSL certificate file
os.environ['SSL_CERT_FILE'] = certifi.where()

def get_base_opts(proxy=None, cookie_path=None):
    """
    Returns base yt-dlp options shared across preview and download.
    """
    ua = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36'
    opts = {
        'proxy': proxy,
        'nocheckcertificate': True,
        'quiet': True,
        'no_warnings': True,
        'noprogress': True,
        'no_color': True,
        'socket_timeout': 60,
        'retries': 10,
        'fragment_retries': 10,
        'force_ipv4': True,
        'ignoreerrors': True,
        'postprocessors': [],
        'postprocessor_args': {},
        'user_agent': ua,
        'referer': 'https://www.google.com/',
        'headers': {
            'User-Agent': ua,
            'Accept': '*/*',
            'Accept-Language': 'en-US,en;q=0.5',
            'Connection': 'keep-alive',
        },
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'web'],
                'web_client_display_id': [True]
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
        
    if not url:
        return 'bv*+ba/b'
        
    # For Instagram and generic image/video sites, 'best' is more reliable for photos
    if "instagram.com" in url or "facebook.com" in url or "t.co" in url:
        return 'best'
        
    if quality == "medium":
        return 'bv*[height<=720]+ba/b[height<=720]'
    if quality == "worst":
        return 'worst'
        
    return 'bv*+ba/b'
