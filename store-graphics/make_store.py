"""
Google Play store graphics for Gemblo Blast, built from real in-game captures (raw/) and the game's own art.
  final/feature_graphic_1024x500.png
  final/screenshot_1..6_1080x1920.png   (9:16)
Run:  python make_store.py
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RES = "../app/src/main/res"
FONT = f"{RES}/font/fredoka.ttf"
OUT = "final"


def fredoka(size, weight=700):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def lerp(a, b, t):
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b))


def gradient(w, h, top, bottom):
    g = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(g)
    for y in range(h):
        d.line([(0, y), (w, y)], fill=lerp(top, bottom, y / (h - 1)))
    return g


def rays(img, cx, cy, color, n=14, alpha=34, spin=0.0):
    """Soft light rays from a point (premium 'sunburst')."""
    w, h = img.size
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r = max(w, h) * 1.5
    for i in range(n):
        a0 = spin + i * 2 * math.pi / n
        a1 = a0 + math.pi / n * 0.55
        d.polygon([(cx, cy), (cx + r * math.cos(a0), cy + r * math.sin(a0)), (cx + r * math.cos(a1), cy + r * math.sin(a1))],
                  fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(6))
    img.alpha_composite(layer)


def glow(img, cx, cy, radius, color, alpha=120):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color + (alpha,))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius * 0.45)))


BLOCKS = [(255, 77, 94), (255, 201, 60), (63, 191, 110), (61, 139, 255), (138, 108, 255)]


def jewel(size, color):
    """A bevelled jewel block like the game's."""
    s = size
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    b = max(2, s // 7)
    light, dark, darker = lerp(color, (255, 255, 255), 0.35), lerp(color, (0, 0, 0), 0.22), lerp(color, (0, 0, 0), 0.38)
    d.rectangle([0, 0, s - 1, s - 1], fill=color)
    d.polygon([(0, 0), (s, 0), (s - b, b), (b, b)], fill=light)
    d.polygon([(0, 0), (b, b), (b, s - b), (0, s)], fill=lerp(color, (255, 255, 255), 0.18))
    d.polygon([(s, 0), (s, s), (s - b, s - b), (s - b, b)], fill=dark)
    d.polygon([(0, s), (s, s), (s - b, s - b), (b, s - b)], fill=darker)
    return im


def scatter_blocks(img, seed, count, area, size_range, alpha_range=(150, 255), blur_some=True):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = area
    for _ in range(count):
        s = rnd.randint(*size_range)
        bl = jewel(s, rnd.choice(BLOCKS)).rotate(rnd.uniform(-35, 35), expand=True, resample=Image.BICUBIC)
        a = rnd.randint(*alpha_range)
        bl.putalpha(bl.getchannel("A").point(lambda v: v * a // 255))
        if blur_some and rnd.random() < 0.35:
            bl = bl.filter(ImageFilter.GaussianBlur(rnd.uniform(1.5, 4)))
        img.alpha_composite(bl, (rnd.randint(x0, x1), rnd.randint(y0, y1)))


def outlined_text(img, xy, text, font, fill=(255, 255, 255), stroke=(30, 20, 60), sw=8, shadow=8, anchor="mm"):
    d = ImageDraw.Draw(img)
    x, y = xy
    d.text((x, y + shadow), text, font=font, fill=stroke, anchor=anchor, stroke_width=sw, stroke_fill=stroke)
    d.text((x, y), text, font=font, fill=fill, anchor=anchor, stroke_width=sw, stroke_fill=stroke)


def phone(shot_path, height):
    """The capture inside a slim dark phone frame with rounded corners and a soft shadow."""
    shot = Image.open(shot_path).convert("RGB")
    sw, sh = shot.size
    h = height
    w = int(h * sw / sh)
    bez = int(w * 0.035)
    r_out = int(w * 0.12)
    r_in = r_out - bez
    frame = Image.new("RGBA", (w + 2 * bez, h + 2 * bez), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle([0, 0, frame.width - 1, frame.height - 1], radius=r_out, fill=(18, 16, 28, 255))
    fd.rounded_rectangle([2, 2, frame.width - 3, frame.height - 3], radius=r_out - 2, outline=(90, 86, 110, 255), width=3)
    screen = shot.resize((w, h), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=r_in, fill=255)
    frame.paste(screen, (bez, bez), mask)
    # shadow
    pad = 60
    out = Image.new("RGBA", (frame.width + 2 * pad, frame.height + 2 * pad), (0, 0, 0, 0))
    sh_ = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh_).rounded_rectangle([pad, pad + 24, pad + frame.width, pad + frame.height + 24], radius=r_out, fill=(0, 0, 0, 120))
    out.alpha_composite(sh_.filter(ImageFilter.GaussianBlur(26)))
    out.alpha_composite(frame, (pad, pad))
    return out


SHOTS = [
    # capture, headline line 1, line 2, subline, top color, bottom color
    ("raw/combo_1.png", "CHAIN EPIC", "COMBOS!", "Clear lines in a row for huge points", (110, 60, 210), (40, 40, 140)),
    ("raw/board.png", "DRAG, DROP", "& BLAST!", "Fill a row or column to clear it", (30, 150, 170), (12, 70, 100)),
    ("raw/journey.png", "GO ON A", "JOURNEY", "Beat levels on a road of surprises", (150, 80, 220), (70, 35, 150)),
    ("raw/reveal_3.png", "UNLOCK THE", "STORY", "Every level reveals a new piece", (60, 175, 100), (20, 95, 60)),
    ("raw/album.png", "COLLECT", "YOUR ALBUM", "Beautiful scenes to complete", (60, 140, 235), (25, 70, 160)),
    ("raw/home.png", "RELAX &", "TRAIN YOUR BRAIN", "Play anytime, even offline", (250, 120, 70), (190, 55, 95)),
]


def screenshot(i, cap, l1, l2, sub, top, bottom):
    W, H = 1080, 1920
    img = gradient(W, H, top, bottom).convert("RGBA")
    rays(img, W // 2, 330, (255, 255, 255), n=16, alpha=22, spin=i * 0.3)
    glow(img, W // 2, 1180, 420, lerp(top, (255, 255, 255), 0.35), 90)
    scatter_blocks(img, 100 + i, 9, (-40, 380, W - 40, H - 120), (46, 96), (90, 200))
    # headline
    outlined_text(img, (W // 2, 150), l1, fredoka(92, 700), stroke=lerp(bottom, (0, 0, 0), 0.45), sw=9, shadow=9)
    outlined_text(img, (W // 2, 262), l2, fredoka(118 if len(l2) < 11 else 96, 700), fill=(255, 226, 90),
                  stroke=lerp(bottom, (0, 0, 0), 0.45), sw=10, shadow=10)
    d = ImageDraw.Draw(img)
    d.text((W // 2, 360), sub, font=fredoka(44, 500), fill=(255, 255, 255), anchor="mm")
    # phone, bottom anchored and slightly cropped by the edge for energy
    ph = phone(cap, 1430)
    img.alpha_composite(ph, ((W - ph.width) // 2, H - ph.height + 150))
    img.convert("RGB").save(f"{OUT}/screenshot_{i}_1080x1920.png", optimize=True)


def feature_graphic():
    W, H = 1024, 500
    img = gradient(W, H, (78, 70, 210), (40, 34, 130)).convert("RGBA")
    rays(img, 700, 230, (255, 255, 255), n=18, alpha=24)
    glow(img, 700, 250, 240, (150, 140, 255), 110)
    scatter_blocks(img, 7, 14, (-20, -20, W - 20, H - 20), (28, 64), (100, 220))
    # logo left
    logo = Image.open(f"{RES}/drawable-nodpi/logo.png").convert("RGBA")
    lw = 430
    logo = logo.resize((lw, int(logo.height * lw / logo.width)), Image.LANCZOS)
    lg = Image.open(f"{RES}/drawable-nodpi/logo_glow.png").convert("RGBA").resize(logo.size, Image.LANCZOS)
    lx, ly = 36, 80
    img.alpha_composite(lg, (lx, ly)); img.alpha_composite(logo, (lx, ly))
    outlined_text(img, (lx + lw // 2, ly + logo.height + 44), "Drop. Clear. Combo!", fredoka(44, 700),
                  fill=(255, 226, 90), stroke=(30, 24, 90), sw=6, shadow=5)
    # Goldie peeks out from behind the phone (drawn first, so the phone sits in front), tilted toward it
    m = Image.open(f"{RES}/drawable-nodpi/mascot.png").convert("RGBA")
    m = m.crop(m.getbbox())
    mh = 230
    m = m.resize((int(m.width * mh / m.height), mh), Image.LANCZOS).rotate(12, expand=True, resample=Image.BICUBIC)
    mx, my = 760, 120
    glow(img, mx + m.width // 2 + 30, my + m.height // 2, 150, (255, 220, 120), 80)
    # white glow border: hides the rough cut edge (a tight white rim that fades into a soft outer glow)
    pad = 40
    a_ = Image.new("L", (m.width + 2 * pad, m.height + 2 * pad), 0)
    a_.paste(m.getchannel("A"), (pad, pad))
    rim = a_.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(2.5))
    soft = a_.filter(ImageFilter.MaxFilter(15)).filter(ImageFilter.GaussianBlur(14))
    halo = Image.new("RGBA", a_.size, (255, 255, 255, 0))
    halo.putalpha(soft.point(lambda v: v * 150 // 255))
    img.alpha_composite(halo, (mx - pad, my - pad))
    halo = Image.new("RGBA", a_.size, (255, 255, 255, 0))
    halo.putalpha(rim.point(lambda v: min(255, v * 245 // 255)))
    img.alpha_composite(halo, (mx - pad, my - pad))
    img.alpha_composite(m, (mx, my))
    # the combo screenshot on a phone, zoomed in, slightly tilted, running off the bottom edge
    ph = phone("raw/combo_1.png", 900).rotate(-6, expand=True, resample=Image.BICUBIC)
    pw = 420
    ph = ph.resize((pw, int(ph.height * pw / ph.width)), Image.LANCZOS)
    px, py = 450, 6
    img.alpha_composite(ph, (px, py))
    # a few crisp blocks in the free corners
    rnd = random.Random(3)
    for k, (bx, by) in enumerate([(440, 40), (930, 40), (975, 380), (890, 440), (430, 430)]):
        s = rnd.randint(28, 42)
        bl = jewel(s, BLOCKS[k % 5]).rotate(rnd.uniform(-40, 40), expand=True, resample=Image.BICUBIC)
        img.alpha_composite(bl, (bx, by))
    img.convert("RGB").save(f"{OUT}/feature_graphic_1024x500.png", optimize=True)


if __name__ == "__main__":
    for i, s in enumerate(SHOTS, 1):
        screenshot(i, *s)
    feature_graphic()
    # a contact sheet to review everything at once
    shots = [Image.open(f"{OUT}/screenshot_{i}_1080x1920.png").resize((270, 480)) for i in range(1, 7)]
    fg = Image.open(f"{OUT}/feature_graphic_1024x500.png").resize((820, 400))
    sheet = Image.new("RGB", (6 * 280 + 10, 480 + 430), (235, 235, 240))
    sheet.paste(fg, ((sheet.width - 820) // 2, 10))
    for i, s in enumerate(shots):
        sheet.paste(s, (10 + i * 280, 420))
    sheet.save("_overview.png")
    print("ok")
