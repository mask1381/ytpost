import yt_dlp
import os
import certifi
import json
import time
import shutil
import subprocess
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

# --- GLOBAL SUPER PATCH START ---

def apply_super_patch(ffmpeg_dir):
    """
    The ultimate patch to redirect all ffmpeg/ffprobe calls to .so files.
    This handles cases where yt-dlp checks version, calls subprocess,
    or uses shutil.which.
    """
    if not ffmpeg_dir:
        return
        
    f_path = os.path.join(ffmpeg_dir, 'libffmpeg.so')
    p_path = os.path.join(ffmpeg_dir, 'libffprobe.so')
    
    if not os.path.exists(f_path):
        return

    # 1. Patch shutil.which (used by many libraries to find executables)
    orig_which = shutil.which
    def patched_which(cmd, mode=os.F_OK | os.X_OK, path=None):
        if cmd == 'ffmpeg' or cmd.endswith('/ffmpeg'): return f_path
        if cmd == 'ffprobe' or cmd.endswith('/ffprobe'): return p_path
        return orig_which(cmd, mode, path)
    shutil.which = patched_which

    # 2. Patch subprocess.Popen (The hammer: catches the actual execution)
    orig_popen = subprocess.Popen
    def patched_popen(args, *a, **k):
        if isinstance(args, list) and len(args) > 0:
            if args[0] == 'ffmpeg': args[0] = f_path
            elif args[0] == 'ffprobe': args[0] = p_path
        return orig_popen(args, *a, **k)
    subprocess.Popen = patched_popen

    # 3. Patch yt-dlp FFmpegPostProcessor (The internal logic)
    FFmpegPostProcessor._get_ffmpeg_path = lambda self: f_path
    FFmpegPostProcessor._get_ffprobe_path = lambda self: p_path
    
    # Force reset cached paths in the class
    if hasattr(FFmpegPostProcessor, '_executable_path'):
        try:
            # Using dict access to bypass any protection if needed
            FFmpegPostProcessor.__dict__['_executable_path'] = None
        except:
            pass

    # 4. Patch yt-dlp utils (Global search functions)
    try:
        import yt_dlp.utils
        yt_dlp.utils.get_executable_path = lambda exe, *a, **k: f_path if exe == 'ffmpeg' else (p_path if exe == 'ffprobe' else exe)
    except:
        pass
        
    # 5. Add to PATH as well (last resort)
    os.environ['PATH'] = ffmpeg_dir + os.pathsep + os.environ.get('PATH', '')

def run_diagnostics(ffmpeg_location):
    res = []
    res.append(f"FFmpeg location: {ffmpeg_location}")
    if ffmpeg_location and os.path.exists(ffmpeg_location):
        try:
            files = os.listdir(ffmpeg_location)
            res.append(f"Files in lib dir: {files}")
            for f in ['libffmpeg.so', 'libffprobe.so']:
                p = os.path.join(ffmpeg_location, f)
                if os.path.exists(p):
                    res.append(f"File {f}: {os.path.getsize(p)} bytes, exec: {os.access(p, os.X_OK)}")
                else:
                    res.append(f"File {f} MISSING")
        except Exception as e:
            res.append(f"Diag error: {str(e)}")
    
    # Test our own patch
    res.append(f"shutil.which('ffmpeg'): {shutil.which('ffmpeg')}")
    try:
        pp = FFmpegPostProcessor()
        res.append(f"FFmpegPostProcessor._get_ffmpeg_path(): {pp._get_ffmpeg_path()}")
    except Exception as e:
        res.append(f"PP Test error: {str(e)}")
        
    return "\n".join(res)

# --- GLOBAL SUPER PATCH END ---

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

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None, cookie_file_path=None, ffmpeg_path=None, proxy=None, progress_listener=None, write_subs=False, audio_only=False, custom_args=None):
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    # 1. Apply Super Patch
    apply_super_patch(ffmpeg_path)

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
        # We don't need ffmpeg_location in ydl_opts because we patched globally,
        # but keeping it doesn't hurt as our patch overrides its effect.
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
            # We must be careful: yt-dlp might call get_versions inside YoutubeDL.__init__
            # That's why apply_super_patch must be called before.
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
