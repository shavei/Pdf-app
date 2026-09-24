"""Review sheets for the preview harness.

Crops every capture in bin/preview/shots to the device's exact screen box
(simulator.json display.location — the sim draws the device image 1:1, so
the box maps straight onto the window capture), scales each screen to ~300px
and lays a device's states out in one labelled row.

  python sheet.py dev1 dev2 ...      one sheet with those devices
  python sheet.py --all [N]          sheets of N devices (default 4) for every
                                     device that has shots -> bin/preview/review_XX.png
"""
import glob, json, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SHOTS = os.path.join(ROOT, "bin", "preview", "shots")
OUT = os.path.join(ROOT, "bin", "preview")
DEV = os.path.join(os.environ["APPDATA"], "Garmin", "ConnectIQ", "Devices")
LABELS = ["glance long", "glance typical", "date", "parasha AMK", "parasha RC", "omer AMK", "omer RC"]
CELL = 300
PAD = 18


def row(dev):
    loc = json.load(open(os.path.join(DEV, dev, "simulator.json"), encoding="utf8"))["display"]["location"]
    ims = []
    for f in sorted(glob.glob(os.path.join(SHOTS, f"{dev}_*.png"))):
        im = Image.open(f).convert("RGB")
        m = 6  # a little bezel so edge clipping is visible
        im = im.crop((loc["x"] - m, loc["y"] - m, loc["x"] + loc["width"] + m, loc["y"] + loc["height"] + m))
        s = CELL / max(im.size)
        ims.append(im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS))
    return ims


def sheet(devs, out):
    rows = [(d, row(d)) for d in devs]
    W = 7 * (CELL + PAD) + PAD
    H = len(rows) * (CELL + 40) + 10
    img = Image.new("RGB", (W, H), (235, 235, 235))
    dr = ImageDraw.Draw(img)
    y = 5
    for d, ims in rows:
        dr.text((PAD, y), d, fill="black")
        x = PAD
        for i, im in enumerate(ims):
            dr.text((x, y + 14), LABELS[i] if i < len(LABELS) else str(i), fill=(90, 90, 90))
            img.paste(im, (x, y + 28))
            x += CELL + PAD
        y += CELL + 40
    img.save(out)
    print(out)


if __name__ == "__main__":
    if sys.argv[1:2] == ["--all"]:
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 4
        devs = sorted({os.path.basename(f).rsplit("_", 1)[0] for f in glob.glob(os.path.join(SHOTS, "*.png"))})
        for k in range(0, len(devs), n):
            sheet(devs[k:k + n], os.path.join(OUT, f"review_{k // n:02d}.png"))
    else:
        sheet(sys.argv[1:], os.path.join(OUT, "sheet_" + sys.argv[1] + ".png"))
