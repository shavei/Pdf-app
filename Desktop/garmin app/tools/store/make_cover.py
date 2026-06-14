"""
Connect IQ Store cover image (500x500, <300KB):
the calendar + aleph launcher art with the app name below.
Rendered at 2x and downscaled.
"""

from PIL import Image, ImageDraw, ImageFont
import os

OUT = r"C:\Users\yosef\Desktop\garmin app\bin\store_images\cover_500.png"
FONT_HE = r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Regular.ttf"
FONT_EN = r"C:\Windows\Fonts\segoeui.ttf"

S = 1000  # 2x supersample of the 500px target
BG = (12, 12, 14, 255)
PAGE = (244, 244, 244, 255)
GRAY = (154, 154, 154, 255)
BLACK = (10, 10, 12, 255)

img = Image.new("RGBA", (S, S), BG)
d = ImageDraw.Draw(img)

# --- Calendar icon, same geometry as the launcher icon (24-unit grid) ---
ICON = 460                     # icon square size in px
ox, oy = (S - ICON) // 2, 120  # top-centered
u = ICON / 24.0                # grid unit


def pt(x, y):
    return (ox + x * u, oy + y * u)


d.rounded_rectangle([pt(4, 3), pt(20, 21)], radius=2.5 * u, fill=PAGE)
d.rounded_rectangle([pt(4, 3), pt(20, 9.5)], radius=2.5 * u, fill=GRAY)
d.rectangle([pt(4, 7), pt(20, 9.5)], fill=PAGE)
d.rounded_rectangle([pt(8, 1.5), pt(9.6, 5)], radius=0.8 * u, fill=PAGE)
d.rounded_rectangle([pt(14.4, 1.5), pt(16, 5)], radius=0.8 * u, fill=PAGE)

target_h = 9.5 * u
f_aleph = ImageFont.truetype(FONT_HE, int(target_h * 1.05))
bbox = f_aleph.getbbox("א")
gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
d.text((ox + 12 * u - gw / 2 - bbox[0], oy + 14 * u - gh / 2 - bbox[1]),
       "א", font=f_aleph, fill=BLACK)

# --- App name ---
f_he = ImageFont.truetype(FONT_HE, 116)
t = "לוח עברי"[::-1]   # PIL draws codepoints LTR; reverse for Hebrew visual order
bb = f_he.getbbox(t)
d.text(((S - (bb[2] - bb[0])) / 2 - bb[0], 660), t, font=f_he,
       fill=(255, 255, 255, 255))

f_en = ImageFont.truetype(FONT_EN, 58)
t2 = "Hebrew Calendar"
bb2 = f_en.getbbox(t2)
d.text(((S - (bb2[2] - bb2[0])) / 2 - bb2[0], 830), t2, font=f_en,
       fill=(150, 150, 155, 255))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.convert("RGB").resize((500, 500), Image.LANCZOS).save(OUT, "PNG", optimize=True)
print(f"cover_500.png: {os.path.getsize(OUT)/1024:.0f} KB")
