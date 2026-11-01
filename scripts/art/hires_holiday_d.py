"""Hi-res holiday film: St Patrick's. Rain on green hills, the sun breaks, a rainbow lands on a pot of gold, coins
spill through a shamrock field, a leprechaun-hatted gnome dances a jig, and the rain comes back. No sprites: everything
is lit primitives, faceted so it keeps the chunky PS2 look. See HOLIDAYS.md."""
from hires_holiday_card import celebrating
import hires_holiday_card as Wear
from hires_kit import *
from hires_batch2c import sm
from hires_raids import FILM, BAR, letterbox, push, play, rays
from hires_last import ink

LIGHT = np.array((-0.5, -0.62, 0.6))
GOLD = [(84, 44, 0), (176, 108, 8), (240, 176, 30), (255, 226, 96), (255, 250, 190)]
IRON = [(6, 6, 10), (14, 14, 20), (26, 26, 34), (46, 48, 60), (84, 88, 106)]
COAT = [(8, 44, 22), (18, 92, 42), (40, 160, 66), (110, 226, 104)]
TROUSER = [(6, 28, 16), (12, 58, 30), (26, 104, 50), (70, 160, 84)]
SKIN = [(120, 62, 44), (196, 116, 84), (244, 170, 130), (255, 214, 180)]
BEARD = [(90, 28, 4), (170, 62, 8), (232, 104, 16), (255, 164, 56)]
WHITE = [(90, 96, 90), (170, 176, 168), (232, 236, 226), (255, 255, 250)]
BLACK = [(4, 4, 6), (14, 14, 18), (34, 34, 42), (90, 92, 104)]
BANDS = [(240, 40, 44), (250, 130, 30), (252, 220, 50), (50, 210, 80), (50, 120, 245), (150, 70, 230)]

def _shade(nx, ny, m, stops, facets):
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    v = np.clip(0.5 + 0.62 * (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]), 0, 1)
    if facets: v = np.floor(v * facets + 0.5) / facets
    return ramp(stops, v)

def ball(cv, cx, cy, rx, ry, stops, rot=0.0, facets=6):
    """a lit ellipsoid, shaded in bands so it reads as a low-poly solid"""
    if rx < 0.3 or ry < 0.3: return
    R = max(rx, ry) + 1
    x0, x1, y0, y1 = max(0, int(cx - R)), min(W, int(cx + R) + 2), max(0, int(cy - R)), min(H, int(cy + R) + 2)
    if x0 >= x1 or y0 >= y1: return
    dx, dy = XX[y0:y1, x0:x1] - cx, YY[y0:y1, x0:x1] - cy
    c, s = math.cos(rot), math.sin(rot)
    nx, ny = (dx * c + dy * s) / rx, (-dx * s + dy * c) / ry
    m = nx * nx + ny * ny <= 1
    if m.any(): cv[y0:y1, x0:x1][m] = _shade(nx, ny, m, stops, facets)[m]

def limb(cv, a, b, ra, rb, stops, facets=5):
    """a tapered capsule lit as a round body"""
    R = max(ra, rb) + 1
    x0, x1 = max(0, int(min(a[0], b[0]) - R)), min(W, int(max(a[0], b[0]) + R) + 2)
    y0, y1 = max(0, int(min(a[1], b[1]) - R)), min(H, int(max(a[1], b[1]) + R) + 2)
    if x0 >= x1 or y0 >= y1: return
    ux, uy = b[0] - a[0], b[1] - a[1]
    L = max(ux * ux + uy * uy, 1e-6)
    px, py = XX[y0:y1, x0:x1] - a[0], YY[y0:y1, x0:x1] - a[1]
    s = np.clip((px * ux + py * uy) / L, 0, 1)
    r = ra + (rb - ra) * s
    nx, ny = (px - s * ux) / r, (py - s * uy) / r
    m = nx * nx + ny * ny <= 1
    if m.any(): cv[y0:y1, x0:x1][m] = _shade(nx, ny, m, stops, facets)[m]

def mask_of(pts):
    im = Image.new("L", (W, H), 0)
    ImageDraw.Draw(im).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    return np.asarray(im, float)[..., None] / 255

