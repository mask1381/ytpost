import yt_dlp
import os
import certifi
import json
import time
import subprocess
import shutil
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

# تنظیم گواهینامه‌های SSL
os.environ['SSL_CERT_FILE'] = certifi.where()

def apply_ffmpeg_patch(native_lib_dir):
    """
    Globally redirects all ffmpeg/ffprobe calls to the .so files in nativeLibraryDir.
    """
    if not native_lib_dir:
        return
        
    ffmpeg_bin = os.path.join(native_lib_dir, 'libffmpeg.so')
    ffprobe_bin = os.path.join(native_lib_dir, 'libffprobe.so')

    # 1. Test execution before patching
    for name, path in [("ffmpeg", ffmpeg_bin), ("ffprobe", ffprobe_bin)]:
        try:
            # Try to run with -version
            process = subprocess.Popen(
                [path, '-version'], 
                stdout=subprocess.PIPE, 
                stderr=subprocess.PIPE,
                text=True
            )
            stdout, stderr = process.communicate(timeout=5)
            if process.returncode == 0:
                print(f"EXEC_TEST: {name} is EXECUTABLE. Version: {stdout.splitlines()[0]}")
            else:
                print(f"EXEC_TEST: {name} failed with exit code {process.returncode}. Stderr: {stderr}")
        except Exception as e:
            print(f"EXEC_TEST: {name} CRASHED: {str(e)}")

    # 2. Patch yt-dlp internal discovery
    FFmpegPostProcessor._get_ffmpeg_path = lambda self: ffmpeg_bin
    FFmpegPostProcessor._get_ffprobe_path = lambda self: ffprobe_bin

    # 3. Patch shutil.which to satisfy internal 'yt-dlp' checks
    original_which = shutil.which
    def patched_which(cmd, mode=os.F_OK | os.X_OK, path=None):
        if cmd == 'ffmpeg': return ffmpeg_bin
        if cmd == 'ffprobe': return ffprobe_bin
        return original_which(cmd, mode, path)
    shutil.which = patched_which

    # 4. Patch yt_dlp.utils.get_executable_path
    try:
        import yt_dlp.utils
        yt_dlp.utils.get_executable_path = lambda exe, *a, **k: ffmpeg_bin if exe == 'ffmpeg' else (ffprobe_bin if exe == 'ffprobe' else exe)
    except:
        pass

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

    # Apply the native library patches
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
