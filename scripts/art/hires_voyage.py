"""Hi-res recipe, the long way home. Andrew 2026-10-31: "zoom out from earth to the solar system to the star cluster to
the galaxy all the way out to another galaxy and zoom in on another solar system to another planet where gielenor is".

One camera, no cuts. Every scale is a level with a canonical framing and an anchor inside its parent; origins follow
rigidly from the log-zoom Z, so each scale stays on screen and shrinks into the next. The landing is a real flat-shaded
3D scene, the chunky low-poly look done literally, with the camera falling out of the clouds."""
from hires_kit import *

N = 168
C = np.array([W / 2.0, H / 2.0])
SPACE_ = np.array([3.0, 3.0, 8.0])

def sm(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)

# ---- the camera: log zoom, slowing at every scale, never stopping ----------------------
Z_EARTH, Z_SOLAR, Z_CLUSTER, Z_GAL, Z_VOID = 0.0, 3.3, 6.5, 9.55, 11.27
Z_GAL2, Z_CLUSTER2, Z_SYS2, Z_PLANET = 9.55, 6.28, 3.1, 0.5
LEVEL_Z = [Z_EARTH, Z_SOLAR, Z_CLUSTER, Z_GAL, Z_VOID, Z_GAL2, Z_CLUSTER2, Z_SYS2, Z_PLANET]
KNOTS = [(0, -0.1), (8, 0.15), (18, 3.0), (25, 3.45), (33, 6.3), (37, 6.6), (43, 7.85), (47, 8.2), (54, 9.45), (59, 9.85),
         (65, 11.2), (68, 11.34), (71, 11.2), (77, 10.0), (82, 9.5), (88, 8.1), (91, 7.8), (97, 6.45), (101, 6.1),
         (108, 3.4), (113, 3.0), (120, 0.75), (125, 0.42), (131, -1.3), (136, -3.0), (N + 1, -3.0)]
PEAK = 68
_ff = np.linspace(0, N + 1, (N + 1) * 8 + 1)
_zz = np.interp(_ff, [k[0] for k in KNOTS], [k[1] for k in KNOTS])
_kern = np.hanning(17); _kern /= _kern.sum()
_zz = np.convolve(np.r_[np.full(8, _zz[0]), _zz, np.full(8, _zz[-1])], _kern, "valid")
def zoom_at(f): return float(np.interp(f, _ff, _zz))

# parent of each level: 0 earth < 1 solar < 2 cluster < 3 galaxy < 4 void > 5 galaxy2 > 6 cluster2 > 7 system2 > 8 planet
def origins(Z, f, a):
    """a[k] = the anchor of the child in the pair (k, k + 1), in the parent's canonical pixels. Screen origins of all levels."""
    r = [math.exp(z - Z) for z in LEVEL_Z]
    O = [None] * 9
    if f < PEAK:
        k = 0
        while k < 3 and Z > LEVEL_Z[k + 1]: k += 1
        v = min(1.0, max(0.0, (Z - LEVEL_Z[k]) / (LEVEL_Z[k + 1] - LEVEL_Z[k])))
        g = v * v * (3 - 2 * v)
        O[k] = C + a[k] * g                              # the child slides from the centre to its place
        O[k + 1] = O[k] - a[k] * r[k + 1]
    else:
        k = 4
        while k < 7 and Z < LEVEL_Z[k + 1]: k += 1
        v = min(1.0, max(0.0, (LEVEL_Z[k] - Z) / (LEVEL_Z[k] - LEVEL_Z[k + 1])))
        g = v * v * (3 - 2 * v)
        O[k + 1] = C + a[k] * (1 - g)
        O[k] = O[k + 1] - a[k] * r[k]
    for i in range(8):                                   # outward along the chain from what is known
        for j in range(8):
            if O[j] is None or O[j + 1] is not None: continue
            O[j + 1] = O[j] - a[j] * r[j + 1] if j < 4 else O[j] + a[j] * r[j]
        for j in range(7, -1, -1):
            if O[j + 1] is None or O[j] is not None: continue
            O[j] = O[j + 1] + a[j] * r[j + 1] if j < 4 else O[j + 1] - a[j] * r[j]
    return O, r

# ---- small painters -----------------------------------------------------------------
def patch(x, y, k):
    x0, x1, y0, y1 = max(0, int(x - k)), min(W, int(x + k) + 1), max(0, int(y - k)), min(H, int(y + k) + 1)
    return (x0, x1, y0, y1) if x0 < x1 and y0 < y1 else None

def soft(cv, x, y, rad, colour, power=1.0):
    p = patch(x, y, rad * 3.2)
    if not p: return
    x0, x1, y0, y1 = p
    cv[y0:y1, x0:x1] += np.exp(-((XX[y0:y1, x0:x1] - x) ** 2 + (YY[y0:y1, x0:x1] - y) ** 2) / (2 * rad * rad))[..., None] * np.array(colour, float) * power

def star(cv, x, y, size, colour, a=1.0):
    """fewer, brighter, bigger: a hot core and a tight halo, so a star takes a whole cell or two"""
    if not (-8 < x < W + 8 and -8 < y < H + 8) or a <= 0.01: return
    c = np.array(colour, float) * a
    soft(cv, x, y, size * 1.25, c, 0.75)
    soft(cv, x, y, size * 0.62, c * 0.5 + 125 * a, 1.0)

def ball(cv, x, y, r, stops, lx, ly, alpha=1.0, bands=0.0, seed=0.0, tilt=0.0, amb=0.14):
    """a lit sphere; bands (0..1) strokes it across like a gas giant"""
    if r <= 0.2 or alpha <= 0.01: return
    p = patch(x, y, r + 2)
    if not p: return
    x0, x1, y0, y1 = p
    nx, ny = (XX[y0:y1, x0:x1] - x) / r, (YY[y0:y1, x0:x1] - y) / r
    rr = nx * nx + ny * ny
    nz = np.sqrt(np.maximum(1 - rr, 0))
    n = math.hypot(lx, ly) or 1
    lx, ly = lx / n * 0.85, ly / n * 0.85
    li = np.clip(nx * lx + ny * ly + nz * 0.5, 0, 1)
    v = 0.5 + 0.35 * nx * lx + 0.35 * ny * ly
    if bands:
        b = ny * math.cos(tilt) + nx * math.sin(tilt)
        v = 0.55 + bands * 0.45 * np.sin(b * 9 + seed + 1.2 * np.sin(b * 23 + seed))
    col = ramp(stops, v)[..., :3] * (amb + 1.05 * li[..., None])
    m = np.clip((1 - np.sqrt(rr)) * r, 0, 1)[..., None] * alpha
    cv[y0:y1, x0:x1] = cv[y0:y1, x0:x1] * (1 - m) + col * m

def saturn_ring(cv, x, y, r, colour, front, alpha=1.0, tilt=-0.22, squash=0.36, r1=1.4, r2=2.3):
    if r <= 0.2 or alpha <= 0.01: return
    p = patch(x, y, r * r2 + 2)
    if not p: return
    x0, x1, y0, y1 = p
    dx, dy = XX[y0:y1, x0:x1] - x, YY[y0:y1, x0:x1] - y
    u = dx * math.cos(tilt) + dy * math.sin(tilt); v = -dx * math.sin(tilt) + dy * math.cos(tilt)
    d = np.hypot(u, v / squash)
    m = np.clip(d - r * r1, 0, 1) * np.clip(r * r2 - d, 0, 1) * ((v >= 0) if front else (v < 0))
    gap = 1 - 0.55 * (np.abs(d - r * 1.75) < max(0.6, r * 0.09))
    m = (m * alpha)[..., None]
    cv[y0:y1, x0:x1] = cv[y0:y1, x0:x1] * (1 - m) + np.array(colour, float) * gap[..., None] * m

