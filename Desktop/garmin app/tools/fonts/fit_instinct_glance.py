"""Solve the Instinct-family glance layout so nothing is hidden by the sub-screen.

On the Instincts the glance content area is partly covered on the right:
  * MIP Instincts (2 / 2S / 2X / 3 Solar / E / Descent G1): the round sub-screen
    window (+ dark halo, ~40px radius) covers the top-right; what is left is a
    ~86x56 column on the left + a 12px strip along the bottom-right
    -> LAYOUT 1 (user-chosen 2026-09-24, "stacked"):
      line 1 (left column): day letter + day   (HebrewGlanceMedium, large)
      line 2 (left column): month              (HebrewGlanceMedium)
      bottom-right strip  : year               (HebrewGlanceSmall, small)
  * Instinct 3 AMOLED 45/50: the carousel overlays a circle + wedge on the
    right -> LAYOUT 2: everything left of the mask,
      top row: date right-aligned;  bottom row: day letter left, year right.

For every device this renders each worst-case string with the exact glyph
bitmaps/advances fontgen writes (Garmin draws RTL text visually reversed, pen
advancing by xadvance, VCENTER line top = y - lineHeight/2), and searches the
largest HebrewGlanceSmall size + positions whose ink stays >= MARGIN px from the
obstruction and the glance edges. Writes resources-glance-<device>/ (glance font
+ GlanceLayout jsonData) and wires it into monkey.jungle. Idempotent.

GlanceLayout = [mode, a, b, c, d]  (glance-local px)
  mode 1: [1, yLine1, yLine2, yYear, xYearRight, xLeft]  (lines 1/2 start at xLeft)
  mode 2: [2, yTop, yBot, xDateRight, xYearRight]
"""
import json, os, re, sys

sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageFont
import fontgen

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEV = os.path.join(os.environ["APPDATA"], "Garmin", "ConnectIQ", "Devices")
PAD, GAP, MARGIN = 6, 8, 3
MIN_YEAR = 12          # smallest acceptable year size in the bottom strip

MODE1 = ["instinct2", "instinct2x", "instinct3solar45mm", "instincte45mm", "descentg1",
         "instinct2s", "instincte40mm"]
MODE2 = ["instinct3amoled45mm", "instinct3amoled50mm"]

MONTHS = ["תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר", "ניסן", "אייר", "סיוון", "תמוז",
          "אב", "אלול", "אדר א׳", "אדר ב׳"]
