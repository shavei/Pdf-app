"""
Generate Hebrew bitmap fonts for Forerunner 165 (360px round).
Sized between Solar (176px) and Instinct AMOLED (390px). The widget 'Large' is
capped at 58px so the longest month name (כ"ט מרחשוון) clears the round edge.
Uses NotoSansHebrew-Regular from Garmin's own font library.
"""

from PIL import Image, ImageDraw, ImageFont
import os

CHARS = (
    "אבגדהוזחטיכךלמםנןסעפףצץקרשת"
    "׳״"
    " ():-"
    "0123456789"
)

FONT_PATH = r"C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Fonts\NotoSansHebrew-Regular.ttf"


def generate_fnt(name, size_px, out_dir):
    font = ImageFont.truetype(FONT_PATH, size_px)

    # Real vertical metrics so descenders (e.g. final nun ן) are not clipped.
    ascent, descent = font.getmetrics()
    line_height = ascent + descent

    glyphs = []
    for ch in CHARS:
        bbox = font.getbbox(ch)
        w = max(bbox[2] - bbox[0] + 2, 2)
        h = max(bbox[3] - bbox[1] + 2, 2)
        glyphs.append((ch, w, h, bbox))

    padding = 2
    tex_w = 512
    x, y = padding, padding
    row_h = 0
    positions = []

    for ch, w, h, bbox in glyphs:
        if x + w + padding > tex_w:
            x = padding
            y += row_h + padding
            row_h = 0
        positions.append((ch, x, y, w, h, bbox))
        if h > row_h:
            row_h = h
        x += w + padding

    tex_h = y + row_h + padding
    tex_h_pow2 = 1
    while tex_h_pow2 < tex_h:
        tex_h_pow2 <<= 1

    img = Image.new("RGBA", (tex_w, tex_h_pow2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    fnt_lines = []
    fnt_lines.append(f'info face="{name}" size={size_px} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1')
    fnt_lines.append(f'common lineHeight={line_height} base={ascent} scaleW={tex_w} scaleH={tex_h_pow2} pages=1 packed=0')
    fnt_lines.append(f'page id=0 file="{name}.png"')
    fnt_lines.append(f'chars count={len(positions)}')

    for ch, cx, cy, cw, ch_h, bbox in positions:
        draw.text((cx - bbox[0], cy - bbox[1]), ch, font=font, fill=(255, 255, 255, 255))
        fnt_lines.append(
            f'char id={ord(ch)}   x={cx}     y={cy}     width={cw}    height={ch_h}   '
            f'xoffset={bbox[0]}   yoffset={bbox[1]}   xadvance={cw + 1}  page=0  chnl=15'
        )

    os.makedirs(out_dir, exist_ok=True)
    img.save(os.path.join(out_dir, f"{name}.png"))
    with open(os.path.join(out_dir, f"{name}.fnt"), "w", encoding="utf-8") as f:
        f.write("\n".join(fnt_lines))

    print(f"  {name} @ {size_px}px -> {out_dir}")


out = r"C:\Users\yosef\Desktop\garmin app\resources-fr165m\fonts"
print("Generating Forerunner 165 Hebrew fonts (NotoSansHebrew)...")
generate_fnt("HebrewSmall",  34, out)   # titles, labels
generate_fnt("HebrewMedium", 46, out)   # content
generate_fnt("HebrewLarge",  58, out)   # date display (58 keeps כ"ט מרחשוון on-screen)
# Dedicated, larger glance fonts.
generate_fnt("HebrewGlanceSmall",  40, out)
generate_fnt("HebrewGlanceMedium", 50, out)
print("Done!")
