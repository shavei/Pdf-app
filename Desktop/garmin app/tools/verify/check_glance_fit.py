"""Exact glance-fit check for every device in manifest.xml.

Resolves each device's font files through its monkey.jungle resourcePath chain
(later dirs override), sums real glyph xadvance widths from the .fnt files,
and replays HebrewCalendarGlanceView's layout for the worst-case strings:
the longest day+month (כ״ט אדר א׳ / כ״ח אדר ב׳) and the widest day letter.
Reports the gap between the day letter and the date, the side margins, and
vertical overflow against the device's glance contentArea. Exit 1 on failure.

  python tools/verify/check_glance_fit.py [--gap N]   (min letter-date gap, default 8)
"""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEV = os.path.join(os.environ["APPDATA"], "Garmin", "ConnectIQ", "Devices")
MIN_GAP = int(sys.argv[sys.argv.index("--gap") + 1]) if "--gap" in sys.argv else 8
MIN_EDGE = 3

DATES = ["כ״ט אדר א׳", "כ״ח אדר ב׳", "כ״ט תשרי", "כ״ט כסלו", "כ״ט תמוז"]
LETTERS = list("אבגדהוש")
YEAR = "תשפ״ח"


def jungle_paths():
    paths = {}
    for line in open(os.path.join(ROOT, "monkey.jungle"), encoding="utf8"):
        m = re.match(r"\s*(\w+)\.resourcePath\s*=\s*\$\(\w+\.resourcePath\);(.*)", line)
        if m:
            paths[m.group(1)] = [p.strip() for p in m.group(2).split(";") if p.strip()]
    return paths


def load_fnt(path):
    adv, lh = {}, None
    for line in open(path, encoding="utf8"):
        if line.startswith("common"):
            lh = int(re.search(r"lineHeight=(\d+)", line).group(1))
        elif line.startswith("char "):
            cid = int(re.search(r"id=(\d+)", line).group(1))
            adv[chr(cid)] = int(re.search(r"xadvance=(-?\d+)", line).group(1))
    return adv, lh


def font_for(chain, name):
    for d in reversed(["resources"] + chain):
        p = os.path.join(ROOT, d, "fonts", name + ".fnt")
        if os.path.exists(p):
            return load_fnt(p)
    raise FileNotFoundError(name)


def width(font, s):
    adv, _ = font
    return sum(adv.get(c, adv.get(" ", 0)) for c in s)


def main():
    devices = re.findall(r'product id="([^"]+)"', open(os.path.join(ROOT, "manifest.xml"), encoding="utf8").read())
    chains = jungle_paths()
    bad = 0
    for d in devices:
        ca = json.load(open(os.path.join(DEV, d, "simulator.json"), encoding="utf8"))["glance"]["contentArea"]
        w, h = ca["width"], ca["height"]
        fs = font_for(chains.get(d, []), "HebrewGlanceSmall")
        fm = font_for(chains.get(d, []), "HebrewGlanceMedium")
        dmW = max(width(fs, s) for s in DATES)
        ltW = max(width(fm, c) for c in LETTERS)
        # replay the view's layout choice (normal vs compact)
        pad = 10
        slack = w - 2 * pad - ltW - MIN_GAP - dmW
        mode = "normal"
        if slack < 0:
            pad = 10 + int((slack - 1) / 2)
            if pad < 6:
                mode, pad = "compact", 10
        if mode == "normal":
            gap = w - 2 * pad - ltW - dmW
        else:
            if dmW > w - 2 * pad:
                pad = max(4, (w - dmW) // 2)
            ltS = max(width(fs, c) for c in LETTERS)
            gap = w - 2 * pad - ltS - width(fs, YEAR)   # letter vs year on the bottom row
        sh, mh = fs[1], fm[1]
        # vertical: top line centred at cy - sh/2, year at cy + mh/2 (both GlanceSmall)
        cy = h // 2
        ink = lambda lhh: lhh * 0.36          # half ink height ≈ 0.36 * lineHeight
        top = (cy - sh / 2 - 1) - ink(sh)
        bot = (cy + mh / 2 + 1) + ink(sh)
        ok = gap >= MIN_GAP and pad >= 6 and top >= 0 and bot <= h
        bad += not ok
        print(f"{'OK ' if ok else 'BAD'} {d:24s} {mode:7s} area {w}x{h}  date {dmW}  letter {ltW}  pad {pad}  gap {gap}"
              f"  ink y {top:.0f}..{bot:.0f}")
    print(f"\n{bad} device(s) failing (min gap {MIN_GAP}, min edge {MIN_EDGE})")
    sys.exit(1 if bad else 0)


main()
