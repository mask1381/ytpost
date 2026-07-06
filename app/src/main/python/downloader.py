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
    Also ensures LD_LIBRARY_PATH is set so dependencies are found.
    """
    if not native_lib_dir:
        return None
        
    ffmpeg_bin = os.path.join(native_lib_dir, 'libffmpeg.so')
    ffprobe_bin = os.path.join(native_lib_dir, 'libffprobe.so')

    # Update environment for subprocesses to find .so dependencies
    custom_env = os.environ.copy()
    custom_env['LD_LIBRARY_PATH'] = native_lib_dir + ":" + custom_env.get('LD_LIBRARY_PATH', '')

    # 1. Detailed Validation Test (subprocess call -version)
    print(f"DIAGNOSTIC: Testing binaries in {native_lib_dir}")
    for name, path in [("ffmpeg", ffmpeg_bin), ("ffprobe", ffprobe_bin)]:
        try:
            if not os.path.exists(path):
                print(f"VALIDATION: {name} MISSING at {path}")
                continue
                
            # Try to run with -version
            proc = subprocess.run(
                [path, '-version'], 
                capture_output=True, 
                text=True,
                timeout=5,
                env=custom_env
            )
            if proc.returncode == 0:
                print(f"VALIDATION: {name} is EXECUTABLE.\nSTDOUT: {proc.stdout.splitlines()[0]}")
            else:
                print(f"VALIDATION: {name} FAILED (Code {proc.returncode}).\nSTDERR: {proc.stderr}\nSTDOUT: {proc.stdout}")
        except Exception as e:
            print(f"VALIDATION: {name} CRASHED: {str(e)}")

    # 2. Patch FFmpegPostProcessor
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

    # 3. Patch subprocess.Popen globally to always include LD_LIBRARY_PATH
    orig_popen = subprocess.Popen
    def patched_popen(args, **kwargs):
        if 'env' not in kwargs:
            kwargs['env'] = custom_env
        else:
            kwargs['env']['LD_LIBRARY_PATH'] = native_lib_dir + ":" + kwargs['env'].get('LD_LIBRARY_PATH', '')
        
        # Also handle cases where args[0] is just 'ffmpeg'
        if isinstance(args, list) and len(args) > 0:
            if args[0] == 'ffmpeg': args[0] = ffmpeg_bin
            elif args[0] == 'ffprobe': args[0] = ffprobe_bin
            
        return orig_popen(args, **kwargs)
    subprocess.Popen = patched_popen

    # 4. Patch shutil.which
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
    # Pinterest links often need a newer yt-dlp version or specific headers
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
            try:
                info = ydl.extract_info(url, download=False)
            except Exception as e:
                # Capture Pinterest raw response error if possible
                err_msg = str(e)
                if "json" in err_msg.lower() or "pinterest" in url.lower():
                    print(f"EXTRACTOR ERROR on {url}: {err_msg}")
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

    # 1. Apply patches and set environment
    ffmpeg_bin_str = apply_ffmpeg_patch(ffmpeg_path)
    print(f"YT-DLP Version: {get_ytdlp_version()}")

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
        'ffmpeg_location': ffmpeg_bin_str
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
        err_msg = str(e)
        if "json" in err_msg.lower():
             print(f"FATAL DOWNLOAD ERROR: {err_msg}")
             # If possible, ytdlp internal logs will be printed to stdout/stderr captured by Chaquopy
        return json.dumps([["ERROR", f"yt-dlp: {err_msg}"]])
