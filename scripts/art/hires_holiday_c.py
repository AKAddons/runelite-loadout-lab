"""Hi-res recipes, holiday films: Earth Day, the solstice, Eid. Brief in HOLIDAYS.md: light, nature, food, colour;
no figures, no sacred text. Each is a raid-manner film of 112 frames."""
from hires_holiday_card import celebrating
import hires_holiday_card as Wear
from hires_kit import *
from hires_batch2c import sm, poly, shadow
from hires_last import ink
from hires_raids import FILM, BAR, letterbox, blackout, push, play, rays

def paint(cv, m, col):
    cv[:] = cv * (1 - m) + np.array(col, float) * m

def lit(cx, cy, r, stops, lx=-0.5, ly=-0.75):
    """a colour ramp across a shape, as if a ball or a turned pot: light from (lx, ly)"""
    return ramp(stops, 0.5 + ((XX - cx) * lx + (YY - cy) * ly) / r * 0.62)

def ell(x, y, rx, ry):
    return np.clip(1 - np.hypot((XX - x) / max(rx, 0.1), (YY - y) / max(ry, 0.1)), 0, 1)[..., None] * max(rx, ry) > 0.5

def spot_glow(cv, x, y, rad, colour, power=1.0):
    """glow, but only over its own patch: a street holds a hundred of these"""
    k = int(rad * 3) + 1
    x0, x1, y0, y1 = max(0, int(x) - k), min(W, int(x) + k), max(0, int(y) - k), min(H, int(y) + k)
    if x0 >= x1 or y0 >= y1: return
    cv[y0:y1, x0:x1] += np.exp(-((XX[y0:y1, x0:x1] - x) ** 2 + (YY[y0:y1, x0:x1] - y) ** 2) / (2 * rad * rad))[..., None] * np.array(colour, float) * power

def starfield(seed, n, top=BAR, bottom=H - BAR, big=0.12):
    r = np.random.RandomState(seed); out = np.zeros((H, W, 3))
    for i in range(n):
        x, y = 8 + r.rand() * (W - 16), top + r.rand() * (bottom - top)
        bright = r.rand() ** 2
        c = np.array((210.0, 225, 255)) if r.rand() > 0.25 else np.array((255.0, 225, 190))
        stamp(out, x, y, c * (0.5 + 0.5 * bright), 2.1 if r.rand() < big else 1.5)
    return out

def rain_mask(t, n, seed, slant=0.22, length=20):
    """falling streaks as a mask; t in whole-film units, speed set by the caller"""
    r = np.random.RandomState(seed)
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    for i in range(n):
        x0, ph, sp = r.rand() * (W + 60), r.rand(), 1 + r.randint(0, 3)
        q = (t * sp + ph) % 1
        y = q * (H + 40) - 20
        x = x0 - y * slant
        d.line([(x, y), (x + slant * length, y - length)], fill=int(150 + 100 * r.rand()), width=1)
    return np.asarray(im, float)[..., None] / 255

# ---- Earth Day: one seed turns the whole world green --------------------------------
TILT = 0.32
_LON = np.linspace(0, 2 * math.pi, 720, endpoint=False)
_LAT = np.linspace(-math.pi / 2, math.pi / 2, 360)

def sphere_field(seed, freqs):
    """waves over the sphere, so the map has no seam and no pinch at the poles"""
    r = np.random.RandomState(seed)
    la, lo = np.meshgrid(_LAT, _LON, indexing="ij")
    nx, ny, nz = np.cos(la) * np.sin(lo), np.sin(la), np.cos(la) * np.cos(lo)
    out = np.zeros_like(la)
    for f in freqs:
        for _ in range(7):
            d = r.normal(size=3); d /= np.linalg.norm(d)
            out += np.sin(f * (nx * d[0] + ny * d[1] + nz * d[2]) * 2 + r.rand() * 6.28) / f
    return (out - out.mean()) / out.std()

class World:
    def __init__(self):
        self.land = sphere_field(4101, (1.1, 1.8, 3, 5, 8, 13))
        self.detail = sphere_field(4111, (6, 10, 16, 26))
        self.cloud = sphere_field(4121, (9, 13, 19, 27))
        self.th = np.percentile(self.land, 56)
        row = int((-TILT + math.pi / 2) / math.pi * 359)                        # the seed: the most land on the camera's line
        score = np.convolve(np.r_[self.land[row], self.land[row]], np.ones(50) / 50, "same")[:720]
        self.L0 = _LON[int(score.argmax())]

    def seed_at(self, cx, cy, R, lon):
        la, dl = -TILT, self.L0 - lon
        x, y, z = math.cos(la) * math.sin(dl), math.sin(la), math.cos(la) * math.cos(dl)
        ny, nz = y * math.cos(TILT) + z * math.sin(TILT), -y * math.sin(TILT) + z * math.cos(TILT)
        return cx + x * R, cy + ny * R, nz

    def paint(self, cv, cx, cy, R, lon, green, cloud=0.35, clear=0.0):
        """green 0..1 is how far the living front has spread from the seed; clear 0..1 washes the oceans bright"""
        nx, ny = (XX - cx) / R, (YY - cy) / R
        rr = nx * nx + ny * ny
        nz = np.sqrt(np.maximum(1 - rr, 0))
        yp = ny * math.cos(TILT) - nz * math.sin(TILT)
        zp = ny * math.sin(TILT) + nz * math.cos(TILT)
        lat = np.arcsin(np.clip(yp, -1, 1)); lo = np.arctan2(nx, zp) + lon
        iy = np.clip(((lat + math.pi / 2) / math.pi * 359).astype(int), 0, 359)
        ix = (lo / (2 * math.pi) * 720).astype(int) % 720
        f, dt = self.land[iy, ix], self.detail[iy, ix]
        land = np.clip((f - self.th) * 9, 0, 1)
        ang = np.arccos(np.clip(np.sin(lat) * math.sin(-TILT) + np.cos(lat) * math.cos(-TILT) * np.cos(lo - self.L0), -1, 1))
        gl = np.clip((green * 3.9 - ang) / 0.42, 0, 1)
        dry = ramp([(86, 66, 50), (138, 104, 70), (178, 140, 92)], 0.5 + dt * 0.3)
        lush = ramp([(16, 92, 40), (44, 150, 56), (112, 206, 84)], 0.5 + dt * 0.32)
        ground_ = dry * (1 - gl[..., None]) + lush * gl[..., None]
        peak = np.clip((f - self.th - 3.4) * 3, 0, 1)[..., None]
        ground_ = ground_ * (1 - peak) + np.array((216.0, 216, 206)) * peak
        dull = ramp([(16, 34, 56), (34, 60, 84)], 0.5 + dt * 0.2)
        vivid = ramp([(8, 52, 130), (28, 118, 216)], 0.5 + dt * 0.2)
        wet = np.clip(clear + gl * 0.8, 0, 1)[..., None]
        ocean = dull * (1 - wet) + vivid * wet
        alb = ground_ * land[..., None] + ocean * (1 - land[..., None])
        ice = np.clip((np.abs(lat) - 1.18 - dt * 0.05) * 9, 0, 1)[..., None]
        alb = alb * (1 - ice) + np.array((224.0, 232, 240)) * ice
        ic = ((lo * 1.0 + 0.15 + lon * 0.25) / (2 * math.pi) * 720).astype(int) % 720
        c = np.clip((self.cloud[iy, ic] - (2.0 - cloud * 1.45)) * 1.1, 0, 1)[..., None]
        alb = alb * (1 - c * 0.92) + np.array((240.0, 244, 248)) * c * 0.92
        sx, sy, sz = -0.62, -0.5, 0.6
        li = np.clip(nx * sx + ny * sy + nz * sz, 0, 1)
        col = alb * (0.05 + 1.02 * li[..., None] ** 0.8)
        hx, hy, hz = sx, sy, sz + 1; hn = math.hypot(math.hypot(hx, hy), hz)
        spec = np.clip((nx * hx + ny * hy + nz * hz) / hn, 0, 1) ** 44 * (1 - land) * (1 - c[..., 0])
        col = col + spec[..., None] * np.array((255.0, 240, 210)) * 0.75
        col = col + ((1 - nz) ** 2.6 * li ** 0.4)[..., None] * np.array((110.0, 180, 255)) * 1.0
        d = np.hypot(XX - cx, YY - cy)
        side = np.clip(((XX - cx) * sx + (YY - cy) * sy) / np.maximum(d, 1) * 0.9 + 0.5, 0, 1)
        cv += (np.exp(-np.maximum(d - R, 0) / max(5.0, R * 0.075)) * (d >= R) * side)[..., None] * np.array((70.0, 140, 255)) * 0.95
        m = np.clip(R - d, 0, 1)[..., None]
        cv[:] = cv * (1 - m) + col * m

