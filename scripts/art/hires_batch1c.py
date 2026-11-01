"""Hi-res recipes, batch 1c: Callisto, Venenatis, Scorpia, Sol Heredit, Phantom Muspah, K'ril."""
from hires_kit import *
from hires_kit import _layer

# ---- shared helpers ------------------------------------------------------------
def smooth(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)

def bump(a, b, x):
    return math.sin(math.pi * (x - a) / (b - a)) if a < x < b else 0.0

def since_(t, t0):
    return t - t0 if t >= t0 else t + 1 - t0

def land(rig, pt, bend, n=5):
    """where a rig.at point ends up once the bend is applied (bend samples backwards)"""
    x, y = pt
    if bend is None: return x, y
    px, py = x, y
    for _ in range(n):
        bx, by = bend(px / rig.w, py / rig.h)
        px, py = x - float(bx), y - float(by)
    return px, py

def poly_mask(w, h, pts):
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).polygon([(x * w, y * h) for x, y in pts], fill=255)
    return np.asarray(im, float) / 255

def px_poly(size, pts):
    return [(x / size[0], y / size[1]) for x, y in pts]

def split(rgb, al, polys, keep=()):
    """A rigid layer lifted off the sprite. The rest keeps the alpha it had outside
    the cut, plus any 'keep' polygons, whose hidden pixels are grown in from around them."""
    h, w = al.shape[:2]
    m = np.zeros((h, w))
    for p in polys: m = np.maximum(m, poly_mask(w, h, p))
    k = np.zeros((h, w))
    for p in keep: k = np.maximum(k, poly_mask(w, h, p))
    front = al * m[..., None]
    back = al * np.maximum(1 - m, k)[..., None]
    known = (al[..., 0] > 0) & (m < 0.5)
    col = rgb * known[..., None]
    for _ in range(60):
        if known.all(): break
        n = sum(np.roll(known, s, a).astype(float) for s in (-1, 1) for a in (0, 1))
        c = sum(np.roll(col, s, a) for s in (-1, 1) for a in (0, 1))
        grow = (~known) & (n > 0)
        col[grow] = c[grow] / n[grow][..., None]
        known = known | grow
    back_rgb = np.where(m[..., None] > 0.5, col, rgb)
    return (back_rgb, back), (rgb, front)

def strands(segs, width=1):
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    for s in segs: d.line(s, fill=255, width=width)
    return np.asarray(im, float) / 255

def scroll_up(seed, cell, t, k=2, kx=0):
    """noise climbing the frame; k whole screens per lap, so it loops"""
    n = _layer(seed, cell)
    return n[(YY.astype(int) + int(t * H * k)) % H, (XX.astype(int) + int(t * W * kx)) % W]

