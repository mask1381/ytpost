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
    # حذف بخش Join تکراری اگر از قبل در متن وجود داشته باشد
    base_caption = f"🎬 {clean_title}\n\n{hashtag_str}".strip()
    final_caption = f"{base_caption}"
    
    return final_caption.strip()