def leaf(cv, x, y, ang, length, width, stops, t=0.0):
    """a teardrop with a midrib and a lit upper half, tip at length along ang"""
    if length < 1: return
    ca, sa = math.cos(ang), math.sin(ang)
    a = ((XX - x) * ca + (YY - y) * sa) / length
    b = -(XX - x) * sa + (YY - y) * ca
    prof = width * np.sin(np.clip(a, 0, 1) * math.pi) ** 0.75 * (1.15 - 0.45 * np.clip(a, 0, 1))
    m = ((a > 0) & (a < 1) & (np.abs(b) < prof))[..., None]
    side = np.where(b * ca < 0, 0.72, 0.32)                                     # the half toward the sky takes the light
    vein = 0.09 * np.sin(a * 22 - np.abs(b) / max(width, 1) * 7)
    c = ramp(stops, side + vein + 0.18 * (1 - a))
    rib = (np.abs(b) < 1.1)[..., None] * 0.16
    cv[:] = np.where(m, c * (1 - rib), cv)

def tree_balls(seed, n, spread):
    r = np.random.RandomState(seed); out = []
    for i in range(n):
        a = i * GOLDEN_ANGLE + r.rand() * 0.5; d = spread * math.sqrt((i + 0.4) / n)
        out.append((math.cos(a) * d, math.sin(a) * d * 0.72 - 0.05, 0.42 + 0.22 * r.rand()))
    return sorted(out, key=lambda b: -b[1])                                     # lower, darker clumps first

def ball(cv, x, y, r, stops, tex):
    if r < 1: return
    k = int(r) + 2
    x0, x1, y0, y1 = max(0, int(x) - k), min(W, int(x) + k), max(0, int(y) - k), min(H, int(y) + k)
    if x0 >= x1 or y0 >= y1: return
    dx, dy = (XX[y0:y1, x0:x1] - x) / r, (YY[y0:y1, x0:x1] - y) / r
    m = dx * dx + dy * dy <= 1
    c = ramp(stops, 0.5 - 0.42 * dx - 0.62 * dy + (tex[y0:y1, x0:x1] - 0.5) * 0.65)
    cv[y0:y1, x0:x1][m] = c[m]

def bird(cv, x, y, s, flap, colour, shade):
    """a gull from the front, the one bird that reads in cells: two long wings in an M, tips rising and falling; flap 0..1"""
    up = s * (1.05 * flap - 0.15)
    for sgn in (-1, 1):
        wing = [(x + sgn * s * 0.12, y - s * 0.16), (x + sgn * s * 0.85, y - up - s * 0.22), (x + sgn * s * 1.65, y - up * 0.35 + s * 0.12), (x + sgn * s * 0.85, y - up + s * 0.14), (x + sgn * s * 0.12, y + s * 0.2)]
        paint(cv, poly(wing), colour if sgn > 0 else shade)
    paint(cv, poly([(x - s * 0.22, y - s * 0.24), (x + s * 0.22, y - s * 0.24), (x + s * 0.16, y + s * 0.42), (x - s * 0.16, y + s * 0.42)]), colour)

