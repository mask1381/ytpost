import re

# Simple map for Bold-Italic Unicode (A-Z, a-z, 0-9)
FANCY_MAP = {
    'A': '𝘼', 'B': '𝘽', 'C': '𝘾', 'D': '𝘿', 'E': '𝙀', 'F': '𝙁', 'G': '𝙂', 'H': '𝙃', 'I': '𝙄', 'J': '𝙅',
    'K': '𝙆', 'L': '𝙇', 'M': '𝙈', 'N': '𝙉', 'O': '𝙊', 'P': '𝙋', 'Q': '𝙌', 'R': '𝙍', 'S': '𝙎', 'T': '𝙏',
    'U': '𝙐', 'V': '𝙑', 'W': '𝙒', 'X': '𝙓', 'Y': '𝙔', 'Z': '𝙕',
    'a': '𝙖', 'b': '𝙗', 'c': '𝙘', 'd': '𝙙', 'e': '𝙚', 'f': '𝙛', 'g': '𝙜', 'h': '𝙝', 'i': '𝙞', 'j': '𝙟',
    'k': '𝙠', 'l': '𝙡', 'm': '𝙢', 'n': '𝙣', 'o': '𝙤', 'p': '𝙥', 'q': '𝙦', 'r': '𝙧', 's': '𝙨', 't': '𝙩',
    'u': '𝙪', 'v': '𝙫', 'w': '𝙬', 'x': '𝙭', 'y': '𝙮', 'z': '𝙯',
    '0': '𝟬', '1': '𝟭', '2': '𝟮', '3': '𝟯', '4': '𝟰', '5': '𝟱', '6': '𝟲', '7': '𝟳', '8': '𝟴', '9': '𝟵'
}

def to_fancy(text):
    return "".join(FANCY_MAP.get(c, c) for c in text)

def build_caption(title, description=None, signature=None):
    """
    Builds a professional HTML caption with fancy fonts and signature.
    """
    # 1. Extract hashtags
    all_content = f"{title} {description or ''}"
    hashtags = re.findall(r'#\w+', all_content)
    # Deduplicate hashtags
    seen = set()
    unique_hashtags = [x for x in hashtags if not (x in seen or seen.add(x))]
    
    # 2. Clean the title (remove hashtags from it)
    clean_title = title
    for tag in hashtags:
        clean_title = clean_title.replace(tag, "")
    clean_title = clean_title.strip()
    
    # 3. Apply fancy font to title
    fancy_title = to_fancy(clean_title)
    
    # 4. Build HTML Structure
    html = f"🎬 <b>{fancy_title}</b>\n\n"
    
    if description:
        # Take first 2 non-empty lines
        lines = [l.strip() for l in description.split('\n') if l.strip()]
        if lines:
            short_desc = "\n".join(lines[:2])
            html += f"<blockquote>{short_desc}</blockquote>\n\n"
            
    if unique_hashtags:
        html += " ".join(unique_hashtags) + "\n\n"
        
    # 5. Fixed Signature
    if not signature:
        signature = '🌸 <a href="https://t.me/genshinworldsensei">GWS | Teyvat Archive</a>'
    
    html += signature
    
    return html.strip()

def extract_video_id(url):
    patterns = [
        r'(?:v=|\/)([0-9A-Za-z_-]{11}).*',
        r'(?:embed\/)([0-9A-Za-z_-]{11}).*',
        r'(?:be\/)([0-9A-Za-z_-]{11}).*'
    ]
    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None