_LON = np.linspace(0, 2 * math.pi, 720, endpoint=False)
_LAT = np.linspace(-math.pi / 2, math.pi / 2, 360)
def sphere_field(seed, freqs):
    r = np.random.RandomState(seed)
    la, lo = np.meshgrid(_LAT, _LON, indexing="ij")
    nx, ny, nz = np.cos(la) * np.sin(lo), np.sin(la), np.cos(la) * np.cos(lo)
    out = np.zeros_like(la)
    for f in freqs:
        for _ in range(7):
            d = r.normal(size=3); d /= np.linalg.norm(d)
            out += np.sin(f * (nx * d[0] + ny * d[1] + nz * d[2]) * 2 + r.rand() * 6.28) / f
    return (out - out.mean()) / out.std()

# ---- globes: an albedo map and a cloud map ------------------------------------------------
class Globe:
    TILT = 0.32
    def __init__(self, albedo, cloud, haze=(70.0, 140, 255)):
        self.alb, self.cloud, self.haze = albedo, cloud, haze

    def paint(self, cv, cx, cy, R, lon, cloud=0.4, light=(-0.62, -0.5, 0.6), alpha=1.0):
        nx, ny = (XX - cx) / R, (YY - cy) / R
        rr = nx * nx + ny * ny
        nz = np.sqrt(np.maximum(1 - rr, 0))
        ct, st = math.cos(self.TILT), math.sin(self.TILT)
        yp, zp = ny * ct - nz * st, ny * st + nz * ct
        lat = np.arcsin(np.clip(yp, -1, 1)); lo = np.arctan2(nx, zp) + lon
        iy = np.clip(((lat + math.pi / 2) / math.pi * 359).astype(int), 0, 359)
        ix = (lo / (2 * math.pi) * 720).astype(int) % 720
        al = self.alb[iy, ix]
        ic = ((lo + 0.3) / (2 * math.pi) * 720).astype(int) % 720
        c = np.clip((self.cloud[iy, ic] - (1.9 - cloud * 1.5)) * 1.3, 0, 1)[..., None]
        al = al * (1 - c * 0.92) + np.array((240.0, 244, 248)) * c * 0.92
        sx, sy, sz = light
        li = np.clip(nx * sx + ny * sy + nz * sz, 0, 1)
        col = al * (0.06 + 1.04 * li[..., None] ** 0.75)
        col = col + ((1 - nz) ** 2.6 * li ** 0.4)[..., None] * np.array(self.haze) * 1.0
        d = np.hypot(XX - cx, YY - cy)
        side = np.clip(((XX - cx) * sx + (YY - cy) * sy) / np.maximum(d, 1) * 0.9 + 0.5, 0, 1)
        cv += (np.exp(-np.maximum(d - R, 0) / max(2.5, R * 0.07)) * (d >= R) * side)[..., None] * np.array(self.haze) * 0.8 * alpha
        m = np.clip(R - d, 0, 1)[..., None] * alpha
        cv[:] = cv * (1 - m) + col * m

CONTINENTS = {
    "n_america": [(-168, 65), (-156, 71), (-125, 70), (-95, 72), (-80, 73), (-62, 60), (-56, 52), (-66, 45), (-75, 38), (-81, 30), (-80, 26), (-84, 30), (-97, 26), (-97, 20), (-90, 21), (-87, 16), (-83, 10), (-78, 8), (-85, 11), (-92, 15), (-105, 20), (-115, 30), (-124, 40), (-125, 50), (-140, 60), (-155, 58), (-165, 60)],
    "s_america": [(-78, 8), (-62, 10), (-50, 0), (-35, -6), (-39, -15), (-48, -25), (-57, -36), (-65, -42), (-68, -54), (-74, -50), (-72, -30), (-70, -18), (-80, -5)],
    "africa": [(-17, 15), (-16, 22), (-8, 33), (3, 37), (11, 37), (10, 32), (20, 31), (32, 31), (37, 20), (43, 12), (51, 11), (40, -5), (40, -15), (35, -24), (27, -34), (18, -34), (12, -17), (9, -2), (9, 4), (-4, 5), (-13, 8)],
    "eurasia": [(-9, 37), (-9, 43), (-1, 44), (-4, 48), (3, 51), (8, 54), (8, 57), (5, 60), (12, 65), (25, 71), (40, 67), (60, 69), (80, 73), (105, 77), (130, 72), (160, 70), (180, 68), (170, 60), (160, 58), (155, 52), (140, 54), (135, 43), (127, 38), (122, 30), (118, 23), (108, 20), (108, 12), (103, 2), (99, 10), (97, 17), (90, 22), (80, 13), (77, 8), (72, 20), (60, 25), (57, 25), (59, 22), (52, 16), (45, 13), (43, 15), (35, 28), (36, 36), (28, 37), (26, 40), (23, 37), (20, 40), (15, 38), (12, 44), (3, 42), (0, 38), (-5, 36)],
    "australia": [(114, -22), (122, -17), (131, -12), (136, -12), (142, -11), (146, -19), (153, -27), (150, -37), (140, -38), (132, -32), (124, -33), (115, -34)],
    "britain": [(-5, 50), (1, 51), (-2, 58), (-6, 57)], "madagascar": [(44, -16), (50, -14), (47, -25), (44, -24)],
    "japan": [(130, 32), (140, 35), (142, 42), (139, 40)], "borneo": [(109, 1), (118, 6), (117, -3), (110, -3)],
    "sumatra": [(95, 5), (104, -2), (106, -6), (100, -3)], "newguinea": [(131, -1), (147, -6), (150, -10), (138, -8)], "newzealand": [(173, -35), (178, -38), (169, -46), (167, -45)],
    "greenland": [(-45, 60), (-22, 70), (-20, 80), (-60, 82), (-70, 76), (-52, 68)],
}
DESERTS = [[(-16, 18), (-8, 32), (30, 30), (36, 18), (10, 15)], [(36, 30), (55, 26), (52, 17), (44, 16)], [(120, -20), (140, -20), (142, -32), (124, -32)], [(55, 45), (100, 46), (105, 38), (70, 36)], [(-115, 40), (-104, 40), (-104, 28), (-112, 28)]]

def _map_mask(polys, blur=2.0):
    im = Image.new("L", (720, 360), 0); d = ImageDraw.Draw(im)
    for poly in polys:
        d.polygon([((lo % 360) / 360 * 720 if lo >= 0 else (lo + 360) / 360 * 720, (90 - la) / 180 * 359) for lo, la in poly], fill=255)
    return np.asarray(im.filter(ImageFilter.GaussianBlur(blur)), float) / 255