def earth_film():
    world = World()
    stars = starfield(4131, 90)
    r0 = 72
    SOIL = [(20, 14, 12), (46, 30, 22), (78, 52, 36)]
    LEAF = [(14, 70, 24), (40, 140, 44), (110, 214, 78), (196, 250, 130)]
    soil_tex = fbm(4141, (16, 7, 3))
    hills = ridge(4143, 148, 12, (50, 20))
    leaf_n = fbm(4145, (7, 3))
    drops = np.random.RandomState(4147)
    splash = [(drops.rand() * W, 182 + drops.rand() * 30, drops.rand()) for _ in range(12)]
    fx_lean = lambda t: 2.2 * wave(t, 8)

    def a_world(u, t):
        # the planet, dry and dim, turning into the light; a green spark wakes and the camera falls toward it
        fall = sm(0.58, 1.0, u) ** 2.2
        R = r0 + 900 * fall
        lon = world.L0 - 1.5 * (1 - sm(0.0, 0.8, u))
        cx, cy = W / 2, H / 2
        cv = stars * (1 - 0.2 * u) + np.zeros((H, W, 3))
        world.paint(cv, cx, cy, R, lon, 0.018 * sm(0.5, 0.9, u), cloud=0.3 + 0.6 * fall)
        wake = sm(0.34, 0.5, u)
        x, y, z = world.seed_at(cx, cy, R, lon)
        if wake > 0 and z > 0:
            for i in range(3):
                q = (u * 5 + i / 3) % 1
                ring(cv, x, y, 3 + 24 * q, 0.9, 1.8, np.array((90.0, 255, 110)) * (1 - q) * wake)
            spot_glow(cv, x, y, 8, (80, 255, 90), 0.8 * wake)
            stamp(cv, x, y, np.array((220.0, 255, 200)) * wake, 2.6)
        cv = cv * (1 - sm(0.88, 1.0, u)) + np.array([176.0, 186, 192]) * sm(0.88, 1.0, u)
        return blackout(cv, 1 - sm(0.0, 0.14, u))

    def b_seed(u, t):
        # out of the cloud, into the rain: a split seed, and the first leaves pushing up
        grow = sm(0.12, 0.92, u)
        sun = sm(0.55, 1.0, u)
        cv = ramp([(28, 34, 44), (46, 54, 62), (70, 76, 70)], np.clip((YY - 20) / 150, 0, 1)) * (1 + 0.5 * sun) + np.array((0, 0, 0.0))
        m = (YY > hills[None, :])[..., None]
        cv = cv * (1 - m) + np.array([30.0, 44, 36]) * (1 + 0.4 * sun) * m
        cv += np.exp(-((XX - 170) ** 2 + (YY - 20) ** 2) / (2 * 80 ** 2))[..., None] * np.array([120.0, 100, 50]) * sun
        rays(cv, 176, 8, 9, 200, np.array((70, 56, 26)) * sun, t)
        by = 168
        gm = (YY > by + 2 * np.sin(XX * 0.11) + 3 * np.sin(XX * 0.043))[..., None]
        s_ = ramp(SOIL, soil_tex * 0.8 + np.clip((YY - by) / 90, 0, 1) * 0.5)
        cv = cv * (1 - gm) + s_ * gm * (1 + 0.3 * sun)
        sx, sy = W / 2 + 2, by + 4
        # the seed, breaking
        ope = sm(0.0, 0.22, u)
        for sgn in (-1, 1):
            cx_ = sx + sgn * (3 + 5 * ope)
            paint(cv, np.clip(1 - np.hypot((XX - cx_) / 8, (YY - sy - 2) / 6), 0, 1)[..., None] > 0.02, (70, 44, 26))
            cv += (np.exp(-((XX - cx_ + 2) ** 2 + (YY - sy) ** 2) / 12))[..., None] * np.array([70.0, 50, 30])
        tall = 112 * grow
        sway = fx_lean(t) * grow
        tip = (sx + sway + 6 * math.sin(grow * 2.4), sy - tall)
        pts = [(sx + (tip[0] - sx) * q ** 1.6, sy - tall * q) for q in np.linspace(0, 1, 18)]
        for (a, b) in zip(pts, pts[1:]):
            ink(cv, a, b, (50, 150, 56), 2.0 + 2.2 * grow)
        for (a, b) in zip(pts, pts[1:]):
            ink(cv, (a[0] - 1.5, a[1]), (b[0] - 1.5, b[1]), (150, 230, 110), 0.9 + 0.4 * grow)
        pairs = [(0.42, 0.45, 56, 15, 0.05), (0.7, 0.3, 66, 18, 0.28), (1.0, 0.72, 58, 16, 0.5)]
        for f, up, ln, wd, start in pairs:                                       # three pairs, unfolding in turn
            g = sm(start, start + 0.42, u * 1.0 / 1.0 + 0.04)
            if g <= 0: continue
            q = min(len(pts) - 1, int(f * (len(pts) - 1)))
            x, y = pts[q]
            lift = up + (1 - g) * 0.95 + 0.04 * wave(t, 8, f)
            for sgn in (-1, 1):
                a = -math.pi / 2 + sgn * (math.pi / 2 - lift * (math.pi / 2))
                if f == 1.0: a = -math.pi / 2 + sgn * 0.62 * g
                leaf(cv, x, y, a, ln * g * 1.1, wd * g, LEAF)
                wet = (t * 37 + f + sgn) % 1
                if g > 0.5: stamp(cv, x + math.cos(a) * ln * 0.55 * g, y + math.sin(a) * ln * 0.55 * g - 3, np.array((255.0, 255, 255)) * (0.3 + 0.5 * wet) * (1 - sun * 0.5), 2.2)
        rain = (1 - sm(0.7, 0.98, u))
        rm = rain_mask(t * 9, 46, 4151)
        cv = cv + rm * np.array([120.0, 150, 180]) * rain
        for x, y, ph in splash:
            q = (t * 24 + ph) % 1
            if rain > 0.2 and abs(x - sx) > 22: ring(cv, x, y, 2 + 11 * q, 0.35, 1.7, np.array((130.0, 170, 200)) * (1 - q) * rain)
        wash = 1 - sm(0.0, 0.12, u)
        return push(cv * (1 - wash) + np.array([176.0, 186, 192]) * wash, 1.0 + 0.12 * u, 0.5, 0.72)

    # the forest: far to near, each tree with its own clumps; the big one on the golden line
    r = np.random.RandomState(4161)
    TREES = []
    for x in np.linspace(-6, W + 6, 11):
        TREES.append((x + r.rand() * 10, 142, 15 + r.rand() * 6, 0.16 + r.rand() * 0.22, 0))
    for i, x in enumerate((14, 52, 88, 126, 168, 202)):
        TREES.append((x + r.rand() * 8, 164, 26 + r.rand() * 7, 0.05 + ((i * 0.618) % 1) * 0.22, 1))
    TREES.append((14, 200, 42, 0.12, 2)); TREES.append((GOLD_X - 12, 206, 70, 0.0, 2)); TREES.append((204, 204, 38, 0.2, 2))
    TREES = [(x, b, s, st, row, tree_balls(4170 + i, 5 + row * 2 + (s > 80) * 3, 1.0)) for i, (x, b, s, st, row) in enumerate(TREES)]
    STOPS = [[(18, 58, 44), (28, 84, 56), (46, 110, 66)],
             [(16, 66, 34), (34, 116, 46), (70, 158, 58), (112, 190, 76)],
             [(12, 66, 26), (30, 124, 40), (80, 186, 58), (168, 236, 96)]]
    TRUNK = (66, 44, 30)
    far_hill = ridge(4181, 122, 18, (60, 24))
    FLOCK = [(0, 0.0, 17), (1, 0.0, 13), (2, 0.0, 11)]
    flowers = [(r.rand() * W, 176 + r.rand() * 36, r.randint(0, 3), r.rand() * 0.5) for _ in range(30)]

    def c_forest(u, t):
        # time-lapse: rain to sun, and a forest shoots up in a golden order, then the birds come
        light = 0.45 + 0.55 * sm(0.0, 0.4, u)                                      # the seedling's shot already had the sun coming
        sky = ramp([(30, 40, 52), (46, 58, 66), (74, 80, 74)], np.clip((YY - 20) / 110, 0, 1))
        day = ramp([(30, 84, 150), (56, 122, 170), (130, 170, 150)], np.clip((YY - 20) / 110, 0, 1))
        cv = sky * (1 - light) + day * light
        rainbow = sm(0.36, 0.56, u) * (1 - sm(0.82, 1.0, u))
        for i, c in enumerate(((150, 40, 50), (150, 100, 30), (120, 130, 30), (40, 120, 50), (40, 70, 130), (80, 40, 120))):
            ring(cv, 64, 150, 118 - i * 5.5, 1.0, 3.2, np.array(c, float) * rainbow * 0.95, (YY < 150))
        cv += np.exp(-((XX - 188) ** 2 + (YY - 28) ** 2) / (2 * 60 ** 2))[..., None] * np.array([80.0, 66, 30]) * light
        m = (YY > far_hill[None, :])[..., None]
        cv = cv * (1 - m) + np.array([34.0, 66, 66]) * (0.7 + 0.6 * light) * m
        gr = (YY > 140)[..., None]
        cv = cv * (1 - gr) + ramp([(22, 50, 28), (40, 92, 40), (58, 120, 44)], leaf_n * 0.5 + np.clip((YY - 140) / 80, 0, 1) * 0.6) * (0.6 + 0.55 * light) * gr
        for x, b, s, st, row, balls in TREES:
            g = sm(st, st + 0.42, u)
            if g <= 0.01: continue
            sc = s * g
            if row == 2: shadow(cv, x + sc * 0.3, b, sc * 0.8, 5 + sc * 0.05, 0.4)
            trunk_h = sc * 0.8
            lean = wave(t, 8, x * 0.01) * sc * 0.015
            paint(cv, poly([(x - sc * 0.11, b), (x + sc * 0.11, b), (x + sc * 0.05 + lean, b - trunk_h), (x - sc * 0.05 + lean, b - trunk_h)]), np.array(TRUNK) * (0.6 + row * 0.25))
            st_ = STOPS[row]
            for bx, by, br in balls:
                ball(cv, x + bx * sc * 0.62 + lean * 1.6, b - trunk_h - sc * 0.22 + by * sc * 0.62, br * sc * 0.68 * (0.72 + 0.28 * sm(st + 0.2, st + 0.5, u)), st_, leaf_n)
        for x, y, c, st in flowers:
            f = sm(0.45 + st * 0.5, 0.62 + st * 0.5, u)
            if f > 0: stamp(cv, x, y, np.array(((255, 180, 200), (255, 230, 110), (230, 230, 255))[c], float) * f, 2.0)
        rain = 0.4 * (1 - sm(0.0, 0.3, u))
        if rain > 0.01: cv = cv + rain_mask(t * 9, 34, 4183) * np.array([110.0, 140, 170]) * rain
        rays(cv, 188, 24, 8, 180, np.array((26, 20, 8)) * light, t)
        fly = sm(0.5, 0.56, u)
        if fly > 0:                                                              # the flock crosses, each wing out of step
            for i, yy, s in FLOCK:
                q = (u - 0.5) * 1.9 - i * 0.1
                if q > 0:
                    x = -20 + (W + 60) * q + 7 * math.sin(i * 1.9)
                    y = (92, 66, 112)[i] - 26 * q + 3 * wave(t, 6, i * 0.2)
                    bird(cv, x, y, s, 0.5 + 0.5 * wave(t, 14, i * 0.31), (255, 252, 236), (190, 186, 170))
        return push(cv, 1.05 - 0.05 * u, 0.5, 0.9) if u < 0.92 else cv * (1 - sm(0.92, 1.0, u)) + np.array([215.0, 228, 226]) * sm(0.92, 1.0, u)

    def d_green(u, t):
        # back through the cloud, up and out: the same world, and the green running round it
        rise = 1 - sm(0.0, 0.5, u)
        R = r0 + 760 * rise ** 2.6
        lon = world.L0 + 1.45 * sm(0.0, 1.0, u)
        cv = stars * (1 - rise) + np.zeros((H, W, 3))
        world.paint(cv, W / 2, H / 2, R, lon, 0.2 + 0.8 * sm(0.04, 0.78, u), cloud=0.42 + 0.3 * rise, clear=sm(0.1, 0.8, u))
        bloom = sm(0.62, 0.8, u) * (1 - sm(0.86, 1.0, u))
        sx, sy = W / 2 - 0.62 * r0 * 1.02, H / 2 - 0.62 * r0 * 0.86                # dawn over the shoulder of the world
        spot_glow(cv, sx, sy, 15, (255, 232, 170), 0.9 * bloom)
        rays(cv, sx, sy, 8, 110, np.array((90, 76, 40)) * bloom, t)
        cv = cv * (1 - (1 - sm(0.0, 0.1, u))) + np.array([215.0, 228, 226]) * (1 - sm(0.0, 0.1, u))
        return blackout(cv, sm(0.88, 1.0, u))

    SHOTS = [(0.0, 0.25, a_world), (0.25, 0.44, b_seed), (0.44, 0.72, c_forest), (0.72, 1.01, d_green)]
    return "earth", "the greening", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- the solstice: one whole day, sunrise to the next, through a ring of stones --------
