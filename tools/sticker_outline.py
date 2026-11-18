"""Add a smooth, solid "sticker" outline around a cut-out PNG, so imperfect edges can never show.

usage: python sticker_outline.py <input.png> <output.png> [width_px] [#rrggbb]
- grows the art's silhouette by `width_px` (round brush), feathers it by ~1 px for a smooth edge,
  fills it with the outline color and puts the art on top
"""
import sys
from PIL import Image, ImageFilter, ImageChops

src, dst = sys.argv[1], sys.argv[2]
width = int(sys.argv[3]) if len(sys.argv) > 3 else 7
color = sys.argv[4] if len(sys.argv) > 4 else "#150B2C"
rgb = tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))

art = Image.open(src).convert("RGBA")
pad = width + 4
canvas = Image.new("RGBA", (art.width + 2 * pad, art.height + 2 * pad), (0, 0, 0, 0))
canvas.paste(art, (pad, pad))

# solid silhouette, then grow it with a round brush (repeated 3x3 max = diamond; blur+threshold rounds it)
mask = canvas.getchannel("A").point(lambda a: 255 if a > 24 else 0)
grown = mask
for _ in range(width):
    grown = grown.filter(ImageFilter.MaxFilter(3))
grown = grown.filter(ImageFilter.GaussianBlur(width * 0.45)).point(lambda a: 255 if a > 110 else 0)
grown = grown.filter(ImageFilter.GaussianBlur(0.9))  # smooth anti-aliased rim
grown = ImageChops.lighter(grown, mask)

# trim the art's own soft outer ring (where leftover white lives), so the solid outline shows there instead
trim = int(sys.argv[5]) if len(sys.argv) > 5 else 2
inner = mask
for _ in range(trim):
    inner = inner.filter(ImageFilter.MinFilter(3))
inner = inner.filter(ImageFilter.GaussianBlur(0.7))
art_alpha = ImageChops.multiply(canvas.getchannel("A"), inner)
canvas.putalpha(art_alpha)

outline = Image.new("RGBA", canvas.size, rgb + (0,))
outline.putalpha(grown)
outline.alpha_composite(canvas)
outline = outline.crop(outline.getbbox())
outline.save(dst, optimize=True)
print("saved", dst, outline.size)
