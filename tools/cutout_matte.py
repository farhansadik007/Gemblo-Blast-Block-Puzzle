"""High-quality cutout of art drawn on a plain white background (keeps every piece).

usage: python cutout_matte.py <input> <output.png> <max_side> [band_px]
1. at FULL resolution, flood-fill the background (light, low-saturation pixels connected to the border);
   enclosed whites (letter faces, eye highlights) stay.
2. color-to-alpha in a band of `band_px` pixels next to the background: alpha = how far the pixel is from
   white, then the white is un-blended out of its color (what pro "remove white background" tools do).
3. downscale LAST, in premultiplied alpha (RGBa), so edges stay smooth with no halo; then crop.
"""
import sys
from collections import deque
from PIL import Image

src, dst, max_side = sys.argv[1], sys.argv[2], int(sys.argv[3])
band = int(sys.argv[4]) if len(sys.argv) > 4 else 4

im = Image.open(src).convert("RGB")
W, H = im.size
px = im.load()

def bg_like(c):
    r, g, b = c
    return min(r, g, b) > 222 and max(r, g, b) - min(r, g, b) < 34

N = W * H
dist = bytearray([255]) * N     # 0 = background, k = k px from it (capped)
q = deque()
for x in range(W):
    q.append(x); q.append((H - 1) * W + x)
for y in range(H):
    q.append(y * W); q.append(y * W + W - 1)
seen = bytearray(N)
while q:
    i = q.popleft()
    if seen[i]:
        continue
    x, y = i % W, i // W
    if not bg_like(px[x, y]):
        continue
    seen[i] = 1
    dist[i] = 0
    if x > 0: q.append(i - 1)
    if x < W - 1: q.append(i + 1)
    if y > 0: q.append(i - W)
    if y < H - 1: q.append(i + W)

# distance (in px) from the background, up to `band`
frontier = deque(i for i in range(N) if dist[i] == 0)
while frontier:
    i = frontier.popleft()
    d = dist[i]
    if d >= band:
        continue
    x, y = i % W, i // W
    for j in ((i - 1) if x > 0 else -1, (i + 1) if x < W - 1 else -1, (i - W) if y > 0 else -1, (i + W) if y < H - 1 else -1):
        if j >= 0 and dist[j] > d + 1:
            dist[j] = d + 1
            frontier.append(j)

out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
op = out.load()
for i in range(N):
    d = dist[i]
    if d == 0:
        continue
    x, y = i % W, i // W
    r, g, b = px[x, y]
    if d <= band:
        # near-white, low-saturation edge noise (JPEG ringing) is background
        if min(r, g, b) > 200 and max(r, g, b) - min(r, g, b) < 30:
            continue
        a = min(255, int((255 - min(r, g, b)) * 255 / 105))
        if a <= 0:
            continue
        if a < 255:
            k = a / 255.0
            r, g, b = (max(0, min(255, int(round((v - (1 - k) * 255) / k)))) for v in (r, g, b))
        op[x, y] = (r, g, b, a)
    else:
        op[x, y] = (r, g, b, 255)

bbox = out.getbbox()
out = out.crop(bbox)
scale = max_side / max(out.size)
if scale < 1:
    size = (round(out.width * scale), round(out.height * scale))
    out = out.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
pad = int(0.015 * max(out.size))
final = Image.new("RGBA", (out.width + 2 * pad, out.height + 2 * pad), (0, 0, 0, 0))
final.paste(out, (pad, pad))
final.save(dst, optimize=True)
print("saved", dst, final.size)