HORIZON = 142
SKY_KEYS = [(0.00, (12, 16, 46), (44, 42, 94)), (0.07, (14, 20, 54), (84, 52, 88)), (0.13, (28, 40, 92), (250, 132, 60)), (0.22, (38, 74, 136), (240, 190, 130)),
            (0.36, (40, 96, 168), (130, 172, 200)), (0.50, (42, 76, 140), (226, 170, 110)), (0.585, (46, 30, 92), (250, 96, 60)), (0.665, (22, 18, 60), (96, 44, 92)),
            (0.76, (12, 16, 46), (44, 42, 94)), (1.00, (12, 16, 46), (44, 42, 94))]

def sky_at(p):
    p = p % 1
    for (a, ta, ha), (b, tb, hb) in zip(SKY_KEYS, SKY_KEYS[1:]):
        if a <= p <= b:
            f = (p - a) / (b - a); f = f * f * (3 - 2 * f)
            return np.array(ta, float) * (1 - f) + np.array(tb, float) * f, np.array(ha, float) * (1 - f) + np.array(hb, float) * f
    return np.array(ta, float), np.array(ha, float)

def sun_at(p):
    """the sun in the wide shot: up through the gap on the morning, down through it at evening; x, y, colour, height 0..1"""
    p = p % 1
    if p < 0.5:
        q = (p - 0.095) / 0.21
        x = W / 2 + 62 * max(q - 0.5, 0) ** 1.3; y = HORIZON + 12 - 175 * q
    else:
        q = (0.615 - p) / 0.21
        x = W / 2 - 62 * max(q - 0.5, 0) ** 1.3; y = HORIZON + 12 - 175 * q
    q = max(q, 0)
    c = np.array((255.0, 150, 60)) * (1 - min(q * 1.6, 1)) + np.array((255.0, 244, 200)) * min(q * 1.6, 1)
    return x, y, c, q

def night_at(p):
    p = p % 1
    return 1 - sm(0.0, 0.08, p) if p < 0.3 else sm(0.62, 0.74, p)

