import yt_dlp
import os
import json
import time
from ytdlp_config import get_base_opts, get_format_selector

def preview_media(url, cookie_file_path=None, proxy=None):
    opts = get_base_opts(proxy, cookie_file_path)
    # Deep extraction for Instagram to see hidden photo metadata
    opts.update({'extract_flat': False, 'playlist_items': '1-5'}) 

    for attempt in range(3):
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if not info: return json.dumps({"error": "No data returned"})

                # 1. Title: Prefer description/caption for Instagram
                raw_title = info.get('title')
                desc = info.get('description')
                if not raw_title or raw_title in ["No Title", "Instagram Post", "Instagram Video"]:
                    title = desc or info.get('webpage_url_basename') or "Instagram Content"
                else:
                    title = raw_title
                
                if len(title) > 100: title = title[:97] + "..."

                # 2. Accurate Media Kind Detection
                ext = (info.get('ext') or '').lower()
                vcodec = info.get('vcodec')
                acodec = info.get('acodec')
                duration = info.get('duration')
                entries = info.get('entries')
                is_carousel = entries is not None
                
                media_kind = 'video'
                # Strong indicators for photos on Instagram
                if ext in ['jpg', 'png', 'jpeg', 'webp']:
                    media_kind = 'photo'
                elif (vcodec == 'none' or not vcodec) and (not duration or duration <= 0):
                    media_kind = 'photo'
                elif "instagram.com/p/" in url and (not vcodec or vcodec == 'none'):
                    media_kind = 'photo'

                # 3. Thumbnail extraction (Search in formats for high-res images)
                thumb = info.get('thumbnail')
                if not thumb or "1x1" in thumb or "placeholder" in thumb:
                    formats = info.get('formats', [])
                    # Simple loop without reversed() for better compatibility
                    for i in range(len(formats)-1, -1, -1):
                        f = formats[i]
                        f_ext = (f.get('ext') or '').lower()
                        if f.get('url') and (f_ext in ['jpg', 'png', 'webp'] or 'image' in (f.get('format_id') or '')):
                            thumb = f.get('url')
                            break
                
                if not thumb and is_carousel and len(entries) > 0:
                    thumb = entries[0].get('thumbnail') or entries[0].get('url')

                return json.dumps({
                    'media_kind': media_kind, 
                    'title': title, 
                    'duration': duration,
                    'thumbnail_url': thumb,
                    'type': 'carousel' if is_carousel else 'single',
                    'item_count': len(entries) if is_carousel else 1
                })
        except Exception as e:
            if attempt < 2: time.sleep(2); continue
            return json.dumps({"error": str(e)})

def get_video_formats(url, cookie_file_path=None, proxy=None):
    opts = get_base_opts(proxy, cookie_file_path)
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info: return json.dumps({"error": "No info returned"})
            
            formats_list = []
            for f in info.get('formats', []):
                formats_list.append({
                    'format_id': f.get('format_id'),
                    'ext': f.get('ext'),
                    'resolution': f.get('resolution') or f"{f.get('width')}x{f.get('height')}",
                    'fps': f.get('fps'),
                    'filesize_approx': f.get('filesize') or f.get('filesize_approx'),
                    'vcodec': f.get('vcodec'),
                    'acodec': f.get('acodec'),
                    'is_progressive': (f.get('vcodec') != 'none' and f.get('acodec') != 'none')
                })
            return json.dumps(formats_list)
    except Exception as e:
        return json.dumps({"error": str(e)})

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None, cookie_file_path=None, ffmpeg_path=None, proxy=None, progress_listener=None, write_subs=False, audio_only=False, custom_args=None):
    if not os.path.exists(download_dir): os.makedirs(download_dir)
    
    opts = get_base_opts(proxy, cookie_file_path)
    opts.update({
        'format': get_format_selector(url, quality, audio_only),
        'outtmpl': os.path.join(download_dir, '%(title).80s.%(ext)s'),
        'restrictfilenames': True,
        'noplaylist': not (not only_first_item),
        'progress_hooks': [lambda d: _handle_progress(d, progress_listener)],
    })

    if ffmpeg_path:
        # Check if it's a directory or the file itself
        if os.path.isdir(ffmpeg_path):
            opts['ffmpeg_location'] = os.path.join(ffmpeg_path, 'libffmpeg.so')
        else:
            opts['ffmpeg_location'] = ffmpeg_path

    if audio_only:
        # Force .m4a and copy stream to preserve duration metadata
        opts['outtmpl'] = os.path.join(download_dir, '%(title).80s.m4a')
        opts['postprocessor_args']['ffmpeg'] = ['-vn', '-c:a', 'copy', '-movflags', '+faststart']
    elif "youtube" in url:
        opts['merge_output_format'] = 'mp4'

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            downloaded_items = []
            
            if 'entries' in info:
                for entry in info['entries']:
                    if entry:
                        file_path = ydl.prepare_filename(entry)
                        if os.path.exists(file_path):
                            downloaded_items.append([file_path, entry.get('title', 'Media')])
            else:
                file_path = ydl.prepare_filename(info)
                if os.path.exists(file_path):
                    downloaded_items.append([file_path, info.get('title', 'Media')])
            
            return json.dumps(downloaded_items)
    except Exception as e:
        return json.dumps([["ERROR", f"yt-dlp: {str(e)}"]])

def _handle_progress(d, listener):
    if d['status'] == 'downloading' and listener:
        try:
            total = d.get('total_bytes') or d.get('total_bytes_estimate')
            downloaded = d.get('downloaded_bytes', 0)
            if total:
                listener.onProgress(int((downloaded / total) * 100))
            else:
                # Fallback for some stream types
                p_str = d.get('_percent_str', '0%').replace('%', '').strip()
                try:
                    listener.onProgress(int(p_str.split('.')[0]))
                except: pass
        except: pass
