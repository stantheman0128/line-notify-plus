import os
import time
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

# Play Store feature graphic — 去 LINE 化版本 (2026-06-23)。
# 起因:vc12 上傳被 Impersonation policy 退件,證據包含舊 feature graphic
# 把綠氣泡 icon + LINE 綠 + 「LINE Notify+」+「重新定義你的 LINE 通知體驗」全堆上去。
# 新版:中性靛藍、堆疊通知卡(代表多則合併成一條)、標題只有 Notify+、不含 LINE 字、不放綠氣泡。

W, H = 1024, 500
ACCENT = (79, 70, 229)        # indigo,刻意避開 LINE 綠
DARK = (26, 26, 26)
SUB = (96, 96, 104)
GREY1 = (120, 120, 128)
GREY2 = (188, 188, 196)
WHITE = (255, 255, 255)

OUT_PATH = Path(__file__).parent / "feature-graphic.png"

img = Image.new('RGB', (W, H), (252, 252, 253))
draw = ImageDraw.Draw(img)

# 背景:左到右極淺灰漸層
for x in range(W):
    t = x / W
    v = int(253 - 10 * t)
    draw.line([(x, 0), (x, H)], fill=(v, v, v))

# 左側:三張堆疊的通知卡,代表「同一聊天室多則訊息合併成一條」
card_w, card_h = 320, 96
cx, base_y = 96, 150
stack = [(-22, -40, (232, 232, 236)),
         (-2, -2, (242, 242, 245)),
         (20, 38, (255, 255, 255))]
for dx, dy, fill in stack:
    x0, y0 = cx + dx, base_y + dy
    box = [x0, y0, x0 + card_w, y0 + card_h]
    sh = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        [box[0], box[1] + 7, box[2], box[3] + 7], radius=22, fill=(0, 0, 0, 28))
    img.paste(sh, (0, 0), sh)
    draw.rounded_rectangle(box, radius=22, fill=fill)

# 最前面那張卡:強調色圓形 avatar + 兩條文字線
fx, fy = cx + 20, base_y + 38
draw.ellipse([fx + 20, fy + 28, fx + 60, fy + 68], fill=ACCENT)
draw.rounded_rectangle([fx + 76, fy + 30, fx + 272, fy + 44], radius=7, fill=GREY1)
draw.rounded_rectangle([fx + 76, fy + 54, fx + 220, fy + 66], radius=7, fill=GREY2)

font_bold = "C:/Windows/Fonts/msjhbd.ttc"
try:
    title_font = ImageFont.truetype(font_bold, 96)
    tagline_font = ImageFont.truetype(font_bold, 34)
    badge_font = ImageFont.truetype(font_bold, 30)
except OSError:
    title_font = ImageFont.load_default()
    tagline_font = ImageFont.load_default()
    badge_font = ImageFont.load_default()

# 合併計數徽記(「9+」代表多則合併),壓在卡片右上角
badge_r = 27
bx, by = cx + 20 + card_w - 6, base_y + 38 - 6
draw.ellipse([bx - badge_r, by - badge_r, bx + badge_r, by + badge_r], fill=ACCENT)
draw.text((bx, by), "9+", font=badge_font, fill=WHITE, anchor='mm')

# 右側:標題 + tagline
text_x = 482
title = "Notify+"
tagline = "洗版的訊息通知，整理成一條"

t_ascent, t_descent = title_font.getmetrics()
g_ascent, g_descent = tagline_font.getmetrics()
title_h = t_ascent + t_descent
tagline_h = g_ascent + g_descent
gap = 30
total_h = title_h + gap + tagline_h
y_start = (H - total_h) // 2

draw.text((text_x, y_start), title, font=title_font, fill=DARK, anchor='lt')
draw.text((text_x, y_start + title_h + gap), tagline,
          font=tagline_font, fill=ACCENT, anchor='lt')

tmp_path = OUT_PATH.with_suffix('.tmp.png')
img.save(tmp_path, 'PNG', optimize=True)

for attempt in range(5):
    try:
        os.replace(tmp_path, OUT_PATH)
        break
    except OSError:
        if attempt == 4:
            raise
        time.sleep(0.3)

print(f"Created {W}x{H} feature graphic at {OUT_PATH}")
print(f"Size: {OUT_PATH.stat().st_size:,} bytes")
