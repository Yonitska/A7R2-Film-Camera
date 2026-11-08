#!/usr/bin/env python3
"""Generate the phone app's own launcher icon. No third-party artwork.

The PNGs are committed under phone-res/; this script documents how they were
made. Pillow is a workstation-only dependency (not used by the APK build).
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SCALE = 8
INK = (33, 29, 24, 255)
GOLD = (199, 154, 74, 255)
DARK = (14, 12, 10, 255)
CREAM = (246, 240, 227, 255)


def render(size):
    px = size * SCALE
    image = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    radius = int(px * 0.22)
    draw.rounded_rectangle((0, 0, px - 1, px - 1), radius=radius, fill=INK)

    # Outer lens ring.
    margin = int(px * 0.18)
    draw.ellipse((margin, margin, px - margin, px - margin), outline=GOLD,
                 width=max(2, int(px * 0.045)))
    # Aperture body.
    inner = int(px * 0.30)
    draw.ellipse((inner, inner, px - inner, px - inner), fill=DARK,
                 outline=(90, 74, 51, 255), width=max(1, int(px * 0.012)))
    # Glass and highlight.
    glass = int(px * 0.38)
    draw.ellipse((glass, glass, px - glass, px - glass), fill=(47, 39, 28, 255))
    dot = int(px * 0.075)
    cx, cy = int(px * 0.43), int(px * 0.43)
    draw.ellipse((cx - dot, cy - dot, cx + dot, cy + dot), fill=CREAM)
    # Tiny film tick at the bottom, a nod to the in-camera film app.
    tick_w, tick_h = int(px * 0.16), max(2, int(px * 0.025))
    x = (px - tick_w) // 2
    y = int(px * 0.86)
    draw.rounded_rectangle((x, y, x + tick_w, y + tick_h), radius=tick_h // 2, fill=GOLD)
    return image.resize((size, size), Image.LANCZOS)


def main():
    for density, size in SIZES.items():
        target = ROOT / "phone-res" / ("drawable-" + density)
        target.mkdir(parents=True, exist_ok=True)
        render(size).save(target / "ic_launcher.png", optimize=True)
    print("Generated phone launcher icons in phone-res/drawable-*")


if __name__ == "__main__":
    main()
