"""Draw the Gemblo Blast app icon: a gold plus piece hovering over its landing spot on a 5x5 jewel board.

usage: python tools/make_icon.py
writes design/icon_512.png (Play Store) and the adaptive launcher icon layers into
app/src/main/res/mipmap-*/ic_launcher_background.png (108dp layer, art inside the 72dp visible area).
Everything is drawn from code: no outside images.
"""
import sys, math, random, os
from PIL import Image, ImageDraw, ImageFilter, ImageChops

S = 4
OUT = 512 * S
ZOOM = 1.0
W = int(OUT * ZOOM)
N = 5
C = W / N
L = 3 * S
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RND = random.Random(11)

def mix(c, d, t):
    return tuple(int(c[i] + (d[i] - c[i]) * t) for i in range(3))
WHITE, BLACK = (255, 255, 255), (0, 0, 0)
RED, YEL, GRN, BLU, PUR = (0xFF, 0x2D, 0x46), (0xFF, 0xC1, 0x07), (0x14, 0xD2, 0x63), (0x16, 0x8B, 0xFF), (0x9A, 0x4D, 0xFF)
LINE, EMPTY, LAND = (0x0C, 0x0F, 0x3C), (0x1E, 0x24, 0x70), (0x34, 0x3C, 0xA8)

PIECE = [(0, 1), (1, 0), (1, 1), (1, 2), (2, 1)]   # plus (5 blocks, not a tetromino)
LR, LC = 2, 1                                # landing top-left cell
PSCALE, LIFT = 1.05, 0.42                    # bigger (lifted toward you), raised above its spot
board = [
    [PUR,  None, GRN,  RED,  BLU],
    [PUR,  None, None, None, GRN],
    [GRN,  None, "G",  None, None],
    [GRN,  "G",  "G",  "G",  PUR],
    [RED,  BLU,  "G",  PUR,  RED],
]

GLOSS = []
def block(d, x, y, s, col):
    b = s * 0.13
    x0, y0, x1, y1 = x, y, x + s, y + s
    i0, j0, i1, j1 = x0 + b, y0 + b, x1 - b, y1 - b
    d.polygon([(x0, y0), (x1, y0), (i1, j0), (i0, j0)], fill=mix(col, WHITE, 0.45))
    d.polygon([(x0, y0), (i0, j0), (i0, j1), (x0, y1)], fill=mix(col, WHITE, 0.22))
    d.polygon([(x1, y0), (x1, y1), (i1, j1), (i1, j0)], fill=mix(col, BLACK, 0.16))
    d.polygon([(x0, y1), (i0, j1), (i1, j1), (x1, y1)], fill=mix(col, BLACK, 0.34))
    n = 32
    for k in range(n):
        t = k / (n - 1)
        d.rectangle([i0, j0 + (j1 - j0) * k / n, i1, j0 + (j1 - j0) * (k + 1) / n + 1],
                    fill=mix(mix(col, WHITE, 0.14), mix(col, BLACK, 0.04), t))
    GLOSS.append((i0, j0, i1, j0 + (j1 - j0) * 0.42))

def apply_gloss(img):
    g = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    for x0, y0, x1, y1 in GLOSS:
        n = 20
        for k in range(n):
            a = int(70 * (1 - k / n) ** 1.5)
            gd.rectangle([x0, y0 + (y1 - y0) * k / n, x1, y0 + (y1 - y0) * (k + 1) / n], fill=(255, 255, 255, a))
    GLOSS.clear()
    return Image.alpha_composite(img, g)

def star(d, x, y, r, col):
    a = math.radians(RND.uniform(-28, 28))
    k = r * 0.2
    pts = [(0, -r), (k, -k), (r, 0), (k, k), (0, r), (-k, k), (-r, 0), (-k, -k)]
    d.polygon([(x + px * math.cos(a) - py * math.sin(a), y + px * math.sin(a) + py * math.cos(a)) for px, py in pts], fill=col)

def cell_box(r, c, inset):
    return (c * C + inset, r * C + inset, (c + 1) * C - inset, (r + 1) * C - inset)

rows = max(r for r, _ in PIECE) + 1
cols = max(c for _, c in PIECE) + 1
PCX = (LC + cols / 2) * C
PCY = (LR + rows / 2) * C - LIFT * C
def piece_box(r, c, inset=0):
    cx = PCX + ((LC + c + 0.5) * C - (LC + cols / 2) * C) * PSCALE
    cy = PCY + ((LR + r + 0.5) * C - (LR + rows / 2) * C) * PSCALE
    h = C * PSCALE / 2 - inset * PSCALE
    return (cx - h, cy - h, cx + h, cy + h)