def _stretch(n):
    return n[(YY.astype(int) // 3) % H, XX.astype(int)]   # flames are taller than wide

def scorpling(cv, x, y, s, face, t, ph, shell, band, tipc, n=4):
    """A small add drawn in bold strokes, since a shrunk sprite dissolves at this cell size:
    three scuttling legs a side, opening claws, an arched tail that flicks."""
    step = 2 * math.pi * (t * n + ph)
    x, y = float(x), float(y)
    legs, tail = [], []
    for i in range(4):
        hip = x + face * (i - 1.5) * 1.7 * s
        sw = math.sin(step + i * 2.1)
        legs.append(((hip, y + s * 0.6), (hip + sw * 1.6 * s, y + s * 1.6), (hip + sw * 2.6 * s, y + s * (3.4 - 1.2 * max(0.0, -math.cos(step + i * 2.1))))))
    nip = 0.5 + 0.5 * math.sin(step * 0.5 + 1)
    hx = x + face * 3.4 * s
    claw = [((hx, y), (hx + face * 3.0 * s, y - (0.8 + nip) * s)), ((hx, y), (hx + face * 3.0 * s, y + (0.8 + nip) * s))]
    flick = math.sin(step * 0.5)
    rx = x - face * 3.2 * s
    pts = [(rx, y)]
    for f in (0.3, 0.6, 0.85, 1.0, 1.1):
        a = f * (2.6 + 0.5 * flick)
        pts.append((rx - face * 2.6 * s * math.sin(a), y - 2.9 * s * (1 - math.cos(a)) * 0.82))
    tip = pts[-1]
    tail.append(pts)
    m = strands([list(l) for l in legs] + claw, max(1, int(round(s))))[..., None]
    cv[:] = cv * (1 - m) + np.array(shell, float) * 0.8 * m
    m = strands(tail, max(2, int(round(s * 1.3))))[..., None]
    cv[:] = cv * (1 - m) + np.array(shell, float) * m
    bd = np.clip((1 - ((XX - x) / (3.9 * s)) ** 2 - ((YY - y) / (1.75 * s)) ** 2) * 4, 0, 1)[..., None]
    stripe = (0.5 + 0.5 * np.sign(np.sin((XX - x) * 1.1 / s)))[..., None]
    cv[:] = cv * (1 - bd) + (np.array(shell, float) * stripe + np.array(band, float) * (1 - stripe)) * bd
    stamp(cv, tip[0], tip[1], tipc, 1.6 + s * 0.5)
    return hx, y, tip

def tooth(t, n):
    """a dart and a pause rather than an even sway; loops with n whole cycles"""
    a = 2 * math.pi * t * n
    return math.sin(a + 0.55 * math.sin(a * 2))

def tint(rgb, x, y, rx, ry, colour, power, rig):
    """a light in the scene landing on the model"""
    d = np.exp(-((rig.xx - rig.pad - x) / rx) ** 2 - ((rig.yy - rig.pad - y) / ry) ** 2)[..., None]
    return rgb + d * np.array(colour, float) * power

# ---- backdrops -----------------------------------------------------------------
def den_backdrop():
    """Callisto's den: warm broken rock, a cave mouth on a dusk-red Wilderness sky, old bones"""
    tex = fbm(401, (26, 11, 5, 2))
    rock = np.array([44.0, 36, 30]) * (0.55 + 0.75 * tex[..., None])
    sky = ramp([(52, 30, 30), (66, 36, 28), (40, 28, 30)], np.clip(YY / 110, 0, 1))
    gap = 1 - ((XX - 62 + 12 * (fbm(404, (14, 6)) - 0.5) * 3) / 52) ** 2 - ((YY - 82 + 12 * (fbm(407, (10, 5)) - 0.5) * 3) / 56) ** 2
    mouth = np.clip(gap * 8, 0, 1)[..., None]
    hills = (YY > ridge(410, 118, 12))[..., None]
    outside = sky * (1 - hills) + np.array([24.0, 20, 22]) * hills
    img = rock * (1 - mouth) + outside * mouth
    rim = np.clip(1 - np.abs(gap) * 7, 0, 1)[..., None]
    img = img + rim * np.array([18.0, 8, 4])
    ground = np.clip((YY - 172) / 8, 0, 1)[..., None]
    floor = np.array([50.0, 42, 34]) * (0.6 + 0.7 * fbm(420, (18, 6, 3))[..., None]) * (0.75 + np.clip((YY - 172) / 90, 0, 1)[..., None] * 0.6)
    img = img * (1 - ground) + floor * ground
    bones = strands([((20, 205), (46, 197)), ((36, 212), (58, 215)), ((170, 196), (196, 204)), ((186, 214), (205, 208)), ((12, 222), (30, 226))], 2)
    return img + bones[..., None] * np.array([34.0, 32, 26])

def web_backdrop():
    """Venenatis's cave: a cold violet hollow, hung with old web and egg sacs"""
    rock = np.array([34.0, 28, 40]) * (0.5 + 0.8 * fbm(501, (24, 10, 4))[..., None])
    arch = np.clip(1 - ((XX - W / 2) / 96) ** 2 - ((YY - 110) / 112) ** 2, 0, 1)[..., None]
    img = rock * (0.75 - 0.4 * arch) + arch * np.array([16.0, 16, 24])
    ground = np.clip((YY - 168 + 6 * (fbm(510, (24, 8)) - 0.5)) / 6, 0, 1)[..., None]
    img = img * (1 - ground) + np.array([40.0, 36, 44]) * (0.55 + 0.8 * fbm(515, (14, 5, 2))[..., None]) * ground
    segs = []
    for cx, cy, rad, n, seed in ((12, 12, 62, 7, 1), (W - 10, 8, 70, 8, 2), (W - 6, 150, 44, 6, 3), (6, 142, 40, 6, 4)):
        r = np.random.RandomState(520 + seed)
        spokes = [(cx + rad * math.cos(a), cy + rad * math.sin(a)) for a in np.linspace(0, 2 * math.pi, n + 8) + r.rand() * 0.4]
        for p in spokes: segs.append(((cx, cy), p))
        for k in (0.35, 0.6, 0.85):
            pts = [(cx + (p[0] - cx) * k, cy + (p[1] - cy) * k + 3 * k) for p in spokes]
            segs.append(pts + pts[:1])
    img = img + strands(segs)[..., None] * np.array([40.0, 40, 48])
    for x, y, s in ((26, 92, 8), (34, 106, 6), (W - 28, 78, 7), (W - 40, 96, 5)):
        sac = np.clip(1 - ((XX - x) / s) ** 2 - ((YY - y) / (s * 1.3)) ** 2, 0, 1)[..., None] ** 0.5
        img = img * (1 - sac) + sac * np.array([58.0, 56, 60]) * (0.6 + 0.6 * fbm(530, (5, 2))[..., None])
    return img

def pit_backdrop():
    """Scorpia's pit: curved earth walls ringing a sunken floor, poison-stained"""
    wall_r = np.sqrt(((XX - W / 2) / 150) ** 2 + ((YY - 250) / 196) ** 2)
    bands = 0.5 + 0.5 * np.sin(wall_r * 52 + fbm(601, (30, 12)) * 7)
    wall = np.array([46.0, 40, 28]) * (0.5 + 0.35 * bands[..., None] + 0.5 * fbm(605, (16, 6, 3))[..., None])
    wall = wall * np.clip(0.55 + (YY[..., None] / 200) * 0.6, 0, 1)
    top = (wall_r > 1.0 + 0.05 * (fbm(610, (24, 9)) - 0.5))[..., None]
    img = wall * (1 - top) + np.array([18.0, 18, 20]) * top
    floor = np.clip((YY - 178 + 8 * (fbm(615, (20, 8)) - 0.5)) / 5, 0, 1)[..., None]
    img = img * (1 - floor) + np.array([54.0, 50, 34]) * (0.55 + 0.7 * fbm(620, (12, 5, 2))[..., None]) * floor
    stain = np.clip(fbm(625, (18, 7)) * 3 - 1.5, 0, 1)[..., None] * floor
    return img + stain * np.array([0.0, 20, 2])

def colosseum_backdrop():
    """the Colosseum at Varlamore: two tiers of arches round the sand, in low sun"""
    wall = np.array([66.0, 52, 40]) * (0.55 + 0.7 * fbm(701, (20, 8, 3))[..., None])
    wall = wall * (1 - 0.28 * ((YY.astype(int) % 14) < 2)[..., None])
    img = wall.copy()
    for y0, y1, spacing, width, off in ((22, 74, 44, 15, 6), (84, 150, 56, 19, 28)):
        xc = (XX - off) % spacing - spacing / 2
        head = y0 + width
        m = ((np.abs(xc) < width) & (YY > head) & (YY < y1)) | ((xc ** 2 + (YY - head) ** 2 < width ** 2) & (YY <= head))
        void = np.array([20.0, 16, 18]) * (0.8 + 0.5 * (YY - y0)[..., None] / (y1 - y0))
        rim = np.clip(1 - np.abs(np.sqrt(xc ** 2 + (np.minimum(YY - head, 0)) ** 2) - width) / 2.5, 0, 1)[..., None]
        img = img * (1 - m[..., None]) + void * m[..., None] + rim * (YY < y1)[..., None] * (YY > y0)[..., None] * 14
    ledge = (np.abs(YY - 78) < 3)[..., None]
    img = img * (1 - ledge) + np.array([78.0, 64, 48]) * ledge
    sand = np.clip((YY - 158 + 3 * (fbm(710, (26, 8)) - 0.5)) / 4, 0, 1)[..., None]
    ripple = 0.5 + 0.5 * np.sin(YY * 0.9 + 6 * fbm(715, (18, 7)))
    sanded = np.array([84.0, 66, 42]) * (0.68 + 0.2 * ripple[..., None] + 0.3 * fbm(720, (8, 3))[..., None]) * (0.82 + np.clip((YY - 158) / 82, 0, 1)[..., None] * 0.2)
    return img * (1 - sand) + sanded * sand

def ghorrock_backdrop():
    """the Ghorrock dungeon: faceted blue ice columns in a closed dark vault"""
    r = np.random.RandomState(801)
    seeds = np.c_[r.rand(46) * W, r.rand(46) * H]
    d = ((XX[..., None] - seeds[:, 0]) * 1.5) ** 2 + ((YY[..., None] - seeds[:, 1]) * 0.45) ** 2
    idx = d.argmin(2); srt = np.sort(d, 2)
    face = r.rand(46)[idx]
    seam = np.clip(1 - (np.sqrt(srt[..., 1]) - np.sqrt(srt[..., 0])) / 2.2, 0, 1)
    img = np.array([28.0, 42, 58]) * (0.5 + 0.9 * face[..., None]) * (0.85 + 0.3 * fbm(805, (10, 4))[..., None])
    img = img + seam[..., None] * np.array([12.0, 26, 34])
    vault = np.clip(((YY - 120) / 150) ** 2 + ((XX - W / 2) / 180) ** 2, 0, 1)[..., None]
    img = img * (0.55 + 0.55 * vault)
    # stalactites
    drip = 16 + 44 * np.abs(np.sin(XX * 0.13 + 2.4 * np.sin(XX * 0.037))) ** 4
    top = (YY < drip)[..., None]
    img = img * (1 - top) + np.array([44.0, 66, 84]) * (0.5 + 0.5 * np.clip((drip - YY) / 40, 0, 1)[..., None]) * top
    floor = np.clip((YY - 204 + 3 * (fbm(810, (20, 7)) - 0.5)) / 3, 0, 1)[..., None]
    return img * (1 - floor) + floor * np.array([48.0, 66, 80]) * (0.55 + 0.7 * fbm(815, (14, 5))[..., None])

def chamber_backdrop():
    """Zamorak's chamber: scorched black-violet stone, his star dim on the wall, a burning pit"""
    course, brick = 22, 38
    row = (YY // course).astype(int); off = (row % 2) * brick // 2
    col = ((XX + off) // brick).astype(int)
    shade = np.random.RandomState(901).rand(40, 40)[row % 40, col % 40]
    mortar = (YY % course < 2) | ((XX + off) % brick < 2)
    stone = (0.55 + 0.6 * shade) * (0.6 + 0.7 * fbm(905, (20, 8, 3)))
    img = np.array([34.0, 22, 34]) * stone[..., None] * (1 - 0.6 * mortar[..., None])
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    cx, cy = W / 2, 72
    for i in range(3):                                                    # his three-pointed star
        a = math.radians(-90 + 120 * i)
        tip = (cx + 72 * math.cos(a), cy + 72 * math.sin(a))
        l = (cx + 24 * math.cos(a + 1.75), cy + 24 * math.sin(a + 1.75))
        r = (cx + 24 * math.cos(a - 1.75), cy + 24 * math.sin(a - 1.75))
        d.polygon([tip, l, r], fill=255)
    star = (np.asarray(im, float) / 255)[..., None]
    img = img * (1 - star) + star * np.array([58.0, 14, 16]) * (0.7 + 0.5 * fbm(910, (10, 4))[..., None])
    floor = np.clip((YY - 196) / 3, 0, 1)[..., None]
    return img * (1 - floor) + floor * np.array([34.0, 26, 30]) * (0.55 + 0.8 * fbm(915, (14, 5, 2))[..., None])

# ---- recipes -------------------------------------------------------------------
def foe(cv, x, y, s, spin, stride, brace=0.0):
    """A small adventurer in bold strokes, for the bear to throw. spin is radians about the hips."""
    ca, sa = math.cos(spin), math.sin(spin)
    def P(px, py): return (x + s * (ca * px - sa * py), y + s * (sa * px + ca * py))
    legs = [[P(0, 0), P(1.5 * stride, 2.3), P(2.4 * stride, 4.6)], [P(0, 0), P(-1.5 * stride, 2.3), P(-2.4 * stride, 4.6)]]
    m = strands(legs, max(2, int(round(s))))[..., None]
    cv[:] = cv * (1 - m) + np.array([88.0, 92, 118]) * m
    m = strands([[P(0, 0), P(0.2, -5)]], int(round(s * 2.4)))[..., None]
    cv[:] = cv * (1 - m) + np.array([150.0, 156, 190]) * m
    m = strands([[P(0.2, -4.2), P(3.2, -3.2 - 3.4 * brace), P(7.4, -5.4 - 4 * brace)]], max(2, int(round(s))))[..., None]
    cv[:] = cv * (1 - m) + np.array([225.0, 230, 240]) * m
    hx, hy = P(0.2, -6.6)
    d = np.clip((1 - ((XX - hx) ** 2 + (YY - hy) ** 2) / (1.7 * s) ** 2) * 4, 0, 1)[..., None]
    cv[:] = cv * (1 - d) + np.array([205.0, 170, 140]) * d

def callisto_roar():
    rgb0, al0 = fit(sprite("Callisto.png"), W * 0.88, H, exposure=0.78, gamma=1.0, sat=1.1)
    h, w = al0.shape[:2]; left = 24; ground = H - 12; top = ground - h
    rig = Rig(rgb0, al0, pad=70)
    lum0 = rig.rgb.mean(2)
    # the dark bands of his coat are where the rage shows
    bands = (np.clip((70 - lum0) / 40, 0, 1) * (rig.al[..., 0] > 0))[..., None]
    pale = np.clip((lum0 - 165) / 50, 0, 1)[..., None]
    back = den_backdrop()
    sky_only = (YY < 168)
    falling = Motes(11, 34, lambda r: (r.rand() * W, 2, -30 * r.rand(), 60, 170))
    blown = Motes(13, 30, lambda r: (70 + r.rand() * 60, 120 + r.rand() * 104, -210 - 120 * r.rand(), -30 + 60 * r.rand(), 30))
    dust = Motes(12, 60, lambda r: (left + w * (0.02 + 0.5 * r.rand()), ground, (r.rand() - 0.5) * 170, -46 * r.rand(), 40))
    slam_at, flung = 0.42, 0.45
    def scene(t):
        # he gathers his whole weight back and up, drops it, and roars with the head thrust low
        rear = smooth(0.04, 0.36, t) * (1 - smooth(0.37, slam_at, t))
        roar = smooth(slam_at - 0.01, slam_at + 0.03, t) * (1 - smooth(0.66, 0.76, t))
        rage = smooth(0.26, slam_at, t) * (1 - smooth(0.72, 0.97, t))      # the state he is in: hackles up, coat burning
        since = since_(t, slam_at); impact = math.exp(-since * 9)
        settle = impact * math.cos(since * 30)
        shake = math.sin(2 * math.pi * t * 11) * roar
        slump = bump(0.72, 1.0, t)
        def body(x, y):
            up = np.clip((0.98 - x) / 0.98, 0, 1) ** 1.15
            mid = np.exp(-((x - 0.57) / 0.17) ** 2) * np.clip((y - 0.64) / 0.2, 0, 1)
            top_ = np.clip(1 - y, 0, 1) ** 0.9
            feet = np.clip((0.86 - y) / 0.25, 0, 1)                         # paws stay on the floor when he drops
            lift = 62 * rear * up * (1 - mid)
            drop = (16 * settle + 7 * slump) * top_ * feet
            saw = np.abs((x * 12) % 1 - 0.5) * 2
            hackle = 11 * rage * saw * np.clip((0.56 - y) / 0.2, 0, 1) * np.clip((x - 0.22) / 0.1, 0, 1)
            return -13 * rear * up + (17 * roar + 2.5 * shake) * up * feet, lift - drop + hackle + 5 * roar * top_ * feet
        head = dict(c=(0.15, 0.56), r=(0.18, 0.21), p=(0.30, 0.54), a=12 * rear - 7 * settle - 2 * roar + 3 * shake + 3 * wave(t, 1, phi_phase(1)) * (1 - rage), lit=0.1 * rage)
        jaw = dict(c=(0.08, 0.715), r=(0.085, 0.05), p=(0.15, 0.665), a=head["a"] - 19 * roar - 2, soft=0.5)
        paw = dict(c=(0.13, 0.87), r=(0.16, 0.16), p=(0.19, 0.74), a=9 * rear + 3 * wave(t, 3) * rear)
        parts = [
            dict(c=(0.68, 0.30), r=(0.26, 0.24), p=(0.68, 0.30), d=(0, -0.018 * wave(t, 2, phi_phase(2)) * (1 - roar) - 0.03 * rear), soft=0.9),   # the great back heaves
            dict(c=(0.34, 0.72), r=(0.13, 0.11), p=(0.32, 0.62), a=-6 * rear + 12 * settle, soft=0.6),  # white ruff swings with the drop
            paw, head, jaw]
        mp = land(rig, rig.at(jaw, 0.085, 0.70), body)
        mouth = (left + mp[0], top + mp[1])
        # the roar punches the air, not the floor: the wall refracts, the sand does not
        ix, iy = XX.copy(), YY.copy()
        fronts = []
        for k in range(4):
            u = (t - slam_at - 0.012 - 0.06 * k) / 0.2
            if 0 < u < 1: fronts.append((u, 10 + 230 * u ** 0.8))
        dd = np.sqrt((XX - mouth[0]) ** 2 + (YY - mouth[1]) ** 2) + 1e-3
        for u, rad in fronts:
            w_ = np.exp(-((dd - rad) / 9) ** 2) * (1 - u) * 13 * sky_only
            ix = ix + (XX - mouth[0]) / dd * w_; iy = iy + (YY - mouth[1]) / dd * w_
        cv = back[np.clip(iy.astype(int), 0, H - 1), np.clip(ix.astype(int), 0, W - 1)].copy()
        cv = cv * (0.74 + 0.16 * rage) + np.exp(-((XX - mouth[0]) / 120) ** 2 - ((YY - mouth[1]) / 100) ** 2)[..., None] * np.array([34.0, 8, 4]) * rage
        cone = np.clip(0.75 - 0.75 * (XX - mouth[0]) / dd, 0.0, 1)
        for u, rad in fronts:
            cv += (np.exp(-((dd - rad) / (2 + 2.5 * u)) ** 2) * cone * (1 - u) ** 1.3)[..., None] * np.array([215.0, 196, 165]) * 0.5
        cv = feather(cv)
        sh = np.clip(1 - ((XX - left - w * 0.5) / (w * 0.56)) ** 2 - ((YY - ground) / 7) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        if slam_at < t < 0.92:
            falling.draw(cv, (t - slam_at) / 0.5 * 0.9, lambda v: np.array((130, 110, 90)) * (1 - v), 1.5)
        # the adventurer walks in, braces, and is thrown clear of the frame
        if t >= 0.80 or t < flung:
            p_ = ((t - 0.80) % 1) / 0.40
            fx_ = -12 + 34 * min(1.0, p_)
            stride = 0.75 * math.sin(2 * math.pi * t * 8) if p_ < 1 else 0.25
            foe(cv, fx_, ground - 11, 2.0, 0.0, stride, brace=smooth(0.25, 0.42, t) if t < flung else 0.0)
        elif t < flung + 0.18:
            v = (t - flung) / 0.18
            foe(cv, 22 - 60 * v, ground - 11 - 52 * v + 46 * v * v, 2.0, -5.0 * v, 1.0, brace=0.0)
        ang = 2 * math.pi * t
        pulse = 0.72 + 0.28 * math.sin(2 * math.pi * t * 8)
        coat = rig.rgb * np.array([1 + 0.12 * rage, 1 - 0.3 * rage, 1 - 0.34 * rage]) + bands * np.array([190.0, 40, 12]) * rage * pulse + pale * np.array([20.0, -6, -30]) * rage
        saved, rig.rgb = rig.rgb, np.clip(coat, 0, 255)
        rgb, al = rig.pose(parts, light=(math.cos(ang) * 0.7 * (1 - roar) - 0.6 * roar, -0.7), relief=0.3, bend=body)
        rig.rgb = saved
        rgb = rgb * (1 + 0.16 * impact)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        for f in ((0.125, 0.578), (0.045, 0.54)):
            ex, ey = land(rig, rig.at(head, *f), body)
            glow(cv, left + ex, top + ey, 2.2 + 1.3 * rage, (225, 34, 18), 0.3 + 0.8 * rage)
        if roar > 0.05:
            plume(cv, mouth[0] - 7, mouth[1] + 3, -0.85, 0.3, 54 * roar, 19, t * 6, [(205, 200, 184), (150, 142, 128), (96, 88, 80), (56, 50, 48)], 431, 0.6 * roar, 0.2)
            glow(cv, mouth[0], mouth[1], 5, (190, 44, 28), 0.6 * roar)
            blown.draw(cv, ((t - slam_at) * 3) % 1, lambda v: np.array((190, 170, 140)) * (1 - v) * roar, 1.8)
        if since < 0.24:
            u = since / 0.24
            for px in (0.16, 0.55):                                         # both forepaws land
                ring(cv, left + px * w, ground, 6 + u * 90, 0.22, 6, np.array([130.0, 106, 84]) * (1 - u))
            dust.draw(cv, u * 0.85, lambda v: np.array((175, 152, 122)) * (1 - v) * 0.85, 2.2)
        return np.clip(cv, 0, 255)
    return "callisto", "the roar", scene

def venenatis_web():
    rgb0, al0 = fit(sprite("Venenatis.png"), W * 1.1, H, exposure=0.92, gamma=0.86, sat=1.2)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 2; top = 34
    rig = Rig(rgb0, al0, pad=50)
    back = feather(web_backdrop())
    # tip x, y, reach x, y, gait phase; eight legs, out of step
    legs = [(0.03, 0.74, 0.11, 0.34, phi_phase(0)), (0.12, 0.30, 0.11, 0.26, phi_phase(1)), (0.37, 0.10, 0.07, 0.16, phi_phase(2)), (0.28, 0.60, 0.05, 0.10, phi_phase(3)),
            (0.59, 0.84, 0.07, 0.32, phi_phase(4)), (0.76, 0.70, 0.055, 0.32, phi_phase(5)), (0.97, 0.56, 0.06, 0.32, phi_phase(6)), (0.89, 0.27, 0.09, 0.17, phi_phase(7))]
    net_c = (W / 2 - 6, 202)
    r0 = np.random.RandomState(533)
    spokes = [(a, 0.85 + 0.3 * r0.rand()) for a in np.linspace(0, 2 * math.pi, 14)[:-1]]
    drips = Motes(21, 10, lambda r: (-5 + r.rand() * 10, 0, 0, 0, 74 + r.rand() * 22))
    cast_at = 0.36
    def scene(t):
        since = since_(t, cast_at)
        cast = bump(0.2, 0.62, t)                                          # she rears, then throws
        rearb = bump(0.05, 0.38, t); throw = math.exp(-since * 11)
        crouch = 7 * wave(t, 2, phi_phase(1)) + 15 * rearb - 18 * throw
        def body(x, y):
            bx, by = 0.0, 0.0
            near = np.exp(-((x - 0.5) / 0.26) ** 2 - ((y - 0.42) / 0.40) ** 2)   # the body rides on planted tips
            by = by + crouch * near
            for tx, ty, rx, ry, ph in legs:
                s = math.sin(2 * math.pi * (t * 2 + ph))
                lift, swing = max(0.0, s) ** 1.5, math.cos(2 * math.pi * (t * 2 + ph))
                g = np.exp(-((x - tx) / rx) ** 2 - ((y - ty) / ry) ** 2)
                by = by + 19 * lift * g
                bx = bx + 8 * swing * g * (1 if tx < 0.5 else -1)
            return bx, by
        head = dict(c=(0.46, 0.54), r=(0.115, 0.17), p=(0.47, 0.42), a=-5 * rearb + 7 * throw + 2 * wave(t, 3), d=(0, 0.02 * throw), lit=0.2 * throw)
        fang = dict(c=(0.435, 0.655), r=(0.05, 0.05), p=(0.44, 0.59), a=head["a"] + 7 * wave(t, 4), d=head["d"], soft=0.6)
        parts = [
            dict(c=(0.56, 0.17), r=(0.115, 0.215), p=(0.54, 0.33), a=7 * wave(t, 1, phi_phase(2)) - 8 * rearb + 10 * throw, lit=0.15 * wave(t, 2), soft=0.45),   # abdomen
            head, fang]
        mp = land(rig, rig.at(fang, 0.43, 0.68), body)
        mouth = (left + mp[0], top + mp[1])
        cv = back.copy()
        pulse = 0.5 + 0.5 * wave(t, 2, phi_phase(3))
        pool = noise(541, 8, t)
        rr = ((XX - net_c[0]) / 74) ** 2 + ((YY - net_c[1] - 2) / 20) ** 2
        den = np.clip((1 - rr + (pool - 0.5) * 1.2) * 2.5, 0, 1)[..., None] * (0.3 + 0.25 * pulse)
        cv[:] = cv * (1 - den) + ramp([(26, 74, 22), (60, 150, 44), (150, 225, 90)], pool)[..., :3] * 0.72 * den
        glow(cv, net_c[0], net_c[1] - 6, 44, (8, 34, 8), 0.5 + 0.4 * pulse)
        # shadow on the floor, then the net she throws, opening out of the fangs and settling
        u = min(1.0, since / 0.3); fade = 1 - smooth(0.62, 0.92, since)
        if fade > 0:
            rad = (10 + 86 * u)
            cy = mouth[1] + (net_c[1] - mouth[1]) * u
            cen = (net_c[0] + (mouth[0] - net_c[0]) * (1 - u), cy)
            r0_ = rad / PHI ** 6.5; turns = math.log(rad / r0_) / (GOLD_B * 2 * math.pi)
            segs = []
            for n_, hand in ((5, 1), (3, -1)):                             # the two Fibonacci families cross into a net
                for i in range(n_):
                    segs.append(phi_spiral(cen[0], cen[1], r0_, turns, rot=2 * math.pi * (i / n_ + 0.1 * since), squash=0.5, hand=hand))
            segs.append((mouth, cen))
            bright = strands(segs)
            cv += bright[..., None] * np.array([165.0, 175, 158]) * fade
        ang = 2 * math.pi * t
        rgb, al = rig.pose(parts, light=(math.cos(ang), -0.5 + 0.3 * math.sin(ang)), relief=0.32, bend=body)
        rgb = tint(rgb, mp[0], mp[1] + 6, w * 0.15, h * 0.32, (50, 170, 44), 0.25 + 0.3 * pulse, rig)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        for t0, (x, y, vx, vy, g) in drips.p:                              # venom runs off the moving fangs
            v = (t * 2 + t0) % 1.0
            stamp(cv, mouth[0] + x, mouth[1] + 2 + g * 1.5 * v * v, ramp([(210, 255, 130), (90, 230, 60), (50, 150, 35)], np.array(v)), 1.5)
        glow(cv, mouth[0], mouth[1] + 2, 8, (60, 170, 45), 0.45 + 0.5 * throw)
        if since < 0.14:
            for f in range(5):
                a = f * 1.26 + 0.4
                cv += strands([(mouth, (mouth[0] + 34 * math.cos(a), mouth[1] + 22 * math.sin(a) + 8))])[..., None] * np.array([190.0, 200, 180]) * (1 - since / 0.14)
        for fx, ph in ((0.43, 0.0), (0.46, 0.5)):                          # eyes
            ex, ey = land(rig, rig.at(head, fx, 0.49), body)
            glow(cv, left + ex, top + ey, 2.5, (210, 50, 30), 0.4 + 0.3 * wave(t, 3, ph))
        return np.clip(cv, 0, 255)
    return "venenatis", "the web", scene

def scorpia_sting():
    rgb0, al0 = fit(sprite("Scorpia.png"), W * 1.1, H, exposure=0.86, gamma=0.92, sat=0.95)
    h, w = al0.shape[:2]; left = 2; ground = H - 16; top = ground - h - 2
    S = (797, 605)
    rgb0 = np.minimum(rgb0, 205.0)                                         # the pale claw tips must not outshine the sting
    tail_poly = px_poly(S, [(245, 262), (305, 256), (330, 195), (398, 128), (472, 92), (488, 140), (494, 215), (465, 272), (432, 306), (468, 334), (542, 322), (560, 200), (530, 90), (480, -5), (390, 20), (320, 100), (280, 195)])
    (brgb, bal), (trgb, tal) = split(rgb0, al0, [tail_poly], keep=[px_poly(S, [(420, 300), (545, 280), (548, 345), (430, 350)])])
    trgb = 255 * (trgb / 255) ** 0.62 * 1.06                               # the sprite's tail is nearly black
    body_rig = Rig(brgb, bal, pad=50); tail_rig = Rig(trgb, tal, pad=50)
    back = feather(pit_backdrop())
    seep = Motes(31, 22, lambda r: (r.rand() * W, H - 14 - 20 * r.rand(), 10 * (r.rand() - 0.5), -46 - 40 * r.rand(), 0))
    splash = Motes(32, 34, lambda r: (0, 0, (r.rand() - 0.5) * 150, -40 - 90 * r.rand(), 190))
    p1, p2, p3, p4 = (0.62, 0.50), (0.66, 0.22), (0.53, 0.05), (0.41, 0.22)
    strike_at = 0.44
    # guardians come down the pit walls, heal until the sting scatters them
    guards = [((W + 26, 58), (181, 100), -1, phi_phase(1)), ((-26, 96), (39, 128), 1, phi_phase(2))]
    shimmer = [Motes(33 + i, 11, lambda r: ((r.rand() - 0.5) * 50, (r.rand() - 0.5) * 22, (r.rand() - 0.5) * 10, -30 - 24 * r.rand(), 0)) for i in range(2)]
    spawn = [(22, 10, 216, 2, phi_phase(3)), (178, 22, 222, 3, phi_phase(4)), (142, 12, 228, 2, phi_phase(5))]   # offspring: centre, reach, y, darts, phase
    SHELL, BAND = (190, 180, 174), (150, 40, 32)
    def rot(pt, about, deg):
        a = math.radians(deg); ca, sa = math.cos(a), math.sin(a)
        ax, ay = about[0] * w, about[1] * h; x, y = pt[0] * w - ax, pt[1] * h - ay
        return (ax + ca * x - sa * y) / w, (ay + sa * x + ca * y) / h
    def scene(t):
        since = since_(t, strike_at); impact = math.exp(-since * 8)
        arch = smooth(0.02, 0.36, t) * (1 - smooth(strike_at - 0.03, strike_at, t))  # draws back
        stab = smooth(strike_at - 0.05, strike_at, t) * (1 - smooth(0.62, 0.96, t))   # snaps down, holds, recovers
        recoil = impact * math.cos(since * 34)
        # wound tight, then the curl opens and throws the stinger out over the claw
        a1 = 13 * arch - 18 * stab + 2 * wave(t, 2) + 2 * recoil
        a2 = a1 + 7 * arch - 8 * stab + 2 * wave(t, 2, phi_phase(1))
        a3 = a2 - 10 * arch + 16 * stab + 2 * wave(t, 2, phi_phase(2))
        a4 = a3 - 6 * arch + 6 * stab
        d2 = rot(p2, p1, a1); d3 = rot(p3, p2, a2 - a1); d3 = rot(d3, p1, a1)
        d4 = rot(p4, p3, a3 - a2); d4 = rot(d4, p2, a2 - a1); d4 = rot(d4, p1, a1)
        parts_t = [
            dict(c=(0.60, 0.20), r=(0.46, 0.42), p=p1, a=a1, soft=0.25),
            dict(c=(0.47, 0.17), r=(0.26, 0.28), p=p2, a=a2, d=(d2[0] - p2[0], d2[1] - p2[1]), soft=0.3),
            dict(c=(0.38, 0.22), r=(0.15, 0.26), p=p3, a=a3, d=(d3[0] - p3[0], d3[1] - p3[1]), soft=0.3),
            dict(c=(0.355, 0.33), r=(0.085, 0.15), p=p4, a=a4, d=(d4[0] - p4[0], d4[1] - p4[1]), soft=0.3)]
        def tilt(x, y):
            up = np.clip(1 - y, 0, 1) ** 0.8
            near = np.exp(-((x - 0.46) / 0.32) ** 2)
            return (10 * stab * near * up - 5 * arch * near * up), (-13 * stab * near * up + 8 * arch * near * up + 2.5 * wave(t, 2, phi_phase(3)) * near)
        def legs_bend(x, y):
            bx, by = tilt(x, y)
            for tx, ty, ph in ((0.97, 0.70, phi_phase(1)), (0.89, 0.80, phi_phase(2)), (0.80, 0.86, phi_phase(3)), (0.24, 0.46, phi_phase(4)), (0.42, 0.42, phi_phase(5))):
                lift = max(0.0, math.sin(2 * math.pi * (t * 2 + ph))) ** 1.6
                by = by + 11 * lift * np.exp(-((x - tx) / 0.09) ** 2 - ((y - ty) / 0.16) ** 2)
            return bx, by
        snap = 0.5 + 0.5 * wave(t, 2, phi_phase(4)) + 0.5 * impact
        parts_b = [
            dict(c=(0.15, 0.72), r=(0.19, 0.17), p=(0.34, 0.68), a=-6 * arch + 13 * stab + 3 * wave(t, 1, phi_phase(5)), soft=0.45),     # the big left claw
            dict(c=(0.06, 0.79), r=(0.075, 0.08), p=(0.10, 0.70), a=-6 * arch + 13 * stab + 12 * snap, soft=0.5),                # its pincer opens
            dict(c=(0.38, 0.86), r=(0.26, 0.145), p=(0.54, 0.78), a=5 * arch - 11 * stab + 3 * wave(t, 1, phi_phase(6)), soft=0.4),      # the lower claw
            dict(c=(0.30, 0.955), r=(0.14, 0.05), p=(0.44, 0.90), a=5 * arch - 11 * stab - 9 * snap, soft=0.5),
            dict(c=(0.43, 0.70), r=(0.08, 0.055), p=(0.43, 0.66), d=(0, 0.015 * snap), soft=0.6),                               # mandibles
        ]
        stip = tail_rig.at(parts_t[-1], 0.33, 0.415); stip = land(tail_rig, stip, tilt)
        tip = (left + stip[0], top + stip[1])
        cv = back.copy()
        glow_pool = noise(641, 9, t)
        rr = ((XX - W * 0.46) / 94) ** 2 + ((YY - ground + 2) / 15) ** 2
        den = np.clip((1 - rr + (glow_pool - 0.5) * 1.3) * 2, 0, 1)[..., None] * (0.16 + 0.4 * impact)
        cv[:] = cv * (1 - den) + ramp([(26, 72, 16), (54, 136, 28), (120, 210, 60)], glow_pool)[..., :3] * 0.75 * den
        seep.draw(cv, t, lambda v: np.array((70, 150, 40)) * math.sin(math.pi * v) * 0.6, 1.0)
        come = smooth(0.70, 1.0, t) if t >= 0.5 else (1 - smooth(strike_at + 0.005, 0.5, t) if t > strike_at else 1.0)
        heal = smooth(0.06, 0.12, t) * (1 - smooth(0.34, 0.40, t))
        beats = [((t - 0.08) / 0.31 * 3 + i * 0.5) % 1 for i in range(2)]    # each touch pulses in its own time
        flare = heal * max(math.sin(math.pi * f) ** 2 * (1 - f) for f in beats) * 1.5
        sh = np.clip(1 - ((XX - W * 0.48) / (w * 0.48)) ** 2 - ((YY - ground + 5) / 8) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.45 * sh
        ang = 2 * math.pi * t; light = (math.cos(ang) * 0.8, -0.6)
        rb, ab = body_rig.pose(parts_b, light=light, relief=0.3, bend=legs_bend)
        rt, at = tail_rig.pose(parts_t, light=light, relief=0.3, bend=tilt)
        rb = tint(rb, stip[0], stip[1], w * 0.18, h * 0.22, (60, 170, 40), 0.12 + 0.3 * impact, body_rig)
        rt = tint(rt, stip[0], stip[1], w * 0.14, h * 0.2, (70, 190, 50), 0.2 + 0.4 * impact, tail_rig)
        for r_, a_ in ((rb, ab), (rt, at)):                                # a brief green rim runs round her shell with each pulse
            if flare > 0.01:
                solid = a_[..., 0] > 0.5
                inner = solid & np.roll(solid, 2, 0) & np.roll(solid, -2, 0) & np.roll(solid, 2, 1) & np.roll(solid, -2, 1)
                r_ += (solid & ~inner)[..., None] * np.array([70.0, 190, 110]) * flare * 0.7 + solid[..., None] * np.array([6.0, 24, 12]) * flare
        over(cv, rb, ab, left - 50, top - 50)
        over(cv, rt, at, left - 50, top - 50)
        for i, (a_, b_, fc, ph) in enumerate(guards):                      # in front of her, claws on her shell
            gx, gy = a_[0] + (b_[0] - a_[0]) * come, a_[1] + (b_[1] - a_[1]) * come
            gx += 2 * wave(t, 2, ph)
            cv *= 1 - 0.6 * np.exp(-((XX - gx) / 22) ** 2 - ((YY - gy + 3) / 16) ** 2)[..., None]   # a pool of shade keeps them apart from her
            hx, hy, _ = scorpling(cv, gx, gy, 2.4, fc, t, ph, SHELL, BAND, (220, 240, 225), 4)
            if heal > 0.02:                                                # no path to her: a pulse where the claws touch, motes lifting off the shell
                cx, cy = hx + fc * 7, hy
                f = beats[i]
                ring(cv, cx, cy, 3 + 21 * f, 1.0, 5, np.array([150.0, 235, 170]) * (1 - f) ** 1.1 * heal)
                glow(cv, cx, cy, 6, (110, 200, 130), 0.8 * (1 - f) * heal)
                for t0, (x, y, vx, vy, g) in shimmer[i].p:
                    v = (t * 3 + t0) % 1
                    stamp(cv, cx + x + vx * v, cy + y + vy * v, np.array((200, 255, 215)) * math.sin(math.pi * v) * heal, 2.8)
        for cx_, reach, y_, darts, ph in spawn:                            # offspring skitter the floor, trailing poison
            pos = lambda tt: cx_ + reach * tooth(tt + ph, darts)
            for k in (3, 2):
                stamp(cv, pos((t - k * 0.04) % 1), y_ + 6, np.array((50, 150, 36)) * (0.7 - k * 0.15), 2.2)
            fc = -1 if pos((t + 0.01) % 1) < pos(t) else 1
            _, _, tp = scorpling(cv, pos(t), y_, 1.8, fc, t, ph, (186, 176, 170), (70, 150, 46), (130, 255, 80), 6)
            glow(cv, tp[0], tp[1], 2.5, (60, 170, 40), 0.4)
        glow(cv, tip[0], tip[1], 4 + 5 * impact, (80, 210, 50), 0.5 + 0.5 * impact)
        if since < 0.42:                                                   # the sting bursts poison where it lands
            u = since / 0.42
            n = noise(651, 7, t * 2)
            bloom = np.exp(-((XX - tip[0]) ** 2 + ((YY - tip[1] - 4) * 1.25) ** 2) / (2 * (7 + 24 * u) ** 2))
            d2_ = np.clip(bloom * 1.9 * (1 - u) + (n - 0.5) * 1.5 * bloom - 0.25, 0, 1)[..., None]
            cv[:] = cv * (1 - 0.55 * d2_) + ramp([(26, 90, 22), (60, 160, 40), (140, 215, 80)], d2_[..., 0])[..., :3] * 0.55 * d2_
            for t0, (x, y, vx, vy, g) in splash.p:
                v = u * (0.75 + 0.25 * t0)
                stamp(cv, tip[0] + vx * v, tip[1] + vy * v + g * v * v, np.array((110, 235, 90)) * (1 - u), 1.4)
        return np.clip(cv, 0, 255)
    return "scorpia", "the sting", scene

def sol_lunge():
    S = (1075, 1558)
    rgb0, al0 = fit(sprite("Sol_Heredit.png"), W, H - 16, exposure=0.86, gamma=0.92, sat=1.05)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 16; ground = H - 8; top = ground - h
    spear_poly = px_poly(S, [(-4, 442), (20, 428), (226, 760), (292, 770), (292, 852), (258, 852), (300, 900), (402, 1042), (500, 1078), (604, 1232), (544, 1228), (612, 1396), (518, 1412), (480, 1272), (432, 1236), (448, 1158), (384, 1066), (290, 900), (215, 840), (212, 780)])
    shield_poly = [(885 / S[0] + 196 / S[0] * math.cos(a), 810 / S[1] + 196 / S[1] * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 28)]
    (b1, a1), (srgb, sal) = split(rgb0, al0, [spear_poly], keep=[px_poly(S, [(380, 1000), (500, 1090), (446, 1244), (366, 1240)]), px_poly(S, [(278, 840), (300, 780), (296, 880)])])
    (b2, a2), (grgb, gal) = split(b1, a1, [shield_poly], keep=[px_poly(S, [(600, 780), (830, 1050), (700, 1030), (640, 880)]), px_poly(S, [(710, 500), (860, 610), (800, 720), (715, 690)])])
    # the shaft is a hair of dark brown: lift and thicken it, leave the gold head alone
    bladed = poly_mask(w, h, px_poly(S, [(370, 1030), (620, 1030), (620, 1420), (370, 1420)]))[..., None]
    thick = np.asarray(Image.fromarray((255 * (srgb / 255) ** 0.55).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)), float)
    thal = np.asarray(Image.fromarray((sal[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)), float)[..., None] / 255
    srgb = np.clip(srgb * 1.22, 0, 255) * bladed + thick * (1 - bladed); sal = sal * bladed + thal * (1 - bladed)
    rig = Rig(b2, a2, pad=60); spear = Rig(srgb, sal, pad=60); shield = Rig(grgb, gal, pad=60)
    back = feather(colosseum_backdrop()) * 0.74 + BG * 0.26
    fist = (0.235, 0.525)
    motes = Motes(41, 24, lambda r: (r.rand() * W, 40 + r.rand() * 110, 26 + 30 * r.rand(), -10 * r.rand(), 0))
    burst = Motes(42, 40, lambda r: ((r.rand() - 0.5) * 12, 0, (r.rand() - 0.5) * 190, -40 - 120 * r.rand(), 200))
    on_floor = YY > 164
    # two figures lie on the sand: the spear's, wide and eight-lobed round his feet, and the shield's, smaller and five-fold
    SPEAR_M = dict(c=(W * 0.54, 204.0), r=102.0, petals=8, sides=6, inner=3, rot=0.0)
    SHIELD_M = dict(c=(W * 0.50, 212.0), r=66.0, petals=5, sides=5, inner=4, rot=math.pi / 10)
    def lines_of(m):
        # where the figure's own lines are: inner figure, polygon, ring, lobe valleys, lobe tips, innermost first
        r_, ro = m["r"], m["rot"]
        return [(0.24 * r_, m["inner"], ro + math.pi / 2), (0.36 * r_, m["sides"], ro + math.pi / m["sides"]), (0.52 * r_, 2 * m["petals"], ro),
                (0.62 * r_, m["petals"], ro + math.pi / m["petals"]), (r_, m["petals"], ro), (0.8 * r_, 2 * m["petals"], ro + math.pi / (2 * m["petals"]))]
    SKIN = [(52, 16, 6), (205, 84, 18), (255, 176, 58), (255, 244, 205)]
    spear_at, shield_at = 0.25, 0.75
    def pose_at(t):
        tp = t * 2
        if t >= 0.5: return 0.0, 0.0
        return smooth(0.08, 0.42, tp) * (1 - smooth(0.42, 0.5, tp)), smooth(0.42, 0.5, tp) * (1 - smooth(0.72, 0.98, tp))
    def spear_tip(t):
        wind, go = pose_at(t)
        a_arm = -8 * wind + 15 * go
        fore = dict(p=(0.275, 0.385), a=a_arm)
        spin = -9 * wind + 54 * go
        fx, fy = rig.at(fore, *fist)
        ang = math.radians(58 + spin)
        fx, fy = fx + 12 * go * math.cos(ang), fy + 12 * go * math.sin(ang)
        return fx, fy, spin, ang
    def column(cv, x, y, hgt, size, colour):
        n = max(1, int(hgt / 2.6))
        for j in range(n):
            f = j / n
            stamp(cv, x + 1.4 * math.sin(j * 1.7), y - j * 2.6, np.array(colour, float) * (1 - 0.65 * f), size * (1 - 0.7 * f))
    def scene(t):
        wind, go = pose_at(t)
        s_since, b_since = since_(t, spear_at), since_(t, shield_at)
        s_imp, b_imp = math.exp(-s_since * 10), math.exp(-b_since * 10)
        tele_s = smooth(0.03, spear_at - 0.02, t) * (1 - smooth(spear_at, spear_at + 0.02, t))
        tele_b = smooth(0.53, shield_at - 0.02, t) * (1 - smooth(shield_at, shield_at + 0.02, t))
        guard = smooth(0.53, 0.71, t) * (1 - smooth(0.71, shield_at, t))    # shield goes up as the rings mark
        slam = smooth(0.71, shield_at, t) * (1 - smooth(0.88, 1.0, t))
        erupt_s = (t - spear_at) / 0.2 if spear_at <= t < spear_at + 0.2 else -1
        erupt_b = (t - shield_at) / 0.24 if shield_at <= t < shield_at + 0.24 else -1
        ins_s, ins_b = smooth(0.03, spear_at - 0.015, t), smooth(0.53, shield_at - 0.015, t)
        heat = max(tele_s, tele_b, go * 0.8 * math.exp(-s_since * 2.5), slam * 0.8 * math.exp(-b_since * 2.5))
        def lean(x, y):
            up = np.clip(0.98 - y, 0, 1) ** 1.1
            shove = 20 * go - 9 * wind + 12 * slam - 5 * guard
            sink = 12 * go - 5 * wind + 19 * slam - 8 * guard
            return shove * up, -sink * up
        settle = s_imp * math.cos(s_since * 28) + b_imp * math.cos(b_since * 28)
        a_arm = -8 * wind + 15 * go
        parts = [
            dict(c=(0.60, 0.10), r=(0.20, 0.135), p=(0.60, 0.195), a=3 * wave(t, 1, phi_phase(1)) - 9 * go + 7 * settle + 5 * wind - 7 * slam + 4 * guard, soft=0.45),
            dict(c=(0.60, 0.245), r=(0.10, 0.085), p=(0.60, 0.31), a=-5 * go + 2 * wave(t, 1) - 5 * slam, soft=0.5),
            dict(c=(0.31, 0.37), r=(0.11, 0.105), p=(0.385, 0.30), a=a_arm * 0.55, soft=0.5),
            dict(c=(0.26, 0.47), r=(0.095, 0.095), p=(0.275, 0.385), a=a_arm, soft=0.45),
            dict(c=(0.53, 0.63), r=(0.08, 0.09), p=(0.53, 0.53), a=-11 * go + 9 * settle + 3 * wave(t, 2, phi_phase(2)), soft=0.5),
            dict(c=(0.38, 0.44), r=(0.08, 0.10), p=(0.42, 0.36), a=8 * go * wave(t, 3, phi_phase(3)) + 5 * go, soft=0.7),
            dict(c=(0.61, 0.48), r=(0.05, 0.07), p=(0.60, 0.42), a=-5 * go + 3 * wave(t, 2), soft=0.7)]
        fx, fy, spin, ang = spear_tip(t)
        d_s = ((fx - fist[0] * w) / w, (fy - fist[1] * h) / h)
        sp = [dict(c=(0.3, 0.6), r=(3, 3), p=fist, a=spin, d=d_s, soft=0.02)]
        sh_c = (0.823, 0.52)
        turn = 360 * smooth(0.53, 0.75, t)                                  # the star on the shield winds up one full turn
        sd = dict(c=sh_c, r=(0.3, 0.3), p=sh_c, a=turn + 2.5 * wave(t, 1), d=(-0.05 * guard - 0.13 * slam + 0.04 * go, -0.09 * guard + 0.13 * slam - 0.012 * wave(t, 1, phi_phase(4))), soft=0.05)
        arm2 = dict(c=(0.73, 0.33), r=(0.09, 0.12), p=(0.70, 0.27), a=-6 * guard + 11 * slam - 3 * go, d=(-0.02 * slam, -0.03 * guard), soft=0.5)
        parts.append(arm2)
        pos = (left - 60, top - 60)
        cv = back * (0.92 - 0.2 * heat)
        motes.draw(cv, t, lambda v: np.array((120, 96, 60)) * math.sin(math.pi * v) * 0.45, 1.0)
        sh = np.clip(1 - ((XX - W * 0.54) / (w * 0.5)) ** 2 - ((YY - ground + 2) / 7) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        # the figure is cut into the sand as he winds up: it lies under him, so his body hides its middle
        flick = 0.78 + 0.22 * math.sin(2 * math.pi * t * 12)
        def figure(m, inscribe, erupt, spent):
            if inscribe <= 0 or t < spent[0] or t >= spent[1]: return
            fade = 1 - max(0.0, erupt) if erupt >= 0 else 1.0
            hot = 0.42 + 0.58 * inscribe * inscribe
            col = np.array([190.0, 124, 40]) * hot * fade * (flick if erupt < 0 else 1)
            layer = cv.copy()
            mandala(layer, m["c"][0], m["c"][1], m["r"], col, t, m["petals"], m["sides"], m["inner"], m["rot"], 0.38, inscribe, 0.34 * inscribe * fade, 0, 1.3)
            cv[:] = cv * (1 - on_floor[..., None]) + layer * on_floor[..., None]
        figure(SPEAR_M, ins_s, erupt_s, (0.0, 0.5))
        figure(SHIELD_M, ins_b, erupt_b, (0.5, 1.0))
        def along_lines(m, erupt, gain, front):
            # fire runs out from the centre along the figure's own lines
            if erupt < 0: return
            cx_, cy_ = m["c"]
            for j, (rad, n_, ro) in enumerate(lines_of(m)):
                u_ = erupt * 1.7 - 0.13 * j
                if not 0 <= u_ < 1: continue
                for i in range(n_):
                    a_ = ro + 2 * math.pi * i / n_
                    x_, y_ = cx_ + rad * math.cos(a_), cy_ + rad * 0.38 * math.sin(a_)
                    if (y_ >= 217) != front or not 2 < x_ < W - 2 or y_ < 164: continue
                    depth = (y_ - 164) / 76
                    column(cv, x_, y_, gain * (18 + 34 * depth) * math.sin(math.pi * u_) ** 0.7, 2.6 + 2.2 * depth, (252, 184, 82))
        along_lines(SPEAR_M, erupt_s, 1.0, False)
        along_lines(SHIELD_M, erupt_b, 0.85, False)
        # the arena's fire comes up onto his armour, and the armour takes it
        light = (0.85 * math.cos(2 * math.pi * t), -0.6 + 1.0 * heat)
        rb, ab = rig.pose(parts, light=light, relief=0.3, bend=lean)
        rs, ass = spear.pose(sp, light=light, relief=0.3, bend=lean)
        rg, ag = shield.pose([sd], light=(light[0], light[1]), relief=0.3, bend=lean)
        under = np.clip((rig.yy - rig.pad) / h - 0.25, 0, 1)[..., None] * 1.33
        flare = max(s_imp, b_imp)
        def burn(r_, gain):
            l_ = np.clip(r_.mean(2) / 215, 0, 1)
            hot = ramp(SKIN, l_)[..., :3]
            k_ = 0.62 * heat * gain
            return r_ * (1 - k_) + hot * k_ + under * np.array([215.0, 112, 26]) * (0.25 * heat + 0.6 * flare) * gain
        rb, rs, rg = burn(rb, 1.0), burn(rs, 1.0), burn(rg, 0.55)
        over(cv, rb, ab, *pos); over(cv, rg, ag, *pos); over(cv, rs, ass, *pos)
        cx, cy = shield.at(sd, *sh_c); cx, cy = land(rig, (cx, cy), lean); cx += left; cy += top
        if tele_b + slam > 0.02:
            glow(cv, cx, cy, 5 + 6 * tele_b, (255, 170, 60), 0.75 * max(tele_b, slam))
        tx, ty = land(rig, (fx + 111 * math.cos(ang), fy + 111 * math.sin(ang)), lean)
        tip = (left + tx, min(top + ty, ground - 2))
        if wind + go > 0.1:
            glow(cv, tip[0], tip[1] - 2, 2.5 + 3 * s_imp, (255, 215, 130), 0.4 * (wind + go) + 0.5 * s_imp)
        if s_since < 0.2:
            u = s_since / 0.2
            for t0, (x, y, vx, vy, g) in burst.p:
                v = u * (0.7 + 0.3 * t0)
                yy_ = ground - 3 + vy * v + g * v * v
                if yy_ < ground + 3: stamp(cv, tip[0] + x + vx * v, yy_, np.array((205, 160, 90)) * (1 - u) ** 0.8, 1.5)
        along_lines(SPEAR_M, erupt_s, 1.0, True)
        along_lines(SHIELD_M, erupt_b, 0.85, True)
        return np.clip(cv, 0, 255)
    return "sol", "the lunge", scene

def muspah_phases():
    box = (W - 4, H - 34)
    forms = []
    for name, e, g, s in (("melee", 0.84, 0.96, 1.25), ("ranged", 0.84, 0.96, 1.2), ("shielded", 0.88, 0.92, 1.15)):
        rgb, al = fit(sprite("Phantom_Muspah_(%s).png" % name), *box, exposure=e, gamma=g, sat=s)
        forms.append(Rig(rgb, al, pad=46))
    back = feather(ghorrock_backdrop())
    w, h = forms[0].w, forms[0].h
    aura = [(100, 110, 120), (60, 210, 90), (70, 230, 200)]
    shards = Motes(51, 26, lambda r: (r.rand() * W, 8, 22 * (r.rand() - 0.5), 60 + r.rand() * 80, 80))
    bolts = Motes(52, 11, lambda r: (0, 0, -60 - 110 * r.rand(), (r.rand() - 0.2) * 160, 40))
    slash = Motes(53, 30, lambda r: (0, 0, (r.rand() - 0.75) * 180, -40 - 80 * r.rand(), 150))
    def scene(t):
        phase = t * 3; k = int(phase) % 3; u = phase % 1
        nxt = (k + 1) % 3
        change = smooth(0.82, 1.0, u)                                       # the last quarter of each form dissolves into the next
        act = bump(0.06, 0.8, u)
        swipe = act if k == 0 else 0.0
        spit = act if k == 1 else 0.0
        ward = act if k == 2 else 0.0
        back_anticip = bump(0.0, 0.3, u) if k == 0 else 0.0
        drift = 6 * wave(t, 2)
        def ghost(x, y):                                                    # no feet: the body rolls like smoke
            return 3.5 * np.sin(2 * math.pi * (t * 2 - y * 1.4)) * y, drift + 7 * np.sin(2 * math.pi * (t * 2 - x * 1.1)) * np.clip((x - 0.45) / 0.55, 0, 1) - 12 * ward * np.clip(1 - y, 0, 1) + 7 * spit * np.clip(1 - y, 0, 1)
        arm_l = dict(c=(0.13, 0.62), r=(0.17, 0.31), p=(0.30, 0.44), a=8 * back_anticip - 15 * swipe * (1 - 2 * smooth(0.2, 0.34, u)) + 5 * wave(t, 2, 0.2) - 4 * ward, soft=0.4)
        arm_r = dict(c=(0.70, 0.86), r=(0.13, 0.16), p=(0.71, 0.62), a=5 * wave(t, 2, 0.55) + 5 * swipe - 4 * ward, soft=0.5)
        head = dict(c=(0.50, 0.32), r=(0.33, 0.31), p=(0.56, 0.56), a=3.5 * wave(t, 1, 0.1) + 8 * ward * 0.5 - 7 * spit, d=(0, -0.012 * ward), lit=0.15 * act, soft=0.5)
        jaw = dict(c=(0.455, 0.555), r=(0.08, 0.05), p=(0.44, 0.50), a=head["a"] - 11 * spit - 3 * wave(t, 2), soft=0.5)
        tail = dict(c=(0.90, 0.66), r=(0.13, 0.15), p=(0.76, 0.60), a=8 * wave(t, 2, 0.25), soft=0.5)
        belly = dict(c=(0.58, 0.72), r=(0.14, 0.13), p=(0.58, 0.72), d=(0, -0.012 * wave(t, 2, 0.35)), soft=0.9)
        parts = [belly, tail, arm_r, arm_l, head, jaw]
        rig = forms[k]
        left, top = (W - w) // 2 + 2, 14
        cv = back.copy()
        col = np.array(aura[k], float); nc = np.array(aura[nxt], float); c = col * (1 - change) + nc * change
        # a soft cold light of the current form on the ice behind
        glow(cv, W * 0.5, H * 0.45, 62, c * 0.28, 0.55 + 0.45 * act)
        fog = np.clip(noise(841, 14, t) * 1.5 - 0.5, 0, 1) * np.clip((YY - 180) / 40, 0, 1)
        cv += fog[..., None] * c * 0.34
        shards.draw(cv, t, lambda v: np.array((120, 160, 180)) * math.sin(math.pi * v) * 0.55, 1.0)
        ang = 2 * math.pi * t; light = (math.cos(ang), -0.5 + 0.4 * math.sin(ang))
        rgb, al = rig.pose(parts, light=light, relief=0.28, bend=ghost)
        n = noise(851, 14, t)
        thresh = change * 1.25 - 0.12
        keep = np.clip((n - thresh) * 5, 0, 1)
        translucent = 0.9 + 0.08 * math.sin(2 * math.pi * t * 5)
        def masked(rgb_, al_, m):
            pad = rig.pad
            ys, xs = np.mgrid[0:al_.shape[0], 0:al_.shape[1]]
            mm = m[np.clip(ys + top - pad, 0, H - 1), np.clip(xs + left - pad, 0, W - 1)]
            return al_ * mm[..., None] * translucent
        if change > 0:
            r2, a2 = forms[nxt].pose(parts, light=light, relief=0.28, bend=ghost)
            r2 = r2 * (1 + 0.5 * (1 - change))
            over(cv, r2, masked(r2, a2, 1 - keep), left - rig.pad, top - rig.pad)
        over(cv, rgb, masked(rgb, al, keep), left - rig.pad, top - rig.pad)
        if change > 0:                                                      # the burning edge of the change
            edge = np.exp(-((n - thresh) / 0.04) ** 2)
            pad = rig.pad
            ys, xs = np.mgrid[0:H, 0:W]
            sil = al[np.clip(ys - top + pad, 0, al.shape[0] - 1), np.clip(xs - left + pad, 0, al.shape[1] - 1), 0]
            cv += (edge * sil * math.sin(math.pi * change))[..., None] * nc * 0.7
        hp = land(rig, rig.at(jaw, 0.47, 0.57), ghost); mouth = (left + hp[0], top + hp[1])
        for fx, fy in ((0.40, 0.385), (0.47, 0.41), (0.56, 0.385)):         # the eye slits
            ex, ey = land(rig, rig.at(head, fx, fy), ghost)
            glow(cv, left + ex, top + ey, 3, c, 0.5 + 0.5 * act)
        if swipe > 0.02:                                                    # melee: claws rake an arc of ice
            cx, cy = land(rig, rig.at(arm_l, 0.11, 0.82), ghost); cx += left; cy += top
            glow(cv, cx, cy, 16, (140, 190, 215), 0.5 * swipe)
            for j in range(9):
                f = j / 8; a_ = 2.2 - 1.6 * f * min(1.0, u * 3)
                stamp(cv, cx - 4 + 42 * math.cos(a_), cy - 6 + 34 * math.sin(a_), np.array((210, 235, 250)) * (1 - f * 0.5) * swipe, 3.0)
            for t0, (x, y, vx, vy, g) in slash.p:
                v = (u * 1.4 + t0 * 0.2) % 1
                stamp(cv, cx + vx * v, cy + 30 + vy * v + g * v * v, np.array((170, 215, 240)) * (1 - v) * swipe, 1.4)
        if spit > 0.02:                                                     # ranged: green shards spat from the mouth
            for t0, (x, y, vx, vy, g) in bolts.p:
                v = (u * 2.5 + t0) % 1
                for b_ in range(3):
                    w_ = max(0.0, v - b_ * 0.05)
                    stamp(cv, mouth[0] + vx * w_, mouth[1] + vy * w_ + g * w_ * w_, np.array((130, 255, 120)) * min(1.0, spit * 1.3) * (1 - b_ * 0.25), 4.2 - b_ * 0.8)
            glow(cv, mouth[0], mouth[1], 13, (50, 200, 70), spit * 0.9)
        if ward > 0.02:                                                     # shielded: a pulsing ward round the whole body
            cx, cy = left + w * 0.5, top + h * 0.5
            dd = np.sqrt((XX - cx) ** 2 + ((YY - cy) * 0.95) ** 2)
            hexes = 0.45 + 0.55 * np.abs(np.sin(XX * 0.28 + YY * 0.16) * np.sin(YY * 0.32 - XX * 0.16))
            shell = np.exp(-((dd - 102 - 3 * wave(t, 6)) / 7) ** 2) * hexes
            wash = np.clip(1 - dd / 104, 0, 1) ** 2 * 0.12 * hexes
            cv += ((shell * 0.9 + wash) * ward)[..., None] * np.array([70.0, 225, 195])
        return np.clip(cv, 0, 255)
    return "muspah", "three phases", scene

def kril_drain():
    S = (1666, 1815)
    rgb0, al0 = fit(sprite("K'ril_Tsutsaroth.png"), W, H - 28, exposure=0.92, gamma=0.86, sat=0.92)
    h, w = al0.shape[:2]; left = (W - w) // 2 - 2; ground = H - 8; top = ground - h
    axe_poly = px_poly(S, [(55, 1018), (170, 1072), (340, 1032), (420, 1040), (440, 1085), (540, 1065), (606, 1102), (545, 1150), (545, 1182), (500, 1238), (440, 1205), (330, 1250), (280, 1318), (200, 1372), (70, 1332), (-4, 1258)])
    (brgb, bal), (argb, aal) = split(rgb0, al0, [axe_poly])
    rig = Rig(brgb, bal, pad=56); axe = Rig(argb, aal, pad=56)
    back = feather(chamber_backdrop())
    r0 = np.random.RandomState(61)
    stars = [(r0.rand(), r0.rand() * 2 * math.pi, 70 + r0.rand() * 60) for _ in range(26)]
    grip = (0.26, 0.62)
    embers = Motes(62, 40, lambda r: (r.rand() * W, H - 8, 18 * (r.rand() - 0.5), -160 - 90 * r.rand(), 30))
    slam_at = 0.50
    def scene(t):
        since = since_(t, slam_at); impact = math.exp(-since * 8)
        draw = smooth(0.0, 0.40, t) * (1 - smooth(0.42, slam_at, t))       # he breathes it all in, axe going up
        down = smooth(0.42, slam_at, t) * (1 - smooth(0.7, 0.98, t))
        fire = smooth(slam_at, slam_at + 0.04, t) * (1 - smooth(0.78, 1.0, t))
        flicker = wave(t, 5)
        def body(x, y):
            up = np.clip(0.97 - y, 0, 1) ** 1.05
            shove = 16 * down - 8 * draw
            sink = 16 * down - 14 * draw + 3 * wave(t, 2, phi_phase(1)) * (1 - down)
            return shove * up, -sink * up
        settle = impact * math.cos(since * 28)
        head = dict(c=(0.385, 0.51), r=(0.145, 0.135), p=(0.42, 0.43), a=7 * draw - 8 * down + 2 * wave(t, 1, phi_phase(2)) + 3 * settle, lit=0.2)
        jaw = dict(c=(0.37, 0.545), r=(0.05, 0.035), p=(0.385, 0.50), a=head["a"] - 10 * draw - 13 * down * impact - 4 * fire, soft=0.5)
        parts = [
            dict(c=(0.62, 0.45), r=(0.25, 0.09), p=(0.62, 0.45), d=(0, -0.012 * draw), soft=0.9),                                           # shoulders swell
            dict(c=(0.76, 0.21), r=(0.24, 0.215), p=(0.59, 0.39), a=-12 * draw + 13 * down + 4 * wave(t, 2, phi_phase(3)) - 6 * settle, lit=0.2 * draw, soft=0.45),   # near wing
            dict(c=(0.39, 0.17), r=(0.145, 0.22), p=(0.46, 0.37), a=9 * draw - 9 * down + 3 * wave(t, 2, phi_phase(4)) + 5 * settle, soft=0.5),        # far wing, a beat behind
            dict(c=(0.84, 0.66), r=(0.09, 0.22), p=(0.78, 0.45), a=-5 * draw + 8 * down + 3 * wave(t, 2, phi_phase(5)), soft=0.45),                    # the dagger arm
            head, jaw]
        rise = 16 * draw - 10 * down
        spin = 34 * draw - 16 * down + 4 * settle
        axe_part = dict(c=(0.2, 0.62), r=(3, 3), p=grip, a=spin, d=(0.012 * down, -rise / h), soft=0.02)
        forearm = dict(c=(0.27, 0.58), r=(0.055, 0.05), p=(0.31, 0.54), a=8 * draw, d=(0, -rise / h * 0.5), soft=0.6)
        parts.insert(3, forearm)
        mp = land(rig, rig.at(jaw, 0.365, 0.565), body); mouth = (left + mp[0], top + mp[1])
        cv = back * (0.72 + 0.28 * fire + 0.08 * flicker * fire)
        # the star on the wall drinks the prayer light, and the fire behind him
        glow(cv, W / 2, 72, 56, (56, 12, 18), 0.25 + 0.65 * draw + 0.3 * fire)
        sh = np.clip(1 - ((XX - W * 0.48) / (w * 0.42)) ** 2 - ((YY - ground) / 6) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        fl = _stretch(scroll_up(951, 6, t, 3)) * 0.6 + _stretch(scroll_up(953, 3, t, 5)) * 0.4
        hgt = (60 + 70 * fbm(957, (22, 9))[0, :][None, :]) * fire * (0.75 + 0.45 * impact)
        fh = np.clip((ground - YY) / np.maximum(hgt, 1), 0, 1.3)
        den = np.clip((0.95 - fh * 1.2) + (fl - 0.5) * 2.0, 0, 1) * (YY < ground + 8) * (fire > 0.01)
        centre = 0.55 + 0.45 * np.abs(XX - W * 0.48) / (W * 0.5)
        den = den * centre
        cv[:] = cv * (1 - den[..., None] * 0.9) + ramp([(235, 150, 50), (205, 84, 20), (140, 34, 12), (60, 14, 10)], 1 - den)[..., :3] * den[..., None] * 0.9
        ang = 2 * math.pi * t; light = (math.cos(ang) * 0.8, -0.5 + 0.4 * fire)
        rgb, al = rig.pose(parts, light=light, relief=0.3, bend=body)
        ra, aa = axe.pose([axe_part], light=light, relief=0.3, bend=body)
        lower = np.clip((rig.yy - rig.pad) / h - 0.4, 0, 1)[..., None] * 1.6
        rgb = rgb * np.array([1 - 0.16 * draw, 1 - 0.04 * draw, 1 + 0.16 * draw])    # the stolen prayer cools him
        rgb = rgb * (1 + 0.15 * impact) + lower * np.array([120.0, 60, 12]) * fire * (0.75 + 0.25 * flicker)
        ra = ra * (1.3 + 0.2 * impact) + np.array([26.0, 10, 0]) * fire
        if draw > 0.02:                                                    # the drain: prayer stars torn in along a spiral to the mouth
            arms_ = [phi_spiral(mouth[0], mouth[1] + 3, 2.5, 2.35, rot=i * 2 * math.pi / 3 - 2 * math.pi * t) for i in range(3)]
            spiral_ink = strands(arms_, 2)[..., None]
            cv += spiral_ink * np.array([92.0, 124, 220]) * draw
            for i, (p0, a0, rad) in enumerate(stars):                      # prayer stars ride the arms in
                v = (t * 2.5 + phi_phase(i)) % 1
                path = arms_[i % 3]
                x_, y_ = path[int((1 - v) ** 0.7 * (len(path) - 1))]
                stamp(cv, x_, y_, np.array([210.0, 230, 255]) * (0.65 + 0.35 * v) * draw * 1.2, 3.4 - v)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ex, ey = land(rig, rig.at(head, 0.385, 0.49), body)
        glow(cv, left + ex, top + ey, 2.4, (255, 225, 60), 0.3 + 0.35 * max(draw, impact))
        glow(cv, mouth[0], mouth[1] + 4, 3.5, (90, 130, 255), 0.4 * draw)
        if draw > 0.02: cv += spiral_ink * np.array([60.0, 90, 190]) * draw * 0.3   # the eye of the spiral shows through him
        ax, ay = axe.at(axe_part, 0.08, 0.66); ax, ay = land(rig, (ax, ay), body); blade = (left + ax, top + ay)
        if fire > 0.01:                                                    # flames also run off the axe, and foreground licks pass in front
            n = noise(961, 9, t * 3)
            b = np.exp(-(((XX - blade[0]) / 26) ** 2 + ((YY - blade[1] + 12) / 34) ** 2))
            d3 = np.clip(b * 1.2 + (n - 0.5) * 1.3 * b - 0.4, 0, 1)[..., None] * fire
            cv[:] = cv * (1 - 0.8 * d3) + ramp([(90, 22, 10), (220, 80, 20), (255, 200, 90)], d3[..., 0])[..., :3] * 0.85 * d3
        over(cv, ra, aa, left - rig.pad, top - rig.pad)
        if fire > 0.01:
            fore = np.clip((0.8 - np.clip((ground + 6 - YY) / 14, 0, 1) * 1.4) + (fl - 0.5) * 1.4, 0, 1) * (YY > ground - 18)
            fore = fore[..., None] * fire * 0.85
            cv[:] = cv * (1 - fore) + ramp([(240, 170, 70), (210, 90, 20), (120, 30, 12)], 1 - fore[..., 0] / 0.85)[..., :3] * fore
            embers.draw(cv, t, lambda v: np.array((255, 150, 50)) * (1 - v) * 0.85 * fire, 1.1)
        return np.clip(cv, 0, 255)
    return "kril", "flames and the drain", scene

RECIPES = [callisto_roar, venenatis_web, scorpia_sting, sol_lunge, muspah_phases, kril_drain]
