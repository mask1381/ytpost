import yt_dlp
import os
import certifi
import json
import time
import shutil
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

# --- MONKEY PATCHING START ---

def apply_ffmpeg_patch(ffmpeg_dir):
    """
    Apply comprehensive patches to redirect ffmpeg/ffprobe to our .so files
    """
    if not ffmpeg_dir:
        return
        
    ffmpeg_bin = os.path.join(ffmpeg_dir, 'libffmpeg.so')
    ffprobe_bin = os.path.join(ffmpeg_dir, 'libffprobe.so')
    
    # 1. Patch shutil.which
    original_which = shutil.which
    def patched_which(cmd, mode=os.F_OK | os.X_OK, path=None):
        if cmd == 'ffmpeg':
            if os.path.exists(ffmpeg_bin): return ffmpeg_bin
        elif cmd == 'ffprobe':
            if os.path.exists(ffprobe_bin): return ffprobe_bin
        return original_which(cmd, mode, path)
    shutil.which = patched_which

    # 2. Patch FFmpegPostProcessor methods
    def _patched_get_ffmpeg_path(self):
        if os.path.exists(ffmpeg_bin): return ffmpeg_bin
        return "ffmpeg"
        
    def _patched_get_ffprobe_path(self):
        if os.path.exists(ffprobe_bin): return ffprobe_bin
        return "ffprobe"

    FFmpegPostProcessor._get_ffmpeg_path = _patched_get_ffmpeg_path
    FFmpegPostProcessor._get_ffprobe_path = _patched_get_ffprobe_path
    
    # 3. Force reset yt-dlp internal caches
    # We clear the class level cached attributes if they exist
    if hasattr(FFmpegPostProcessor, '_executable_path'):
        setattr(FFmpegPostProcessor, '_executable_path', None)
    
    # Also patch the global search functions in yt-dlp utils
    try:
        import yt_dlp.utils
        def patched_get_exe(exe):
            if exe == 'ffmpeg': return ffmpeg_bin
            if exe == 'ffprobe': return ffprobe_bin
            return exe
        yt_dlp.utils.get_executable_path = patched_get_exe
    except:
        pass

def run_diagnostics(ffmpeg_location):
    res = []
    res.append(f"FFmpeg location: {ffmpeg_location}")
    if ffmpeg_location and os.path.exists(ffmpeg_location):
        try:
            files = os.listdir(ffmpeg_location)
            res.append(f"Directory exists. Files found: {files}")
            for f in ['libffmpeg.so', 'libffprobe.so']:
                p = os.path.join(ffmpeg_location, f)
                if os.path.exists(p):
                    # Fixed: os.path.getsize instead of os.getsize
                    res.append(f"File {f}: {os.path.getsize(p)} bytes, exec: {os.access(p, os.X_OK)}")
                else:
                    res.append(f"File {f} MISSING")
        except Exception as e:
            res.append(f"Listdir error: {str(e)}")
    else:
        res.append(f"Directory {ffmpeg_location} DOES NOT EXIST")
    
    return "\n".join(res)

# --- MONKEY PATCHING END ---

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
            result = {'media_kind': 'video', 'title': info.get('title', 'Unknown'), 'duration': info.get('duration')}
            if info.get('entries'):
                result['type'] = 'carousel'
                result['item_count'] = len(info['entries'])
            else:
                result['type'] = 'single'
            return json.dumps(result)
    except Exception as e:
        return json.dumps({"error": str(e)})

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None, cookie_file_path=None, ffmpeg_path=None, proxy=None, progress_listener=None, write_subs=False, audio_only=False, custom_args=None):
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    # Apply all patches before doing anything
    apply_ffmpeg_patch(ffmpeg_path)

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
        'ffmpeg_location': ffmpeg_path
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