def earth_globe():
    wob = sphere_field(4111, (6, 10, 16, 26))
    fine = sphere_field(4112, (12, 20, 30))
    land = _map_mask(CONTINENTS.values(), 2.2) + 0.06 * wob
    land = np.clip((land - 0.5) * 7 + 0.5, 0, 1)[..., None]
    sea = ramp([(8, 42, 120), (24, 96, 196)], 0.55 + fine * 0.14)
    green = ramp([(24, 92, 42), (56, 138, 56), (106, 168, 74)], 0.5 + fine * 0.3)
    sand = ramp([(166, 130, 84), (206, 170, 110)], 0.5 + fine * 0.3)
    dmask = np.clip((_map_mask(DESERTS, 6) + 0.1 * wob - 0.35) * 5, 0, 1)[..., None]
    ground_ = green * (1 - dmask) + sand * dmask
    alb = sea * (1 - land) + ground_ * land
    glat = (90 - np.arange(360) / 359 * 180)
    ice = np.clip((np.abs(glat)[:, None] - 72 + wob * 3) / 4, 0, 1)[..., None]
    gl = _map_mask([CONTINENTS["greenland"]], 1.5)[..., None]
    alb = alb * (1 - ice) + np.array((226.0, 234, 242)) * ice
    alb = alb * (1 - gl) + np.array((226.0, 234, 242)) * gl
    return Globe(alb, sphere_field(4121, (9, 13, 19, 27)))

def gielinor_globe():
    f = sphere_field(7101, (1.1, 1.8, 3, 5, 8))
    dt = sphere_field(7111, (10, 16, 26))
    ds = sphere_field(7121, (1.5, 2.5))
    th = np.percentile(f, 56)
    land = np.clip((f - th) * 9, 0, 1)[..., None]
    sea = ramp([(10, 70, 140), (24, 130, 196), (60, 178, 206)], np.clip(0.45 + (f - th) * 0.5 + dt * 0.08, 0, 1))
    grass = ramp([(30, 112, 40), (58, 166, 52), (120, 204, 72)], 0.5 + dt * 0.32)
    sand = np.array((212.0, 184, 120))
    desert = np.clip((ds - 1.25) * 4, 0, 1)[..., None]
    grass = grass * (1 - desert) + sand * desert
    peak = np.clip((f - th - 1.75) * 3, 0, 1)[..., None]
    grass = grass * (1 - peak) + np.array((132.0, 122, 112)) * peak
    coast = np.clip(1 - np.abs(f - th) * 16, 0, 1)[..., None]
    alb = sea * (1 - land) + grass * land
    alb = alb * (1 - coast * 0.5) + sand * coast * 0.5
    g = Globe(alb, sphere_field(7131, (3, 5, 8, 12)), haze=(110.0, 190, 255))
    row = int((-Globe.TILT + math.pi / 2) / math.pi * 359)
    score = np.convolve(np.r_[f[row], f[row]], np.ones(50) / 50, "same")[:720]
    g.L0 = _LON[int(score.argmax())]
    return g

# ---- galaxies, baked to a big bitmap and resampled on the way past -----------------------
GS, GR = 1400, 640.0
def _big_noise(seed, cell, size=GS):
    n = size // cell + 3
    r = np.random.RandomState(seed).rand(n, n)
    im = Image.fromarray((r * 255).astype(np.uint8)).resize((size + 2 * cell, size + 2 * cell), Image.BICUBIC)
    return np.asarray(im, float)[:size, :size] / 255

class Galaxy:
    def __init__(self, seed, arms, pitch, squash, rot, sharp, palette, core_col, knot_col, bar=0.0, dust=0.6, r0=0.2, minor=0.0):
        self.arms, self.pitch, self.squash, self.rot, self.sharp, self.r0, self.minor = arms, pitch, squash, rot, sharp, r0, minor
        yy, xx = np.mgrid[0:GS, 0:GS].astype(np.float32)
        u, v = self.flat((xx - GS / 2) / GR, (yy - GS / 2) / GR)
        r = np.hypot(u, v) + 1e-4; th = np.arctan2(v, u)
        n1, n2, n3 = _big_noise(seed, 90), _big_noise(seed + 1, 34), _big_noise(seed + 2, 12)
        tex = 0.5 * n1 + 0.33 * n2 + 0.17 * n3
        arm = self.arm(u, v)
        env = np.clip((1.02 - r) / 0.3, 0, 1) ** 1.3
        lit = arm * (0.5 + 1.0 * (tex + 0.15)) * env * (0.18 + np.exp(-r / 0.6))
        lit = np.clip(lit * np.clip((r - 0.06) / 0.14, 0, 1), 0, None)
        mist = np.exp(-r / 0.34) * 0.32 * (0.6 + 0.8 * tex) * env
        corev = np.exp(-(r / 0.11) ** 2) * 1.25 + np.exp(-r / 0.075) * 0.45
        if bar:
            ca, sa = math.cos(0.5), math.sin(0.5)
            bu, bv = u * ca + v * sa, -u * sa + v * ca
            corev = corev + np.exp(-(bu / 0.27) ** 2 - (bv / 0.06) ** 2) * bar
        img = np.zeros((GS, GS, 3), np.float32)
        img += (lit[..., None] * np.array(palette[0], np.float32)) * 1.05
        img += (mist[..., None] * np.array(palette[1], np.float32))
        img += (corev[..., None] * np.array(core_col, np.float32))
        # dust on the inner edge of each arm, the dark lane that makes it look wound
        if dust:
            lane = self.arm(u, v, shift=0.34) * np.clip((r - 0.08) / 0.1, 0, 1) * env
            img *= (1 - dust * np.clip(lane * (0.5 + tex), 0, 1) ** 2)[..., None]
        out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
        d = ImageDraw.Draw(out, "RGBA")
        rs = np.random.RandomState(seed + 5)
        # star-forming knots along the arms
        got = 0
        while got < 150:
            ang, rad = rs.rand() * 2 * math.pi, 0.14 + 0.82 * math.sqrt(rs.rand())
            px, py = self.unflat(rad * math.cos(ang), rad * math.sin(ang))
            ux, vx = rad * math.cos(ang), rad * math.sin(ang)
            if float(self.arm(np.array(ux), np.array(vx))) < 0.42: continue
            got += 1
            s = 2.5 + 4.5 * rs.rand()
            x, y = GS / 2 + px * GR, GS / 2 + py * GR
            col = knot_col if rs.rand() < 0.45 else palette[0]
            d.ellipse((x - s, y - s, x + s, y + s), fill=tuple(int(c) for c in col) + (210,))
        self.img = out.filter(ImageFilter.GaussianBlur(1.6))

    def flat(self, x, y):
        ca, sa = math.cos(self.rot), math.sin(self.rot)
        return (x * ca + y * sa), (-x * sa + y * ca) / self.squash

    def unflat(self, u, v):
        ca, sa = math.cos(self.rot), math.sin(self.rot)
        v = v * self.squash
        return u * ca - v * sa, u * sa + v * ca

    def arm(self, u, v, shift=0.0):
        r = np.hypot(u, v) + 1e-4; th = np.arctan2(v, u)
        ph = self.arms * (th - np.log(r / self.r0) / self.pitch) + shift * 2 * math.pi
        a = (0.5 + 0.5 * np.cos(ph)) ** self.sharp
        if self.minor:
            a = np.maximum(a, self.minor * (0.5 + 0.5 * np.cos(ph / 1 + math.pi)) ** (self.sharp * 1.6))
        return a

    def anchor(self, rad, near):
        """a point riding the crest of an arm, near a direction, in canonical pixels (the galaxy's radius is 95)"""
        best, val = None, -1
        for i in range(720):
            a = i / 720 * 2 * math.pi
            u, v = rad * math.cos(a), rad * math.sin(a)
            x, y = self.unflat(u, v)
            if x * near[0] + y * near[1] < 0.6 * rad * self.squash: continue
            q = float(self.arm(np.array(u), np.array(v)))
            if q > val: best, val = (x, y), q
        return np.array(best) * 95

    def sample_near(self, cx, cy, n, seed, spread):
        """star positions along the arm around a point, in canonical pixels"""
        rs = np.random.RandomState(seed); out = []
        while len(out) < n:
            x, y = cx + (rs.rand() - 0.5) * 2 * spread, cy + (rs.rand() - 0.5) * 2 * spread
            u, v = self.flat(x / 95, y / 95)
            if float(self.arm(np.array(u), np.array(v))) > rs.rand() * 0.4 + 0.5: out.append((x, y, rs.rand()))
        return out

    def blit(self, cv, cx, cy, R, alpha=1.0):
        """R is the on-screen radius of the disc"""
        if alpha <= 0.01: return
        sc = R / GR
        hw = GS / 2 * sc
        x0, x1, y0, y1 = max(0, int(math.floor(cx - hw))), min(W, int(math.ceil(cx + hw))), max(0, int(math.floor(cy - hw))), min(H, int(math.ceil(cy + hw)))
        if x0 >= x1 or y0 >= y1: return
        box = ((x0 - cx) / sc + GS / 2, (y0 - cy) / sc + GS / 2, (x1 - cx) / sc + GS / 2, (y1 - cy) / sc + GS / 2)
        box = (max(0, box[0]), max(0, box[1]), min(GS, box[2]), min(GS, box[3]))
        im = self.img.resize((x1 - x0, y1 - y0), Image.BICUBIC, box=box)
        cv[y0:y1, x0:x1] += np.asarray(im, float) * alpha

