import yt_dlp
import os
import certifi
import json
import time
import subprocess
import shutil
import urllib.request
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

# تنظیم گواهینامه‌های SSL
os.environ['SSL_CERT_FILE'] = certifi.where()

def get_current_ip(proxy=None):
    try:
        handlers = []
        if proxy:
            if proxy.startswith('socks'):
                import socks
                import socket
                # Parse socks5h://127.0.0.1:10808
                prefix, rest = proxy.split("://")
                addr, port = rest.split(":")
                socks.set_default_proxy(socks.SOCKS5, addr, int(port), rdns=True)
                socket.socket = socks.socksocket
            else:
                handlers.append(urllib.request.ProxyHandler({'http': proxy, 'https': proxy}))
        
        opener = urllib.request.build_opener(*handlers)
        with opener.open("https://api.ipify.org", timeout=5) as response:
            return response.read().decode('utf-8')
    except Exception as e:
        return f"Error: {str(e)}"

def apply_ffmpeg_patch(native_lib_dir):
    if not native_lib_dir:
        return None
        
    ffmpeg_bin = os.path.join(native_lib_dir, 'libffmpeg.so')
    ffprobe_bin = os.path.join(native_lib_dir, 'libffprobe.so')

    custom_env = os.environ.copy()
    custom_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + custom_env.get('LD_LIBRARY_PATH', '')

    FFmpegPostProcessor._get_ffmpeg_path = lambda self: ffmpeg_bin
    FFmpegPostProcessor._get_ffprobe_path = lambda self: ffprobe_bin
    
    orig_init = FFmpegPostProcessor.__init__
    def patched_init(self, *args, **kwargs):
        orig_init(self, *args, **kwargs)
        if hasattr(self, '_paths'):
            self._paths = {'ffmpeg': ffmpeg_bin, 'ffprobe': ffprobe_bin}
        if hasattr(self, '_executables'):
            self._executables = {'ffmpeg': ffmpeg_bin, 'ffprobe': ffprobe_bin}
        if hasattr(self, 'env'):
            self.env = custom_env
            
    FFmpegPostProcessor.__init__ = patched_init

    orig_popen = subprocess.Popen
    def patched_popen(args, **kwargs):
        new_env = kwargs.get('env', os.environ).copy()
        new_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + new_env.get('LD_LIBRARY_PATH', '')
        kwargs['env'] = new_env
        
        if isinstance(args, list) and len(args) > 0:
            if args[0] == 'ffmpeg': args[0] = ffmpeg_bin
            elif args[0] == 'ffprobe': args[0] = ffprobe_bin
        elif isinstance(args, str):
            if args.startswith('ffmpeg '): args = args.replace('ffmpeg ', ffmpeg_bin + ' ', 1)
            elif args.startswith('ffprobe '): args = args.replace('ffprobe ', ffprobe_bin + ' ', 1)
            
        return orig_popen(args, **kwargs)
    subprocess.Popen = patched_popen

    orig_which = shutil.which
    def patched_which(cmd, mode=os.F_OK | os.X_OK, path=None):
        if cmd == 'ffmpeg': return ffmpeg_bin
        if cmd == 'ffprobe': return ffprobe_bin
        return orig_which(cmd, mode, path)
    shutil.which = patched_which

    return ffmpeg_bin

def get_ytdlp_version():
    try:
        import yt_dlp
        return str(yt_dlp.version.__version__)
    except Exception as e:
        return f"Error: {str(e)}"

def check_ffmpeg_encoders(ffmpeg_path):
    """
    اجرای دستور -encoders برای بررسی کدک‌های موجود در باینری FFmpeg
    """
    try:
        if not ffmpeg_path or not os.path.exists(ffmpeg_path):
            return f"Error: FFmpeg not found at {ffmpeg_path}"
        
        # Ensure LD_LIBRARY_PATH is set for dependencies
        native_dir = os.path.dirname(ffmpeg_path)
        custom_env = os.environ.copy()
        custom_env['LD_LIBRARY_PATH'] = native_dir + ":" + custom_env.get('LD_LIBRARY_PATH', '')

        result = subprocess.run(
            [ffmpeg_path, '-encoders'], 
            capture_output=True, 
            text=True, 
            timeout=15,
            env=custom_env
        )
        return result.stdout
    except Exception as e:
        return f"Error running encoders check: {str(e)}"

