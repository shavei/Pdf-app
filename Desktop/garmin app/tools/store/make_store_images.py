"""
Crop simulator captures to just the watch and save as <150KB JPEGs
for the Connect IQ Store listing (bin/store_images/).

Pass A: cut the capture at the status bar (a band of light-gray rows)
and drop full-black canvas rows. Pass B: bbox the dark watch against
the white window background.
"""

from PIL import Image
import os

SHOTS = r"C:\Users\yosef\Desktop\garmin app\bin\shots\v15"
OUT = r"C:\Users\yosef\Desktop\garmin app\bin\store_images"
MAX_BYTES = 150 * 1024

# v1.5.0 gallery: lead with the date page, then showcase the new weekly
# parasha page across MIP + AMOLED families, finish with the glance.
PICKS = [
    ("instinct2_widget_date.png",  "1_instinct2_date.jpg"),
    ("fenix7_widget_date.png",     "2_fenix7_date.jpg"),
    ("venu3_widget_parasha.png",   "3_venu3_parasha.jpg"),
    ("fr965_widget_parasha.png",   "4_fr965_parasha.jpg"),
    ("fr955_widget_parasha.png",   "5_fr955_parasha.jpg"),
    ("fenix7_glance.png",          "6_fenix7_glance.jpg"),
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

    # Pass A: find the first status-bar row (mostly light gray) or
    # full-black canvas row; everything below is junk.
    cut = h
    for y in range(20, h):
        gray_f, dark_f = row_stats(y)
        if gray_f > 0.65 or dark_f > 0.97:
            cut = y
            break
    # The watch sits on the white window background; canvas/divider areas
    # to its right have no white at all. Keep only columns with some white.
    rows = range(0, cut, 3)
    n = len(rows)

    def white_frac(x):
        return sum(1 for y in rows if px[x, y] >= 235) / n

    xs = [x for x in range(w) if white_frac(x) > 0.04]
    x_start, x_end = (xs[0], xs[-1] + 1) if xs else (0, w)
    img2 = img.crop((x_start, 0, x_end, cut))

    # Pass B: bbox of clearly-dark content (<200 skips faint gray
    # window-border lines that would otherwise frame the crop)
    g2 = img2.convert("L").point(lambda v: 255 if v < 200 else 0)
    bbox = g2.getbbox()
    if bbox is None:
        return img2
    x0, y0, x1, y1 = bbox
    img2 = img2.crop((x0, y0, x1, y1))

    # Clean white border
    m = 16
    out = Image.new("RGB", (img2.width + 2 * m, img2.height + 2 * m),
                    (255, 255, 255))
    out.paste(img2, (m, m))
    return out


os.makedirs(OUT, exist_ok=True)
for src, dst in PICKS:
    img = Image.open(os.path.join(SHOTS, src)).convert("RGB")
    img = crop_watch(img)
    out_path = os.path.join(OUT, dst)
    for q in (92, 88, 84, 78, 70):
        img.save(out_path, "JPEG", quality=q, optimize=True)
        if os.path.getsize(out_path) < MAX_BYTES:
            break
    # Lossless twin (pure white bg) for compositing the hero image
    img.save(out_path.replace(".jpg", ".png"), "PNG", optimize=True)
    kb = os.path.getsize(out_path) / 1024
    print(f"  {dst}: {img.size[0]}x{img.size[1]}, {kb:.0f} KB")
print("Done!")
