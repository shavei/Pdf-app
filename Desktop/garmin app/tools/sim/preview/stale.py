"""Flag devices whose consecutive state captures show the same screen (stale frame)."""
import glob, json, os
from PIL import Image, ImageChops
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SHOTS = os.path.join(ROOT, "bin", "preview", "shots")
DEV = os.path.join(os.environ["APPDATA"], "Garmin", "ConnectIQ", "Devices")
devs = sorted({os.path.basename(f).rsplit("_", 1)[0] for f in glob.glob(os.path.join(SHOTS, "*.png"))})
for d in devs:
    loc = json.load(open(os.path.join(DEV, d, "simulator.json"), encoding="utf8"))["display"]["location"]
    box = (loc["x"], loc["y"], loc["x"] + loc["width"], loc["y"] + loc["height"])
    fs = sorted(glob.glob(os.path.join(SHOTS, f"{d}_*.png")))
    ims = [Image.open(f).convert("L").crop(box) for f in fs]
    for i in range(1, len(ims)):
        if ImageChops.difference(ims[i], ims[i - 1]).getbbox() is None:
            print(f"STALE {d} states {i-1}={i}")