def coin(cv, x, y, r, spin):
    """a gold coin turning on its edge: wide when it faces you, a sliver when it does not"""
    f = abs(math.cos(spin))
    ball(cv, x, y, 0.9 + r * f, r, GOLD[:4], facets=4)
    if f > 0.45:
        ball(cv, x, y, r * f * 0.62, r * 0.62, [GOLD[1], GOLD[2], GOLD[3]], facets=3)
        stamp(cv, x - r * f * 0.35, y - r * 0.4, (255, 255, 220), 1.6)

def pot(cv, cx, cy, s, heat=1.0):
    """iron cauldron, heaped; cy is the middle of its belly"""
    for dx in (-0.55, 0.55):
        limb(cv, (cx + dx * s, cy + 0.6 * s), (cx + dx * 1.1 * s, cy + 0.98 * s), 0.13 * s, 0.09 * s, IRON)
    ball(cv, cx, cy, s, 0.82 * s, IRON, facets=7)
    for sx in (-1, 1):                                                           # ring handles
        ring(cv, cx + sx * 1.0 * s, cy - 0.28 * s, 0.17 * s, 1.0, max(1.2, 0.07 * s), (110, 112, 128))
    ball(cv, cx, cy - 0.62 * s, 0.86 * s, 0.25 * s, IRON, facets=5)              # the lip
    ball(cv, cx, cy - 0.68 * s, 0.70 * s, 0.34 * s, [GOLD[0], GOLD[1], GOLD[2], GOLD[3]], facets=4)     # the heap
    if s > 24:
        r = np.random.RandomState(4107)
        for i in range(16):
            a, d = r.rand() * 2 * math.pi, math.sqrt(r.rand())
            coin(cv, cx + math.cos(a) * d * 0.6 * s, cy - 0.74 * s + math.sin(a) * d * 0.24 * s, 0.11 * s, r.rand() * 3)
    glow(cv, cx, cy - 0.95 * s, 0.5 * s, (255, 170, 40), 0.22 * heat)

# ---- shamrocks: three hearts with a pale chevron, drawn once a size and turned from a cache ----
_ROCK = {}
def rock_base(s):
    if s in _ROCK: return _ROCK[s]
    n = int(s * 3.4) | 1
    yy, xx = np.mgrid[0:n, 0:n].astype(float)
    cx, cy = n / 2, n * 0.62
    rgb, al = np.zeros((n, n, 3)), np.zeros((n, n))
    stem = (np.abs(xx - cx - (yy - cy) * 0.12) < max(1.0, s * 0.07)) & (yy > cy)
    rgb[stem], al[stem] = (44, 130, 50), 1
    for ang, lift in ((-90, 1.16), (150, 0.9), (30, 1.0)):
        a = math.radians(ang)
        u, v = ((xx - cx) * math.cos(a) + (yy - cy) * math.sin(a)) / s, (-(xx - cx) * math.sin(a) + (yy - cy) * math.cos(a)) / s
        lobes = np.minimum((u - 0.68) ** 2 + (np.abs(v) - 0.2) ** 2 - 0.29 ** 2, 1)
        wedge = (np.abs(v) < u * 0.5) & (u > 0) & (u < 0.72)
        m = (lobes < 0) | wedge
        tone = 0.72 + 0.34 * np.clip(u, 0, 1) - 0.34 * np.abs(v) * 0.7
        c = np.array([34.0, 150, 56]) * lift * tone[..., None]
        chevron = np.exp(-((u - 0.52 + np.abs(v) * 0.6) / 0.11) ** 2)
        c = c + chevron[..., None] * np.array([70.0, 80, 44]) * lift
        rib = (np.abs(v) < 0.035)
        c = np.where(rib[..., None], c * 0.5, c)
        edge = m & ~(np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(2 * max(1, int(s * 0.04)) + 1))) > 0)   # the outline only, so the leaf has no holes
        c = np.where(edge[..., None], c * 0.5, c)
        rgb[m], al[m] = c[m], 1
    _ROCK[s] = (rgb, al, n, cx, n * 0.97)
    return _ROCK[s]

_TURN = {}
def rock(cv, x, y, s, deg, tone=1.0):
    """a shamrock planted at (x, y), leaning deg about its foot"""
    d = int(round(deg / 2.0)) * 2
    k = (s, d)
    if k not in _TURN:
        rgb, al, n, bx, by = rock_base(s)
        a = np.clip(np.dstack([rgb, al[..., None] * 255]), 0, 255).astype(np.uint8)
        im = Image.fromarray(a, "RGBA").rotate(d, Image.BILINEAR, center=(bx, by))
        arr = np.asarray(im, float)
        _TURN[k] = (arr[..., :3], (arr[..., 3:] > 128).astype(float), bx, by)
    rgb, al, bx, by = _TURN[k]
    over(cv, np.clip(rgb * tone, 0, 255), al, int(x - bx), int(y - by))

