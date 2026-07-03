import yt_dlp
import os

def download_video(url, download_dir):
    """
    دانلود ویدیو با استفاده از yt-dlp
    خروجی: مسیر فایل دانلود شده یا ERROR
    """
    if not os.path.exists(download_dir):
        os.makedirs(download_dir)

    ydl_opts = {
        'outtmpl': os.path.join(download_dir, '%(title)s.%(ext)s'),
        'format': 'best',
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            file_path = ydl.prepare_filename(info)
            return file_path
    except Exception as e:
        return f"ERROR: {str(e)}"