def solstice_film():
    tex = fbm(4201, (10, 4, 2))
    grass = fbm(4203, (16, 6, 3))
    hills = ridge(4205, HORIZON - 3, 8, (70, 30))
    r = np.random.RandomState(4207)
    # the ring in the wide: an ellipse; back stones small and flanking the gap, front stones big and clear of the middle
    STONES = []
    for i in range(14):
        a = 2 * math.pi * (i + 0.5) / 14 - math.pi / 2
        x, y = W / 2 + 98 * math.cos(a), 165 + 22 * math.sin(a)
        d = (y - 143) / 44                                                       # 0 far, 1 near
        if abs(x - W / 2) < 44 and y > 170: continue                             # a clear aisle to the gap
        wide = 8 + 12 * d; tall = 28 + 58 * d
        STONES.append((y, x, wide, tall * (0.85 + 0.3 * r.rand()), (r.rand() - 0.5) * 5, r.rand()))
    STONES.sort()
    GAP = [(W / 2 - 22, 148, 10, 54), (W / 2 + 22, 148, 10, 54)]
    star_r = np.random.RandomState(4209)
    POLE = (W / 2, 62)
    SKY_STARS = [(10 + 130 * math.sqrt(star_r.rand()), star_r.rand() * 2 * math.pi, 0.4 + 0.6 * star_r.rand() ** 2, 1.3 + star_r.rand() * 0.7) for _ in range(84)]
    SAM = fbm(4211, (30, 12))

    def stone_face(x, base, wide, tall, lean, seed, amb, sunc, sx, height):
        top_l = (x - wide * 0.8 + lean, base - tall)
        top_r = (x + wide * 0.75 + lean + 2 * (seed - 0.5), base - tall * (0.93 + 0.1 * seed))
        return [(x - wide, base + 1), (x + wide, base + 1), top_r, top_l]

    def wide(p, t):
        top, hor = sky_at(p)
        v = np.clip((YY - BAR) / (HORIZON - BAR), 0, 1)[..., None]
        cv = top * (1 - v ** 1.5) + hor * v ** 1.5
        night = night_at(p)
        if night > 0.01:                                                         # the sky wheels round the star over the gap, long exposure
            turn = 2 * math.pi * p
            trail = 0.95 * (sm(0.66, 0.84, p) if p > 0.3 else 1.0)
            for rad, a0, b, sz in SKY_STARS:
                n = 1 + int(trail * rad / 2.2)
                for k in range(n):
                    a = a0 + turn - trail * k / max(1, n)
                    y = POLE[1] + rad * math.sin(a)
                    if y < HORIZON - 6:
                        stamp(cv, POLE[0] + rad * math.cos(a), y, np.array((210.0, 226, 255)) * b * night * (1 - 0.75 * k / max(1, n)) * 1.25 * min(1.0, (HORIZON - 6 - y) / 44), sz)
            stamp(cv, POLE[0], POLE[1], np.array((255.0, 250, 225)) * night, 2.8)
            spot_glow(cv, POLE[0], POLE[1], 6, (150, 170, 255), 0.5 * night)
        sx, sy, sc, q = sun_at(p)
        up = 0.0 < q < 1.05 and sy > -30
        amb = np.clip(1.0 - night, 0.0, 1) * (0.42 + 0.58 * np.clip(q * 1.1, 0, 1)) + 0.16
        if up:
            d = np.hypot(XX - sx, YY - sy)
            cv += np.exp(-(d / 62) ** 2)[..., None] * sc * 0.42
            m = np.clip(16 - d, 0, 1)[..., None]
            cv = cv * (1 - m) + (sc * 0.5 + np.array((127.0, 127, 127))) * m
        hil = (YY > hills[None, :])[..., None]
        cv = cv * (1 - hil) + np.array([34.0, 36, 40]) * (0.22 + amb) * (1 + 0.5 * hor.mean() / 255) * hil
        gm = (YY > HORIZON)[..., None]
        z = np.clip((YY - HORIZON) / 78, 0, 1)[..., None]
        g = ramp([(30, 44, 30), (58, 74, 44), (88, 94, 58)], grass * 0.9 + z[..., 0] * 0.25)
        g = g * (0.28 + 0.68 * amb) + hor * 0.14 * (1 - z)
        cv = cv * (1 - gm) + g * gm
        lowsun = up and q < 0.6
        # shadows: every stone throws one along the line from the sun's foot, long when the sun is low
        fx, fy = (sx if up else W / 2), HORIZON - 2
        reach = (1.0 + 0.5 / max(q, 0.09)) if up else 1.0
        strength = (0.72 * min(1.0, q * 4)) if up else 0.0
        im = Image.new("L", (W, H), 0); dr = ImageDraw.Draw(im)
        for y, x, wd, tl, lean, seed in STONES:
            for bx in (x - wd, x + wd):
                pass
            dr.polygon([(x - wd, y), (x + wd, y), (fx + (x + wd - fx) * reach, fy + (y - fy) * reach), (fx + (x - wd - fx) * reach, fy + (y - fy) * reach)], fill=255)
        for x, y, wd, tl in GAP:
            dr.polygon([(x - wd, y), (x + wd, y), (fx + (x + wd - fx) * reach * 1.8, fy + (y - fy) * reach * 1.8), (fx + (x - wd - fx) * reach * 1.8, fy + (y - fy) * reach * 1.8)], fill=255)
        sh = np.asarray(im, float)[..., None] / 255 * (YY > HORIZON)[..., None]
        cv = cv * (1 - sh * strength)
        if lowsun:                                                               # the road of light down the aisle
            aisle = np.clip(1 - np.abs(XX - fx - (YY - HORIZON) * (W / 2 - fx) * 0.002) / np.maximum(10 + (YY - HORIZON) * 0.5, 1), 0, 1) * (YY > HORIZON) * (1 - sh[..., 0])
            cv += aisle[..., None] * sc * 0.5 * (1 - q)
        def block(pts, wide_, x, base, tl, seed):
            mask = poly(pts)
            face = np.array([84.0, 84, 90]) * (0.45 + 0.9 * tex[..., None]) * (0.2 + amb * 1.05)
            # chunky lit facets: one side turned to the sun
            lean_side = np.where(XX > x + wide_ * 0.2 * (seed - 0.5), 1.0, 0.72)[..., None] if sx > x else np.where(XX < x - wide_ * 0.2 * (seed - 0.5), 1.0, 0.72)[..., None]
            cvc = face * lean_side
            cv[:] = cv * (1 - mask) + cvc * mask
            if night > 0.05:                                                     # starlight on both shoulders, so the ring holds at night
                cv[:] = cv + mask * ((np.abs(XX - x) > wide_ * 0.62)[..., None]) * np.array((16.0, 20, 38)) * night
            if up and q < 0.8:                                                  # the sun catches its edge
                edge = (XX < x + 3 - wide_) if sx < x else (XX > x + wide_ - 3)
                rim = mask * edge[..., None] * np.clip((YY - (base - tl))[..., None] / 6, 0, 1)
                cv[:] = cv + rim * sc * 0.7 * (1 - q * 0.6)
        for y, x, wd, tl, lean, seed in STONES:
            if y >= 150: continue
            block(stone_face(x, y, wd, tl, lean, seed, amb, sc, sx, q), wd, x, y, tl, seed)
        for x, y, wd, tl in GAP:                                                  # the trilithon the sun threads
            block([(x - wd, y), (x + wd, y), (x + wd * 0.8, y - tl), (x - wd * 0.8, y - tl)], wd, x, y, tl, 0.5)
        lint = [(W / 2 - 36, 148 - 54 + 1), (W / 2 + 36, 148 - 54 + 1), (W / 2 + 34, 148 - 67), (W / 2 - 35, 148 - 66)]
        mask = poly(lint)
        cv[:] = cv * (1 - mask) + np.array([92.0, 92, 98]) * (0.5 + 0.8 * tex[..., None]) * (0.2 + amb * 1.05) * mask
        for y, x, wd, tl, lean, seed in STONES:
            if y < 150: continue
            block(stone_face(x, y, wd, tl, lean, seed, amb, sc, sx, q), wd, x, y, tl, seed)
        if up and q < 0.55:
            fl = np.clip(1 - np.abs(q - 0.14) / 0.14, 0, 1)                      # the moment the sun stands exactly in the gap
            rays(cv, sx, sy, 10, 240, sc * 0.3 * (0.5 + fl), t)
            spot_glow(cv, sx, sy, 20, sc, 0.65 * fl)
        return cv

    def camera(p):
        """pushes into the gap for sunrise, sits back for the evening; 1 at the loop"""
        return 1 + 0.42 * sm(0.04, 0.17, p) if p < 0.4 else 1 + 0.14 * sm(0.5, 0.6, p) * (1 - sm(0.66, 0.82, p))

    def a_wide(u, t):
        return push(wide(t, t), camera(t), 0.5, 0.56)

    # the dial: straight down on the ring at midday; shadows go round it like a clock
    DIAL = (W / 2, H / 2 + 2)
    RING = polygon_pts(DIAL[0], DIAL[1], 58, 12, 0.13)

    def b_dial(u, t):
        ang = math.pi * (1.1 + 0.8 * u)                                          # where the sun stands, seen from above
        el = math.sin(math.pi * u)                                               # high at the middle: shadows draw in and run out
        sx, sy = DIAL[0] + 84 * math.cos(ang), DIAL[1] + 84 * math.sin(ang)
        warm = abs(2 * u - 1) ** 2
        base = ramp([(34, 52, 30), (58, 82, 42), (82, 104, 56)], grass)
        cv = base * (0.75 + 0.3 * el) * (np.array((1.0, 1.0, 1.0)) * (1 - warm) + np.array((1.2, 0.94, 0.72)) * warm)
        d = np.hypot(XX - DIAL[0], YY - DIAL[1])
        cv *= 1 - 0.28 * np.clip(1 - np.abs(d - 58) / 16, 0, 1)[..., None]       # the worn path
        cv *= 1 - 0.18 * np.clip(1 - d / 26, 0, 1)[..., None]
        ring(cv, DIAL[0], DIAL[1], 34, 1.0, 1.6, (18, 18, 8))
        length = 16 + 74 * (1 - el) ** 1.5
        dx, dy = -math.cos(ang), -math.sin(ang)
        px, py = -dy, dx
        def shadow_of(x, y, wd, ln):
            pts = [(x + px * wd, y + py * wd), (x - px * wd, y - py * wd), (x - px * wd * 0.8 + dx * ln, y - py * wd * 0.8 + dy * ln), (x + px * wd * 0.8 + dx * ln, y + py * wd * 0.8 + dy * ln)]
            return pts
        im = Image.new("L", (W, H), 0); dr = ImageDraw.Draw(im)
        for x, y in RING: dr.polygon(shadow_of(x, y, 6.5, length), fill=255)
        dr.polygon(shadow_of(DIAL[0], DIAL[1], 7.5, length * 1.35), fill=255)
        sh = np.asarray(im, float)[..., None] / 255
        cv = cv * (1 - sh * 0.68) + sh * np.array([6.0, 8, 22]) * 0.5
        for i, (x, y) in enumerate(RING):
            a = math.atan2(y - DIAL[1], x - DIAL[0])
            ca, sa = math.cos(a), math.sin(a)
            pts = [(x + ca * dxx * 6 - sa * dyy * 9, y + sa * dxx * 6 + ca * dyy * 9) for dxx, dyy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
            m = poly(pts)
            cv[:] = cv * (1 - m) + np.array([150.0, 150, 150]) * (0.55 + 0.7 * tex[..., None]) * (0.75 + 0.3 * el) * m
        m = (np.hypot(XX - DIAL[0], YY - DIAL[1]) < 9)[..., None]
        cv[:] = cv * (1 - m) + np.array([190.0, 186, 170]) * (0.55 + 0.65 * tex[..., None]) * m
        sc = np.array((255.0, 170, 70)) * warm + np.array((255.0, 246, 210)) * (1 - warm)
        ds = np.hypot(XX - sx, YY - sy)
        cv += np.exp(-(ds / 46) ** 2)[..., None] * sc * 0.55
        rays(cv, sx, sy, 12, 120, sc * 0.22, t)
        m = np.clip(14 - ds, 0, 1)[..., None]
        cv[:] = cv * (1 - m) + np.array([255.0, 250, 225]) * m
        return push(cv, 1.04 + 0.03 * math.sin(math.pi * u), 0.5, 0.5)

    SHOTS = [(0.0, 0.3, a_wide), (0.3, 0.48, b_dial), (0.48, 1.01, a_wide)]
    return "solstice", "the turning day", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- Eid: the moon is seen, the city answers, and everyone sits down to eat -------------
AMBER, RUBY, TEAL = (255, 176, 70), (255, 80, 70), (70, 220, 200)

def crescent(cv, x, y, r, cut, rot, power=1.0):
    d1 = np.hypot(XX - x, YY - y)
    d2 = np.hypot(XX - x - cut * math.cos(rot), YY - y - cut * math.sin(rot))
    m = (np.clip(r - d1, 0, 1) * np.clip(d2 - r * 0.93, 0, 1))[..., None] * power
    cv += np.exp(-(d1 / (r * 1.7)) ** 2)[..., None] * np.array((150.0, 136, 96)) * 0.42 * power
    cv[:] = cv * (1 - m) + np.array((255.0, 248, 214)) * m

def lantern(cv, x, y, s, colour, on, halo=0.32):
    """a pierced brass lantern on its cord: a cap, a six-sided glass body, a foot. on 0..1"""
    s = max(s, 1.5)
    body = [(x - s * 0.55, y - s * 0.7), (x + s * 0.55, y - s * 0.7), (x + s * 0.85, y), (x + s * 0.55, y + s * 0.8), (x - s * 0.55, y + s * 0.8), (x - s * 0.85, y)]
    col = np.array(colour, float)
    if on > 0: spot_glow(cv, x, y, s * 1.5, col, halo * on)
    m = poly(body)
    dark = np.array((42.0, 28, 22))
    bright = col * 0.9 + np.array((255.0, 240, 190)) * 0.1
    hot = np.exp(-((XX - x) ** 2 + (YY - y) ** 2) / (2 * (s * 0.55) ** 2))[..., None]
    fill_ = dark * (1 - on) + (bright * (0.7 + 0.3 * hot) + np.array((255.0, 240, 190)) * hot * 0.22) * on
    cv[:] = cv * (1 - m) + fill_ * m
    if s > 5:                                                                    # the lattice ribs
        for dx in (-0.3, 0.3):
            ink(cv, (x + dx * s, y - s * 0.62), (x + dx * s, y + s * 0.72), (110, 76, 34), 0.6)
    brass = (150, 112, 54) if on > 0.3 else (70, 54, 34)
    paint(cv, poly([(x - s * 0.62, y - s * 0.7), (x + s * 0.62, y - s * 0.7), (x + s * 0.2, y - s * 1.25), (x - s * 0.2, y - s * 1.25)]), brass)
    paint(cv, poly([(x - s * 0.62, y + s * 0.8), (x + s * 0.62, y + s * 0.8), (x + s * 0.2, y + s * 1.15), (x - s * 0.2, y + s * 1.15)]), brass)

def star8(x, y, r, rot=0.0, squash=1.0):
    """two squares, turned: the eight-point star of a tiled wall"""
    pts = []
    for i in range(16):
        a = rot + i * math.pi / 8
        rr = r if i % 2 == 0 else r * 0.62
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a) * squash))
    return pts

