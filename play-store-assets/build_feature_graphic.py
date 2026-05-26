import os
import time
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

W, H = 1024, 500
LINE_GREEN = (6, 199, 85)
DARK = (26, 26, 26)
WHITE = (255, 255, 255)

ROOT = Path(__file__).parent.parent
ICON_PATH = ROOT / "index" / "composer-2.5" / "icons" / "exports" / "play_store_icon.png"
OUT_PATH = Path(__file__).parent / "feature-graphic.png"

img = Image.new('RGB', (W, H), WHITE)
draw = ImageDraw.Draw(img)

for x in range(W):
    t = x / W
    v = int(255 - 10 * t)
    draw.line([(x, 0), (x, H)], fill=(v, v, v))

icon = Image.open(ICON_PATH).convert('RGBA')
icon_size = 360
icon = icon.resize((icon_size, icon_size), Image.LANCZOS)
icon_x = 60
icon_y = (H - icon_size) // 2

corner_radius = int(icon_size * 0.22)
mask = Image.new('L', (icon_size, icon_size), 0)
ImageDraw.Draw(mask).rounded_rectangle(
    [0, 0, icon_size, icon_size], radius=corner_radius, fill=255)
icon.putalpha(mask)

shadow_offset = 12
shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
shadow_draw = ImageDraw.Draw(shadow)
for blur_r in range(20, 0, -2):
    alpha = max(0, 8 - blur_r // 4)
    shadow_draw.rounded_rectangle(
        [icon_x - blur_r, icon_y - blur_r + shadow_offset,
         icon_x + icon_size + blur_r, icon_y + icon_size + blur_r + shadow_offset],
        radius=corner_radius + blur_r, fill=(0, 0, 0, alpha))
img.paste(shadow, (0, 0), shadow)

img.paste(icon, (icon_x, icon_y), icon)

font_bold = "C:/Windows/Fonts/msjhbd.ttc"
try:
    title_font = ImageFont.truetype(font_bold, 82)
    tagline_font = ImageFont.truetype(font_bold, 36)
except OSError:
    title_font = ImageFont.load_default()
    tagline_font = ImageFont.load_default()

text_x = icon_x + icon_size + 40
title = "LINE Notify+"
tagline = "重新定義你的 LINE 通知體驗"

t_ascent, t_descent = title_font.getmetrics()
g_ascent, g_descent = tagline_font.getmetrics()
title_h = t_ascent + t_descent
tagline_h = g_ascent + g_descent

gap = 28
total_h = title_h + gap + tagline_h
y_start = (H - total_h) // 2

draw.text((text_x, y_start), title, font=title_font, fill=DARK, anchor='lt')
draw.text((text_x, y_start + title_h + gap), tagline,
          font=tagline_font, fill=LINE_GREEN, anchor='lt')

tmp_path = OUT_PATH.with_suffix('.tmp.png')
img.save(tmp_path, 'PNG', optimize=True)

for attempt in range(5):
    try:
        os.replace(tmp_path, OUT_PATH)
        break
    except OSError as e:
        if attempt == 4:
            raise
        time.sleep(0.3)

print(f"Created {W}x{H} feature graphic at {OUT_PATH}")
print(f"Size: {OUT_PATH.stat().st_size:,} bytes")
