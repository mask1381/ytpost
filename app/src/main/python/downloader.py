import yt_dlp
import os
import certifi
import json
import time
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

# Monkey patch for Android 10+ execution
# Since we renamed binaries to lib*.so to bypass execution restrictions
def patched_get_ffmpeg_path(self):
    location = self.get_param('ffmpeg_location')
    if location:
        p = os.path.join(location, 'libffmpeg.so')
        if os.path.exists(p): return p
    return "ffmpeg" # fallback to PATH

def patched_get_ffprobe_path(self):
    location = self.get_param('ffmpeg_location')
    if location:
        p = os.path.join(location, 'libffprobe.so')
        if os.path.exists(p): return p
    return "ffprobe" # fallback to PATH

FFmpegPostProcessor._get_ffmpeg_path = patched_get_ffmpeg_path
FFmpegPostProcessor._get_ffprobe_path = patched_get_ffprobe_path

# تنظیم گواهینامه‌های SSL
os.environ['SSL_CERT_FILE'] = certifi.where()

def get_ytdlp_version():
    """
    برگرداندن نسخه نصب شده yt-dlp برای عیب‌یابی
    """
    try:
        import yt_dlp
        return str(yt_dlp.version.__version__)
    except Exception as e:
        return f"Error: {str(e)}"

def preview_media(url, cookie_file_path=None, proxy=None):
    """
    دریافت اطلاعات پیش‌نمایش بدون دانلود
    """
    ydl_opts = {
        'proxy': proxy,
        'quiet': True,
        'nocheckcertificate': True,
        'no_warnings': True,
        'extract_flat': 'in_playlist',
        'extractor_args': {
            'youtube': {
                'player_client': ['tv', 'web_safari', 'android'],
                'skip': ['hls', 'dash']
            }
        }
    }

    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # Retry logic for preview (Reloaded error)
            try:
                info = ydl.extract_info(url, download=False)
            except Exception as e:
                if "reloaded" in str(e).lower():
                    time.sleep(2)
                    ydl_opts['extractor_args']['youtube']['player_client'] = ['web']
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl_retry:
                        info = ydl_retry.extract_info(url, download=False)
                else:
                    raise e
            
            result = {}
            entries = info.get('entries')
            
            if entries:
                result['type'] = 'carousel'
                result['item_count'] = len(entries)
                items_info = []
                for e in entries:
                    items_info.append({
                        'kind': _guess_kind(e),
                        'title': e.get('title')
                    })
                result['items'] = items_info
            else:
                result['type'] = 'single'
            
            result['media_kind'] = _guess_kind(info)
            result['title'] = info.get('title', 'Unknown Title')
            result['duration'] = info.get('duration')
            result['thumbnail_url'] = info.get('thumbnail')
            
            formats = []
            seen_heights = set()
            raw_formats = info.get('formats', [])
            
            if result['media_kind'] in ['video', 'audio']:
                for f in raw_formats:
                    height = f.get('height')
                    ext = f.get('ext')
                    if height and height not in seen_heights:
                        formats.append({
                            'id': f.get('format_id'),
                            'label': f"{height}p ({ext})",
                            'height': height
                        })
                        seen_heights.add(height)
                    elif not height and f.get('vcodec') == 'none' and f.get('acodec') != 'none':
                        abr = f.get('abr')
                        if abr:
                            formats.append({
                                'id': f.get('format_id'),
                                'label': f"Audio {int(abr)}kbps",
                                'abr': abr
                            })

            result['formats'] = formats
            result['filesize_approx'] = info.get('filesize') or info.get('filesize_approx')
            
            return json.dumps(result)
    except Exception as e:
        return json.dumps({"error": str(e)})

