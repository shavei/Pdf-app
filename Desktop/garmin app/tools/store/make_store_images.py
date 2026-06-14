"""
Build the Connect IQ Store gallery screenshots from the v15 simulator
captures (bin/shots/v15) into bin/store_images/.

The Garmin dashboard allows at most 5 gallery images, so this emits 5.

For each pick:
  - crop_watch() lifts the watch from the sim capture onto a tight white crop
  - that clean crop is saved as the .png twin (used by make_hero.py)
  - a styled .jpg card is rendered for upload: the watch cut out (anti-aliased)
    and placed on a subtle gradient with a soft drop shadow, matching the hero
"""

import os
from PIL import Image
import storelib

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.abspath(os.path.join(HERE, "..", ".."))
SHOTS = os.path.join(PROJ, "bin", "shots", "v15")
OUT = os.path.join(PROJ, "bin", "store_images")
MAX_BYTES = 150 * 1024

# Styled card canvas (portrait) — every watch is fit into the same box so the
# five cards look consistent in the carousel.
CARD_W, CARD_H = 640, 800
BOX_W, BOX_H = int(CARD_W * 0.84), int(CARD_H * 0.82)

# 5-image gallery (Garmin max), feature-forward for v1.6.0: date, then the new
# Omer / next-event surfaces across AMOLED, MIP and Instinct families, then glance.
PICKS = [
    ("venu3_widget_date.png",          "1_venu3_date.jpg"),
    ("epix2_widget_omer.png",          "2_epix2_omer.jpg"),
    ("fr55_widget_event.png",          "3_fr55_event.jpg"),
    ("instinct3solar_omer_page.png",   "4_solar_omer.jpg"),
    ("fr265_glance.png",               "5_fr265_glance.jpg"),
]


def crop_watch(img):
    g = img.convert("L")
    w, h = g.size
    px = g.load()

    def row_stats(y):
        gray = dark = 0
        for x in range(0, w, 2):
            v = px[x, y]
            if 215 <= v <= 250:
                gray += 1
            if v < 180:
                dark += 1
        n = len(range(0, w, 2))
        return gray / n, dark / n

    # Pass A: cut at the first status-bar row (mostly light gray) or full-black
    # canvas row; everything below is junk.
    cut = h
    for y in range(20, h):
        gray_f, dark_f = row_stats(y)
        if gray_f > 0.65 or dark_f > 0.97:
            cut = y
            break
    rows = range(0, cut, 3)
    n = len(rows)

    def white_frac(x):
        return sum(1 for y in rows if px[x, y] >= 235) / n

    xs = [x for x in range(w) if white_frac(x) > 0.04]
    x_start, x_end = (xs[0], xs[-1] + 1) if xs else (0, w)
    img2 = img.crop((x_start, 0, x_end, cut))

    # Pass B: bbox of clearly-dark content (<200 skips faint window-border lines)
    g2 = img2.convert("L").point(lambda v: 255 if v < 200 else 0)
    bbox = g2.getbbox()
    if bbox is not None:
        img2 = img2.crop(bbox)

    # Clean white border (also guarantees pure-white corners for the cut-out)
    m = 16
    out = Image.new("RGB", (img2.width + 2 * m, img2.height + 2 * m),
                    (255, 255, 255))
    out.paste(img2, (m, m))
    return out


def style_card(crop):
    """Render a clean white-bg crop into a consistent gradient card with a
    cut-out watch + soft drop shadow."""
    scale = storelib.fit_scale(crop.width, crop.height, BOX_W, BOX_H)
    rw, rh = max(1, round(crop.width * scale)), max(1, round(crop.height * scale))
    watch = crop.resize((rw, rh), Image.LANCZOS)
    rgba, alpha = storelib.cutout(watch)

    card = storelib.gradient_v(CARD_W, CARD_H).convert("RGBA")
    x = (CARD_W - rw) // 2
    y = (CARD_H - rh) // 2 - 8
    storelib.paste_with_shadow(card, rgba, alpha, x, y,
                               blur=22, opacity=0.30, dy=18)
    return card.convert("RGB")


def save_jpeg_under(img, path, max_bytes):
    for q in (92, 88, 84, 78, 72, 66):
        img.save(path, "JPEG", quality=q, optimize=True)
        if os.path.getsize(path) < max_bytes:
            return q
    return q


os.makedirs(OUT, exist_ok=True)
for src, dst in PICKS:
    cap = Image.open(os.path.join(SHOTS, src)).convert("RGB")
    crop = crop_watch(cap)

    # Clean white-bg twin for the hero compositor
    crop.save(os.path.join(OUT, dst.replace(".jpg", ".png")), "PNG", optimize=True)

    # Styled card for the store
    out_path = os.path.join(OUT, dst)
    q = save_jpeg_under(style_card(crop), out_path, MAX_BYTES)
    kb = os.path.getsize(out_path) / 1024
    print(f"  {dst}: {CARD_W}x{CARD_H}, q{q}, {kb:.0f} KB")
print(f"Done! {len(PICKS)} gallery cards (Garmin allows max 5).")
