"""
Shared rendering helpers for the Connect IQ Store images (cover, gallery,
hero) so they share one consistent look: subtle gradient backgrounds,
anti-aliased watch cut-outs, and soft drop shadows.
"""

import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter


def find_font(candidates):
    """Return the first existing font path from candidates."""
    for p in candidates:
        if os.path.exists(p):
            return p
    raise FileNotFoundError(f"none of these fonts exist: {candidates}")


def gradient_v(w, h, top=(255, 255, 255), bot=(243, 245, 248)):
    """Vertical gradient RGB image (top colour -> bottom colour)."""
    g = Image.new("RGB", (1, h))
    p = g.load()
    for y in range(h):
        f = y / (h - 1) if h > 1 else 0.0
        p[0, y] = tuple(int(top[i] + (bot[i] - top[i]) * f) for i in range(3))
    return g.resize((w, h))


def cutout(img_rgb, thresh=55, shrink=1, feather=1.0):
    """Cut a watch from its white crop background with NO visible edge halo.

    Flood-fills the seed colour in from the four corners (so interior whites
    stay opaque), then ERODES the silhouette inward by `shrink` px so the
    near-white anti-aliased fringe at the watch edge is dropped — otherwise
    that fringe shows as a light outline on a non-white background. Finally
    feathers the matte. Returns (rgba, alpha)."""
    w, h = img_rgb.size
    flood = img_rgb.copy()
    seed = (255, 0, 255)
    for c in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        ImageDraw.floodfill(flood, c, seed, thresh=thresh)
    diff = ImageChops.difference(flood, Image.new("RGB", (w, h), seed)).convert("L")
    alpha = diff.point(lambda v: 0 if v < 12 else 255)        # 255 = watch
    if shrink:
        # MinFilter shrinks the opaque (255) region, eating the light fringe ring
        alpha = alpha.filter(ImageFilter.MinFilter(2 * shrink + 1))
    alpha = alpha.filter(ImageFilter.GaussianBlur(feather))
    rgba = img_rgb.convert("RGBA")
    rgba.putalpha(alpha)
    return rgba, alpha


def paste_with_shadow(canvas_rgba, rgba, alpha, x, y,
                      blur=20, opacity=0.32, dx=0, dy=16, color=(18, 20, 28)):
    """Composite a cut-out onto an RGBA canvas with a soft drop shadow."""
    w, h = rgba.size
    pad = blur * 3
    sh = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    sil = Image.new("RGBA", (w, h), color + (255,))
    sil.putalpha(alpha.point(lambda v: int(v * opacity)))
    sh.paste(sil, (pad, pad), sil)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    canvas_rgba.alpha_composite(sh, dest=(x - pad + dx, y - pad + dy))
    canvas_rgba.alpha_composite(rgba, dest=(x, y))


def fit_scale(src_w, src_h, box_w, box_h, max_up=1.5):
    """Scale factor to fit (src_w, src_h) inside the box, capped to max_up."""
    return min(box_w / src_w, box_h / src_h, max_up)