# ---- clusters and solar systems -------------------------------------------------------
class Cluster:
    def __init__(self, seed, n, spread, palette, home):
        rs = np.random.RandomState(seed); self.stars = []
        self.home = np.array(home, float)
        tries = 0
        while len(self.stars) < n:
            a = rs.rand() * 2 * math.pi; d = spread * rs.rand() ** 0.72
            x, y = d * math.cos(a), d * math.sin(a) * 1.05
            if any((x - s[0]) ** 2 + (y - s[1]) ** 2 < 120 for s in self.stars) and tries < 900:
                tries += 1; continue
            if (x - home[0]) ** 2 + (y - home[1]) ** 2 < 170: continue
            self.stars.append((x, y, 1.7 + 2.0 * rs.rand() ** 2, palette[rs.randint(len(palette))]))

    def draw(self, cv, O, r, a=1.0):
        s_ = min(r, 1.5) ** 0.5
        for x, y, size, col in self.stars:
            star(cv, O[0] + x * r, O[1] + y * r, size * s_, col, a)

def orbit_at(orb, ang, squash=0.74):
    return np.array([orb * math.cos(ang), orb * squash * math.sin(ang)])

JUP = [(120, 74, 44), (214, 170, 118), (240, 214, 170)]
MARS = [(112, 52, 30), (206, 104, 60)]
VENUS = [(160, 130, 80), (240, 214, 150)]
MERC = [(90, 86, 84), (170, 164, 156)]
SATB = [(150, 120, 74), (226, 196, 130), (246, 228, 172)]
NEPT = [(30, 60, 150), (86, 140, 240)]

def draw_system(cv, O, r, sun_r, sun_stops, sun_glow, planets, orbits, home_pos, orb_col, sun_core=(255, 255, 230)):
    """planets: (orb, ang, size, kind, stops, band, tint). alpha falls away as the system shrinks into its star"""
    pa = sm(0.18, 0.5, r)
    oa = sm(0.3, 0.7, r) * (1 - sm(3, 7, r))
    if oa > 0:
        for orb in orbits:
            ring(cv, O[0], O[1], orb * r, 0.74, 1.3, np.array(orb_col, float) * oa)
    sr = max(sun_r * r, 3.2)
    soft(cv, O[0], O[1], min(sr * 1.5 + 3, 120), sun_glow, 0.42)
    soft(cv, O[0], O[1], min(sr * 3.2 + 5, 170), sun_glow, 0.07)
    ball(cv, O[0], O[1], sr, sun_stops, 0, 0, 1.0, amb=1.0)
    soft(cv, O[0], O[1], sr * 0.7, sun_core, 0.8)
    if pa <= 0: return
    for orb, ang, size, kind, stops, band, tint in planets:
        p = O + orbit_at(orb, ang) * r
        rad = max(size * r, 2.8 * pa)
        lx, ly = O[0] - p[0], O[1] - p[1]
        if kind == "ringed": saturn_ring(cv, p[0], p[1], rad, tint, False, pa)
        ball(cv, p[0], p[1], rad, stops, lx, ly, pa, bands=band, seed=orb * 0.1)
        if kind == "ringed": saturn_ring(cv, p[0], p[1], rad, tint, True, pa)

# ---- the landing: a real low-poly scene -----------------------------------------------------
class Mesh:
    def __init__(self): self.t, self.c = [], []
    def tri(self, a, b, c, col):
        self.t.append((a, b, c)); self.c.append(col)
    def quad(self, a, b, c, d, col):
        self.tri(a, b, c, col); self.tri(a, c, d, col)
    def finish(self, sun=(-0.62, 0.62, -0.48), amb=0.4):
        T = np.array(self.t, float); Col = np.array(self.c, float)
        n = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
        n = n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-6)
        fac = n @ np.array(sun)
        fac = np.where(fac > 0, fac, fac * -0.22)
        return T, np.clip(Col * (amb + 0.86 * fac[:, None]), 0, 255)

_WATER = np.array([(13, 70), (12, 25), (11, 5), (8, -10), (0, -15), (-12, -17), (-35, -22), (-110, -20)], float)
def river_dist(x, z):
    best = np.full(np.shape(x), 1e9)
    for a, b in zip(_WATER, _WATER[1:]):
        ab = b - a; q = np.clip(((x - a[0]) * ab[0] + (z - a[1]) * ab[1]) / (ab @ ab), 0, 1)
        best = np.minimum(best, np.hypot(x - a[0] - ab[0] * q, z - a[1] - ab[1] * q))
    return best

def seg_dist(x, z, a, b):
    a, b = np.array(a, float), np.array(b, float); ab = b - a
    q = np.clip(((x - a[0]) * ab[0] + (z - a[1]) * ab[1]) / (ab @ ab), 0, 1)
    return np.hypot(x - a[0] - ab[0] * q, z - a[1] - ab[1] * q)

def terrain_h(x, z):
    far = np.clip((np.hypot(x, z) - 16) / 34, 0, 1)
    h = far ** 1.5 * (1.7 * np.sin(x * 0.11 + 1) + 1.9 * np.sin(z * 0.09 + x * 0.05) + 1.0 * np.sin(z * 0.23 - x * 0.17))
    h = h + np.clip((np.hypot(x, z - 40) - 60) / 40, 0, 1) ** 2 * 26 * (0.7 + 0.3 * np.sin(x * 0.07))   # the far hills
    rd = river_dist(x, z)
    bank = np.clip((rd - 2.6) / 3.2, 0, 1)
    return h * bank + (-0.9) * (1 - bank) * (rd < 5.8)

