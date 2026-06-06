"""
LINE 官方帳號「商業簡介」封面圖。

與 Play Store 的 feature-graphic（1024x500 寬版）不同：LINE OA 封面較高、近正方，
故改成「置中直式堆疊」版面（icon 上 / 名稱中 / 標語下），所有內容收在中央安全區，
即使被裁成寬條或方形都不會切到主體。

預設尺寸 1080x878（LINE OA 首頁封面常見建議值）。若實際需要別的比例，改 W/H 重跑即可。
複用 canonical icon（play-store-icon-512.png）與微軟正黑體，配色沿用 App 主題綠。
"""
import os
import time
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

W, H = 1080, 878
LINE_GREEN = (6, 199, 85)
DARK = (26, 26, 26)
WHITE = (255, 255, 255)
LIGHT_GREEN = (240, 249, 242)  # 與 App 主題 LightGreen 一致

ICON_PATH = Path(__file__).parent / "play-store-icon-512.png"
OUT_PATH = Path(__file__).parent / "line-oa-cover.png"

img = Image.new("RGB", (W, H), WHITE)
draw = ImageDraw.Draw(img)

# 由上而下：白 → 極淡綠 的柔和漸層
for y in range(H):
    t = y / H
    r = int(WHITE[0] + (LIGHT_GREEN[0] - WHITE[0]) * t)
    g = int(WHITE[1] + (LIGHT_GREEN[1] - WHITE[1]) * t)
    b = int(WHITE[2] + (LIGHT_GREEN[2] - WHITE[2]) * t)
    draw.line([(0, y), (W, y)], fill=(r, g, b))

# icon（圓角遮罩）
icon = Image.open(ICON_PATH).convert("RGBA")
icon_size = 320
icon = icon.resize((icon_size, icon_size), Image.LANCZOS)
corner_radius = int(icon_size * 0.22)
mask = Image.new("L", (icon_size, icon_size), 0)
ImageDraw.Draw(mask).rounded_rectangle(
    [0, 0, icon_size, icon_size], radius=corner_radius, fill=255)
icon.putalpha(mask)

# 字體
font_bold = "C:/Windows/Fonts/msjhbd.ttc"
try:
    title_font = ImageFont.truetype(font_bold, 88)
    tagline_font = ImageFont.truetype(font_bold, 40)
except OSError:
    title_font = ImageFont.load_default()
    tagline_font = ImageFont.load_default()

title = "LINE Notify+"
tagline = "重新定義你的 LINE 通知體驗"

t_asc, t_desc = title_font.getmetrics()
title_h = t_asc + t_desc
g_asc, g_desc = tagline_font.getmetrics()
tagline_h = g_asc + g_desc

gap_icon = 56
gap_text = 24
total_h = icon_size + gap_icon + title_h + gap_text + tagline_h
y0 = (H - total_h) // 2
cx = W // 2

# icon 陰影
icon_x = cx - icon_size // 2
icon_y = y0
shadow_offset = 14
shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = ImageDraw.Draw(shadow)
for blur_r in range(22, 0, -2):
    alpha = max(0, 8 - blur_r // 4)
    sd.rounded_rectangle(
        [icon_x - blur_r, icon_y - blur_r + shadow_offset,
         icon_x + icon_size + blur_r, icon_y + icon_size + blur_r + shadow_offset],
        radius=corner_radius + blur_r, fill=(0, 0, 0, alpha))
img.paste(shadow, (0, 0), shadow)
img.paste(icon, (icon_x, icon_y), icon)

# 名稱 + 標語（水平置中）
draw.text((cx, y0 + icon_size + gap_icon),
          title, font=title_font, fill=DARK, anchor="ma")
draw.text((cx, y0 + icon_size + gap_icon + title_h + gap_text),
          tagline, font=tagline_font, fill=LINE_GREEN, anchor="ma")

tmp_path = OUT_PATH.with_suffix(".tmp.png")
img.save(tmp_path, "PNG", optimize=True)
for attempt in range(5):
    try:
        os.replace(tmp_path, OUT_PATH)
        break
    except OSError:
        if attempt == 4:
            raise
        time.sleep(0.3)

print(f"Created {W}x{H} LINE OA cover at {OUT_PATH}")
print(f"Size: {OUT_PATH.stat().st_size:,} bytes")