def run_ffprobe_test(native_lib_dir):
    """
    تست ساده ffprobe برای اطمینان از سلامت باینری و لود شدن کتابخانه‌ها
    """
    try:
        if not native_lib_dir:
            return "Error: native_lib_dir is None"
            
        ffprobe_path = os.path.join(native_lib_dir, 'libffprobe.so')
        if not os.path.exists(ffprobe_path):
            return f"Error: libffprobe.so not found at {ffprobe_path}"

        custom_env = os.environ.copy()
        custom_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + custom_env.get('LD_LIBRARY_PATH', '')
        
        proc = subprocess.run(
            [ffprobe_path, '-version'],
            capture_output=True,
            text=True,
            timeout=10,
            env=custom_env
        )
        
        if proc.returncode == 0:
            return f"FFPROBE OK: {proc.stdout.splitlines()[0]}"
        else:
            return f"FFPROBE FAILED (Code {proc.returncode}): {proc.stderr}"
    except Exception as e:
        return f"FFPROBE CRASHED: {str(e)}"

def preview_media(url, cookie_file_path=None, proxy=None):
    print(f"DEBUG: Proxy passed to preview: {proxy}")
    print(f"DEBUG: Current IP: {get_current_ip(proxy)}")
    
    ydl_opts = {
        'proxy': proxy,
        'quiet': True,
        'nocheckcertificate': True,
        'no_warnings': True,
        'extract_flat': 'in_playlist',
        'socket_timeout': 30,
        'retries': 10,
        'fragment_retries': 10,
        'extractor_retries': 5,
        'source_address': None,
        'force_ipv4': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'tv'],
                'skip': ['hls', 'dash']
            }
        }
    }
    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path

    max_retries = 3
    for attempt in range(max_retries + 1):
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
            err_msg = str(e)
            if attempt < max_retries and ("incomplete" in err_msg.lower() or "unable to download api page" in err_msg.lower() or "reloaded" in err_msg.lower()):
                wait_time = 2 ** (attempt + 1)
                print(f"DEBUG: Preview retry {attempt + 1}/{max_retries} after {wait_time}s due to error: {err_msg}")
                time.sleep(wait_time)
                continue
            return json.dumps({"error": err_msg})

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None, cookie_file_path=None, ffmpeg_path=None, proxy=None, progress_listener=None, write_subs=False, audio_only=False, custom_args=None):
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    ffmpeg_bin_str = apply_ffmpeg_patch(ffmpeg_path)
    
    print(f"DEBUG: Proxy passed to download: {proxy}")
    print(f"DEBUG: Current IP: {get_current_ip(proxy)}")

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
        'ffmpeg_location': ffmpeg_bin_str,
        'keepvideo': True,
        'merge_output_format': 'mp4',
        'socket_timeout': 30,
        'retries': 10,
        'fragment_retries': 10,
        'extractor_retries': 5,
        'source_address': None,
        'force_ipv4': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'tv'],
                'skip': ['hls', 'dash']
            }
        }
    }

    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path

    postprocessors = []
    if audio_only:
        postprocessors.append({'key': 'FFmpegExtractAudio', 'preferredcodec': 'opus', 'preferredquality': '192'})
    
    if write_subs:
        ydl_opts['writesubtitles'] = True
        postprocessors.append({'key': 'FFmpegEmbedSubtitle'})
        if not audio_only: ydl_opts['merge_output_format'] = 'mkv'

    if postprocessors:
        ydl_opts['postprocessors'] = postprocessors

    max_retries = 3
    for attempt in range(max_retries + 1):
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                
                temp_path = ydl.prepare_filename(info)
                print(f"DEBUG: Downloaded temp file: {temp_path}")

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
            err_msg = str(e)
            if attempt < max_retries and ("incomplete" in err_msg.lower() or "unable to download api page" in err_msg.lower() or "reloaded" in err_msg.lower()):
                wait_time = 2 ** (attempt + 1)
                print(f"DEBUG: Download retry {attempt + 1}/{max_retries} after {wait_time}s due to error: {err_msg}")
                time.sleep(wait_time)
                continue
            print(f"DOWNLOAD ERROR: {err_msg}")
            return json.dumps([["ERROR", f"yt-dlp: {err_msg}"]])