GRASS = [(66, 142, 48), (84, 160, 54), (58, 128, 46), (98, 172, 60), (74, 150, 50)]
def _terrain(mesh, rs):
    n = 74
    u = np.linspace(-1, 1, n + 1)
    gx = 104 * (0.56 * u + 0.44 * u ** 3)
    X, Z = np.meshgrid(gx, gx + 12)
    Hh = terrain_h(X, Z)
    water = []
    for i in range(n):
        for j in range(n):
            for tri_ in (((i, j), (i, j + 1), (i + 1, j + 1)), ((i, j), (i + 1, j + 1), (i + 1, j))):
                p = [(X[a, b], Hh[a, b], Z[a, b]) for a, b in tri_]
                cx, cy, cz = np.mean(p, axis=0)
                rd = float(river_dist(np.array(cx), np.array(cz)))
                road = min(float(seg_dist(np.array(cx), np.array(cz), a, b)) for a, b in PATHS)
                if rd < 3.1:
                    continue
                col = GRASS[rs.randint(len(GRASS))]
                if rd < 4.2: col = (178, 164, 104)
                elif road < 1.25: col = (168, 136, 88)
                elif FIELDS[0][0] < cx < FIELDS[0][1] and FIELDS[0][2] < cz < FIELDS[0][3]:
                    col = ((214, 186, 74) if (i + j) % 2 else (196, 164, 62))
                elif FIELDS[1][0] < cx < FIELDS[1][1] and FIELDS[1][2] < cz < FIELDS[1][3]:
                    col = ((110, 170, 60) if i % 2 else (138, 112, 66))
                mesh.tri(p[0], p[1], p[2], col)
    # the water: tiles, chunky as the land
    step = 2.6
    for x in np.arange(-112, 112, step):
        for z in np.arange(-70, 112, step):
            if float(river_dist(np.array(x + step / 2), np.array(z + step / 2))) < 4.4:
                q = [(x, -0.32, z), (x + step, -0.32, z), (x + step, -0.32, z + step), (x, -0.32, z + step)]
                water.append((q[0], q[1], q[2])); water.append((q[0], q[2], q[3]))
    return water

PATHS = [((-2, -7), (-2, -40)), ((-2, -40), (-16, -70)), ((-2, -7), (-1, -6.5))]
FIELDS = [(-40, -18, -8, 16), (20, 44, -16, 6)]

def box(m, x0, x1, y0, y1, z0, z1, col, tile=0.0, rs=None, top=None):
    """a block; tile > 0 chops the walls into stone panels with their own tone"""
    def tone():
        return tuple(np.array(col) * (0.86 + 0.28 * rs.rand())) if rs is not None else col
    def wall(a, b):                                      # a, b ground points, in plan
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        nu = max(1, int(L / tile)) if tile else 1
        nv = max(1, int((y1 - y0) / tile)) if tile else 1
        for i in range(nu):
            for j in range(nv):
                pa = (a[0] + (b[0] - a[0]) * i / nu, a[1] + (b[1] - a[1]) * i / nu)
                pb = (a[0] + (b[0] - a[0]) * (i + 1) / nu, a[1] + (b[1] - a[1]) * (i + 1) / nu)
                ya, yb = y0 + (y1 - y0) * j / nv, y0 + (y1 - y0) * (j + 1) / nv
                m.quad((pb[0], ya, pb[1]), (pa[0], ya, pa[1]), (pa[0], yb, pa[1]), (pb[0], yb, pb[1]), tone())
    wall((x0, z0), (x1, z0)); wall((x1, z0), (x1, z1)); wall((x1, z1), (x0, z1)); wall((x0, z1), (x0, z0))
    m.quad((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), top or tone())

def prism(m, cx, cz, r, y0, y1, n, col, rs=None, top=None, r2=None, rot=0.0, tiers=1):
    r2 = r if r2 is None else r2
    for k in range(n):
        a0, a1 = rot + 2 * math.pi * k / n, rot + 2 * math.pi * (k + 1) / n
        for j in range(tiers):
            ya, yb = y0 + (y1 - y0) * j / tiers, y0 + (y1 - y0) * (j + 1) / tiers
            ra, rb = r + (r2 - r) * j / tiers, r + (r2 - r) * (j + 1) / tiers
            c = tuple(np.array(col) * (0.86 + 0.28 * rs.rand())) if rs is not None else col
            m.quad((cx + ra * math.cos(a1), ya, cz + ra * math.sin(a1)), (cx + ra * math.cos(a0), ya, cz + ra * math.sin(a0)),
                   (cx + rb * math.cos(a0), yb, cz + rb * math.sin(a0)), (cx + rb * math.cos(a1), yb, cz + rb * math.sin(a1)), c)
    if top is not None:
        for k in range(n):
            a0, a1 = rot + 2 * math.pi * k / n, rot + 2 * math.pi * (k + 1) / n
            m.tri((cx, y1, cz), (cx + r2 * math.cos(a1), y1, cz + r2 * math.sin(a1)), (cx + r2 * math.cos(a0), y1, cz + r2 * math.sin(a0)), top)

STONE = (178, 172, 160)
def crenels(m, x0, x1, z0, z1, y, step=2.2, sz=1.15, h=1.15, col=(204, 198, 184)):
    for x in np.arange(x0, x1 + 0.01, step):
        box(m, x - sz / 2, x + sz / 2, y, y + h, z0 - sz / 2, z0 + sz / 2, col)
        box(m, x - sz / 2, x + sz / 2, y, y + h, z1 - sz / 2, z1 + sz / 2, col)
    for z in np.arange(z0 + step, z1 - 0.01, step):
        box(m, x0 - sz / 2, x0 + sz / 2, y, y + h, z - sz / 2, z + sz / 2, col)
        box(m, x1 - sz / 2, x1 + sz / 2, y, y + h, z - sz / 2, z + sz / 2, col)

def tower(m, cx, cz, r, h, rs):
    prism(m, cx, cz, r, 0, h, 8, STONE, rs, top=(120, 116, 110), rot=math.pi / 8, tiers=4)
    for k in range(8):
        a = 2 * math.pi * k / 8
        x, z = cx + r * math.cos(a), cz + r * math.sin(a)
        box(m, x - 0.55, x + 0.55, h, h + 1.0, z - 0.55, z + 0.55, (204, 198, 184))

def window(m, x0, x1, y0, y1, z, col=(26, 22, 30)):
    m.quad((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z), col)

def castle(m, rs):
    s = 6.5
    box(m, -s, s, 0, 4.4, -s, s, STONE, 1.7, rs, top=(118, 114, 106))
    crenels(m, -s, s, -s, s, 4.4)
    box(m, -s + 0.8, s - 0.8, 4.4, 4.45, -s + 0.8, s - 0.8, (100, 96, 92))
    # the keep
    box(m, -3.4, 3.4, 0, 9.0, -2.4, 4.2, (190, 184, 170), 1.9, rs, top=(146, 138, 128))
    crenels(m, -3.4, 3.4, -2.4, 4.2, 9.0, 2.2, 1.15, 1.15, (210, 204, 190))
    for x in (-2.0, 0.0, 2.0):
        window(m, x - 0.55, x + 0.55, 5.8, 7.8, -2.46)
    for cx, cz in ((-s, -s), (s, -s), (-s, s), (s, s)):
        tower(m, cx, cz, 2.0, 7.4, rs)
    # the gatehouse, and a door you could walk through
    box(m, -4.0, 0.0, 0, 6.0, -s - 1.0, -s + 0.4, (186, 178, 164), 1.6, rs, top=(144, 136, 126))
    crenels(m, -4.0, 0.0, -s - 1.0, -s + 0.4, 6.0, 2.0, 1.0, 1.0, (210, 204, 190))
    window(m, -3.4, -0.6, 0, 3.9, -s - 1.06, (64, 38, 20))
    window(m, -2.6, -1.4, 0, 3.2, -s - 1.09, (20, 12, 10))
    for x in (-5.3, 2.4, 4.2):
        window(m, x - 0.45, x + 0.45, 2.0, 3.7, -s - 0.05)
    for cx, cz in ((-s, -s), (s, -s)):
        window(m, cx - 0.5, cx + 0.5, 4.2, 6.0, cz - 2.08)

