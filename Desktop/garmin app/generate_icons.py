"""
Render the launcher icon (calendar page + aleph) at the exact per-device
launcher-icon sizes. PIL re-draw of resources/drawables/launcher_icon.svg
(no SVG rasterizer available on this machine), supersampled then downscaled.

Sizes: 40 (fenix7/fr255/fr955), 56 (vivoactive5), 65 (fenix8 47mm/fr965),
70 (venu2/venu3).
"""

from PIL import Image, ImageDraw, ImageFont
import os

BASE = r"C:\Users\yosef\Desktop\garmin app"
SS = 40          # supersample: 24*40 = 960px master canvas
S = 24 * SS

PAGE = (244, 244, 244, 255)   # #F4F4F4
GRAY = (154, 154, 154, 255)   # #9A9A9A
BLACK = (0, 0, 0, 255)


FONT_PATH = r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Regular.ttf"


def pt(x, y):
    return (x * SS, y * SS)


img = Image.new("RGBA", (S, S), BLACK)
d = ImageDraw.Draw(img)

# Calendar page
d.rounded_rectangle([pt(4, 3), pt(20, 21)], radius=2.5 * SS, fill=PAGE)
# Gray header band (top corners rounded), then restore page color below y=7
d.rounded_rectangle([pt(4, 3), pt(20, 9.5)], radius=2.5 * SS, fill=GRAY)
d.rectangle([pt(4, 7), pt(20, 9.5)], fill=PAGE)
# Binding rings
d.rounded_rectangle([pt(8, 1.5), pt(9.6, 5)], radius=0.8 * SS, fill=PAGE)
d.rounded_rectangle([pt(14.4, 1.5), pt(16, 5)], radius=0.8 * SS, fill=PAGE)

# Aleph: real NotoSansHebrew glyph, centered in the page body (x 4-20, y 7-21)
target_h = 9.5 * SS
font = ImageFont.truetype(FONT_PATH, int(target_h * 1.05))
bbox = font.getbbox("א")
gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
gx = 12 * SS - gw / 2 - bbox[0]
gy = 14 * SS - gh / 2 - bbox[1]
d.text((gx, gy), "א", font=font, fill=BLACK)

TARGETS = {
    40: os.path.join(BASE, "resources-mip260", "drawables"),
    56: os.path.join(BASE, "resources-icon56", "drawables"),
    65: os.path.join(BASE, "resources-amoled454", "drawables"),
    70: os.path.join(BASE, "resources-icon70", "drawables"),
}

for size, out_dir in TARGETS.items():
    os.makedirs(out_dir, exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(os.path.join(out_dir, "launcher_icon.png"))
    print(f"  launcher_icon.png @ {size}px -> {out_dir}")
print("Done!")
