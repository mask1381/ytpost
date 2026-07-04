import re

def build_caption(title: str, source_url: str):
    """
    استخراج هشتگ‌ها، تمیز کردن عنوان و ساخت کپشن نهایی
    """
    # استخراج هشتگ‌ها (مواردی که با # شروع می‌شوند)
    hashtags = re.findall(r'#\w+', title)
    
    # پاک کردن هشتگ‌ها از متن عنوان
    clean_title = title
    for tag in hashtags:
        clean_title = clean_title.replace(tag, "")
    
    # حذف فاصله‌های اضافی
    clean_title = clean_title.strip()
    
    # ساخت متن هشتگ‌ها برای کپشن
    hashtag_str = " ".join(hashtags)
    
    # فرمت نهایی
    caption = f"🎬 {clean_title}\n\n{hashtag_str}\n\n📎 Join: https://t.me/genshinworldsensei"
    
    return caption.strip()