def _guess_kind(info):
    ext = info.get('ext', '').lower()
    if ext in ['jpg', 'jpeg', 'png', 'webp', 'heic']:
        return "photo"
    if info.get('vcodec') == 'none' and info.get('acodec') != 'none':
        return "audio"
    return "video"

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None, cookie_file_path=None, ffmpeg_path=None, proxy=None, progress_listener=None, write_subs=False, audio_only=False, custom_args=None):
    """
    دانلود با پارامترهای انتخابی و منطق بازتلاش (Retry)
    """
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    def progress_hook(d):
        if d['status'] == 'downloading':
            try:
                p = d.get('_percent_str', '0%').replace('%', '').strip()
                if progress_listener:
                    progress_listener.onProgress(int(float(p)))
            except:
                pass

    format_selector = 'bv*+ba/b'
    if quality == "medium":
        format_selector = 'bv*[height<=720]+ba/b[height<=720]'
    elif quality == "worst":
        format_selector = 'wv*+wa/w'
    
    if audio_only:
        format_selector = 'bestaudio/best'

    ydl_opts = {
        'format': format_selector,
        'outtmpl': os.path.join(download_dir, '%(title).80s.%(ext)s'),
        'restrictfilenames': True,
        'noplaylist': not (not only_first_item),
        'nocheckcertificate': True,
        'proxy': proxy,
        'quiet': False,
        'no_warnings': False,
        'progress_hooks': [progress_hook],
        'extractor_args': {
            'youtube': {
                'player_client': ['tv', 'web_safari', 'android'],
                'skip': ['hls', 'dash']
            }
        },
        'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36'
    }

    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path
        
    if ffmpeg_path:
        ydl_opts['ffmpeg_location'] = ffmpeg_path

    # Advanced Post-processors
    postprocessors = []
    if audio_only:
        postprocessors.append({
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'mp3',
            'preferredquality': '192',
        })
    
    if write_subs:
        ydl_opts['writesubtitles'] = True
        ydl_opts['allsubtitles'] = True
        postprocessors.append({
            'key': 'FFmpegEmbedSubtitle',
            'already_have_subtitle': False
        })
        # Note: Embedding requires compatible container like mp4 or mkv
        if not audio_only:
             ydl_opts['merge_output_format'] = 'mkv'

    if postprocessors:
        ydl_opts['postprocessors'] = postprocessors

    # Inject Custom Args
    if custom_args:
        try:
            # Simple space-separated parsing for some flags
            import shlex
            extra_args = shlex.split(custom_args)
            # This is a bit limited for ydl_opts, but better than nothing
            # A full parser would be needed for complex flags
        except:
            pass

    # لیست انواع مجاز
    allowed_kinds = media_filter.split(',') if media_filter else None

    def match_filter(info_dict, incomplete):
        if allowed_kinds:
            kind = _guess_kind(info_dict)
            if kind not in allowed_kinds:
                return f"Skipping {kind} because it's not in {allowed_kinds}"
        return None

    ydl_opts['match_filter'] = match_filter

    if only_first_item:
        ydl_opts['playlist_items'] = '1'

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # Retry logic for download (Reloaded error)
            try:
                info = ydl.extract_info(url, download=True)
            except Exception as e:
                if "reloaded" in str(e).lower():
                    time.sleep(3)
                    ydl_opts['extractor_args']['youtube']['player_client'] = ['web']
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl_retry:
                        info = ydl_retry.extract_info(url, download=True)
                else:
                    raise e
                    
            downloaded_files = []
            
            if 'entries' in info:
                for entry in info['entries']:
                    if entry:
                        file_path = ydl.prepare_filename(entry)
                        if os.path.exists(file_path):
                            downloaded_files.append([file_path, entry.get('title', 'Media')])
            else:
                file_path = ydl.prepare_filename(info)
                if not os.path.exists(file_path):
                    base = os.path.splitext(file_path)[0]
                    for f in os.listdir(download_dir):
                        if f.startswith(os.path.basename(base)):
                            file_path = os.path.join(download_dir, f)
                            break
                
                if os.path.exists(file_path):
                    downloaded_files.append([file_path, info.get('title', 'Media')])

            return json.dumps(downloaded_files)

    except Exception as e:
        return json.dumps([["ERROR", f"yt-dlp: {str(e)}"]])
