"""
Connect IQ Store hero image (1440x720, <2048KB).
White background (matches the screenshot crops), app icon tile + name
on the left, FR965 + Instinct 2 shots on the right.
"""

from PIL import Image, ImageDraw, ImageFont
import os

BASE = r"C:\Users\yosef\Desktop\garmin app\bin\store_images"
OUT = os.path.join(BASE, "hero_1440x720.png")
FONT_HE = r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Regular.ttf"
FONT_EN = r"C:\Windows\Fonts\segoeui.ttf"

W, H = 1440, 720
hero = Image.new("RGB", (W, H), (255, 255, 255))
d = ImageDraw.Draw(hero)

# --- Left: icon tile (dark rounded square with the calendar art) ---
TILE = 210
tx, ty = 200, 120
d.rounded_rectangle([tx, ty, tx + TILE, ty + TILE], radius=46, fill=(12, 12, 14))

u = TILE * 0.82 / 24.0          # calendar drawn on its 24-unit grid
ox = tx + (TILE - 24 * u) / 2
oy = ty + (TILE - 24 * u) / 2
PAGE = (244, 244, 244)
GRAY = (154, 154, 154)


def pt(x, y):
    return (ox + x * u, oy + y * u)


d.rounded_rectangle([pt(4, 3), pt(20, 21)], radius=2.5 * u, fill=PAGE)
d.rounded_rectangle([pt(4, 3), pt(20, 9.5)], radius=2.5 * u, fill=GRAY)
d.rectangle([pt(4, 7), pt(20, 9.5)], fill=PAGE)
d.rounded_rectangle([pt(8, 1.5), pt(9.6, 5)], radius=0.8 * u, fill=PAGE)
d.rounded_rectangle([pt(14.4, 1.5), pt(16, 5)], radius=0.8 * u, fill=PAGE)

f_aleph = ImageFont.truetype(FONT_HE, int(9.5 * u * 1.05))
bb = f_aleph.getbbox("א")
d.text((ox + 12 * u - (bb[2] - bb[0]) / 2 - bb[0],
        oy + 14 * u - (bb[3] - bb[1]) / 2 - bb[1]),
       "א", font=f_aleph, fill=(10, 10, 12))

# --- Left: app name + subtitle ---
f_he = ImageFont.truetype(FONT_HE, 104)
t = "לוח עברי"[::-1]   # PIL draws codepoints LTR; reverse for Hebrew
bb = f_he.getbbox(t)
cx_left = tx + TILE / 2
d.text((cx_left - (bb[2] - bb[0]) / 2 - bb[0], 392), t, font=f_he,
       fill=(20, 20, 24))

f_en = ImageFont.truetype(FONT_EN, 46)
t2 = "Hebrew Calendar"
bb2 = f_en.getbbox(t2)
d.text((cx_left - (bb2[2] - bb2[0]) / 2 - bb2[0], 542), t2, font=f_en,
       fill=(130, 130, 136))

# --- Right: watch shots, masked so the JPEG-white background drops out ---
def paste_watch(name, height, x, y):
    img = Image.open(os.path.join(BASE, name))
    img = img.resize((int(img.width * height / img.height), height),
                     Image.LANCZOS)
    mask = img.convert("L").point(lambda v: 255 if v < 248 else 0)
    hero.paste(img, (x, y), mask)
    return img.width


w1 = paste_watch("4_fr965_parasha.png", 560, 620, (H - 560) // 2)
paste_watch("1_instinct2_date.png", 440, 620 + w1 + 35, H - 440 - 70)

hero.save(OUT, "PNG", optimize=True)
print(f"hero_1440x720.png: {hero.size}, {os.path.getsize(OUT)/1024:.0f} KB")
