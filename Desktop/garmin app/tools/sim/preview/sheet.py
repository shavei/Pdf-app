import glob, os, sys
from PIL import Image, ImageDraw
# Contact sheet of bin/preview/shots/<device>_*.png, one row per device.
here = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "bin", "preview")
shots = os.path.join(here, "shots")
devs = sys.argv[1:]
rows = []
for d in devs:
    fs = sorted(glob.glob(os.path.join(shots, f"{d}_*.png")))
    ims = []
    for f in fs:
        im = Image.open(f).convert("RGB")
        im.thumbnail((360, 360))
        ims.append(im)
    rows.append((d, ims))
W = 4 * 370 + 10
H = sum(max([i.height for i in ims] or [20]) + 30 for _, ims in rows)
sheet = Image.new("RGB", (W, H), "white")
dr = ImageDraw.Draw(sheet)
y = 0
for d, ims in rows:
    dr.text((5, y + 5), d, fill="black")
    x = 5
    for im in ims:
        sheet.paste(im, (x, y + 25)); x += 370
    y += max([i.height for i in ims] or [20]) + 30
out = os.path.join(here, "sheet_" + devs[0] + ".png")
sheet.save(out)
print(out)
