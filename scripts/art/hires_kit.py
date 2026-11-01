"""The kit behind the hi-res moods: glyph matching, painting, backdrops, the rig. See HIRES.md."""
import math, pathlib, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/com/loadoutlab/render"
OUT = RES / "moods"
SPR = ROOT / "scripts/art/sprites"
COLS, ROWS, CW, CH = 43, 24, 5, 10   # painted cells at 9 pt: DejaVu inks 5 x 10, so solid areas close up
W, H = COLS * CW, ROWS * CH
BG = np.array([40.0, 40.0, 40.0])    # DARK_GRAY_COLOR behind the loader
FRAMES = 32

# ---- the glyph atlas: GlyphAtlas.java draws the tiles the way the loader paints them ----
_atlas = np.asarray(Image.open(ROOT / "scripts/art/atlas_9pt.png").convert("L"), float) / 255
GLYPHS = list((ROOT / "scripts/art/atlas_9pt.txt").read_text(encoding="utf-8"))
TILES = np.stack([_atlas[:, i * CW:(i + 1) * CW] for i in range(len(GLYPHS))])
G = TILES.reshape(len(GLYPHS), -1)
GG = (G ** 2).sum(1)
COVER = G.mean(1)
SPACE = GLYPHS.index(" ")

def asciify(img, prev=None, solid_price=0.22, hold=0.93):
    """Per cell, the glyph + colour with the least squared error against img
    on the panel colour. Coverage is priced so the body keeps a texture, and
    last frame's glyph gets a discount so still areas do not shimmer."""
    P = img.reshape(ROWS, CH, COLS, CW, 3).transpose(0, 2, 1, 3, 4).reshape(ROWS * COLS, CH * CW, 3) - BG
    dots = P.transpose(0, 2, 1) @ G.T                 # N x 3 x glyphs
    F = np.clip(dots / np.maximum(GG, 1e-6), -BG[0], 255 - BG[0])
    pp = (P ** 2).sum((1, 2))[:, None]
    err = pp - 2 * (F * dots).sum(1) + (F ** 2).sum(1) * GG
    err[:, SPACE] = pp[:, 0]
    err = err + solid_price * (pp + 225 * G.shape[1]) * COVER[None, :] ** 2
    err[:, SPACE] = np.minimum(err[:, SPACE], pp[:, 0])
    if prev is not None: err[np.arange(ROWS * COLS), prev.ravel()] *= hold
    best = err.argmin(1)
    col = F[np.arange(ROWS * COLS), :, best] + BG
    col = np.clip(col, 0, 255).astype(int)
    col = (col + 8 >> 4 << 4).clip(0, 240)            # 4 bits a channel: neighbours share a run
    return best.reshape(ROWS, COLS), col.reshape(ROWS, COLS, 3)

def _rim():
    """A one-pixel line in eighth blocks; the blank corner cells round the card off (a cell holds one stroke)."""
    rim = np.full((ROWS, COLS), -1)
    rim[0, 1:-1], rim[-1, 1:-1] = GLYPHS.index("▔"), GLYPHS.index("▁")
    rim[1:-1, 0], rim[1:-1, -1] = GLYPHS.index("▏"), GLYPHS.index("▕")
    rim[[0, 0, -1, -1], [0, -1, 0, -1]] = SPACE
    return rim

RIM = _rim()

RIM_COLOURS = {"leviathan": (176, 128, 224), "hydra": (96, 160, 224), "graardor": (112, 176, 96), "kq": (160, 192, 80), "flask": (140, 200, 140)}  # where the count picks a flash, not the body

