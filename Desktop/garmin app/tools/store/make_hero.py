"""
Connect IQ Store hero image (1440x720, < 2048 KB).

Layout: branded left panel (app icon tile + Hebrew/English name + feature
tagline) and two device shots on the right (FR965 parasha page + Instinct 2
date page).

Quality technique:
  - The whole synthetic layer (gradient, icon tile, hand-drawn calendar,
    type) is rendered on a SUPERSAMPLED canvas (SSx) and downscaled with
    LANCZOS, so text and icon edges are crisp.
  - Watch shots are cut from their clean white-bg PNG twins (emitted by
    make_store_images.py) with an anti-aliased corner flood-fill silhouette
    and given a soft drop shadow. Shared look lives in storelib.py.
"""

import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import storelib

# ---------------------------------------------------------------- paths (robust)
HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.abspath(os.path.join(HERE, "..", ".."))
BASE = os.path.join(PROJ, "bin", "store_images")
OUT = os.path.join(BASE, "hero_1440x720.png")

FONT_HE = storelib.find_font([
    r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Regular.ttf",
    r"C:\Windows\Fonts\arial.ttf",
])
FONT_HE_BOLD = storelib.find_font([
    r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Bold.ttf",
    FONT_HE,
])
FONT_EN = storelib.find_font([
    r"C:\Windows\Fonts\segoeui.ttf",
    r"C:\Windows\Fonts\arial.ttf",
])
FONT_EN_SB = storelib.find_font([
    r"C:\Windows\Fonts\segoeuisb.ttf",
    FONT_EN,
])

# ---------------------------------------------------------------- canvas + supersample
W, H = 1440, 720
SS = 3                                   # supersample factor for the synthetic layer
BW, BH = W * SS, H * SS


def S(v):
    return int(round(v * SS))


big = storelib.gradient_v(BW, BH)

# ---------------------------------------------------------------- left: icon tile
TILE = 210
tx, ty = 188, 116
# soft shadow under the tile, on its own layer so it can blur
tile_sh = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
ImageDraw.Draw(tile_sh).rounded_rectangle(
    [S(tx), S(ty + 10), S(tx + TILE), S(ty + TILE + 10)],
    radius=S(46), fill=(15, 18, 28, 70))
tile_sh = tile_sh.filter(ImageFilter.GaussianBlur(S(9)))
big = Image.alpha_composite(big.convert("RGBA"), tile_sh).convert("RGB")
d = ImageDraw.Draw(big)
d.rounded_rectangle([S(tx), S(ty), S(tx + TILE), S(ty + TILE)],
                    radius=S(46), fill=(12, 12, 14))

# calendar art on its 24-unit grid, centred in the tile
u = TILE * 0.82 / 24.0
ox = tx + (TILE - 24 * u) / 2
oy = ty + (TILE - 24 * u) / 2
PAGE = (244, 244, 244)
GRAY = (158, 158, 158)


def pt(x, y):
    return (S(ox + x * u), S(oy + y * u))


d.rounded_rectangle([pt(4, 3), pt(20, 21)], radius=S(2.5 * u), fill=PAGE)
d.rounded_rectangle([pt(4, 3), pt(20, 9.5)], radius=S(2.5 * u), fill=GRAY)
d.rectangle([pt(4, 7), pt(20, 9.5)], fill=PAGE)
d.rounded_rectangle([pt(8, 1.5), pt(9.6, 5)], radius=S(0.8 * u), fill=PAGE)
d.rounded_rectangle([pt(14.4, 1.5), pt(16, 5)], radius=S(0.8 * u), fill=PAGE)

f_aleph = ImageFont.truetype(FONT_HE_BOLD, S(9.5 * u * 1.05))
bb = f_aleph.getbbox("א")
d.text((S(ox + 12 * u) - (bb[2] - bb[0]) / 2 - bb[0],
        S(oy + 14 * u) - (bb[3] - bb[1]) / 2 - bb[1]),
       "א", font=f_aleph, fill=(10, 10, 12))

# ---------------------------------------------------------------- left: type
cx_left = tx + TILE / 2


def draw_centered(text, font_path, size, y, fill):
    f = ImageFont.truetype(font_path, S(size))
    bb = f.getbbox(text)
    d.text((S(cx_left) - (bb[2] - bb[0]) / 2 - bb[0], S(y)), text, font=f, fill=fill)


draw_centered("לוח עברי"[::-1], FONT_HE_BOLD, 108, 384, (18, 18, 22))   # PIL is LTR
draw_centered("Hebrew Calendar", FONT_EN_SB, 47, 536, (90, 92, 98))

# thin accent rule + feature tagline
rule_w = 250
d.rounded_rectangle([S(cx_left - rule_w / 2), S(612), S(cx_left + rule_w / 2), S(615)],
                    radius=S(2), fill=(206, 210, 216))
draw_centered("GLANCE  ·  WIDGET  ·  WEEKLY PARASHA", FONT_EN_SB, 22, 632,
              (132, 135, 142))

# ---------------------------------------------------------------- downscale synthetic layer
hero = big.resize((W, H), Image.LANCZOS).convert("RGBA")

# ---------------------------------------------------------------- right: device shots
def place(name, target_h, x, y):
    img = Image.open(os.path.join(BASE, name)).convert("RGB")
    w = round(img.width * target_h / img.height)
    img = img.resize((w, target_h), Image.LANCZOS)
    rgba, alpha = storelib.cutout(img)
    storelib.paste_with_shadow(hero, rgba, alpha, x, y, blur=20, opacity=0.32, dy=16)
    return w


# FR965 (parasha) large + Instinct 2 (date) secondary. Sizes chosen so both
# fit the right zone (x 565..~1400) with a gap and a right margin.
big_h = 560
big_x = 565
w1 = place("2_fr965_parasha.png", big_h, big_x, (H - big_h) // 2)
small_h = 408
place("3_instinct2_date.png", small_h, big_x + w1 + 28, H - small_h - 70)

# ---------------------------------------------------------------- save
hero.convert("RGB").save(OUT, "PNG", optimize=True)
kb = os.path.getsize(OUT) / 1024
print(f"hero_1440x720.png: {hero.size}, {kb:.0f} KB"
      + ("  ** OVER 2048 KB **" if kb > 2048 else ""))
