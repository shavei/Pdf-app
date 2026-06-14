"""
Connect IQ Store cover image (500x500, < 300 KB): the calendar + aleph
launcher art with the app name below, on a dark branded card.

Rendered on a 3x supersampled canvas and downscaled (LANCZOS) for crisp
edges. Shares fonts/helpers with the rest of the store images via storelib.
"""

import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import storelib

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(PROJ, "bin", "store_images", "cover_500.png")

FONT_HE = storelib.find_font([
    r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Bold.ttf",
    r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Regular.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
])
FONT_EN = storelib.find_font([
    r"C:\Windows\Fonts\segoeuisb.ttf",
    r"C:\Windows\Fonts\segoeui.ttf",
])

# ---- 500px design space, drawn at 3x ----
PX = 500
SS = 3
BIG = PX * SS


def S(v):
    return int(round(v * SS))


PAGE = (244, 244, 244)
GRAY = (158, 158, 158)
BLACK = (10, 10, 12)

# subtle dark vertical gradient for depth
img = storelib.gradient_v(BIG, BIG, top=(26, 27, 32), bot=(9, 9, 12)).convert("RGBA")

# --- soft shadow under the icon (lifts it off the card) ---
ICON = 230
ox, oy = (PX - ICON) // 2, 58
u = ICON / 24.0


def pt(x, y, off=0):
    return (S(ox + x * u), S(oy + y * u + off))


sh = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
ImageDraw.Draw(sh).rounded_rectangle([pt(4, 3, 8), pt(20, 21, 8)],
                                     radius=S(2.5 * u), fill=(0, 0, 0, 130))
sh = sh.filter(ImageFilter.GaussianBlur(S(7)))
img = Image.alpha_composite(img, sh)
d = ImageDraw.Draw(img)

# --- calendar icon (24-unit grid, same geometry as the launcher icon) ---
d.rounded_rectangle([pt(4, 3), pt(20, 21)], radius=S(2.5 * u), fill=PAGE)
d.rounded_rectangle([pt(4, 3), pt(20, 9.5)], radius=S(2.5 * u), fill=GRAY)
d.rectangle([pt(4, 7), pt(20, 9.5)], fill=PAGE)
d.rounded_rectangle([pt(8, 1.5), pt(9.6, 5)], radius=S(0.8 * u), fill=PAGE)
d.rounded_rectangle([pt(14.4, 1.5), pt(16, 5)], radius=S(0.8 * u), fill=PAGE)

f_aleph = ImageFont.truetype(FONT_HE, S(9.5 * u * 1.05))
bb = f_aleph.getbbox("א")
d.text((S(ox + 12 * u) - (bb[2] - bb[0]) / 2 - bb[0],
        S(oy + 14 * u) - (bb[3] - bb[1]) / 2 - bb[1]),
       "א", font=f_aleph, fill=BLACK)


# --- app name + subtitle ---
def centered(text, font_path, size, y, fill):
    f = ImageFont.truetype(font_path, S(size))
    bb = f.getbbox(text)
    d.text((BIG / 2 - (bb[2] - bb[0]) / 2 - bb[0], S(y)), text, font=f, fill=fill)


centered("לוח עברי"[::-1], FONT_HE, 58, 332, (255, 255, 255))   # PIL is LTR
centered("Hebrew Calendar", FONT_EN, 28, 416, (150, 152, 158))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.convert("RGB").resize((PX, PX), Image.LANCZOS).save(OUT, "PNG", optimize=True)
kb = os.path.getsize(OUT) / 1024
print(f"cover_500.png: {PX}x{PX}, {kb:.0f} KB"
      + ("  ** OVER 300 KB **" if kb > 300 else ""))