def tree(m, x, z, s, rs):
    y = float(terrain_h(np.array(x), np.array(z)))
    prism(m, x, z, 0.32 * s, y, y + 1.5 * s, 4, (92, 62, 36))
    greens = [(46, 110, 38), (58, 128, 42), (38, 98, 40), (70, 138, 44)]
    c = greens[rs.randint(4)]
    rot = rs.rand()
    prism(m, x, z, 1.5 * s, y + 1.2 * s, y + 2.6 * s, 6, c, rs, r2=1.9 * s, rot=rot)
    prism(m, x, z, 1.9 * s, y + 2.6 * s, y + 3.9 * s, 6, tuple(np.array(c) * 1.1), rs, r2=1.0 * s, rot=rot)
    prism(m, x, z, 1.0 * s, y + 3.9 * s, y + 4.5 * s, 6, tuple(np.array(c) * 1.2), rs, r2=0.1 * s, rot=rot)

def cottage(m, x, z, w, d, h, rs, roof=(132, 74, 42), wall=(190, 170, 124)):
    y = float(terrain_h(np.array(x), np.array(z)))
    box(m, x - w, x + w, y, y + h, z - d, z + d, wall, 1.3, rs)
    t = y + h + w * 0.9
    m.quad((x - w - 0.3, y + h, z - d - 0.3), (x, t, z - d - 0.3), (x, t, z + d + 0.3), (x - w - 0.3, y + h, z + d + 0.3), roof)
    m.quad((x + w + 0.3, y + h, z - d - 0.3), (x + w + 0.3, y + h, z + d + 0.3), (x, t, z + d + 0.3), (x, t, z - d - 0.3), tuple(np.array(roof) * 0.82))
    m.tri((x - w, y + h, z - d - 0.01), (x + w, y + h, z - d - 0.01), (x, t, z - d - 0.01), wall)
    window(m, x - 0.45, x + 0.45, y, y + 1.3, z - d - 0.04, (54, 34, 22))

def bridge(m, rs):
    x0, x1 = -3.2, -0.8
    z = [-20.5, -18.9, -17.3, -15.7, -14.1, -12.5, -10.9]
    y = [0.0, 0.8, 1.35, 1.55, 1.35, 0.8, 0.0]
    for i in range(6):
        c = tuple(np.array((226, 214, 186)) * (0.9 + 0.2 * rs.rand()))
        m.quad((x0, y[i], z[i]), (x1, y[i], z[i]), (x1, y[i + 1], z[i + 1]), (x0, y[i + 1], z[i + 1]), c)
        for xa, xb in ((x0 - 0.45, x0), (x1, x1 + 0.45)):
            top = 0.95
            m.quad((xa, y[i] + top, z[i]), (xb, y[i] + top, z[i]), (xb, y[i + 1] + top, z[i + 1]), (xa, y[i + 1] + top, z[i + 1]), (176, 170, 158))
            for xw in (xa, xb):
                m.quad((xw, y[i] - 1.1, z[i]), (xw, y[i + 1] - 1.1, z[i + 1]), (xw, y[i + 1] + top, z[i + 1]), (xw, y[i] + top, z[i]), (138, 132, 122))

def clear_of_things(x, z):
    if abs(x) < 10 and abs(z) < 11: return False
    if float(river_dist(np.array(x), np.array(z))) < 6: return False
    if min(float(seg_dist(np.array(x), np.array(z), a, b)) for a, b in PATHS) < 3: return False
    if (FIELDS[0][0] - 1 < x < FIELDS[0][1] + 1 and FIELDS[0][2] - 1 < z < FIELDS[0][3] + 1): return False
    if (FIELDS[1][0] - 1 < x < FIELDS[1][1] + 1 and FIELDS[1][2] - 1 < z < FIELDS[1][3] + 1): return False
    return True