# ---- the country: three hills, walls between the fields, a cottage ----
def hill_line(base, a, f, ph, b=0.0, g=0.0):
    return base + a * np.sin(XX[0] * f + ph) + b * np.sin(XX[0] * g + ph * 2)

def country():
    img = np.zeros((H, W, 3))
    lines = []
    for i, (base, a, f, ph, b, g, col, wall, seed) in enumerate((
            (124, 14, 0.021, 0.6, 5, 0.06, (52, 104, 86), 0.72, 4201),
            (150, 18, 0.018, 3.4, 5, 0.05, (56, 126, 58), 0.6, 4203),
            (182, 16, 0.024, 5.0, 4, 0.07, (70, 150, 42), 0.5, 4205))):
        ln = hill_line(base, a, f, ph, b, g)[None, :]
        d = YY - ln
        m = (d > 0)[..., None]
        # the fields: a skewed grid over the slope, each patch its own green, stone walls between
        size = 30 + 14 * i
        ux, vy = XX + d * 1.4, d * (2.2 - 0.4 * i) + size * 0.3
        cu, cv_ = np.floor(ux / size).astype(int), np.floor(vy / (size * 0.62)).astype(int)
        shades = np.random.RandomState(seed).rand(32, 32)
        patch = shades[cv_ % 32, cu % 32]
        walls = ((ux % size) < 1.6 + i * 0.5) | ((vy % (size * 0.62)) < 1.3 + i * 0.5)
        tex = fbm(seed + 1, (14, 6, 3))
        c = np.array(col, float) * (0.6 + 0.7 * patch)[..., None] * (0.82 + 0.34 * tex)[..., None]
        c = c * (1.25 - 0.42 * np.clip(d / 34, 0, 1))[..., None]                # bright crest, falling to shade: the next hill stands off it
        c = np.where(walls[..., None], c * wall, c)
        img = img * (1 - m) + c * m
        lines.append(ln)
        if i == 1:                                                               # the cottage sits on the middle hill
            cx = 58
            gy = float(ln[0, cx]) + 8
            for pts, colr in (([(cx - 18, gy), (cx - 18, gy - 15), (cx + 18, gy - 15), (cx + 18, gy)], (214, 208, 186)),
                              ([(cx - 23, gy - 14), (cx - 10, gy - 32), (cx + 10, gy - 32), (cx + 23, gy - 14)], (128, 98, 44))):
                k = mask_of(pts)
                img = img * (1 - k) + np.array(colr, float) * k
            k = mask_of([(cx + 10, gy - 26), (cx + 10, gy - 38), (cx + 16, gy - 38), (cx + 16, gy - 20)])
            img = img * (1 - k) + np.array((70.0, 64, 60)) * k
    return img, lines