def skyline(seed, base, lo, hi, blk, domes, minarets):
    """one layer of the city: a coverage mask, a shade (lit side, dark side, round domes), roof strings for lanterns, windows"""
    r = np.random.RandomState(seed)
    mask = Image.new("L", (W, H), 0); shade = Image.new("L", (W, H), 0)
    dm, ds = ImageDraw.Draw(mask), ImageDraw.Draw(shade)
    roofs, wins = [], []
    x = -6
    i = 0
    while x < W + 6:
        wd = blk * (0.7 + 0.8 * r.rand()); top = lo + (hi - lo) * r.rand()
        dm.rectangle((x, top, x + wd, base), fill=255)
        ds.rectangle((x, top, x + wd, base), fill=205); ds.rectangle((x + wd * 0.68, top, x + wd, base), fill=120)
        ds.rectangle((x, top, x + wd, top + 2), fill=240)
        roofs.append((x + 2, x + wd - 2, top))
        if r.rand() < domes:
            rd = wd * 0.38
            cx = x + wd * 0.5
            dm.ellipse((cx - rd, top - rd * 1.15, cx + rd, top + rd * 0.8), fill=255)
            for k in range(8):                                                   # a round dome by rings of shade
                f = 1 - k / 8
                ds.ellipse((cx - rd * f - (1 - f) * rd * 0.5, top - rd * 1.15 * f - (1 - f) * rd * 0.4, cx + rd * f - (1 - f) * rd * 0.5, top + rd * 0.8 * f), fill=int(110 + 130 * (1 - f)))
            dm.rectangle((cx - 1, top - rd * 1.15 - 5, cx + 1, top - rd * 1.15), fill=255); ds.rectangle((cx - 1, top - rd * 1.15 - 5, cx + 1, top - rd * 1.15), fill=210)
        for wy in np.arange(top + 7, base - 4, 13 + r.randint(0, 4)):
            for wx in np.arange(x + 5, x + wd - 4, 11):
                if r.rand() < 0.3: wins.append((wx, wy, r.rand()))
        x += wd + (r.randint(0, 3))
        i += 1
    for mx in minarets:
        tall = lo - 26 - r.rand() * 8
        dm.rectangle((mx - 4, tall, mx + 4, base), fill=255); ds.rectangle((mx - 4, tall, mx + 2, base), fill=210); ds.rectangle((mx + 2, tall, mx + 4, base), fill=120)
        dm.rectangle((mx - 7, tall + 10, mx + 7, tall + 13), fill=255); ds.rectangle((mx - 7, tall + 10, mx + 7, tall + 13), fill=232)
        dm.polygon([(mx - 4, tall), (mx + 4, tall), (mx, tall - 11)], fill=255); ds.polygon([(mx - 4, tall), (mx + 4, tall), (mx, tall - 11)], fill=190)
    return np.asarray(mask, float)[..., None] / 255, np.asarray(shade, float)[..., None] / 255, roofs, wins

