"""Make a soft white glow layer matching a cut-out PNG's silhouette (drawn BEHIND the art).

usage: python soft_glow.py <art.png> <glow.png> [radius_px] [strength 0..1]
The glow has the same size as the art, so both can be stacked in one box. Thin white edges on the art
then read as part of the glow instead of a cutout flaw.
"""
import sys
from PIL import Image, ImageFilter

src, dst = sys.argv[1], sys.argv[2]
radius = float(sys.argv[3]) if len(sys.argv) > 3 else 9
strength = float(sys.argv[4]) if len(sys.argv) > 4 else 0.85

art = Image.open(src).convert("RGBA")
mask = art.getchannel("A").point(lambda a: 255 if a > 24 else 0)

# only the BIG shapes glow (letters, blocks): drop small loose pieces such as sparkles, stars and dots
from collections import deque
W, H = mask.size
mp = mask.load()
seen = bytearray(W * H)
keep = Image.new("L", mask.size, 0)
kp = keep.load()
min_area = int(W * H * 0.004)
for sy in range(H):
    for sx in range(W):
        if seen[sy * W + sx] or mp[sx, sy] == 0:
            continue
        comp, dq = [], deque([(sx, sy)])
        seen[sy * W + sx] = 1
        while dq:
            x, y = dq.popleft()
            comp.append((x, y))
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < W and 0 <= ny < H and not seen[ny * W + nx] and mp[nx, ny]:
                    seen[ny * W + nx] = 1
                    dq.append((nx, ny))
        if len(comp) >= min_area:
            for x, y in comp:
                kp[x, y] = 255
mask = keep
mask = mask.filter(ImageFilter.MaxFilter(5))                      # start just outside the edge
glow = mask.filter(ImageFilter.GaussianBlur(radius))
glow = glow.point(lambda a: int(min(255, a * 1.6) * strength))   # fuller near the edge, soft falloff
layer = Image.new("RGBA", art.size, (255, 255, 255, 0))
layer.putalpha(glow)
layer.save(dst, optimize=True)
print("saved", dst, layer.size)
