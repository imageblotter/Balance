#!/usr/bin/env python3
"""Renders the UI bitmap font (assets/ui.fnt + assets/ui.png) from DejaVu Sans Bold.

DejaVu fonts are free to use and redistribute (see assets/FONT-LICENSE.txt).
Usage: tools/make_font.py [path/to/DejaVuSans-Bold.ttf]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SIZE = 64
PAD = 3
ATLAS = 1024
ATLAS_H = 512
EXTRA = "×·•"

ttf = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
out = Path(__file__).resolve().parent.parent / "assets"

font = ImageFont.truetype(ttf, SIZE)
ascent, descent = font.getmetrics()
codes = list(range(32, 127)) + [ord(c) for c in EXTRA]

atlas = Image.new("RGBA", (ATLAS, ATLAS_H), (255, 255, 255, 0))
chars = []
x = y = PAD
row_h = 0
for code in codes:
    ch = chr(code)
    left, top, right, bottom = font.getbbox(ch, anchor="la")
    w, h = max(right - left, 0), max(bottom - top, 0)
    if x + w + PAD > ATLAS:
        x, y, row_h = PAD, y + row_h + PAD, 0
    if w and h:
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).text((-left, -top), ch, font=font, fill=255, anchor="la")
        atlas.paste(Image.new("RGBA", (w, h), (255, 255, 255, 255)), (x, y), mask)
    chars.append((code, x, y, w, h, left, top, round(font.getlength(ch))))
    x += w + PAD
    row_h = max(row_h, h)

height = y + row_h + PAD
assert height <= ATLAS_H, height
atlas.save(out / "ui.png")

lines = [
    f'info face="DejaVu Sans Bold" size={SIZE} bold=1 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0',
    f"common lineHeight={ascent + descent} base={ascent} scaleW={ATLAS} scaleH={ATLAS_H} pages=1 packed=0",
    'page id=0 file="ui.png"',
    f"chars count={len(chars)}",
]
for code, cx, cy, w, h, xo, yo, adv in chars:
    lines.append(f"char id={code} x={cx} y={cy} width={w} height={h} xoffset={xo} yoffset={yo} xadvance={adv} page=0 chnl=15")
(out / "ui.fnt").write_text("\n".join(lines) + "\n")
print(f"wrote {len(chars)} glyphs, atlas content height {height}px")