class Land:
    def __init__(self):
        self.albedo, self.lines = country()
        self.sky_mask = (self.albedo.sum(2) <= 0)[..., None]
        self.sun = (GOLD_X - 34, 62)
        r = np.random.RandomState(4211)
        t1 = Image.new("L", (W, H), 0); t2 = Image.new("L", (W, H), 0)
        for tile, n, length in ((t1, 70, 14), (t2, 34, 26)):
            d = ImageDraw.Draw(tile)
            for _ in range(n):
                x, y = r.rand() * W, r.rand() * H
                for ox in (0, W, -W):
                    for oy in (0, H, -H):
                        d.line((x + ox, y + oy, x + ox - length * 0.26, y + oy + length), fill=255, width=1 if tile is t1 else 2)
        self.rain1, self.rain2 = np.asarray(t1, float) / 255, np.asarray(t2, float) / 255
        self.sky = ramp([(30, 58, 100), (52, 96, 132), (120, 150, 136)], np.clip((YY - 20) / 120, 0, 1))
        self.dsun = np.hypot(XX - self.sun[0], YY - self.sun[1])

    def rain(self, cv, t, power):
        if power <= 0.01: return
        far = np.roll(np.roll(self.rain1, int(t * H * 12), 0), int(-t * W * 3), 1)
        near = np.roll(np.roll(self.rain2, int(t * H * 24), 0), int(-t * W * 7), 1)
        cv += far[..., None] * np.array((62.0, 74, 92)) * power + near[..., None] * np.array((104.0, 122, 146)) * power

    def paint(self, open_, t, raining=None, bow=0.0, pot_on=0.0, sun=1.0, bow_alpha=1.0):
        """open_ 0 is a shut grey sky over wet dim hills, 1 is sun all the way down"""
        n = noise(4221, 34, t)
        gap = open_ * 1.75 - self.dsun / 165
        den = np.clip((n - 0.5) * 2.4 + 1.5 - np.clip(gap + 0.5, 0, 2.5) * 1.6 * min(1.0, open_ * 6), 0, 1)
        cloud = ramp([(24, 28, 36), (34, 38, 48), (52, 56, 66), (64, 68, 78)], n)
        sky = self.sky.copy()
        s = np.clip(15 - self.dsun, 0, 1)[..., None] * min(1.0, open_ * 3) * sun
        sky = sky * (1 - s) + np.array((255.0, 232, 150)) * s
        sky += np.exp(-(self.dsun / 46) ** 2)[..., None] * np.array((110.0, 84, 34)) * open_ * sun
        col = sky * (1 - den[..., None]) + cloud * den[..., None]
        silver = (den * (1 - den) * 4) * np.clip(1 - self.dsun / 120, 0, 1) * min(1.0, open_ * 4)
        col += silver[..., None] * np.array((190.0, 170, 110))
        # the pool of sun runs out from under the gap and over the country
        reach = np.clip((open_ * 340 - np.hypot(XX - self.sun[0], (YY - 150) * 1.6)) / 70, 0, 1)
        grey = np.array((0.27, 0.34, 0.46))
        warm = np.array((0.96, 0.98, 0.74))
        light = grey + (warm - grey) * reach[..., None]
        land = self.albedo * light
        cv = col * self.sky_mask + land * (1 - self.sky_mask)
        if bow > 0: self.arc(cv, bow, bow_alpha)
        if open_ > 0.12 and sun > 0:                                             # soft shafts, strongest while the gap is new
            ang = np.arctan2(YY - self.sun[1], XX - self.sun[0])
            shaft = (0.5 + 0.5 * np.cos(ang * 9 + 1.5 * math.sin(2 * math.pi * t * 3))) ** 3 * np.clip(1 - self.dsun / 200, 0, 1) * (self.dsun > 15)
            cv += shaft[..., None] * np.array((62.0, 54, 26)) * min(1.0, open_ * 2) * (1.5 - open_) * sun
        # the window answers the rain
        wet = 1 - sm(0.2, 0.6, open_)
        if wet > 0:
            cx = 58; gy = float(self.lines[1][0, cx]) + 8
            glow(cv, cx + 6, gy - 8, 5, (255, 180, 70), 0.9 * wet)
            stamp(cv, cx + 6, gy - 8, np.array((255.0, 210, 110)) * wet, 3.2)
        if pot_on > 0:
            self.little_pot(cv, pot_on, t)
        self.rain(cv, t, (1 - sm(0.25, 0.72, open_)) if raining is None else raining)
        return cv

    FOOT = (172, 176)
    def arc(self, cv, reveal, alpha=1.0):
        """six bands from over the left hills to the pot on the right; reveal 0..1 draws it from the left end"""
        cx, cy = 42, 188
        d = np.hypot(XX - cx, YY - cy)
        a = np.arctan2(cy - YY, XX - cx)                                         # pi at the left horizon, 0 at the right
        sweep = (np.pi * 1.02 - a) / (np.pi * 1.02)
        for i, c in enumerate(BANDS):
            r0 = 130 - 6.2 * (i + 1) + 3
            m = np.clip(3.4 - np.abs(d - r0), 0, 1) * np.clip((reveal * 1.04 - sweep) * 40, 0, 1) * (YY < self.lines[2]) * (YY < cy + 4)
            m = m * (0.86 + 0.14 * np.sin(sweep * 40)) * alpha
            cv[:] = cv * (1 - m[..., None]) + np.array(c, float) * m[..., None]
        if 0.02 < reveal < 0.98:
            s_ = (1 - reveal) * np.pi * 1.02
            glow(cv, cx + 115 * math.cos(s_), cy - 115 * math.sin(s_), 16, (255, 250, 220), 0.9)

    def little_pot(self, cv, on, t):
        x, y = self.FOOT
        pot(cv, x, y, 19 * on, 0.5)
        glow(cv, x, y - 18, 12, (255, 210, 90), 0.3 * on * (1 + 0.3 * wave(t, 20)))
        for i in range(6):
            q = (t * 18 + i / 6) % 1
            stamp(cv, x + 16 * math.cos(i * GOLDEN_ANGLE), y - 12 - 24 * q, np.array((255.0, 230, 130)) * (1 - q) * on, 1.8)