class Scene3D:
    SS = 3
    def __init__(self):
        rs = np.random.RandomState(9001)
        ground_ = Mesh()
        water = _terrain(ground_, rs)
        self.ground, self.ground_c = ground_.finish(amb=0.6)
        self.water = np.array(water, float)
        wr = np.random.RandomState(9002)
        self.water_phase = wr.rand(len(water)) * 6.28
        self.water_tone = 0.86 + 0.28 * wr.rand(len(water))
        m = Mesh()
        castle(m, rs); bridge(m, rs)
        for x, z, w, d, h in ((24, -28, 2.6, 2.0, 2.4), (29, -21, 2.0, 2.6, 2.2), (-19, -30, 2.3, 1.9, 2.2)):
            cottage(m, x, z, w, d, h, rs)
        placed = 0
        tr = np.random.RandomState(9003)
        while placed < 120:
            x, z = (tr.rand() - 0.5) * 150, tr.rand() * 130 - 44
            if not clear_of_things(x, z): continue
            near = math.hypot(x, z) < 26
            if near and tr.rand() < 0.5: continue
            tree(m, x, z, 0.9 + 0.5 * tr.rand(), tr); placed += 1
        for x, z in ((-17, -27), (-20, -25), (12, -26), (15, -23), (-14, -10), (-16, 3), (-13, 12), (15, -3), (-5, 16), (6, 17), (18, 12)):
            tree(m, x, z, 1.05, tr)
        self.solid, self.solid_c = m.finish()
        sky = ramp([(84, 150, 226), (126, 186, 238), (196, 224, 238)], np.clip(np.linspace(0, 1, H * self.SS)[:, None] * 2.4, 0, 1))
        self.sky = np.repeat(sky[:, None, :], W * self.SS, axis=1).reshape(H * self.SS, W * self.SS, 3)
        self.flags = [(-6.5, 7.4, -6.5), (6.5, 7.4, -6.5), (-6.5, 7.4, 6.5), (6.5, 7.4, 6.5), (0, 10.0, 1)]

    def project(self, T, E, Fw, Rt, Up, f):
        rel = T - E
        d = rel @ Fw
        x = W / 2 + f * (rel @ Rt) / np.maximum(d, 0.01)
        y = H / 2 - f * (rel @ Up) / np.maximum(d, 0.01)
        return x, y, d

    def render(self, E, Tg, f, t, frame_seed=0):
        E, Tg = np.array(E, float), np.array(Tg, float)
        Fw = Tg - E; Fw /= np.linalg.norm(Fw)
        ref = np.array((0.0, 1.0, 0.0)) if abs(Fw[1]) < 0.97 else np.array((0.0, 0.0, 1.0))
        Rt = np.cross(ref, Fw); Rt /= np.linalg.norm(Rt)
        Up = np.cross(Fw, Rt)
        S = self.SS
        im = Image.fromarray(self.sky_for(E, Tg, f).astype(np.uint8))
        d_ = ImageDraw.Draw(im)
        haze = np.array((184.0, 216, 232))
        def emit_(T, Col, sort=True):
            x, y, dd = self.project(T, E, Fw, Rt, Up, f)
            ok = (dd > 0.6).all(1) & (x.max(1) > -20) & (x.min(1) < W + 20) & (y.max(1) > -20) & (y.min(1) < H + 20)
            x, y, dd, Col2 = x[ok], y[ok], dd[ok], Col[ok]
            depth = dd.mean(1)
            fog = np.clip((depth - 55) / 110, 0, 0.82)[:, None]
            Col2 = np.clip(Col2 * (1 - fog) + haze * fog, 0, 255).astype(int)
            order = np.argsort(-depth) if sort else np.arange(len(depth))
            xs, ys = x * S, y * S
            for i in order:
                pts = [(xs[i, 0], ys[i, 0]), (xs[i, 1], ys[i, 1]), (xs[i, 2], ys[i, 2])]
                c = tuple(Col2[i])
                d_.polygon(pts, fill=c, outline=c)
        wc = np.array((50.0, 124, 202))[None] * (self.water_tone * (1 + 0.1 * np.sin(self.water_phase + t * 2 * math.pi * 14)))[:, None]
        emit_(np.concatenate([self.ground, self.water]), np.concatenate([self.ground_c, wc]))
        flags = Mesh()
        for k, (x, y, z) in enumerate(self.flags):
            flags.quad((x - 0.07, y, z), (x + 0.07, y, z), (x + 0.07, y + 2.6, z), (x - 0.07, y + 2.6, z), (90, 66, 44))
            flap = [0.0, 0.45 * math.sin(t * 2 * math.pi * 16 + k), -0.2 + 0.4 * math.sin(t * 2 * math.pi * 16 + k + 1.3)]
            for j in range(2):
                xa, xb = x + j * 0.85, x + (j + 1) * 0.85
                col = (38, 80, 200) if j == 0 else (48, 96, 222)
                flags.quad((xa, y + 1.4, z + flap[j]), (xb, y + 1.4, z + flap[j + 1]), (xb, y + 2.6, z + flap[j + 1]), (xa, y + 2.6, z + flap[j]), col)
        fT, fC = np.array(flags.t, float), np.array(flags.c, float)
        emit_(np.concatenate([self.solid, fT]), np.concatenate([self.solid_c, fC]))
        return np.asarray(im.resize((W, H), Image.LANCZOS), float)

    def sky_for(self, E, Tg, f):
        """sky gradient from the true horizon line down the frame"""
        Fw = (np.array(Tg, float) - np.array(E, float)); Fw /= np.linalg.norm(Fw)
        pitch = math.asin(max(-1, min(1, Fw[1])))
        hor = H / 2 + f * math.tan(pitch) if abs(pitch) < 1.4 else -1e4
        yy = (np.arange(H * self.SS) / self.SS - hor) / -46
        v = np.clip(yy, 0, 1)[:, None]
        col = np.array((176.0, 216, 244)) * (1 - v) + np.array((44.0, 104, 216)) * v
        return np.repeat(col[:, None, :], W * self.SS, axis=1)

