"""
Generate tablet-sized screenshots from the phone screenshots by centering each
phone shot on a tablet-ratio canvas with a soft backdrop.

Play Store tablet specs: each side 320-3840px, aspect ratio within 16:9..9:16.
Portrait tablet canvases (~0.625 ratio) so the tall phone shot fits naturally.

NOT a real tablet UI — just satisfies the upload requirement without stretching.
Real tablet-optimized layout is a v1.2 roadmap item.
"""
import os
import time
from PIL import Image, ImageDraw
from pathlib import Path

SRC = Path(__file__).parent / "screenshots"
OUT = SRC / "tablet"
OUT.mkdir(exist_ok=True)

# auto-pick all top-level NN-*.png phone shots
SHOTS = sorted(p.name for p in SRC.glob("*.png"))

TABLETS = [("7in", 1600, 2560), ("10in", 1800, 2880)]


def soft_backdrop(w, h):
    img = Image.new("RGB", (w, h), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        draw.line([(0, y), (w, y)],
                  fill=(int(244 - 14 * t), int(252 - 8 * t), int(246 - 12 * t)))
    return img


def make(shot_name, label, cw, ch):
    phone = Image.open(SRC / shot_name).convert("RGBA")
    pw, ph = phone.size
    target_h = int(ch * 0.82)
    scale = target_h / ph
    target_w = int(pw * scale)
    phone = phone.resize((target_w, target_h), Image.LANCZOS)

    canvas = soft_backdrop(cw, ch)
    radius = int(target_w * 0.06)
    mask = Image.new("L", (target_w, target_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, target_w, target_h], radius=radius, fill=255)
    phone.putalpha(mask)

    px, py = (cw - target_w) // 2, (ch - target_h) // 2
    shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    for br in range(28, 0, -2):
        sd.rounded_rectangle(
            [px - br, py - br + 10, px + target_w + br, py + target_h + br + 10],
            radius=radius + br, fill=(0, 0, 0, max(0, 6 - br // 6)))
    canvas.paste(shadow, (0, 0), shadow)
    canvas.paste(phone, (px, py), phone)

    out_path = OUT / f"{label}-{shot_name}"
    tmp = out_path.with_suffix(".tmp.png")
    canvas.save(tmp, "PNG", optimize=True)
    for _ in range(5):
        try:
            os.replace(tmp, out_path); break
        except OSError:
            time.sleep(0.3)
    return out_path


count = 0
for label, cw, ch in TABLETS:
    for shot in SHOTS:
        p = make(shot, label, cw, ch)
        count += 1
        print(f"  {p.name}  ({cw}x{ch})")
print(f"Created {count} tablet screenshots from {len(SHOTS)} phone shots")
