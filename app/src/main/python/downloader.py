import yt_dlp
import os
import certifi

# استفاده از socks5h برای حل مشکل DNS در لایه پایتون (بسیار مهم برای ایران)
# پورت فیلترشکن v2ray معمولاً 10808 است.
PROXY = "socks5h://127.0.0.1:10808"

# تنظیم گواهینامه‌های SSL برای جلوگیری از خطای CERTIFICATE_VERIFY_FAILED
os.environ['SSL_CERT_FILE'] = certifi.where()

def download_video(url, download_dir):
    """
    دانلودر جهانی و قدرتمند: استفاده از 100% توان yt-dlp بدون نیاز به ffmpeg
    """
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    # استراتژی انتخاب فرمت:
    # 'best[vcodec!=none][acodec!=none]' -> بهترین فایلی که هم ویدیو دارد هم صدا (Muxed)
    # '/best' -> اگر اولی نبود، هر فایلی که بهترین بود
    # این استراتژی 100% نیاز به ffmpeg را در اندروید حذف می‌کند.
    
    ydl_opts = {
        'format': 'best[vcodec!=none][acodec!=none]/best',
        'outtmpl': os.path.join(download_dir, '%(title).80s.%(ext)s'),
        'restrictfilenames': True,
        'noplaylist': True,
        'nocheckcertificate': True,
        'proxy': PROXY,
        'quiet': True,
        'no_warnings': True,
        # شبیه‌سازی کلاینت‌های مختلف برای دور زدن محدودیت‌های جدید یوتیوب
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios', 'web'],
                'skip': ['hls', 'dash'] # صرف‌نظر از استریم‌های تکه‌تکه که نیاز به ffmpeg دارند
            }
        },
        'user_agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36'
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # 1. دریافت اطلاعات بدون دانلود برای استخراج عنوان
            info = ydl.extract_info(url, download=True)
            file_path = ydl.prepare_filename(info)
            
            # 2. مدیریت تغییر فرمت در لحظه آخر (مانند mkv به webm)
            if not os.path.exists(file_path):
                base = os.path.splitext(file_path)[0]
                for f in os.listdir(download_dir):
                    if f.startswith(os.path.basename(base)):
                        file_path = os.path.join(download_dir, f)
                        break

            if os.path.exists(file_path):
                title = info.get('title', 'Video')
                return [file_path, title]
            else:
                return ["ERROR", "فایل دانلود شد اما در مسیر نهایی یافت نشد."]

    except Exception as e:
        # اگر خطا مربوط به فرمت بود، تلاش با ساده‌ترین حالت ممکن (تضمینی)
        if "format is not available" in str(e).lower():
            return _fallback_simple_download(url, download_dir)
        return ["ERROR", f"yt-dlp: {str(e)}"]

def _fallback_simple_download(url, download_dir):
    """آخرین سنگر: دانلود با فرمت پایه 'b' که همیشه یک فایل واحد می‌دهد"""
    try:
        ydl_opts = {
            'format': 'b',
            'outtmpl': os.path.join(download_dir, 'fallback_%(id)s.%(ext)s'),
            'proxy': PROXY,
            'quiet': True,
            'noplaylist': True,
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            return [ydl.prepare_filename(info), info.get('title', 'Fallback Video')]
    except Exception as e:
        return ["ERROR", f"حتی دانلود ساده هم شکست خورد: {str(e)}"]

def get_media_type(url):
    try:
        with yt_dlp.YoutubeDL({'quiet': True, 'proxy': PROXY}) as ydl:
            info = ydl.extract_info(url, download=False)
            ext = info.get('ext', '').lower()
            return "photo" if ext in ['jpg', 'jpeg', 'png', 'webp'] else "video"
    except:
        return "video"