# ---- board
img = Image.new("RGBA", (W, W), LINE + (255,))
d = ImageDraw.Draw(img)
for r in range(N):
    for c in range(N):
        x0, y0, x1, y1 = cell_box(r, c, L)
        v = board[r][c]
        if v is None:
            d.rectangle([x0, y0, x1, y1], fill=EMPTY)
        elif v == "G":
            d.rectangle([x0, y0, x1, y1], fill=LAND)
        else:
            block(d, x0, y0, x1 - x0, v)
img = apply_gloss(img)

# ---- streaks above the piece's top cells
tops = {}
for r, c in PIECE:
    tops[c] = min(tops.get(c, 99), r)
st = Image.new("RGBA", (W, W), (0, 0, 0, 0))
sd = ImageDraw.Draw(st)
for c, r in tops.items():
    x0, y0, x1, _ = piece_box(r, c)
    for fx, ln in [(0.3, 0.9), (0.72, 1.3)]:
        x = x0 + (x1 - x0) * fx
        y_end, y_start = int(y0), int(max(0, y0 - ln * C))
        for y in range(y_start, y_end):
            a = int(210 * ((y - y_start) / max(1, y_end - y_start)) ** 1.4)
            sd.line([(x - 3 * S, y), (x + 3 * S, y)], fill=(255, 250, 220, a))
img = Image.alpha_composite(img, st.filter(ImageFilter.GaussianBlur(1.5 * S)))

# ---- shadow on its landing spot, glow, piece
ps = Image.new("RGBA", (W, W), (0, 0, 0, 0))
psd = ImageDraw.Draw(ps)
for r, c in PIECE:
    x0, y0, x1, y1 = piece_box(r, c)
    psd.rectangle([x0 + 8 * S, y0 + 30 * S, x1 + 2 * S, y1 + 26 * S], fill=(2, 3, 20, 170))
img = Image.alpha_composite(img, ps.filter(ImageFilter.GaussianBlur(14 * S)))

gl = Image.new("RGB", (W, W), (0, 0, 0))
gld = ImageDraw.Draw(gl)
for r, c in PIECE:
    x0, y0, x1, y1 = piece_box(r, c)
    gld.rectangle([x0 - 10 * S, y0 - 10 * S, x1 + 10 * S, y1 + 10 * S], fill=(150, 110, 20))
gl = gl.filter(ImageFilter.GaussianBlur(44 * S))
img = Image.merge("RGBA", (*ImageChops.screen(img.convert("RGB"), gl).split(), img.split()[3]))

d = ImageDraw.Draw(img)
for r, c in PIECE:
    x0, y0, x1, y1 = piece_box(r, c, L)
    block(d, x0, y0, x1 - x0, YEL)
img = apply_gloss(img)
d = ImageDraw.Draw(img)
for r, c in PIECE:              # no glints on the gold piece (owner's call);
    RND.uniform(12, 18)            # keep the random sequence so the other sparkles stay the same
    RND.uniform(-28, 28)

# ---- sparkles around the piece (cell units)
for fx, fy, r, col in [
    (0.95, 1.40, 28 * S, (255, 255, 255, 255)),
    (1.55, 0.62, 13 * S, (255, 236, 160, 235)),
    (3.40, 1.30, 18 * S, (255, 255, 255, 235)),
    (4.30, 2.45, 22 * S, (255, 236, 160, 255)),
    (2.95, 0.75, 11 * S, (255, 255, 255, 220)),
]:
    star(d, fx * C, fy * C, r, col)

# ---- crop to the zoomed view
cx0, cy0 = 0, 0
img = img.crop((cx0, cy0, cx0 + OUT, cy0 + OUT))
W = OUT
# ---- vignette
vg = Image.new("L", (W, W), 0)
ImageDraw.Draw(vg).ellipse([-W * 0.25, -W * 0.25, W * 1.25, W * 1.25], fill=255)
vg = vg.filter(ImageFilter.GaussianBlur(90 * S))
img = Image.composite(img, Image.alpha_composite(img, Image.new("RGBA", (W, W), (4, 5, 30, 110))), vg)

art = img.convert("RGB")

# Play Store icon (Play applies its own rounded mask)
art.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "design", "icon_512.png"))

# Adaptive launcher background layer: 108dp canvas, art fills the central 72dp (what masks show),
# the outer margin is the vignette's dark edge so parallax / wide masks never show a seam.
EDGE = (8, 10, 40)
layer = Image.new("RGB", (W * 3 // 2, W * 3 // 2), EDGE)
layer.paste(art, (W // 4, W // 4))
for name, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]:
    d_ = os.path.join(ROOT, "app", "src", "main", "res", "mipmap-" + name)
    os.makedirs(d_, exist_ok=True)
    layer.resize((px, px), Image.LANCZOS).save(os.path.join(d_, "ic_launcher_background.png"), optimize=True)
print("icon written")
