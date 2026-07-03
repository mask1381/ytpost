import asyncio
import feedparser

def _ensure_event_loop():
    try:
        asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

def fetch_new_items(url):
    """
    دریافت آیتم‌های جدید از یک فید RSS.
    خروجی: لیستی از URLها
    """
    _ensure_event_loop()
    try:
        feed = feedparser.parse(url)
        links = []
        for entry in feed.entries:
            links.append(entry.link)
        return links
    except Exception as e:
        print(f"Error fetching RSS: {e}")
        return []