def stpatrick_jig():
    land = Land()

    def a_rain(u, t):
        # the long grey; then the gap opens and the sun pours over one hill and on to the next
        o = sm(0.42, 1.0, u) ** 1.3
        cv = land.paint(o, t)
        return push(cv, 1.08 - 0.08 * sm(0.0, 1.0, u), 0.42, 0.55)

    def b_bow(u, t):
        # the rainbow draws itself over the country and lands; the pot flares where it touches
        bow = sm(0.05, 0.62, u)
        on = sm(0.6, 0.68, u)
        cv = land.paint(1.0, t, 0.0, bow, on)
        hit = math.exp(-(u - 0.62) * 16) if u > 0.62 else 0.0
        x, y = land.FOOT
        glow(cv, x, y - 8, 10 + 26 * hit, (255, 240, 170), 1.0 * hit)
        ring(cv, x, y + 2, 10 + 90 * (1 - hit), 0.32, 3, np.array((255.0, 220, 120)) * hit)
        z = sm(0.62, 1.0, u)
        return push(cv, 1 + 0.5 * z, 0.92, 0.72)

    # ---- the close-up: the bow pours in and the coins come out ----
    r = np.random.RandomState(4301)
    spill = [(i * 0.016 + r.rand() * 0.01, (r.rand() - 0.5) * 230, -(170 + r.rand() * 200), 190 + r.rand() * 26, r.rand() * 6, 6.5 + r.rand() * 3.5) for i in range(40)]
    grass = fbm(4303, (10, 5, 2))
    blades = (np.abs(((XX + YY * 0.25) % 7) - 3.5) < 1.1) * 0.22
    def meadow(top, tone=1.0):
        m = (YY > top)[..., None]
        z = np.clip((YY - top) / 60, 0, 1)[..., None]
        return m, np.array((44.0, 116, 40)) * (0.55 + 0.7 * grass[..., None] - blades[..., None]) * (0.7 + 0.5 * z) * tone
    plain = land.paint(1.0, 0.0, 0.0, sun=0.0) * 0.55
    PX, PY, PS = 107, 158, 50
    cr, cc = -220, 150

    def c_pot(u, t):
        cv = plain.copy()
        m, gr = meadow(172)
        cv = cv * (1 - m) + gr * m
        glow(cv, PX, 196, 60, (150, 110, 30), 0.3)
        # the bow, a pillar falling into the mouth
        d = np.hypot(XX - cr, YY - cc)
        for i, c in enumerate(BANDS):
            k = np.clip(4 - np.abs(d - (327 + 21 - 7 * (i + 0.5))), 0, 1) * (YY < PY - PS * 0.6)
            cv = cv * (1 - k[..., None]) + np.array(c, float) * k[..., None]
        for i in range(10):                                                      # light running down it
            q = (t * 30 + i * 0.1) % 1
            y = 20 + q * (PY - PS - 20)
            x = cr + math.sqrt(max(327 ** 2 - (y - cc) ** 2, 0))
            stamp(cv, x + 16 * math.sin(i * 2.4), y, (255, 255, 240), 2.0)
        def coins(front):
            for j, (t0, vx, vy, gy, sp, rad) in enumerate(spill):
                q = (u - t0) * 1.45
                if q <= 0 or (j % 2 == 0) != front: continue
                x, y = PX + vx * q * 0.9, PY - PS * 0.72 + vy * q + 330 * q * q
                if y > gy:                                                       # landed: it rolls on and lies flat
                    x, y = PX + vx * q * 0.9, gy
                coin(cv, x, y, rad, sp + q * 22)
        coins(False)
        pot(cv, PX, PY, PS, 1.0 + 0.3 * sm(0.0, 0.3, u))
        coins(True)
        for i, x in enumerate((6, 40, 176, 210)):
            rock(cv, x, 228, 15 + (i % 2) * 5, 8 * wave(t, 10, phi_phase(i)), 1.1)
        flash = math.exp(-u * 12)
        cv += flash * np.array((255.0, 226, 140)) * 0.85
        return push(cv, 1.06 - 0.06 * u, 0.5, 0.7)

    # ---- the field: a gust goes through ----
    rr = np.random.RandomState(4401)
    ROWS_ = [(126, 9, 14, 0.36), (140, 12, 11, 0.42), (158, 16, 9, 0.5), (186, 22, 7, 0.58), (226, 30, 5, 0.64)]
    field = [[(x + rr.rand() * 12 - 6, y + rr.rand() * 6, s, tn) for x in np.linspace(-6, W + 6, n)] for y, s, n, tn in ROWS_]
    back = land.paint(1.0, 0.3, 0.0, sun=0.0) * 0.5
    hm, hg = meadow(124, 0.7)
    back = back * (1 - hm) + hg * hm
    rollers = [(0.0, 214, 9), (0.22, 200, 7), (0.4, 222, 10), (0.56, 192, 6)]

    def d_field(u, t):
        cv = back.copy()
        gust = lambda x: math.exp(-(((x / W) - (u * 2.2 - 0.5)) / 0.28) ** 2)
        for row_i, row in enumerate(field):
            for j, (x, y, s, tn) in enumerate(row):
                g = gust(x)
                a = -6 * math.sin(2 * math.pi * (u * 3 + phi_phase(j + row_i * 3))) - 24 * g
                rock(cv, x, y, s, a, tn * (1 + 0.5 * g))
            if row_i == 3:                                                       # the coins come through the rows
                for t0, y, rad in rollers:
                    q = (u - t0) * 1.7
                    if 0 < q < 1:
                        x = -10 + q * (W + 20)
                        coin(cv, x, y - 26 - abs(math.sin(q * 9)) * 18, rad, q * 26)
        # the hero: one big shamrock on the golden line
        g = gust(GOLD_X)
        a = -4 * wave(t, 8) - 16 * g
        hx, hy = GOLD_X - 28, 222
        rock(cv, hx + 3, hy + 3, 54, a, 0.14)                                    # a dark copy behind it cuts it out of the field
        rock(cv, hx, hy, 54, a, 1.45 + 0.35 * g)
        for i in range(16):                                                      # what the wind carries
            q = (u * 2.6 + i * 0.137) % 1
            x, y = q * W * 1.1 - 8, 60 + 130 * ((i * 0.618) % 1) + 8 * math.sin(q * 11 + i)
            stamp(cv, x, y, np.array((170.0, 240, 130)) * (1 - abs(q - 0.5)), 1.7)
        return push(cv, 1.04 + 0.05 * u, 0.3 + 0.5 * u, 0.7)

    # ---- the jig ----
    BEATS = 6
    jig_back = land.paint(1.0, 0.1, 0.0, sun=0.0) * 0.42
    jm, jg = meadow(176, 0.8)
    jig_back = jig_back * (1 - jm) + jg * jm
    d_ = np.hypot(XX - 107, YY - 182)
    halo = np.zeros((H, W, 3)); hal = np.zeros((H, W))
    for i, c in enumerate(BANDS):
        k = np.clip(5 - np.abs(d_ - (136 - 9.5 * i)), 0, 1) * (YY < 180)
        halo = halo * (1 - k[..., None]) + np.array(c, float) * k[..., None]
        hal = np.maximum(hal, k)
    jig_back = jig_back * (1 - hal[..., None] * 0.5) + halo * 0.5
    ringed = polygon_pts(107, 202, 74, 5, -math.pi / 2, 0.24)

    def gnome(cv, x, floor, b, u):
        side = 1 if int(b) % 2 == 0 else -1
        p = b % 1
        arc_ = math.sin(math.pi * p)
        hop = 18 * arc_
        lean = side * (4 + 5 * arc_)
        hip = (x + lean * 0.4, floor - 42 - hop)
        sh = (hip[0] + lean, hip[1] - 28)
        hd = (sh[0] + lean * 0.5, sh[1] - 25)
        ground_foot = (x - side * 7, floor - 5 - hop * 0.8)
        kick = (x + side * (38 + 8 * arc_), floor - 18 - 26 * arc_)
        knee_s = ((hip[0] + ground_foot[0]) / 2 - side * 3, (hip[1] + ground_foot[1]) / 2)
        knee_k = ((hip[0] + kick[0]) / 2 + side * 2, (hip[1] + kick[1]) / 2 - 7)
        # hand on the hip behind, the other up in the air
        up = (sh[0] + side * (47 + 3 * arc_), sh[1] - 30 - 10 * arc_ + 5 * math.cos(math.pi * p))
        elbow_u = (sh[0] + side * 34, sh[1] - 4)
        hipside = (hip[0] - side * 24, hip[1] - 6); elbow_h = (sh[0] - side * 34, sh[1] + 2)
        limb(cv, (sh[0] - side * 18, sh[1] + 5), elbow_h, 8, 7, COAT)
        limb(cv, elbow_h, hipside, 7, 6, COAT)
        ball(cv, hipside[0], hipside[1], 6.5, 6.5, SKIN, facets=4)
        for hp, kn, ft, sg in ((hip, knee_s, ground_foot, -side), (hip, knee_k, kick, side)):
            top = (hp[0] + sg * 9, hp[1] + 6)
            limb(cv, top, kn, 11, 8, TROUSER)
            limb(cv, kn, ft, 6.5, 5, WHITE, facets=4)
            ball(cv, ft[0] + sg * 7, ft[1] + 3, 12, 6.5, BLACK, facets=4)
            ball(cv, ft[0] + sg * 5, ft[1] + 1, 3.6, 3, GOLD, facets=3)
        ball(cv, (hip[0] + sh[0]) / 2, (hip[1] + sh[1]) / 2 + 1, 26, 28, COAT, rot=math.radians(lean), facets=6)
        ball(cv, hip[0] + lean * 0.1, hip[1] - 2, 25, 6.5, BLACK, facets=3)
        k = mask_of([(hip[0] - 7, hip[1] - 8), (hip[0] + 7, hip[1] - 8), (hip[0] + 7, hip[1] + 4), (hip[0] - 7, hip[1] + 4)])
        cv[:] = cv * (1 - k) + np.array(GOLD[3], float) * k
        k = mask_of([(hip[0] - 3, hip[1] - 4.5), (hip[0] + 3, hip[1] - 4.5), (hip[0] + 3, hip[1] + 1), (hip[0] - 3, hip[1] + 1)])
        cv[:] = cv * (1 - k) + np.array((30.0, 22, 8)) * k
        for dy in (-14, -24):
            ball(cv, sh[0] - lean * 0.15, hip[1] + dy, 3, 3, GOLD, facets=3)
        # the head: beard first, then the face comes through it
        ball(cv, hd[0], hd[1] + 12, 25, 25, BEARD, facets=5)
        for i in range(-2, 3):
            ball(cv, hd[0] + i * 9, hd[1] + 30 - abs(i) * 4, 6, 7, BEARD, facets=3)
        ball(cv, hd[0], hd[1] - 3, 21, 16, SKIN, facets=5)
        for sx in (-1, 1):
            ball(cv, hd[0] + sx * 23, hd[1] - 2, 5, 7, SKIN, facets=3)
            ball(cv, hd[0] + sx * 9, hd[1] - 5, 5.5, 6, WHITE[1:], facets=2)
            ball(cv, hd[0] + sx * 9 + side * 1.6, hd[1] - 4, 3, 3.6, [(10, 10, 14), (20, 20, 28)], facets=1)
            ball(cv, hd[0] + sx * 13, hd[1] + 3, 4, 3, [(190, 70, 60), (230, 100, 90)], facets=1)
            limb(cv, (hd[0] + sx * 3, hd[1] + 9), (hd[0] + sx * 15, hd[1] + 7), 4, 3, BEARD[1:], facets=3)
        ball(cv, hd[0], hd[1] + 2, 5.5, 5, [(170, 60, 44), (226, 96, 72), (255, 150, 120)], facets=3)
        ball(cv, hd[0], hd[1] + 14, 6, 3 + 2 * arc_, [(70, 10, 12), (120, 20, 20)], facets=1)
        # the hat, tipping the other way a moment late
        tip = math.radians(-side * (6 + 8 * arc_))
        ca, sa = math.cos(tip), math.sin(tip)
        bx, by = hd[0], hd[1] - 21
        rot = lambda a, c: (bx + a * ca - c * sa, by + a * sa + c * ca)
        ball(cv, bx, by, 33, 7.5, [(6, 40, 20), (14, 84, 38), (34, 140, 58), (90, 200, 90)], rot=tip, facets=3)
        crown = [rot(-20, 0), rot(-24, -36), rot(24, -36), rot(20, 0)]
        k = mask_of(crown)
        v = np.clip((XX - bx + 16) / 44, 0, 1)
        shade = ramp([(70, 190, 86), (40, 156, 66), (18, 104, 44), (8, 58, 28)], np.floor(v * 5) / 5)
        cv[:] = cv * (1 - k) + shade * k
        ball(cv, *rot(0, -36), 24, 6, [(10, 60, 30), (26, 120, 54), (60, 180, 80)], rot=tip, facets=3)
        k = mask_of([rot(-21, -2), rot(-22, -13), rot(22, -13), rot(21, -2)])
        cv[:] = cv * (1 - k) + np.array((14.0, 14, 18)) * k
        k = mask_of([rot(-9, 0), rot(-9, -15), rot(9, -15), rot(9, 0)])
        cv[:] = cv * (1 - k) + np.array((255.0, 214, 70)) * k
        k = mask_of([rot(-4, -4), rot(-4, -11), rot(4, -11), rot(4, -4)])
        cv[:] = cv * (1 - k) + np.array((16.0, 12, 8)) * k
        limb(cv, (sh[0] + side * 18, sh[1] + 5), elbow_u, 8, 7, COAT)
        limb(cv, elbow_u, up, 7, 6, COAT)
        limb(cv, (up[0] * 0.8 + elbow_u[0] * 0.2, up[1] * 0.8 + elbow_u[1] * 0.2), up, 7.5, 7.5, WHITE, facets=3)
        ball(cv, up[0], up[1] - 4, 7, 7, SKIN, facets=4)
        coin(cv, up[0], up[1] - 15, 8, b * 3.1)
        glow(cv, up[0], up[1] - 15, 10, (255, 210, 90), 0.4)
        return (x, floor), side, p

    def e_jig(u, t):
        cv = jig_back.copy()
        b = u * BEATS
        p = b % 1
        stomp = math.exp(-p * 5)
        cv += (stomp * np.clip(1 - np.abs(YY - 190) / 60, 0, 1))[..., None] * np.array((36.0, 30, 8))
        floor = 214
        for i, (x, y) in enumerate(ringed):                                      # five coins in a ring, hopping in turn behind him first
            q = (b - i * 0.2) % 1
            if y < 202: coin(cv, x, y - 34 * math.sin(math.pi * q), 8, b * 2.1 + i)
        ball(cv, 107, floor + 3, 42 - 14 * math.sin(math.pi * p), 6, [(10, 40, 14), (18, 56, 20)], facets=1)
        ring(cv, 107, floor, 18 + 80 * (1 - stomp), 0.25, 2.4, np.array((255.0, 220, 110)) * stomp * 0.75)
        gnome(cv, 107, floor, b, u)
        for i, (x, y) in enumerate(ringed):
            q = (b - i * 0.2) % 1
            if y >= 202: coin(cv, x, y - 34 * math.sin(math.pi * q), 8, b * 2.1 + i)
        for i in range(10):                                                      # sparks off the coin in his hand fly up the screen
            q = (b * 0.5 + i * 0.1) % 1
            stamp(cv, 22 + i * 19 + 8 * math.sin(q * 7 + i), 190 - 120 * q, np.array((255.0, 230, 130)) * (1 - q) * 0.8, 1.5)
        for i, (x, s) in enumerate(((8, 27), (38, 20), (178, 20), (208, 27))):
            rock(cv, x, 234, s, 8 * math.sin(math.pi * 2 * (b / 2 + phi_phase(i))), 1.15)
        return push(cv, 1.05 + 0.03 * u, 0.5, 0.7)

    def f_close(u, t):
        # the sun goes in, the bow fades, the first drops: the frame the film opens on
        o = 1 - sm(0.0, 0.9, u)
        return push(land.paint(o, t, None, 1.0, sm(0.2, 0.55, o), 1.0, sm(0.3, 0.8, o)), 1 + 0.08 * sm(0.0, 1.0, u), 0.42, 0.55)

    SHOTS = [(0.0, 0.22, a_rain), (0.22, 0.42, b_bow), (0.42, 0.58, c_pot), (0.58, 0.70, d_field), (0.70, 0.90, e_jig), (0.90, 1.01, f_close)]
    return "stpatrick", "end of the rainbow", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

RECIPES = [celebrating(stpatrick_jig, Wear.tophat, [(20, 100, 60), (40, 170, 90), (150, 240, 160)])]
