import yt_dlp
import os
import certifi
import json

# استفاده از socks5h برای حل مشکل DNS در لایه پایتون (بسیار مهم برای ایران)
PROXY = "socks5h://127.0.0.1:10808"

# تنظیم گواهینامه‌های SSL
os.environ['SSL_CERT_FILE'] = certifi.where()

def preview_media(url):
    """
    دریافت اطلاعات پیش‌نمایش بدون دانلود
    """
    ydl_opts = {
        'proxy': PROXY,
        'quiet': True,
        'nocheckcertificate': True,
        'no_warnings': True,
        'extract_flat': 'in_playlist', # برای سرعت بیشتر در پست‌های چندتایی
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            result = {}
            entries = info.get('entries')
            
            if entries:
                result['type'] = 'carousel'
                result['item_count'] = len(entries)
                # بررسی نوع آیتم‌ها در کاروسل (تخمینی)
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
            
            # استخراج فرمت‌ها (کیفیت‌ها)
            formats = []
            seen_heights = set()
            raw_formats = info.get('formats', [])
            
            # فقط برای ویدیوها یا صداها فرمت معنی دارد
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
                        # احتمالا فایل صوتی
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

def download_video(url, download_dir, quality="best", only_first_item=False, media_filter=None):
    """
    دانلود با پارامترهای انتخابی
    """
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    # تعیین Format Selector بر اساس کیفیت انتخابی
    format_selector = 'bestvideo+bestaudio/best'
    if quality == "medium":
        format_selector = 'best[height<=720]/medium'
    elif quality == "worst":
        format_selector = 'worst'

    ydl_opts = {
        'format': format_selector,
        'outtmpl': os.path.join(download_dir, '%(title).80s.%(ext)s'),
        'restrictfilenames': True,
        'noplaylist': not (not only_first_item), # اگر only_first_item باشد، پلی‌لیست (کاروسل) را غیرفعال می‌کنیم
        'nocheckcertificate': True,
        'proxy': PROXY,
        'quiet': False,
        'no_warnings': False,
        'extractor_args': {
            'youtube': {
                'player_client': ['web', 'tv', 'ios'],
                'skip': ['hls', 'dash']
            }
        },
        'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36'
    }

    # فیلتر کردن آیتم‌ها (اگر کاروسل باشد)
    def match_filter(info_dict, incomplete):
        if media_filter:
            kind = _guess_kind(info_dict)
            if kind != media_filter:
                return f"Skipping {kind} because media_filter is {media_filter}"
        return None

    if media_filter:
        ydl_opts['match_filter'] = match_filter

    if only_first_item:
        ydl_opts['playlist_items'] = '1'

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            
            downloaded_files = []
            
            if 'entries' in info:
                # اگر کاروسل دانلود شده باشد
                for entry in info['entries']:
                    if entry:
                        file_path = ydl.prepare_filename(entry)
                        if os.path.exists(file_path):
                            downloaded_files.append([file_path, entry.get('title', 'Media')])
            else:
                # فایل تکی
                file_path = ydl.prepare_filename(info)
                # بررسی فیزیکی وجود فایل (گاهی yt-dlp نام نهایی را کمی تغییر می‌دهد)
                if not os.path.exists(file_path):
                    base = os.path.splitext(file_path)[0]
                    for f in os.listdir(download_dir):
                        if f.startswith(os.path.basename(base)):
                            file_path = os.path.join(download_dir, f)
                            break
                
                if os.path.exists(file_path):
                    downloaded_files.append([file_path, info.get('title', 'Media')])

            if downloaded_files:
                # بازگشت لیست فایل‌ها برای پشتیبانی از کاروسل
                return json.dumps(downloaded_files)
            else:
                return json.dumps([["ERROR", "فایل(ها) یافت نشد."]])

    except Exception as e:
        return json.dumps([["ERROR", f"yt-dlp: {str(e)}"]])

def get_media_type(url):
    # این تابع را برای سازگاری نگه می‌داریم ولی بهتر است از preview_media استفاده شود
    try:
        with yt_dlp.YoutubeDL({'quiet': True, 'proxy': PROXY}) as ydl:
            info = ydl.extract_info(url, download=False)
            return _guess_kind(info)
    except:
        return "video"