ONES = ["", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"]
TENS = ["", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"]
HUND = ["", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"]


def gem(n):
    if n == 15: return "ט״ו"
    if n == 16: return "ט״ז"
    s = HUND[n // 100] + TENS[n // 10 % 10] + ONES[n % 10]
    return s + "׳" if len(s) == 1 else s[:-1] + "״" + s[-1]


DATES = [gem(d) + " " + m for d in range(1, 31) for m in MONTHS]
YEARS = [gem(y % 1000) for y in range(5786, 5821)]
LETTERS = list("אבגדהוז")

_fonts = {}


def font(size):
    """glyph masks + metrics exactly as fontgen.generate_fnt writes them."""
    if size in _fonts:
        return _fonts[size]
    f = ImageFont.truetype(fontgen.FONT_PATH, size)
    asc, desc = f.getmetrics()
    g = {}
    for ch in fontgen.CHARS:
        b = f.getbbox(ch)
        w, h = max(b[2] - b[0] + 2, 2), max(b[3] - b[1] + 2, 2)
        im = Image.new("L", (w, h), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(im).text((-b[0], -b[1]), ch, font=f, fill=255)
        pts = [(x, y) for y in range(h) for x in range(w) if im.getpixel((x, y)) >= 96]
        g[ch] = (b[0], b[1], w + 1, pts)
    _fonts[size] = (g, asc + desc)
    return _fonts[size]


def width(fnt, s):
    return sum(fnt[0].get(c, fnt[0][" "])[2] for c in s)


def ink(fnt, s, x, y, just):
    """ink pixels of s drawn at (x,y) with LEFT/RIGHT justify + VCENTER."""
    g, lh = fnt
    W = width(fnt, s)
    pen = x if just == "L" else x - W
    top = y - lh // 2
    out = []
    for c in reversed(s):                       # RTL: visual order is reversed
        xo, yo, adv, pts = g.get(c, g[" "])
        out.extend((pen + xo + px, top + yo + py) for px, py in pts)
        pen += adv
    return out


def yrange(fnt, strings):
    lo = min(py for s in strings for _, py in ink(fnt, s, 0, 0, "L"))
    hi = max(py for s in strings for _, py in ink(fnt, s, 0, 0, "L"))
    return lo, hi


def solve(dev):
    sim = json.load(open(os.path.join(DEV, dev, "simulator.json"), encoding="utf8"))
    ca, disp = sim["glance"]["contentArea"], sim["display"]["location"]
    w, h = ca["width"], ca["height"]
    # Obstruction = everything inside the glance content area that is NOT
    # white in a real carousel capture of a build whose glance fills itself
    # white (bin/preview/masks/full_<dev>.png, see CLAUDE.md). The profile's
    # sub-screen coordinates under-state the carousel's dark window + halo.
    cap = Image.open(os.path.join(ROOT, "bin", "preview", "masks", f"full_{dev}.png")).convert("L")
    ox, oy = disp["x"] + ca["x"], disp["y"] + ca["y"]
    opaque = set()
    for yy in range(h):
        for xx in range(w):
            if cap.getpixel((ox + xx, oy + yy)) < 200:
                for dx in range(-MARGIN, MARGIN + 1):
                    for dy in range(-MARGIN, MARGIN + 1):
                        opaque.add((xx + dx, yy + dy))
    blocked = lambda x, y: (x, y) in opaque

    def clear(pts):
        return all(MARGIN <= x < w - MARGIN and 1 <= y < h - 1 and not blocked(x, y) for x, y in pts)

    if dev in MODE1:
        days = sorted({d.split(" ")[0] for d in DATES})
        months = MONTHS
        for sm in range(34, 9, -1):
            fm = font(sm)
            ltW = max(width(fm, c) for c in LETTERS)
            lo1, hi1 = yrange(fm, days + LETTERS)
            lo2, hi2 = yrange(fm, months)
            found = None
            # the top-left corner is bevelled and the top rows are dark: search
            # the first ink row and the left inset for the two stacked lines
            for top in range(1, 12):
                y1 = top - lo1
                y2 = y1 + hi1 + 3 - lo2       # line 2 ink starts 3px under line 1 ink
                for x0 in range(PAD, 22):
                    if all(clear(ink(fm, c, x0, y1, "L")) for c in LETTERS)                             and all(clear(ink(fm, d, x0 + ltW + GAP, y1, "L")) for d in days)                             and all(clear(ink(fm, m, x0, y2, "L")) for m in months):
                        found = (y1, y2, x0)
                        break
                if found:
                    break
            if not found:
                continue
            y1, y2, x0 = found
            line2 = set(p for m in months for p in ink(fm, m, x0, y2, "L"))
            for ss in range(sm, MIN_YEAR - 1, -1):
                fs = font(ss)
                lo, hi = yrange(fs, YEARS)
                yY = h - 2 - hi
                for xY in range(w - PAD, PAD, -1):
                    pts = [p for yr in YEARS for p in ink(fs, yr, xY, yY, "R")]
                    if clear(pts) and not any((x + dx, y + dy) in line2 for x, y in pts
                                              for dx in (-3, 0, 3) for dy in (-3, 0, 3)):
                        return (sm, ss), [1, y1, y2, yY, xY, x0], (w, h)
        raise SystemExit(f"no layout fits for {dev}")

    for s in range(40, 9, -1):
        f = font(s)
        lo, hi = yrange(f, DATES + YEARS + LETTERS)
        ltW = max(width(f, c) for c in LETTERS)
        yrW = max(width(f, yr) for yr in YEARS)
        for top in range(1, 16):
            yTop = top - lo
            xA = next((x for x in range(w - PAD, PAD, -1)
                       if all(clear(ink(f, d, x, yTop, "R")) for d in DATES)), None)
            if xA is None:
                continue
            for bot in range(1, 16):
                yBot = h - 1 - bot - hi
                if yTop + hi + 3 > yBot + lo:
                    break
                x0 = next((x for x in range(PAD, 40)
                           if all(clear(ink(f, c, x, yBot, "L")) for c in LETTERS)), None)
                if x0 is None:
                    continue
                xB = next((x for x in range(w - PAD, PAD, -1)
                           if all(clear(ink(f, yr, x, yBot, "R")) for yr in YEARS)
                           and x - yrW >= x0 + ltW + GAP), None)
                if xB is not None:
                    return (None, s), [2, yTop, yBot, xA, xB, x0], (w, h)
    raise SystemExit(f"no layout fits for {dev}")


def main():
    jpath = os.path.join(ROOT, "monkey.jungle")
    jungle = open(jpath, encoding="utf8").read().split("\n")
    for dev in MODE1 + MODE2:
        (sm, ss), lay, (w, h) = solve(dev)
        d = os.path.join(ROOT, f"resources-glance-{dev}")
        fd = os.path.join(d, "fonts")
        for f_ in os.listdir(fd) if os.path.isdir(fd) else []:
            os.remove(os.path.join(fd, f_))
        fontgen.generate_fnt("HebrewGlanceSmall", ss, fd)
        xml = '    <font id="HebrewGlanceSmall" filename="HebrewGlanceSmall.fnt" scope="glance" />\n'
        if sm:
            fontgen.generate_fnt("HebrewGlanceMedium", sm, fd)
            xml += '    <font id="HebrewGlanceMedium" filename="HebrewGlanceMedium.fnt" scope="glance" />\n'
        with open(os.path.join(fd, "fonts.xml"), "w", encoding="utf-8") as fp:
            fp.write('<?xml version="1.0"?>\n<resources>\n'
                     f"    <!-- {dev}: glance clear of the sub-screen (tools/fonts/fit_instinct_glance.py) -->\n"
                     + xml + "</resources>\n")
        s = f"{sm}/{ss}" if sm else ss
        os.makedirs(os.path.join(d, "data"), exist_ok=True)
        with open(os.path.join(d, "data", "glance.xml"), "w", encoding="utf-8") as fp:
            fp.write("<resources>\n"
                     f"    <!-- [mode, a, b, c, d] (see fit_instinct_glance.py docstring) for a {w}x{h} glance; see fit_instinct_glance.py -->\n"
                     f'    <jsonData id="GlanceLayout" scope="glance">{json.dumps(lay)}</jsonData>\n'
                     "</resources>\n")
        print(f"{dev:22s} {w}x{h}  glanceSmall {s}px  layout {lay}")
        for i, line in enumerate(jungle):
            if re.match(rf"\s*{dev}\.resourcePath\s*=", line):
                line = re.sub(r";resources-(gs\d+|glance-\w+|glancexover)", "", line)
                jungle[i] = line + f";resources-glance-{dev}"
    open(jpath, "w", encoding="utf8").write("\n".join(jungle))


main()