def rim_colour(key, cells):
    """The hue covering the most lit, coloured cells over the whole lap: the body, not a brief flash or a dim
    wall. One colour for every frame, so the rim holds still."""
    if key in RIM_COLOURS: return np.array(RIM_COLOURS[key])
    c = np.concatenate([col.reshape(-1, 3) for _, col in cells]).astype(float)
    c = c[(c.max(1) >= 112) & (c.max(1) - c.min(1) >= 48)]
    hue = np.arctan2(math.sqrt(3) * (c[:, 1] - c[:, 2]), 2 * c[:, 0] - c[:, 1] - c[:, 2])
    bins = ((hue + math.pi) / (2 * math.pi) * 12).astype(int) % 12
    pick = c[bins == np.bincount(bins, minlength=12).argmax()].mean(0)
    return (pick * 224 / max(pick.max(), 1)).astype(int) >> 4 << 4

def framed(best, col, colour):
    best, col = best.copy(), col.copy()
    on = RIM >= 0
    best[on], col[on] = RIM[on], colour
    return best, col

def emit(best, col):
    lines = []
    for r in range(ROWS):
        html, cur = "", None
        for c in range(COLS):
            ch = GLYPHS[best[r, c]]
            k = None if ch == " " else "#%02x%02x%02x" % tuple(col[r, c])
            if k != cur:
                if cur is not None: html += "</font>"
                if k is not None: html += "<font color=" + k + ">"
                cur = k
            html += ch
        if cur is not None: html += "</font>"
        assert not html.startswith(("#", "==")) and html != "--", html
        lines.append(html)
    return lines

def draw(best, col):
    out = np.tile(BG, (ROWS * CH, COLS * CW, 1))
    for r in range(ROWS):
        for c in range(COLS):
            t = TILES[best[r, c]][..., None]; y, x = r * CH, c * CW
            out[y:y + CH, x:x + CW] = BG * (1 - t) + col[r, c] * t
    return out.astype(np.uint8)

# ---- painting ----------------------------------------------------------------
def sprite(name):
    a = np.asarray(Image.open(SPR / name).convert("RGBA"), float).copy()
    mask = (a[..., 3] > 0) & (np.abs(a[..., :3] - a[0, 0, :3]).sum(2) > 12)
    ys, xs = np.where(mask)
    a[..., 3] = mask * 255.0
    return Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.uint8), "RGBA")

