import yt_dlp
import os
import certifi
import json
import time

# تنظیم گواهینامه‌های SSL
os.environ['SSL_CERT_FILE'] = certifi.where()

def get_ytdlp_version():
    try:
        import yt_dlp
        return str(yt_dlp.version.__version__)
    except Exception as e:
        return f"Error: {str(e)}"

def preview_media(url, cookie_file_path=None, proxy=None):
    ydl_opts = {
        'proxy': proxy,
        'quiet': True,
        'nocheckcertificate': True,
        'no_warnings': True,
        'extract_flat': 'in_playlist',
    }
    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            result = {
                'media_kind': 'video', 
                'title': info.get('title', 'Unknown'), 
                'duration': info.get('duration'),
                'thumbnail_url': info.get('thumbnail')
            }
            if info.get('entries'):
                result['type'] = 'carousel'
                result['item_count'] = len(info['entries'])
            else:
                result['type'] = 'single'
            return json.dumps(result)
    except Exception as e:
        return json.dumps({"error": str(e)})

def _guess_kind(info):
    ext = info.get('ext', '').lower()
    if ext in ['jpg', 'jpeg', 'png', 'webp', 'heic']: return "photo"
    if info.get('vcodec') == 'none' and info.get('acodec') != 'none': return "audio"
    return "video"

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None, cookie_file_path=None, ffmpeg_path=None, proxy=None, progress_listener=None, write_subs=False, audio_only=False, custom_args=None):
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    def progress_hook(d):
        if d['status'] == 'downloading':
            try:
                p = d.get('_percent_str', '0%').replace('%', '').strip()
                if progress_listener: progress_listener.onProgress(int(float(p)))
            except: pass

    format_selector = 'bv*+ba/b'
    if quality == "medium": format_selector = 'bv*[height<=720]+ba/b[height<=720]'
    elif quality == "worst": format_selector = 'wv*+wa/w'
    if audio_only: format_selector = 'bestaudio/best'

    ydl_opts = {
        'format': format_selector,
        'outtmpl': os.path.join(download_dir, '%(title).80s.%(ext)s'),
        'restrictfilenames': True,
        'noplaylist': not (not only_first_item),
        'nocheckcertificate': True,
        'proxy': proxy,
        'progress_hooks': [progress_hook],
        'ffmpeg_location': ffmpeg_path  # This points to the 'bin' folder containing 'ffmpeg' and 'ffprobe'
    }

    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path

    postprocessors = []
    if audio_only:
        postprocessors.append({'key': 'FFmpegExtractAudio', 'preferredcodec': 'mp3', 'preferredquality': '192'})
    
    if write_subs:
        ydl_opts['writesubtitles'] = True
        postprocessors.append({'key': 'FFmpegEmbedSubtitle'})
        if not audio_only: ydl_opts['merge_output_format'] = 'mkv'

    if postprocessors:
        ydl_opts['postprocessors'] = postprocessors

    if only_first_item:
        ydl_opts['playlist_items'] = '1'

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            downloaded_files = []
            
            if 'entries' in info:
                for entry in info['entries']:
                    if entry:
                        file_path = ydl.prepare_filename(entry)
                        if os.path.exists(file_path):
                            downloaded_files.append([file_path, entry.get('title', 'Media')])
            else:
                file_path = ydl.prepare_filename(info)
                if os.path.exists(file_path):
                    downloaded_files.append([file_path, info.get('title', 'Media')])

            return json.dumps(downloaded_files)

    except Exception as e:
        return json.dumps([["ERROR", f"yt-dlp: {str(e)}"]])
