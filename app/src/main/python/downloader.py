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

def run_ffprobe_test(native_lib_dir):
    """
    مستقیماً ffprobe را تست کرده و خروجی کامل را برمی‌گرداند.
    """
    if not native_lib_dir:
        return "ERROR: native_lib_dir is None"
        
    ffprobe_path = os.path.join(native_lib_dir, 'libffprobe.so')
    if not os.path.exists(ffprobe_path):
        return f"ERROR: libffprobe.so not found at {ffprobe_path}"

    custom_env = os.environ.copy()
    # ست کردن مسیر کتابخانه‌های نیتیو برای پیدا کردن وابستگی‌ها (مثل libssl)
    custom_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + custom_env.get('LD_LIBRARY_PATH', '')
    
    try:
        # تست با آرگومان -version
        proc = subprocess.run(
            [ffprobe_path, '-version'],
            capture_output=True,
            text=True,
            timeout=10,
            env=custom_env
        )
        
        result = [
            f"FFPROBE TEST RESULT (Code: {proc.returncode})",
            f"STDOUT: {proc.stdout}",
            f"STDERR: {proc.stderr}",
            f"ENV LD_LIBRARY_PATH: {custom_env['LD_LIBRARY_PATH']}"
        ]
        return "\n".join(result)
    except Exception as e:
        return f"FFPROBE TEST CRASHED: {str(e)}"

def apply_ffmpeg_patch(native_lib_dir):
    """
    Globally redirects all ffmpeg/ffprobe calls to the .so files in nativeLibraryDir.
    Also ensures LD_LIBRARY_PATH is set so dependencies are found.
    """
    if not native_lib_dir:
        return None
        
    ffmpeg_bin = os.path.join(native_lib_dir, 'libffmpeg.so')
    ffprobe_bin = os.path.join(native_lib_dir, 'libffprobe.so')

    custom_env = os.environ.copy()
    custom_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + custom_env.get('LD_LIBRARY_PATH', '')

    # 1. Patch FFmpegPostProcessor
    FFmpegPostProcessor._get_ffmpeg_path = lambda self: ffmpeg_bin
    FFmpegPostProcessor._get_ffprobe_path = lambda self: ffprobe_bin
    
    orig_init = FFmpegPostProcessor.__init__
    def patched_init(self, *args, **kwargs):
        orig_init(self, *args, **kwargs)
        if hasattr(self, '_paths'):
            self._paths = {'ffmpeg': ffmpeg_bin, 'ffprobe': ffprobe_bin}
        if hasattr(self, '_executables'):
            self._executables = {'ffmpeg': ffmpeg_bin, 'ffprobe': ffprobe_bin}
        # Inject env into the postprocessor if the version supports it
        if hasattr(self, 'env'):
            self.env = custom_env
            
    FFmpegPostProcessor.__init__ = patched_init

    # 2. Patch subprocess.Popen globally to always include LD_LIBRARY_PATH
    orig_popen = subprocess.Popen
    def patched_popen(args, **kwargs):
        # Inject our environment
        new_env = kwargs.get('env', os.environ).copy()
        new_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + new_env.get('LD_LIBRARY_PATH', '')
        kwargs['env'] = new_env
        
        # Handle command replacement
        if isinstance(args, list) and len(args) > 0:
            if args[0] == 'ffmpeg': args[0] = ffmpeg_bin
            elif args[0] == 'ffprobe': args[0] = ffprobe_bin
        elif isinstance(args, str):
            if args.startswith('ffmpeg '): args = args.replace('ffmpeg ', ffmpeg_bin + ' ', 1)
            elif args.startswith('ffprobe '): args = args.replace('ffprobe ', ffprobe_bin + ' ', 1)
            
        return orig_popen(args, **kwargs)
    subprocess.Popen = patched_popen

    # 3. Patch shutil.which
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

def preview_media(url, cookie_file_path=None, proxy=None):
    ydl_opts = {
        'proxy': proxy,
        'quiet': True,
        'nocheckcertificate': True,
        'no_warnings': True,
        'extract_flat': 'in_playlist',
        'extractor_args': {
            'youtube': {
                'player_client': ['web', 'android', 'ios'],
                'skip': ['hls', 'dash']
            }
        }
    }
    if cookie_file_path and os.path.exists(cookie_file_path):
        ydl_opts['cookiefile'] = cookie_file_path

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            try:
                info = ydl.extract_info(url, download=False)
            except Exception as e:
                err_msg = str(e)
                if "reloaded" in err_msg.lower() or "bot" in err_msg.lower() or "sign in" in err_msg.lower():
                    time.sleep(2)
                    ydl_opts['extractor_args']['youtube']['player_client'] = ['ios']
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl_retry:
                        info = ydl_retry.extract_info(url, download=False)
                else:
                    # Pinterest specific debug
                    if "json" in err_msg.lower() or "pinterest" in url.lower() or "pin.it" in url.lower():
                        print(f"DEBUG Pinterest Error: {err_msg}")
                    raise e
                
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

    # Apply patches and set environment
    ffmpeg_bin_str = apply_ffmpeg_patch(ffmpeg_path)

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
        'merge_output_format': 'mp4',  # Force MP4 for better Telegram compatibility
        'extractor_args': {
            'youtube': {
                'player_client': ['web', 'android', 'ios'],
                'skip': ['hls', 'dash']
            }
        }
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
            try:
                info = ydl.extract_info(url, download=True)
            except Exception as e:
                err_msg = str(e)
                if "reloaded" in err_msg.lower() or "bot" in err_msg.lower() or "sign in" in err_msg.lower():
                    time.sleep(3)
                    ydl_opts['extractor_args']['youtube']['player_client'] = ['ios']
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl_retry:
                        info = ydl_retry.extract_info(url, download=True)
                else:
                    raise e
            
            # Log the downloaded file path for debugging
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
        print(f"DOWNLOAD ERROR: {err_msg}")
        return json.dumps([["ERROR", f"yt-dlp: {err_msg}"]])