def fit(spr, w, h, exposure=0.82, gamma=0.9, grain=0.7, seed=7, sat=1.15):
    """Scaled to fit, then graded for glyphs: normalised to the sprite's own
    range, local contrast so facets survive, and a surface grain."""
    s = min(w / spr.width, h / spr.height)
    im = spr.resize((max(1, int(spr.width * s)), max(1, int(spr.height * s))), Image.LANCZOS)
    a = np.asarray(im, float); rgb, al = a[..., :3], a[..., 3:] / 255
    al = (al > 0.45).astype(float)
    on = al[..., 0] > 0
    lo, hi = np.percentile(rgb[on].mean(1), [3, 97])
    rgb = (rgb - lo) / max(hi - lo, 1) * 255 * exposure
    blur = np.asarray(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), float)
    rgb = rgb + 0.55 * (rgb - blur)
    lum = rgb.mean(2, keepdims=True)
    rgb = lum + (rgb - lum) * sat
    rgb = 255 * (np.clip(rgb, 0, 255) / 255) ** gamma
    hh, ww = rgb.shape[:2]; yy, xx = np.mgrid[0:hh, 0:ww]
    hatch = np.sin((xx + yy * 0.7) * 0.9) * np.sin((xx - yy * 0.7) * 0.55)
    speck = np.random.RandomState(seed).rand(hh // 3 + 1, ww // 3 + 1)
    speck = np.asarray(Image.fromarray((speck * 255).astype(np.uint8)).resize((ww, hh), Image.NEAREST), float) / 255 - 0.5
    rgb = rgb * (1 + grain * (0.2 * hatch + 0.3 * speck))[..., None]
    return np.clip(rgb, 0, 255), al

def warp(rgb, al, fy=None, fx=None):
    h, w = al.shape[:2]; yy, xx = np.mgrid[0:h, 0:w].astype(float)
    sy = yy + (fy(xx, yy) if fy else 0); sx = xx + (fx(xx, yy) if fx else 0)
    iy = np.clip(np.round(sy).astype(int), 0, h - 1); ix = np.clip(np.round(sx).astype(int), 0, w - 1)
    a2 = al[iy, ix].copy(); a2[(sy < 0) | (sy >= h) | (sx < 0) | (sx >= w)] = 0
    return rgb[iy, ix], a2

def over(cv, rgb, al, x, y):
    h, w = al.shape[:2]
    x0, y0, x1, y1 = max(0, x), max(0, y), min(W, x + w), min(H, y + h)
    if x0 >= x1 or y0 >= y1: return
    s = (slice(y0 - y, y1 - y), slice(x0 - x, x1 - x))
    cv[y0:y1, x0:x1] = cv[y0:y1, x0:x1] * (1 - al[s]) + rgb[s] * al[s]

def _layer(seed, cell):
    r = np.random.RandomState(seed).rand(H // cell + 3, W // cell + 3)
    im = Image.fromarray((r * 255).astype(np.uint8)).resize((W + 2 * cell, H + 2 * cell), Image.BICUBIC)
    return np.asarray(im, float)[:H, :W] / 255
def noise(seed, cell, t):
    """value noise that loops in time: two layers on a circle plus fine detail"""
    a = t * 2 * math.pi
    return 0.5 + (_layer(seed, cell) - 0.5) * math.cos(a) + (_layer(seed + 1, cell) - 0.5) * math.sin(a) + 0.4 * (_layer(seed + 2, max(1, cell // 2)) - 0.5)

YY, XX = np.mgrid[0:H, 0:W].astype(float)
def glow(cv, x, y, rad, colour, power=1.0):
    cv += np.exp(-((XX - x) ** 2 + (YY - y) ** 2) / (2 * rad * rad))[..., None] * np.array(colour, float) * power

def ramp(stops, v):
    """a colour ramp over v in 0..1, array or scalar"""
    v = np.clip(v, 0, 1) * (len(stops) - 1)
    i = np.minimum(v.astype(int), len(stops) - 2); f = (v - i)[..., None]
    s = np.array(stops, float)
    return s[i] * (1 - f) + s[i + 1] * f

def stamp(cv, x, y, colour, size):
    rad = int(size + 1); ix, iy = int(x), int(y)
    for dy in range(-rad, rad + 1):
        for dx in range(-rad, rad + 1):
            if 0 <= iy + dy < H and 0 <= ix + dx < W:
                cv[iy + dy, ix + dx] += np.array(colour, float) * max(0.0, 1 - (dx * dx + dy * dy) / (size * size + 0.01))

def plume(cv, x0, y0, dirx, diry, length, spread, t, stops, seed, power=1.0, wobble=0.5):
    """A breath as a fluid: a cone, eaten by noise rushing down its axis, hottest at the mouth."""
    ux, uy = dirx / math.hypot(dirx, diry), diry / math.hypot(dirx, diry)
    along = (XX - x0) * ux + (YY - y0) * uy
    across = -(XX - x0) * uy + (YY - y0) * ux
    u = along / length
    a = t * 2 * math.pi
    r1, r2 = _layer(seed, 14), _layer(seed + 1, 7)
    def scroll(n, sp):
        return n[(YY.astype(int) - int(uy * sp * t * H)) % H, (XX.astype(int) - int(ux * sp * t * W)) % W]
    n = 0.6 * scroll(r1, 3) + 0.4 * scroll(r2, 5)
    width = (0.15 + u) * spread
    body = 1 - np.abs(across + wobble * spread * math.sin(a * 3) * u) / np.maximum(width, 1)
    den = np.clip(body * 1.5 + (n - 0.5) * 2.4 - np.clip(u, 0, 1) ** 2, 0, 1) * (u > 0) * (u < 1)
    den *= power
    cv[:] = cv * (1 - den[..., None] * 0.85) + ramp(stops, 1 - den * (1.1 - 0.5 * np.clip(u, 0, 1)))[..., :3] * den[..., None] * 0.85 + den[..., None] * 15

def ring(cv, cx, cy, radius, squash, width, colour, mask=1.0):
    d = np.abs(np.sqrt((XX - cx) ** 2 + ((YY - cy) / squash) ** 2) - radius)
    cv += (np.clip(1 - d / width, 0, 1) * mask)[..., None] * np.array(colour, float)

def bolt(cv, x0, y0, x1, y1, seed, colour, jag=8, size=1.2):
    r = np.random.RandomState(seed); pts = [(x0, y0), (x1, y1)]
    for level in range(4):
        nxt = [pts[0]]
        for a, b in zip(pts, pts[1:]):
            nxt += [((a[0] + b[0]) / 2 + (r.rand() - 0.5) * jag / (level + 1), (a[1] + b[1]) / 2 + (r.rand() - 0.5) * jag / (level + 1)), b]
        pts = nxt
    for a, b in zip(pts, pts[1:]):
        for i in range(5):
            stamp(cv, a[0] + (b[0] - a[0]) * i / 5, a[1] + (b[1] - a[1]) * i / 5, colour, size)

class Motes:
    """particles on a loop: each has its own phase, a start, a velocity and a gravity"""
    def __init__(self, seed, n, spawn):
        r = np.random.RandomState(seed); self.p = [(r.rand(), spawn(r)) for _ in range(n)]
    def draw(self, cv, t, colour, size=1.4, life=1.0):
        for t0, (x, y, vx, vy, g) in self.p:
            u = (t + t0) % 1.0
            if u > life: continue
            stamp(cv, x + vx * u, y + vy * u + g * u * u, colour(u / life), size)

# ---- backdrops: painted once per mood, dim, feathered into the panel at the edges ----
EDGE = (np.clip(np.minimum(XX, W - 1 - XX) / 16, 0, 1) * np.clip(np.minimum(YY, H - 1 - YY) / 16, 0, 1))[..., None]
def feather(rgb): return BG + (rgb - BG) * EDGE
def fbm(seed, cells=(40, 20, 10, 5)):
    out, total = 0.0, 0.0
    for i, c in enumerate(cells):
        out = out + _layer(seed + i, c) * 0.55 ** i; total += 0.55 ** i
    return out / total
def ridge(seed, base, height, cells=(36, 14, 6)):
    """a skyline: for each column, the y where the silhouette starts"""
    return base - height * (fbm(seed, cells)[0, :] - 0.5) * 2.4

def ice_backdrop():
    sky = ramp([(26, 30, 42), (32, 40, 54), (46, 56, 70)], np.clip(YY / 165, 0, 1))
    far = ridge(101, 128, 34); near = ridge(105, 150, 20)
    tex = fbm(110)[..., None]
    img = sky.copy()
    m = (YY > far[None, :])[..., None]
    img = img * (1 - m) + (np.array([40.0, 48, 62]) + (tex - 0.5) * 30 + np.clip(1 - (YY - far[None, :]) / 12, 0, 1)[..., None] * 44) * m
    m = (YY > near[None, :])[..., None]
    img = img * (1 - m) + (np.array([34.0, 42, 54]) + (tex - 0.5) * 34 + np.clip(1 - (YY - near[None, :]) / 8, 0, 1)[..., None] * 50) * m
    ice = fbm(120, (30, 12, 5))
    crack = (np.abs(fbm(130, (26, 13, 6)) - 0.5) < 0.012)[..., None]
    floe = np.array([48.0, 62, 76]) * (0.75 + 0.5 * ice[..., None]) * (0.75 + 0.45 * np.clip((YY - 165) / 75, 0, 1)[..., None]) + crack * 60
    m = (YY > 165)[..., None]
    return feather(img * (1 - m) + floe * m)

def keep_backdrop():
    """the Bandos keep: coursed stone, mortar, stains, two war banners"""
    course, brick = 18, 32
    row = (YY // course).astype(int); off = (row % 2) * brick // 2
    col = ((XX + off) // brick).astype(int)
    shade = np.random.RandomState(201).rand(40, 40)[row % 40, col % 40]
    mortar = ((YY % course < 2) | ((XX + off) % brick < 2))
    stone = (0.7 + 0.6 * shade) * (0.75 + 0.5 * fbm(210, (24, 8, 3)))
    img = (np.array([56.0, 50, 46]) * stone[..., None]) * (1 - 0.65 * mortar[..., None])
    for x0 in (22, W - 46):
        m = ((np.abs(XX - x0 - 12) < 12) & (YY < 112 - np.abs(XX - x0 - 12) * 0.8) & (YY > 18))[..., None]
        cloth = np.array([72.0, 30, 24]) * (0.8 + 0.25 * np.sin((XX - x0) * 0.8))[..., None]
        trim = (np.abs(np.abs(XX - x0 - 12) - 10) < 1.5)[..., None] * np.array([34.0, 28, 8])
        img = img * (1 - m) + (cloth + trim) * m
    return img

def lair_backdrop():
    """the KBD's cave: strata, stalactites overhead, a lava seam low down"""
    bend = fbm(301, (30, 12))
    strata = 0.5 + 0.5 * np.sin(YY * 0.32 + bend * 9)
    rock = np.array([36.0, 30, 34]) * (0.6 + 0.4 * strata[..., None] * (0.4 + fbm(310, (16, 6, 3))[..., None]))
    drip = 24 + 44 * np.abs(np.sin(XX * 0.19 + 2 * np.sin(XX * 0.05))) ** 3 + 10 * fbm(320, (12, 5))[0, :][None, :]
    top = (YY < drip)[..., None]
    return rock * (1 - top) + np.array([14.0, 12, 16]) * top

# ---- the rig: parts of the sprite move on their own, and a light moves over the surface ----
class Rig:
    """Parts are soft ellipses in sprite fractions, each rotating about its own
    pivot. Later parts win where they overlap. Shading comes from the sprite's
    own relief, so a moving light turns flat colour into form."""
    def __init__(self, rgb, al, pad=30):
        self.h, self.w = al.shape[:2]; self.pad = pad
        self.rgb = np.pad(rgb, ((pad, pad), (pad, pad), (0, 0)), mode="edge")
        self.al = np.pad(al, ((pad, pad), (pad, pad), (0, 0)))
        self.yy, self.xx = np.mgrid[0:self.al.shape[0], 0:self.al.shape[1]].astype(float)
        lum = Image.fromarray(np.clip(self.rgb.mean(2), 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))
        gy, gx = np.gradient(np.asarray(lum, float))
        self.gx, self.gy = np.tanh(gx / 14), np.tanh(gy / 14)

    def px(self, fx, fy): return self.pad + fx * self.w, self.pad + fy * self.h

    def weight(self, part, x, y):
        cx, cy = self.px(*part["c"]); rx, ry = part["r"][0] * self.w, part["r"][1] * self.h
        d = np.sqrt(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2)
        return np.clip((1 - d) / part.get("soft", 0.35), 0, 1)

    def pose(self, parts, light=(0.0, -1.0), relief=0.45, bend=None):
        sx, sy = self.xx.copy(), self.yy.copy()
        for part in parts:
            a = math.radians(part.get("a", 0.0)); ca, sa = math.cos(a), math.sin(a)
            px, py = self.px(*part["p"]); dx, dy = part.get("d", (0, 0))
            rx, ry = self.xx - dx * self.w - px, self.yy - dy * self.h - py
            ux, uy = px + ca * rx + sa * ry, py - sa * rx + ca * ry    # where this pixel comes from
            w = self.weight(part, ux, uy)
            sx, sy = sx * (1 - w) + ux * w, sy * (1 - w) + uy * w
        if bend:
            bx, by = bend((self.xx - self.pad) / self.w, (self.yy - self.pad) / self.h)
            sx, sy = sx + bx, sy + by
        shade = 1 + relief * (self.gx * light[0] + self.gy * light[1])
        for part in parts:
            if part.get("lit"): shade = shade * (1 + part["lit"] * self.weight(part, self.xx, self.yy))
        lit = np.clip(self.rgb * shade[..., None], 0, 255)
        iy = np.clip(np.round(sy).astype(int), 0, self.al.shape[0] - 1); ix = np.clip(np.round(sx).astype(int), 0, self.al.shape[1] - 1)
        return lit[iy, ix], self.al[iy, ix]

    def at(self, part, fx, fy):
        """where a sprite point lands once its part has moved; canvas offset not included"""
        a = math.radians(part.get("a", 0.0)); ca, sa = math.cos(a), math.sin(a)
        px, py = self.px(*part["p"]); x, y = self.px(fx, fy); dx, dy = part.get("d", (0, 0))
        return px + ca * (x - px) - sa * (y - py) + dx * self.w - self.pad, py + sa * (x - px) + ca * (y - py) + dy * self.h - self.pad

def wave(t, n=1, phase=0.0): return math.sin(2 * math.pi * (t * n + phase))

# ---- golden geometry (Andrew 2026-10-31: "more geometric/curvature patterns please ; use the golden ratio") ----
PHI = (1 + 5 ** 0.5) / 2
GOLDEN_ANGLE = 2 * math.pi * (1 - 1 / PHI)            # 137.5 degrees: the sunflower's turn
GOLD_B = math.log(PHI) / (math.pi / 2)                # a golden spiral grows by phi every quarter turn

def phi_spiral(cx, cy, r0, turns, rot=0.0, squash=1.0, hand=1):
    """Points along a golden spiral from radius r0 outward, ready for a stroke or for particles to ride."""
    pts = []
    for i in range(int(turns * 48) + 1):
        a = i / 48 * 2 * math.pi
        r = r0 * math.exp(GOLD_B * a)
        pts.append((cx + r * math.cos(hand * a + rot), cy + r * math.sin(hand * a + rot) * squash))
    return pts

def phi_stroke(cv, pts, colour, size=1.2, fade=True):
    """Draws a phi_spiral, fading along its length when fade is set. Heavier ink at the eye of the curve."""
    for i, (x, y) in enumerate(pts):
        stamp(cv, x, y, np.array(colour, float) * ((1 - i / len(pts)) if fade else 1.0), size)

def phi_field(cx, cy, t, arms=2, spin=1, squash=1.0, sharp=3.0, hand=1):
    """A whole-frame 0..1 mask of turning golden spiral arms: vortices, portals, charged floors.
    spin must be a whole number for the loop to close."""
    dx, dy = XX - cx, (YY - cy) / squash
    r = np.maximum(np.hypot(dx, dy), 1.0)
    phase = arms * (hand * np.arctan2(dy, dx) - np.log(r) / GOLD_B) - 2 * math.pi * spin * t
    return np.clip(0.5 + 0.5 * np.cos(phase), 0, 1) ** sharp

def phyllotaxis(cx, cy, n, spacing, rot=0.0, squash=1.0, start=1):
    """Sunflower seeds: n points at the golden angle, for sigils, spores, ember bursts, scale patterns."""
    out = []
    for i in range(start, start + n):
        r = spacing * math.sqrt(i); a = i * GOLDEN_ANGLE + rot
        out.append((cx + r * math.cos(a), cy + r * math.sin(a) * squash))
    return out

def phi_radii(r0, n):
    """Ring radii in golden progression: r0, r0 phi, r0 phi squared..."""
    return [r0 * PHI ** i for i in range(n)]

def phi_phase(i):
    """A part's phase offset: golden steps never line up, so parts never move in unison."""
    return (i / PHI) % 1.0

GOLD_X, GOLD_Y = W / PHI, H / PHI                     # the golden section lines of the frame

# ---- golden shapes (Andrew 2026-10-31: "not everything has to be a swirl. play with
# concentricity, shapes, pentagonal, icosohedron, hexagon") ----
def line(cv, a, b, colour, size=1.2):
    n = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1]) / 1.5))
    for i in range(n + 1):
        stamp(cv, a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n, colour, size)

def polygon_pts(cx, cy, r, n, rot=0.0, squash=1.0):
    return [(cx + r * math.cos(rot + 2 * math.pi * i / n), cy + r * math.sin(rot + 2 * math.pi * i / n) * squash) for i in range(n)]

def polygon(cv, cx, cy, r, n, colour, rot=0.0, squash=1.0, size=1.2):
    p = polygon_pts(cx, cy, r, n, rot, squash)
    for i in range(n): line(cv, p[i], p[(i + 1) % n], colour, size)

def pentagram(cv, cx, cy, r, colour, rot=-math.pi / 2, squash=1.0, size=1.2):
    """A pentagon's diagonals: each cuts the next in the golden ratio."""
    p = polygon_pts(cx, cy, r, 5, rot, squash)
    for i in range(5): line(cv, p[i], p[(i + 2) % 5], colour, size)

def nested(cv, cx, cy, r, n, levels, colour, rot=0.0, twist=0.0, squash=1.0, size=1.2):
    """Concentric polygons, each 1/phi of the last, dimming inward; twist turns each level."""
    for i in range(levels):
        polygon(cv, cx, cy, r / PHI ** i, n, np.array(colour, float) * (1 - 0.6 * i / max(1, levels - 1)), rot + twist * i, squash, size)

def rings(cv, cx, cy, r, levels, colour, squash=1.0, width=2.0):
    """Concentric circles with golden spacing, largest first."""
    for i in range(levels): ring(cv, cx, cy, r / PHI ** i, squash, width, np.array(colour, float) * (1 - 0.5 * i / max(1, levels)))

def hex_mask(side, cx=W / 2, cy=H / 2, rot=0.0, width=1.5, squash=1.0):
    """0..1 mask of a hexagon lattice's edges: wards, crystal, honeycomb, floors."""
    x = (XX - cx) * math.cos(rot) + (YY - cy) / squash * math.sin(rot)
    y = -(XX - cx) * math.sin(rot) + (YY - cy) / squash * math.cos(rot)
    q = (2 / 3 * x) / side; r_ = (-x / 3 + math.sqrt(3) / 3 * y) / side
    a, b, c = q, r_, -q - r_
    ra, rb, rc = np.round(a), np.round(b), np.round(c)
    da, db, dc = np.abs(ra - a), np.abs(rb - b), np.abs(rc - c)
    fix_a, fix_b = (da > db) & (da > dc), (db >= da) & (db > dc)
    ra = np.where(fix_a, -rb - rc, ra); rb = np.where(fix_b, -ra - rc, rb)
    rc = -ra - rb
    u, v, w_ = a - ra, b - rb, c - rc
    rim = np.maximum(np.maximum(np.abs(u - v), np.abs(v - w_)), np.abs(w_ - u))   # 1 on a cell's edge, 0 at its centre
    return np.clip(1 - (1 - rim) * side * 1.5 / width, 0, 1)

_G = PHI
SOLIDS = {
    "icosahedron": ([(0, a, b * _G) for a in (-1, 1) for b in (-1, 1)] + [(a, b * _G, 0) for a in (-1, 1) for b in (-1, 1)] + [(b * _G, 0, a) for a in (-1, 1) for b in (-1, 1)], 2.0),
    "dodecahedron": ([(a, b, c) for a in (-1, 1) for b in (-1, 1) for c in (-1, 1)]
                     + [(0, a / _G, b * _G) for a in (-1, 1) for b in (-1, 1)] + [(a / _G, b * _G, 0) for a in (-1, 1) for b in (-1, 1)] + [(b * _G, 0, a / _G) for a in (-1, 1) for b in (-1, 1)], 2 / _G),
}
def wire_solid(cv, cx, cy, r, t, colour, solid="icosahedron", spin=1, tilt=0.45, size=1.3, squash=1.0):
    """A turning wireframe built from golden rectangles; far edges dim for depth. spin is whole turns a loop."""
    pts, edge = SOLIDS[solid]
    a = 2 * math.pi * spin * t; ca, sa, ct, st = math.cos(a), math.sin(a), math.cos(tilt), math.sin(tilt)
    scale = r / max(math.sqrt(sum(c * c for c in p)) for p in pts)
    out = []
    for x, y, z in pts:
        x, z = x * ca + z * sa, -x * sa + z * ca
        y, z = y * ct - z * st, y * st + z * ct
        out.append((cx + x * scale, cy + y * scale * squash, z))
    far = max(abs(p[2]) for p in out) or 1
    for i in range(len(pts)):
        for j in range(i + 1, len(pts)):
            if abs(math.dist(pts[i], pts[j]) - edge) < 1e-6:
                depth = 0.45 + 0.55 * ((out[i][2] + out[j][2]) / 2 / far * 0.5 + 0.5)
                line(cv, out[i][:2], out[j][:2], np.array(colour, float) * depth, size)
    return out

def polar(cv, cx, cy, fn, colour, rot=0.0, squash=1.0, size=1.2, upto=1.0, steps=240):
    """Trace r = fn(theta) as a line; upto (0..1) draws only that share, so a figure can inscribe itself."""
    last = None
    for i in range(int(steps * upto) + 1):
        th = 2 * math.pi * i / steps
        r = fn(th)
        p = (cx + r * math.cos(th + rot), cy + r * math.sin(th + rot) * squash)
        if last: line(cv, last, p, colour, size)
        last = p

def mandala(cv, cx, cy, r, colour, t=0.0, petals=4, sides=4, inner=3, rot=0.0, squash=1.0, reveal=1.0, disc=0.0, spin=0, size=1.2):
    """Andrew's reference: many shapes nested on one centre, not one shape repeated. Outward in:
    a dark disc, double lotus lobes, a fringe of small petals, a ring, a dotted ring, a polygon and
    an inner figure. reveal (0..1) inscribes the layers from the middle out; disc (0..1) dims what
    is behind it; spin turns the layers in opposite directions, whole steps a loop."""
    col = np.array(colour, float)
    if disc > 0:
        d = np.sqrt((XX - cx) ** 2 + ((YY - cy) / squash) ** 2)
        cv *= (1 - disc * np.clip((r * 0.98 - d) / 3, 0, 1))[..., None]
    turn = 2 * math.pi * spin * t
    live = lambda a: float(np.clip((reveal - a) / 0.25, 0, 1))
    tri, poly, ring_, fringe, lobes = r * 0.24, r * 0.36, r * 0.52, r * 0.66, r
    if live(0.0): polygon(cv, cx, cy, tri, inner, col, rot + math.pi / 2 - turn, squash, size * 1.2)
    if live(0.15):
        polygon(cv, cx, cy, poly, sides, col * 0.9, rot + math.pi / sides + turn / sides, squash, size)
    if live(0.3):
        polar(cv, cx, cy, lambda th: ring_, col * 0.75, rot, squash, size, live(0.3))
        for x, y in polygon_pts(cx, cy, ring_ * 0.88, 24, rot - turn / 6, squash): stamp(cv, x, y, col * 0.8, size * 0.9)
    if live(0.45):
        n = petals * 4
        polar(cv, cx, cy, lambda th: fringe - (fringe - ring_) * abs(math.cos(n * (th - turn / n) / 2)) ** 0.7, col * 0.55, rot, squash, size * 0.8, live(0.45), 360)
    if live(0.6):
        lobe = lambda f: (lambda th: lobes * f * (0.56 + 0.44 * abs(math.cos(petals * th / 2)) ** 0.55))
        polar(cv, cx, cy, lobe(1.0), col, rot, squash, size * 1.35, live(0.6), 360)
        polar(cv, cx, cy, lobe(0.9), col * 0.6, rot, squash, size * 0.9, live(0.6), 360)
    if live(0.75):
        polar(cv, cx, cy, lambda th: lobes * 1.04, col * 0.45, rot, squash, size * 0.9, live(0.75))