def eid_film():
    far = skyline(4301, 176, 124, 148, 24, 0.5, (42, 182))
    mid = skyline(4311, 196, 148, 170, 32, 0.34, ())
    near = skyline(4321, H, 182, 198, 44, 0.2, ())
    wall_tex = fbm(4331, (12, 5, 2))[..., None]
    dune = ridge(4333, 132, 12, (90, 30))
    stars = starfield(4335, 80, BAR, 150)
    r = np.random.RandomState(4337)
    LIGHTS = []                                                                  # street by street: each roof line lights along its length, far to near, out from the middle
    for layer, (lay, delay) in enumerate(((far, 0.0), (mid, 0.28), (near, 0.56))):
        for x0, x1, top in lay[2]:
            for x in np.arange(x0, x1, 9 + layer * 3):
                if 6 < x < W - 6:
                    LIGHTS.append((x, top - 1.5 + 1.5 * math.sin(x * 0.7), delay + 0.3 * abs(x - W * 0.62) / W + 0.02 * r.rand(), layer, (AMBER, AMBER, RUBY, AMBER, TEAL)[r.randint(0, 5)]))
    WINS = [(x, y, st, 0) for x, y, st in far[3]] + [(x, y, st, 1) for x, y, st in mid[3]] + [(x, y, st, 2) for x, y, st in near[3]]

    def city(cv, night, lit_, t, flick=1.0):
        """lays the three layers down; lit_ is the progress of the lights, 0..1.2"""
        m = (YY > dune[None, :])[..., None]
        cv[:] = cv * (1 - m) + np.array([74.0, 52, 46]) * (1 - 0.72 * night) * m
        tones = [(np.array([108.0, 70, 70]), np.array([32.0, 30, 66])), (np.array([78.0, 52, 56]), np.array([24.0, 22, 50])), (np.array([50.0, 34, 40]), np.array([16.0, 14, 32]))]
        for i, (lay, (dusk, dark)) in enumerate(zip((far, mid, near), tones)):
            mask, shade = lay[0], lay[1]
            col = (dusk * (1 - night) + dark * night) * (0.45 + 0.75 * shade) * (0.75 + 0.5 * wall_tex)
            cv[:] = cv * (1 - mask) + col * mask
            for x, y, st, l in (w for w in WINS if w[3] == i):
                on = sm(0.1 + st * 0.5 + i * 0.28, 0.2 + st * 0.5 + i * 0.28, lit_)
                if on > 0:
                    x0, x1, y0, y1 = int(x) - 2, int(x) + 3, int(y) - 2 - i, int(y) + 3 + i
                    cv[max(0, y0):min(H, y1), max(0, x0):min(W, x1)] = np.array((200.0, 132, 56)) * on * (0.8 + 0.2 * wave(t, 8, st))
            for x, y, st, layer, c in LIGHTS:
                if layer != i: continue
                on = sm(st, st + 0.05, lit_)
                if on <= 0: continue
                pop = 1 + 0.8 * (1 - sm(st + 0.03, st + 0.13, lit_))
                spot_glow(cv, x, y, (2.4 + layer) * pop, c, 0.42 * on)
                stamp(cv, x, y, np.array((255.0, 238, 190)) * on, (1.7 + layer * 0.7) * pop)

    def sky(night):
        dusk = ramp([(34, 44, 96), (92, 66, 112), (236, 112, 72), (255, 186, 96)], np.clip((YY - BAR) / 112, 0, 1) ** 1.3)
        dark = ramp([(8, 10, 34), (14, 18, 52), (30, 28, 72), (58, 40, 86)], np.clip((YY - BAR) / 125, 0, 1) ** 1.3)
        return dusk * (1 - night) + dark * night

    MOON = (GOLD_X, 66)

    def a_moon(u, t):
        # the last light goes; a line of silver no one could miss, and the first few stars
        night = 0.18 + 0.42 * sm(0.0, 1.0, u)
        cv = sky(night)
        cv += stars * sm(0.5, 1.0, u) * 0.5
        seen = sm(0.3, 0.55, u)
        crescent(cv, MOON[0], MOON[1], 25, 8.5, -0.95, seen)
        glint = math.exp(-((u - 0.5) / 0.08) ** 2)                               # the moment it is seen
        if glint > 0.02:
            for a in (0, math.pi / 2):
                ca, sa = math.cos(a), math.sin(a)
                g = 34 * glint
                line(cv, (MOON[0] - 16 - ca * g, MOON[1] + 15 - sa * g), (MOON[0] - 16 + ca * g, MOON[1] + 15 + sa * g), np.array((255.0, 240, 200)) * glint * 0.6, 1.0)
        city(cv, night, 0.0, t)
        return blackout(push(cv, 1.08 - 0.08 * sm(0.0, 1.0, u), 0.6, 0.6), 1 - sm(0.0, 0.16, u))

    def b_city(u, t):
        # the answer: roof by roof, the lanterns run outward from the old quarter
        night = 0.6 + 0.4 * sm(0.0, 0.6, u)
        cv = sky(night)
        cv += stars * (0.5 + 0.5 * u) * 0.65
        crescent(cv, MOON[0], MOON[1], 25, 8.5, -0.95)
        city(cv, night, u * 1.02, t)
        return push(cv, 1.0 + 0.3 * u, 0.62, 0.9)

    # the street, in one point perspective: rows of lanterns strung across, lighting toward us
    VP = (W / 2, 106)
    DEPTHS = [1.24, 1.8, 2.7, 4.2]
    WH = [r.randint(90, 150) for _ in range(12)]
    PAL = [AMBER, AMBER, RUBY, AMBER, TEAL, AMBER]

    def z_xy(side, z, up):
        return VP[0] + side * 118 / z, VP[1] - up / z

    def c_street(u, t):
        z_shift = 0.24 * u
        cv = sky(1.0).copy()
        cv += stars * 0.8
        crescent(cv, W / 2 + 4, 56, 15, 5, -0.95)
        floor = (YY > VP[1])[..., None]
        depth = 95 / np.maximum(YY - VP[1], 1)
        cob = 0.72 + 0.28 * (np.sin((XX - VP[0]) * depth * 0.5) * np.sin((depth + z_shift) * 9) > 0)
        cv = cv * (1 - floor) + np.array([38.0, 30, 34])[None, None] * cob[..., None] * floor
        zs = [z for z in np.linspace(9, 0.8, 12)]
        for i, z1 in enumerate(zs[:-1]):                                         # the walls, far to near
            z2 = zs[i + 1]
            for side in (-1, 1):
                h = WH[(i + (side > 0) * 5) % 12]
                a1, a2 = max(z1 - z_shift, 0.3), max(z2 - z_shift, 0.3)
                pts = [z_xy(side, a1, h), z_xy(side, a2, h), z_xy(side, a2, -95), z_xy(side, a1, -95)]
                m = poly(pts)
                tone = 0.62 + 0.4 * ((i + (side > 0)) % 2)
                base = np.array([66.0, 48, 50]) * tone * (0.7 + 0.6 * wall_tex) * (0.6 + 0.5 * np.clip(2.5 / a2, 0, 1))
                cv[:] = cv * (1 - m) + base * m
                # one lit arched door and a window, per block
                mz = (a1 + a2) / 2
                on = sm(0.08 + (9 - z1) * 0.06, 0.2 + (9 - z1) * 0.06, u)
                za, zb = mz + (a1 - a2) * 0.2, mz - (a1 - a2) * 0.2
                if on > 0:
                    paint(cv, poly([z_xy(side, za, -20), z_xy(side, zb, -20), z_xy(side, zb, -95), z_xy(side, za, -95)]), np.array((206.0, 134, 60)) * (0.35 + 0.65 * on))
                    paint(cv, poly([z_xy(side, za, h - 34), z_xy(side, zb, h - 34), z_xy(side, zb, h - 56), z_xy(side, za, h - 56)]), np.array((206.0, 134, 60)) * on * 0.85)
                else:
                    paint(cv, poly([z_xy(side, za, -20), z_xy(side, zb, -20), z_xy(side, zb, -95), z_xy(side, za, -95)]), (26, 20, 26))
        for k, z0 in enumerate(reversed(DEPTHS)):                                # strings far to near: the wave comes at the camera
            z = z0 - z_shift
            if z < 0.62: continue
            start = 0.08 + k * 0.2
            on = sm(start, start + 0.07, u)
            pts_x = [(side * 116 / z + VP[0]) for side in (-1, 1)]
            hang = 64 / z
            n = 5
            ys = lambda f: VP[1] - 84 / z + 22 / z * (1 - (2 * f - 1) ** 2)      # the sag of the cord
            prev = None
            for j in range(41):
                f = j / 40
                p = (pts_x[0] + (pts_x[1] - pts_x[0]) * f, ys(f))
                if prev: ink(cv, prev, p, (120, 96, 62) if on > 0.2 else (44, 36, 34), 0.8)
                prev = p
            floor_x = VP[0]
            pool = np.exp(-(((XX - floor_x) / (120 / z)) ** 2 + ((YY - (VP[1] + 95 / z)) / (24 / z)) ** 2))[..., None]
            cv += pool * np.array((110.0, 70, 26)) * on * 0.32
            for j in range(n):
                f = (j + 0.5) / n
                x, y = pts_x[0] + (pts_x[1] - pts_x[0]) * f, ys(f) + 7 / z
                sway = 2.6 / z * wave(t, 8, f + k * 0.17)
                lantern(cv, x + sway, y + 9 / z, 14 / z, PAL[(j + k) % 6], on, 0.14)
        return cv

    # the table: painted once, then the pot and the glass and the steam move on it
    table = np.zeros((H, W, 3))
    table[:] = ramp([(44, 28, 28), (58, 36, 32), (70, 42, 34)], np.clip((YY - 30) / 110, 0, 1))
    for iy, y in enumerate(range(26, 150, 26)):                                  # a tiled wall of eight-point stars
        for ix, x in enumerate(range(-6, W + 26, 30)):
            xx = x + (iy % 2) * 15
            paint(table, poly(star8(xx, y, 11, math.pi / 8)), (96, 62, 44))
            paint(table, poly(star8(xx, y, 6, math.pi / 8)), (46, 74, 76))
    # the window: an arch of night sky, with the moon in it
    win = ((XX - 134) ** 2 / 26 ** 2 + (YY - 66) ** 2 / 28 ** 2 < 1) & (YY < 66) | ((np.abs(XX - 134) < 26) & (YY >= 66) & (YY < 112))
    table[win] = np.array([12.0, 14, 44]) + 0
    table += stars * win[..., None] * 0.8
    crescent(table, 142, 62, 15, 5, -0.95)
    for off, c in ((30, (120, 88, 52)), (28, (64, 44, 30))):
        for sign in (-1, 1):
            ink(table, (134 + sign * off, 62), (134 + sign * off, 114), c, 1.2)
    # the cloth, in perspective, with a band of stars
    tz = np.clip((YY - 134) / 86, 0, 1)
    clothm = (YY > 134)[..., None]
    tu = (XX - W / 2) / (0.55 + 0.55 * tz)
    cloth = ramp([(70, 18, 26), (112, 26, 34), (144, 36, 40)], tz)
    band = (np.abs(tz - 0.55) < 0.11)[..., None]
    star_band = (np.abs(((tu + 1000) % 26) - 13) + np.abs((tz - 0.55) / 0.11) * 13 < 11)[..., None]
    cloth = cloth * (1 - band * 0.45) + band * star_band * np.array([190.0, 150, 70]) * 0.6
    table = table * (1 - clothm) + cloth * clothm
    table[132:135] = np.array([30.0, 16, 18])
    # the tray of pastry, front right: the brass round, then diamonds in rows
    TX, TY = 146, 170
    d = np.hypot((XX - TX) / 62, (YY - TY) / 25)
    table[d < 1.08] = np.array([40.0, 22, 16])
    tm = d < 1
    table[tm] = ramp([(226, 170, 70), (180, 126, 50), (120, 80, 36)], np.clip(d + (XX - TX) / 180 + (YY - TY) / 90, 0, 1))[tm]
    inner = d < 0.82
    table[inner] = table[inner] * 0.66
    for row, dy in enumerate((-13, -4, 5, 14)):
        n = (5, 6, 6, 4)[row]
        for col in range(n):
            x = TX + (col - (n - 1) / 2) * 16 + 2
            y = TY + dy
            pts = [(x, y - 6), (x + 8.5, y), (x, y + 6), (x - 8.5, y)]
            m = poly(pts)
            pastry = ramp([(150, 92, 34), (226, 164, 66), (252, 214, 120)], 0.55 + (y - YY) / 12 + (x - XX) / 28 + (wall_tex[..., 0] - 0.5))
            table = table * (1 - m) + pastry * m
            for a, b in zip(pts, pts[1:] + pts[:1]): ink(table, a, b, (86, 46, 18), 0.6)
            stamp(table, x, y - 1, (20, 150, 30), 1.9)
    # dates in a dish, front left; round cookies dusted white between
    DX, DY = 70, 198
    d2 = np.hypot((XX - DX) / 40, (YY - DY) / 15)
    table[d2 < 1] = ramp([(210, 214, 220), (110, 130, 160)], np.clip(d2 + (YY - DY) / 50, 0, 1))[d2 < 1]
    rr = np.random.RandomState(4361)
    for i, (x, y) in enumerate(((-24, -3), (-8, -6), (9, -5), (24, -2), (-17, 3), (0, 2), (17, 3))):
        cx_, cy_ = DX + x, DY + y
        m = (np.hypot((XX - cx_) / 9, (YY - cy_) / 5.2) < 1)[..., None]
        table = table * (1 - m) + ramp([(30, 12, 10), (86, 40, 22), (150, 86, 50)], 0.55 + (cx_ - XX) / 26 + (cy_ - YY) / 14) * m
        stamp(table, cx_ - 2.5, cy_ - 2, (200, 170, 140), 1.5)
    for i, (x, y) in enumerate(((186, 136), (176, 141), (198, 142))):
        m = (np.hypot((XX - x) / 11, (YY - y) / 7) < 1)[..., None]
        table = table * (1 - m) + ramp([(170, 140, 110), (232, 214, 186), (255, 250, 240)], 0.55 + (x - XX) / 26 + (y - YY) / 18) * m

    pot_stops = [(50, 30, 18), (128, 86, 40), (214, 170, 84), (255, 240, 170)]
    PX, PY = 44, 130                                                             # the pot's pivot, on its foot

    def pot(cv, tilt, t):
        ca, sa = math.cos(tilt), math.sin(tilt)
        def P(pts): return [(PX + (x * ca - y * sa), PY + (x * sa + y * ca)) for x, y in pts]
        ccx, ccy = P([(0, -32)])[0]
        shade_ = lit(ccx, ccy, 40, pot_stops)
        # the handle, behind
        h0 = [(-24, -52), (-44, -56), (-52, -38), (-40, -18), (-22, -14)]
        for a, b in zip(P(h0), P(h0)[1:]): ink(cv, a, b, (130, 92, 44), 2.8)
        spout = [(20, -40), (34, -52), (52, -74), (58, -72), (42, -40), (28, -20), (18, -20)]
        m = poly(P(spout)); cv[:] = cv * (1 - m) + lit(ccx + 32, ccy - 20, 34, pot_stops) * m
        body = [(0 + 30 * math.cos(a), -28 + 26 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 28)]
        m = poly(P(body)); cv[:] = cv * (1 - m) + shade_ * m
        neck = [(-11, -48), (11, -48), (15, -62), (-15, -62)]
        m = poly(P(neck)); cv[:] = cv * (1 - m) + lit(ccx, ccy - 26, 20, pot_stops) * m
        lid = [(0 + 16 * math.cos(a), -64 + 7 * math.sin(a)) for a in np.linspace(math.pi, 2 * math.pi, 12)] + [(16, -62), (-16, -62)]
        m = poly(P(lid)); cv[:] = cv * (1 - m) + lit(ccx, ccy - 36, 16, pot_stops) * m
        foot = [(-16, -3), (16, -3), (20, 2), (-20, 2)]
        m = poly(P(foot)); cv[:] = cv * (1 - m) + lit(ccx, ccy + 32, 26, pot_stops) * m
        band = P([(-29, -30), (29, -30)])
        ink(cv, band[0], band[1], (90, 56, 22), 1.0)
        fx, fy = P([(0, -74)])[0]
        stamp(cv, fx, fy, (255, 222, 130), 2.6)
        return P([(57, -74)])[0]

    GX, GY = 94, 180                                                             # the glass, on its saucer

    def tea_glass(cv, level):
        d = np.hypot((XX - GX) / 25, (YY - GY - 3) / 8)
        cv[d < 1] = ramp([(240, 244, 250), (150, 170, 190)], np.clip(d, 0, 1))[d < 1]
        waist = lambda y: 15 - 5 * np.sin(np.clip((y - (GY - 46)) / 46, 0, 1) * math.pi) ** 1.6
        prof = 13 * 0 + waist(YY)
        m = ((np.abs(XX - GX) < prof) & (YY > GY - 46) & (YY < GY))
        tea = ((YY > GY - 3 - 39 * level) & m)
        cv[m] = cv[m] * 0.55 + np.array((120.0, 140, 160)) * 0.45
        am = ramp([(250, 170, 60), (196, 76, 30)], np.clip((YY - (GY - 42)) / 42, 0, 1))
        cv[tea] = am[tea]
        ink(cv, (GX - 7, GY - 40), (GX - 5, GY - 8), (255, 250, 230), 0.9)
        top = (np.abs(YY - (GY - 46)) < 1.4) & (np.abs(XX - GX) < 15)
        cv[top] = np.array((225.0, 235, 245))

    def d_table(u, t):
        # lantern over a low table: tea is poured, the steam goes up; the sweets wait
        cv = table.copy()
        flick = 1 + 0.06 * wave(t, 24)
        cv = cv * (0.62 + 0.42 * sm(0.0, 0.2, u)) + np.exp(-(((XX - 150) / 110) ** 2 + ((YY - 110) / 100) ** 2))[..., None] * np.array([26.0, 15, 5]) * flick
        pour = sm(0.2, 0.34, u) * (1 - sm(0.7, 0.82, u))
        mouth = pot(cv, 0.0 + 0.58 * pour, t)
        level = 0.14 + 0.72 * sm(0.3, 0.74, u)
        tea_glass(cv, level)
        if pour > 0.8:                                                            # the stream, falling in a long curve
            x0, y0 = mouth
            tgt_x, tgt_y = GX, GY - 3 - 39 * level
            last = None
            for q in np.linspace(0, 1, 16):
                p = (x0 + (tgt_x - x0) * q, y0 + (tgt_y - y0) * q ** 2)
                if last: ink(cv, last, p, (252, 170, 56), 1.8)
                last = p
            stamp(cv, tgt_x, tgt_y, (255, 240, 200), 2.6)
        for k in range(3):                                                        # steam: three threads, fading as they rise
            for i in range(24):
                q = i / 24
                x = GX + (k - 1) * 4 + 4 * math.sin(q * 9 + k * 2.1 + t * 2 * math.pi * 10) * (0.3 + q)
                stamp(cv, x, GY - 50 - q * 66, np.array((150.0, 142, 134)) * (1 - q) * 0.3, 2.2)
        for x, y, s, c, ph in ((186, 56, 15, AMBER, 0.0),):
            ink(cv, (x, BAR), (x + 1.5 * wave(t, 6, ph), y - s * 1.2), (100, 74, 40), 0.8)
            lantern(cv, x + 1.5 * wave(t, 6, ph), y, s, c, flick)
        return push(blackout(cv, 1 - sm(0.0, 0.1, u)), 1.0 + 0.06 * u, 0.6, 0.5)

    def e_night(u, t):
        # the whole city lit, stars out, the moon high; and slowly dark again
        cv = sky(1.0)
        cv += stars * 0.95
        crescent(cv, MOON[0], MOON[1], 25, 8.5, -0.95)
        city(cv, 1.0, 1.3, t)
        for i in range(7):                                                        # paper lanterns let go from the roofs, drifting up past the moon
            q = (u * 0.8 + i * 0.13) % 1
            x = 22 + i * 28 + 8 * math.sin(q * 6 + i)
            y = 178 - q * 150
            lantern(cv, x, y, 3.6, AMBER, 1 - sm(0.8, 1.0, q))
        return blackout(push(cv, 1.1 - 0.1 * u, 0.6, 0.6), sm(0.82, 1.0, u))

    SHOTS = [(0.0, 0.19, a_moon), (0.19, 0.36, b_city), (0.36, 0.57, c_street), (0.57, 0.84, d_table), (0.84, 1.01, e_night)]
    return "eid", "the first lantern", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

RECIPES = [celebrating(earth_film, Wear.sprout, [(30, 90, 170), (50, 170, 120), (140, 230, 120)]),
           celebrating(solstice_film, Wear.sun, [(190, 90, 30), (240, 160, 50), (255, 226, 130)]),
           celebrating(eid_film, Wear.crescent, [(30, 40, 120), (70, 80, 190), (240, 200, 110)])]