# ---- the recipe ----------------------------------------------------------------------------
def flask_voyage():
    earth = earth_globe(); gili = gielinor_globe()
    rs = np.random.RandomState(7001)

    # the two galaxies: a cool barred one, and a rose one with three tight arms, seen tilted
    milky = Galaxy(7010, 2, 0.30, 0.86, 0.35, 3.0, ((150, 190, 255), (70, 90, 150)), (255, 220, 150), (205, 225, 255), bar=0.9, dust=0.6, minor=0.25)
    rose = Galaxy(7020, 3, 0.25, 0.6, -0.62, 1.7, ((255, 96, 150), (150, 50, 110)), (255, 190, 120), (90, 250, 210), dust=0.45)

    # the anchors, each child in its parent's canonical pixels
    earth_ang = math.radians(42)
    a0 = orbit_at(40, earth_ang)
    a1 = np.array([22.0, -14.0])                                # the Sun in its cluster
    a2 = milky.anchor(0.62, (0.8, 0.55))                        # the cluster on an arm
    a3 = np.array([-46.0, 58.0])                                # the Milky Way, down in the corner of the void
    a4 = np.array([52.0, -64.0])                                # the other one, up in the far corner
    a5 = rose.anchor(0.6, (-0.75, 0.6))
    a6 = np.array([-18.0, 16.0])
    home2_ang = math.radians(150)
    a7 = orbit_at(46, home2_ang)
    A = [a0, a1, a2, a3, a4, a5, a6, a7]

    cluster1 = Cluster(7030, 24, 88, [(190, 215, 255), (160, 195, 255), (235, 240, 255), (255, 225, 170)], a1)
    cluster2 = Cluster(7040, 30, 80, [(255, 170, 110), (255, 130, 110), (255, 210, 150), (140, 240, 215)], a6)
    arm1 = milky.sample_near(a2[0], a2[1], 42, 7050, 34)
    arm2 = rose.sample_near(a5[0], a5[1], 42, 7060, 32)

    # the planets. Earth is painted by its globe; everything else by ball
    solar = [(24, 2.5, 3.2, "ball", VENUS, 0, None),
             (51, 4.9, 3.0, "ball", MARS, 0, None), (66, 2.75, 9.5, "ball", JUP, 0.7, None),
             (84, 5.55, 7.0, "ringed", SATB, 0.3, (232, 208, 146))]
    other = [(26, 0.9, 3.4, "ball", [(90, 40, 100), (220, 120, 210)], 0, None),
             (66, 5.2, 10.5, "ball", [(50, 50, 130), (150, 110, 230), (225, 195, 250)], 0.8, None),
             (84, 2.2, 6.0, "ringed", [(30, 110, 120), (130, 225, 215)], 0, (160, 245, 225))]
    orbits1 = [40, 84]
    orbits2 = [46, 84]
    SUN = [(250, 150, 40), (255, 210, 80), (255, 244, 170)]
    RED = [(220, 70, 40), (255, 130, 70), (255, 200, 130)]

    # small far galaxies for the void
    small = [(-82, -74, 10, 0.5, 0.5, (190, 200, 255)), (78, 66, 12, 0.7, -0.5, (255, 216, 170)), (-78, 12, 7, 0.4, 0.9, (170, 240, 220)),
             (10, 10, 8, 0.42, 0.3, (240, 190, 240)), (-6, -92, 6, 0.8, 0.0, (220, 225, 255)), (92, -10, 6, 0.5, 1.2, (200, 220, 255)), (18, 92, 7, 0.45, -0.9, (200, 220, 255))]
    flow = [(rs.rand(), rs.rand() * 2 * math.pi, 2.0 + 0.9 * rs.rand(), [(200, 220, 255), (255, 235, 200), (235, 240, 255)][rs.randint(3)]) for _ in range(9)]

    def space(f, t):
        Z = zoom_at(f)
        O, r = origins(Z, f, A)
        cv = np.tile(SPACE_, (H, W, 1)).astype(float)
        # far stars rushing past, in log space, so they read as travel at every scale
        fa = 1 - sm(9.3, 10.4, Z)
        if fa > 0:
            for p, ang, sz, c in flow:
                ph = (p - Z * 0.5) % 1
                rad = 16 * math.exp(ph * 2.5)
                aa = min(1.0, math.sin(math.pi * ph) * 4) * fa
                star(cv, C[0] + rad * math.cos(ang), C[1] + rad * 1.1 * math.sin(ang), sz, c, aa * 0.62)
        # the void's other galaxies
        rv = r[4]
        va = sm(9.0, 10.3, Z)
        if va > 0:
            for x, y, R, sq, rot, col in small:
                px, py = O[4][0] + x * rv, O[4][1] + y * rv
                p = patch(px, py, R * rv * 2.2 + 4)
                if not p: continue
                x0, x1, y0, y1 = p
                dx, dy = XX[y0:y1, x0:x1] - px, YY[y0:y1, x0:x1] - py
                u, v = dx * math.cos(rot) + dy * math.sin(rot), (-dx * math.sin(rot) + dy * math.cos(rot)) / sq
                d = np.hypot(u, v) / (R * rv)
                cv[y0:y1, x0:x1] += (np.exp(-(d * 2.3) ** 2) * 1.05 + np.exp(-d * 2.6) * 0.32)[..., None] * np.array(col, float) * va

        # the arm and the galaxy, as bitmaps
        for g, lv, stars_, palette in ((milky, 3, arm1, ((170, 205, 255), (235, 240, 255), (255, 220, 170))), (rose, 5, arm2, ((255, 140, 180), (255, 200, 200), (120, 245, 215)))):
            R = 95 * r[lv]
            if R < 2 or R > 2600: continue
            ga = 1 - sm(760, 1500, R)
            if 120 < R:                                   # close to an arm, the bitmap is soft: push its contrast
                layer = np.zeros_like(cv); g.blit(layer, O[lv][0], O[lv][1], R, 1.0)
                k = sm(130, 300, R)
                layer = layer * (1 - k) + k * 255 * (layer / 255) ** 1.7 * 1.45
                cv += layer * ga
            else:
                g.blit(cv, O[lv][0], O[lv][1], R, ga)
            sa = sm(1.6, 2.6, r[lv]) * (1 - sm(9, 15, r[lv]))
            if sa > 0:
                for i, (x, y, q) in enumerate(stars_):
                    star(cv, O[lv][0] + x * r[lv], O[lv][1] + y * r[lv], 1.9 + 1.2 * q, palette[i % 3], sa)
        # the clusters, condensing into a knot on the arm
        for c, lv, col in ((cluster1, 2, (215, 228, 255)), (cluster2, 6, (255, 196, 150))):
            rc = r[lv]
            if rc > 40: continue
            a_ = sm(0.3, 0.62, rc)
            if a_ > 0: c.draw(cv, O[lv], rc, a_)
            if rc < 1.4:
                soft(cv, O[lv][0], O[lv][1], max(2.4, 16 * rc), col, 0.22 + 0.5 * (1 - a_))
        # the two solar systems
        rs_ = r[1]
        if rs_ > 0.012 and f < PEAK:
            draw_system(cv, O[1], rs_, 8, SUN, (255, 196, 90), solar, orbits1, None, (88, 100, 128))
            pa = sm(0.18, 0.5, rs_)
            Re = 78 * r[0]
            if pa > 0:
                if Re < 5:
                    ball(cv, O[0][0], O[0][1], max(Re, 3.8 * pa), [(30, 100, 210), (70, 170, 255), (130, 220, 190)], -0.7, -0.6, pa, amb=0.7)
                else:
                    earth.paint(cv, O[0][0], O[0][1], Re, math.radians(16 + 14 * f / 20), cloud=0.42, alpha=pa)
        rs2 = r[7]
        if rs2 > 0.012 and f >= PEAK:
            draw_system(cv, O[7], rs2, 15, RED, (255, 120, 70), other, orbits2, None, (120, 84, 92), sun_core=(255, 235, 190))
            pa = sm(0.18, 0.5, rs2)
            Rg = 82 * r[8]
            lx, ly = O[7][0] - O[8][0], O[7][1] - O[8][1]
            n_ = math.hypot(lx, ly) or 1
            lx, ly = lx / n_, ly / n_
            if pa > 0:
                if Rg < 2500 and Rg >= 0:
                    light = (lx * 0.7, ly * 0.7, 0.6) if Rg < 30 else (-0.62, -0.5, 0.6)
                    if Rg < 30:
                        k = sm(12, 30, Rg)
                        light = (lx * 0.7 * (1 - k) - 0.62 * k, ly * 0.7 * (1 - k) - 0.5 * k, 0.6)
                    gili.paint(cv, O[8][0], O[8][1], max(Rg, 3.4 * pa), gili.L0 + 0.28 - 0.28 * sm(112, 134, f), cloud=0.3 + 0.5 * sm(122, 134, f), light=light, alpha=pa)
        return cv

    scene3d = Scene3D()
    cloud_a = np.asarray(Image.fromarray((np.clip(0.55 * _big_noise(7201, 60, 1000) + 0.3 * _big_noise(7202, 24, 1000) + 0.15 * _big_noise(7203, 9, 1000), 0, 1) * 255).astype(np.uint8)), float)

    def cloud_layer(cv, f, cover):
        if cover <= 0: return cv
        z = 0.2 + 0.72 * sm(126, 146, f)
        hw, hh = 170 * (1 - z) + 62, 190 * (1 - z) + 70
        im = Image.fromarray(cloud_a.astype(np.uint8)).resize((W, H), Image.BICUBIC, box=(500 - hw, 500 - hh, 500 + hw, 500 + hh))
        n = (np.asarray(im, float) / 255 - 0.28) / 0.44
        al = np.clip((n - (1 - cover * 1.55)) * 3.2, 0, 1)[..., None]
        col = np.array((226.0, 234, 244)) * (0.74 + 0.34 * np.clip(n, 0, 1))[..., None]
        return cv * (1 - al) + col * al

    E0, T0 = np.array((0.0, 118.0, -14.0)), np.array((0.0, 0.0, -2.0))
    E1, T1 = np.array((-8.5, 10.5, -35.0)), np.array((-0.7, 4.0, -6.0))

    def landing(f, t):
        v = sm(133, 152, f)
        v = 1 - (1 - v) ** 1.6
        drift = sm(150, 168, f)
        # the log of height falls evenly: the descent keeps the same pace as the rest of the film
        hE = math.exp(math.log(E0[1]) * (1 - v) + math.log(E1[1]) * v)
        q = (E0[1] - hE) / (E0[1] - E1[1])
        E = E0 * (1 - q) + E1 * q
        E[1] = hE
        Tg = T0 * (1 - q) + T1 * q
        E = E + np.array((1.2 * drift, -0.2 * drift, 0.6 * drift))
        img = scene3d.render(E, Tg, 232, t)
        lum = img.mean(2, keepdims=True)
        return np.clip(lum + (img - lum) * 1.35, 0, 255)

    def scene(t):
        f = t * N
        if f < 136.5:
            cv = space(f, t)
        else:
            cv = landing(f, t)
        cover = sm(127.5, 135, f) * (1 - sm(137, 145, f))
        cv = cloud_layer(cv, f, cover)
        if f < 5:
            cv = BG + (cv - BG) * sm(0, 5, f)
        if f > 163:
            cv = BG + (cv - BG) * (1 - sm(163, 167.5, f))
        return cv

    return ("flask", "the long way home", scene, N)

RECIPES = [flask_voyage]
