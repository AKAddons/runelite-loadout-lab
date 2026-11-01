"""Hi-res recipes, batch 2c: the Dagannoth Kings, Barrows, the Perilous Moons, the demonic gorilla, the Hueycoatl, Vet'ion."""
from hires_kit import *
from hires_kit import _layer

# ---- shared helpers ------------------------------------------------------------
def sm(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)

def since(t, t0): return (t - t0) % 1.0

def cut_sprite(name, thr=128, drop_floor=False):
    """Hard alpha at thr, so no soft ground shadow rides with the feet. drop_floor removes a
    detached shape under the figure, such as the glowing disc under each moon."""
    a = np.asarray(Image.open(SPR / name).convert("RGBA"), float).copy()
    m = a[..., 3] > thr
    if drop_floor:
        rows = m.any(1); y = np.where(rows)[0][-1]
        while rows[y]: y -= 1
        m[y:] = False
    ys, xs = np.where(m)
    a[..., 3] = m * 255.0
    return Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.uint8), "RGBA")

def lum_of(rig, lo=4, hi=96):
    """the sprite's own brightness, 0..1: every colour state is a ramp over this, so the form survives"""
    b = rig.rgb.mean(2); on = rig.al[..., 0] > 0
    l, h_ = np.percentile(b[on], [lo, hi])
    return np.clip((b - l) / max(h_ - l, 1), 0, 1)

def rim_of(rig, size=5):
    edge = Image.fromarray((rig.al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(size))
    return (rig.al[..., 0] - np.asarray(edge, float) / 255)[..., None]

def heal(rgb, size=5):
    """Grey closing: thin dark seams would land in a 5 x 10 cell as black dashes"""
    im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.MaxFilter(size)).filter(ImageFilter.MinFilter(size)), float)

def up_of(y, y0=0.85):
    return np.clip((y0 - y) / y0, 0, 1)

def bent(x, y, fy, lean, drop, y0=0.85):
    u = float(np.clip((y0 - fy) / y0, 0, 1))
    return x + lean * u, y + drop * u

def darken_round(cv, al, ox, oy, power=0.8, reach=5, mask=1.0):
    """a dark edge around a posed figure, so it stands off what is behind it"""
    a = al[..., 0] if al.ndim == 3 else al
    hal = np.zeros_like(a)
    for dy, dx in ((0, 0), (0, reach), (0, -reach), (reach, 0), (-reach, 0), (reach - 1, reach - 1), (reach - 1, 1 - reach), (1 - reach, reach - 1), (1 - reach, 1 - reach)):
        hal = np.maximum(hal, np.roll(np.roll(a, dy, 0), dx, 1))
    iy, ix = YY.astype(int) - oy, XX.astype(int) - ox
    ok = (iy >= 0) & (iy < a.shape[0]) & (ix >= 0) & (ix < a.shape[1])
    d = np.where(ok, hal[np.clip(iy, 0, a.shape[0] - 1), np.clip(ix, 0, a.shape[1] - 1)], 0)
    cv *= 1 - (power * d * mask)[..., None]

def over_above(cv, rgb, al, x, y, cut):
    """paste, but only the rows above canvas y = cut: a body rising out of the ground"""
    keep = np.clip(cut - (np.arange(al.shape[0]) + y), 0, 1)[:, None, None]
    over(cv, rgb, al * keep, x, y)

def arc_slash(cv, cx, cy, rad, a0, a1, colour, width, squash=1.0, reveal=1.0):
    """a tapered sweep of light from angle a0 to a1"""
    n = max(4, int(abs(a1 - a0) * rad / 1.8))
    for i in range(int(n * reveal) + 1):
        f = i / n
        a = a0 + (a1 - a0) * f
        stamp(cv, cx + rad * math.cos(a), cy + rad * math.sin(a) * squash, np.array(colour, float) * (0.4 + 0.6 * f), width * math.sin(math.pi * min(1.0, f + 0.08)) + 0.5)

def shadow(cv, x, y, rx, ry, power=0.5):
    cv *= 1 - power * np.clip(1 - ((XX - x) / rx) ** 2 - ((YY - y) / ry) ** 2, 0, 1)[..., None]

def shake(cv, sx, sy):
    """the whole picture moves together, so feet never slide on the floor"""
    return cv[np.clip(np.arange(H) - int(round(sy)), 0, H - 1)][:, np.clip(np.arange(W) - int(round(sx)), 0, W - 1)]

def coursed(course, brick, seed, colour, cells=(24, 8, 3)):
    row = (YY // course).astype(int); off = (row % 2) * brick // 2
    col = ((XX + off) // brick).astype(int)
    shade = np.random.RandomState(seed).rand(60, 60)[row % 60, col % 60]
    mortar = ((YY % course < 1.6) | ((XX + off) % brick < 1.6))
    stone = (0.65 + 0.6 * shade) * (0.75 + 0.5 * fbm(seed + 1, cells))
    return (np.array(colour, float) * stone[..., None]) * (1 - 0.65 * mortar[..., None])

def spike(cv, x, y, wid, tall, colour, tip=None, lean=0.0):
    """a shard standing on (x, y): faceted, lighter toward the tip"""
    if tall < 1: return
    v = np.clip((y - YY) / tall, 0, 1)
    d = (XX - x - lean * v) / np.maximum(wid * (1 - v), 0.01)
    m = (YY <= y) & (YY >= y - tall) & (np.abs(d) < 1)
    col = np.array(colour, float) * (0.75 + 0.45 * (d > 0))[..., None]
    if tip is not None: col = col + (np.array(tip, float) - np.array(colour, float)) * v[..., None]
    cv[:] = np.where(m[..., None], col, cv)

def poly(pts):
    im = Image.new("L", (W, H), 0); ImageDraw.Draw(im).polygon(pts, fill=255)
    return np.asarray(im, float)[..., None] / 255

def strokes(cv, segs, colour, width=2):
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    for s in segs: d.line(s, fill=255, width=width)
    m = np.asarray(im, float)[..., None] / 255
    cv[:] = cv * (1 - m) + np.array(colour, float) * m

# ---- the Dagannoth Kings: the tide brings each one in ---------------------------
def waterbirth_backdrop():
    """the Waterbirth cave: wet slate, a tide line, two ledges where the waiting kings stand"""
    img = np.array([28.0, 44, 50]) * (0.55 + 0.8 * fbm(1001, (30, 12, 5))[..., None])
    wet = 0.5 + 0.5 * np.sin(XX * 0.33 + 7 * fbm(1003, (30, 10)))
    img = img + (wet * np.clip((YY - 30) / 170, 0, 1))[..., None] * np.array([2.0, 11, 13])
    drip = 10 + 30 * np.abs(np.sin(XX * 0.12 + 2.2 * np.sin(XX * 0.04))) ** 4
    top = (YY < drip)[..., None]
    img = img * (1 - top) + np.array([14.0, 22, 26]) * top
    for pts in ([(0, 88), (66, 86), (56, 102), (24, 116), (0, 122)], [(W, 92), (W - 62, 90), (W - 50, 108), (W - 22, 118), (W, 126)]):
        m = poly(pts)
        img = img * (1 - m) + np.array([20.0, 32, 38]) * (0.7 + 0.6 * fbm(1007, (8, 3))[..., None]) * m
    return img

def dks_tide():
    def load(name, tall, wide): return fit(cut_sprite(name), wide, tall, exposure=0.92, gamma=0.86, sat=1.15, grain=0.5)
    # k: 0 Rex (melee), 1 Prime (magic), 2 Supreme (ranged)
    names = ["Dagannoth_Rex.png", "Dagannoth_Prime.png", "Dagannoth_Supreme.png"]
    big, small = [], []
    for n in names:
        rgb, al = load(n, H - 26, W - 16)
        rgb = heal(rgb, 5)
        small.append(fit(cut_sprite(n), 62, 80, exposure=0.8, gamma=1.0, grain=0.2))
        big.append((Rig(rgb, al, pad=40), al.shape[:2]))
    lums = [lum_of(r) for r, _ in big]
    natural = [r.rgb.copy() for r, _ in big]
    states = [ramp([(58, 16, 6), (170, 60, 16), (246, 148, 48), (255, 238, 190)], lums[0])[..., :3],
              ramp([(10, 34, 92), (26, 96, 190), (96, 200, 255), (232, 250, 255)], lums[1])[..., :3],
              ramp([(14, 52, 18), (44, 136, 40), (136, 226, 70), (238, 255, 190)], lums[2])[..., :3]]
    # Prime keeps his red spines, Supreme his dark ones: only the hide turns
    tones = [np.array([255.0, 130, 40]), np.array([90.0, 190, 255]), np.array([130.0, 235, 70])]
    silh = []
    for (rgb, al) in small:
        l = np.clip(rgb.mean(2) / 200, 0, 1)
        silh.append((ramp([(22, 36, 44), (46, 70, 80), (92, 124, 132)], l)[..., :3], al))
    back = waterbirth_backdrop()
    spray = Motes(1011, 26, lambda r: (r.rand() * W, H - 16, 26 * (r.rand() - 0.5), -46 - 60 * r.rand(), 90))
    drops = Motes(1012, 12, lambda r: (r.rand() * W, 6, 0, H - 40, 0))
    burst = Motes(1013, 30, lambda r: (0, 0, (r.rand() - 0.5) * 120, -40 - 90 * r.rand(), 170))
    foam_n = (_layer(1015, 10), _layer(1016, 5))
    spec = [
        dict(head=dict(c=(0.20, 0.13), r=(0.21, 0.14), p=(0.38, 0.22)), a1=dict(c=(0.12, 0.33), r=(0.26, 0.12), p=(0.36, 0.30)), a2=dict(c=(0.14, 0.53), r=(0.26, 0.13), p=(0.40, 0.46)),
             tail=dict(c=(0.88, 0.70), r=(0.14, 0.16), p=(0.76, 0.58)), mouth=(0.09, 0.19), hand=(0.05, 0.34), left=W - 190),
        dict(head=dict(c=(0.21, 0.26), r=(0.22, 0.13), p=(0.38, 0.34)), a1=dict(c=(0.16, 0.50), r=(0.26, 0.13), p=(0.38, 0.42)), a2=dict(c=(0.20, 0.67), r=(0.26, 0.11), p=(0.44, 0.60)),
             tail=dict(c=(0.84, 0.76), r=(0.17, 0.14), p=(0.72, 0.68)), mouth=(0.10, 0.31), hand=(0.03, 0.55), left=W - 158),
        dict(head=dict(c=(0.21, 0.26), r=(0.22, 0.13), p=(0.38, 0.34)), a1=dict(c=(0.16, 0.50), r=(0.26, 0.13), p=(0.38, 0.42)), a2=dict(c=(0.20, 0.67), r=(0.26, 0.11), p=(0.44, 0.60)),
             tail=dict(c=(0.84, 0.76), r=(0.17, 0.14), p=(0.72, 0.68)), mouth=(0.10, 0.31), hand=(0.03, 0.55), left=W - 158)]
    STRIKE = 0.40
    def paint(k, u, t):
        rig, (h, w) = big[k]; s = spec[k]; tone = tones[k]
        left = s["left"]; ground = H - 11; top = ground - h
        wind = sm(0.06, STRIKE - 0.03, u) * (1 - sm(STRIKE - 0.03, STRIKE + 0.01, u))
        go = sm(STRIKE - 0.02, STRIKE + 0.03, u) * (1 - sm(0.62, 0.86, u))
        hit = math.exp(-max(0.0, u - STRIKE) * 11) if u >= STRIKE else 0.0
        state = sm(0.04, STRIKE - 0.04, u) * (1 - sm(0.62, 0.84, u))
        cv = back * (0.72 + 0.16 * state + 0.25 * hit)
        wash = np.exp(-((XX - 110) / 120) ** 2 - ((YY - 110) / 100) ** 2)[..., None]
        cv += wash * tone * (0.07 * state + 0.14 * hit)
        cv = feather(cv)
        drops.draw(cv, t * 2 % 1, lambda v: (90, 130, 140), 1.0)
        # the two who wait up on the ledges, dim, their eyes in their own colours
        for j, (ox, gy) in zip(((k + 1) % 3, (k + 2) % 3), ((4, 90), (W - 56, 94))):
            r_, a_ = silh[j]
            over(cv, r_, a_, ox, gy - a_.shape[0])
            ex, ey = ox + a_.shape[1] * (0.16 if j == 0 else 0.19), gy - a_.shape[0] * (0.84 if j == 0 else 0.72)
            glow(cv, ex, ey, 2.4, tones[j], 0.8 + 0.2 * wave(t, 3, j * 0.3))
        breath = wave(t, 2, 0.1 * k)
        lean = 9 * wind - (20 if k == 0 else 11) * go
        drop = -8 * wind + (11 if k == 0 else 5) * go + 2 * breath
        head = dict(s["head"], a=3 * wave(t, 3, phi_phase(1)) + 8 * wind - 7 * go, lit=0.18 * state, soft=0.45)
        jaw_open = 12 * (wind if k == 1 else 0) + 8 * go * (k == 1)
        if k == 0:
            a1 = dict(s["a1"], a=13 * wind - 15 * go + 3 * wave(t, 2, phi_phase(2)), soft=0.4)
            a2 = dict(s["a2"], a=8 * wind - 12 * go + 3 * wave(t, 2, phi_phase(3)), soft=0.4)
        elif k == 1:
            a1 = dict(s["a1"], a=8 * wind + 4 * go + 2.5 * wave(t, 2, phi_phase(2)), soft=0.4)
            a2 = dict(s["a2"], a=-5 * wind + 5 * go + 3 * wave(t, 2, phi_phase(3)), soft=0.4)
        else:
            a1 = dict(s["a1"], a=15 * wind - 13 * go + 2 * wave(t, 2, phi_phase(2)), soft=0.4)
            a2 = dict(s["a2"], a=5 * wind - 5 * go + 3 * wave(t, 2, phi_phase(3)), soft=0.4)
        parts = [
            dict(c=(0.56, 0.40), r=(0.18, 0.16), p=(0.56, 0.40), d=(0, -0.012 * breath), soft=0.9),
            s["tail"] | dict(a=6 * wave(t, 2, phi_phase(4)) + 6 * go, soft=0.5),
            a2, a1, head]
        def body(x, y):
            u_ = up_of(y)
            return -lean * u_, -drop * u_
        rig.rgb = np.clip(natural[k] * (1 - state) + states[k] * state + tone * 0.2 * hit, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        shadow(cv, left + w * 0.55, ground, w * 0.5, 6, 0.4)
        darken_round(cv, al, left - rig.pad, top - rig.pad, 0.45, 4)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        mx, my = rig.at(head, *s["mouth"]); mx, my = bent(mx, my, s["mouth"][1], lean, drop); mouth = (left + mx, top + my)
        hx, hy = rig.at(a1, *s["hand"]); hx, hy = bent(hx, hy, s["hand"][1], lean, drop); hand = (left + hx, top + hy)
        ex, ey = rig.at(head, s["mouth"][0] + 0.09, s["mouth"][1] - 0.05); ex, ey = bent(ex, ey, s["mouth"][1], lean, drop)
        glow(cv, left + ex, top + ey, 2.6 + 2 * state, tone, 0.4 + 0.8 * state)
        v = u - STRIKE
        if k == 0 and v >= 0 and v < 0.26:                                      # Rex: three claw rakes, nothing thrown
            f = v / 0.26
            for i, rad in enumerate((40, 56, 72)):                              # three parallel curves, out in front of him
                arc_slash(cv, hand[0] + 52, hand[1] + 26, rad, -2.2, -4.0 + 0.1 * i, np.array((255.0, 232, 180)) * (1 - f * 0.7), 3.2, 1.1, min(1.0, f * 3.4))
            for t0, (_, _, vx, vy, g) in burst.p[:14]:
                stamp(cv, hand[0] + vx * f - 20, hand[1] + 30 + vy * f + g * f * f, np.array((230.0, 240, 240)) * (1 - f), 1.4)
        if k == 1:                                                              # Prime: seawater gathers in his throat and leaves as a jet
            if wind > 0.02:
                glow(cv, mouth[0], mouth[1], 5 + 8 * wind, tone, 0.9 * wind)
                for i in range(8):
                    q = (t * 6 + phi_phase(i)) % 1.0; a = i * GOLDEN_ANGLE
                    stamp(cv, mouth[0] + 34 * (1 - q) * math.cos(a), mouth[1] + 34 * (1 - q) * math.sin(a), np.array((200.0, 240, 255)) * q * wind, 1.6)
            if 0 <= v < 0.3:
                f = v / 0.3
                plume(cv, mouth[0], mouth[1], -0.8, 0.62, 50 + 130 * min(1.0, f * 2.6), 30, (t * 5) % 1, [(245, 254, 255), (150, 225, 255), (50, 140, 225), (18, 66, 140)], 1021, min(1.0, 1.5 - f), wobble=0.12)
                ring(cv, 16, H - 20, 8 + 60 * f, 0.26, 4, np.array((170.0, 230, 255)) * (1 - f))
        if k == 2:                                                              # Supreme: a volley of spikes, fanned out of the throwing hand
            if wind > 0.02: glow(cv, hand[0], hand[1], 4 + 5 * wind, tone, 0.7 * wind)
            if 0 <= v < 0.28:
                f = v / 0.28
                for i in range(6):
                    q = f * 1.5 - i * 0.07
                    if q <= 0: continue
                    q = min(q, 1.0)
                    a = -2.75 + i * 0.24
                    x0, y0 = hand[0] + 150 * q * math.cos(a), hand[1] + 150 * q * math.sin(a) + 220 * q * q * 0.5
                    x1, y1 = x0 - 11 * math.cos(a), y0 - 11 * math.sin(a) - 8 * q
                    if q < 1:
                        line(cv, (x0, y0), (x1, y1), (240, 255, 210), 2.4)
                        line(cv, (x1, y1), (x1 - (x0 - x1) * 1.6, y1 - (y0 - y1) * 1.6), np.array(tone) * 0.6, 1.4)
        # the sea round his feet: it hides nothing of the floor's stillness, since it moves on its own
        n = noise(1025, 9, t)
        line_y = H - 20 + 4 * np.sin(XX * 0.06 + 2 * math.pi * t) + 2.5 * np.sin(XX * 0.17 - 4 * math.pi * t)
        sea = (YY > line_y)[..., None]
        water = ramp([(18, 44, 58), (30, 74, 88), (64, 120, 132)], np.clip((YY - line_y) / 22 * 0.5 + n * 0.6, 0, 1))[..., :3]
        cv[:] = cv * (1 - sea * 0.86) + water * sea * 0.86
        crest = np.clip(1 - np.abs(YY - line_y) / 1.8, 0, 1)[..., None] * (0.5 + 0.5 * (n > 0.5))[..., None]
        cv += crest * np.array([120.0, 165, 170])
        return cv
    def scene(t):
        p = t * 3; k = int(p) % 3; u = p % 1
        cv = paint(k, u, t)
        if u > 0.80:                                                            # a wave crosses the frame; behind it stands the next king
            f = (u - 0.80) / 0.20
            nxt = paint((k + 1) % 3, 0.0, t)
            sh = int(t * 2 * H)
            n = 0.6 * foam_n[0][(YY.astype(int) + sh) % H, XX.astype(int)] + 0.4 * foam_n[1][(YY.astype(int) - sh) % H, XX.astype(int)]
            front = W + 90 - f * (W + 190) + (n - 0.5) * 40 + 18 * np.sin(YY * 0.05 + 2)      # right to left, against the way they face
            m = np.clip((XX - front) / 4, 0, 1)[..., None]
            cv = cv * (1 - m) + nxt * m
            band = np.exp(-((XX - front - 26) / 34) ** 2)
            den = np.clip(band * 1.8 + (n - 0.5) * 1.4 * band - 0.1, 0, 1)
            sea = ramp([(196, 226, 228), (120, 182, 192), (50, 112, 130), (24, 64, 80)], 1 - den + 0.25 * (n - 0.5))[..., :3]
            cv = cv * (1 - den[..., None] * 0.9) + sea * den[..., None] * 0.9
        return np.clip(cv, 0, 255)
    return "dks", "the tide", scene

# ---- Barrows: a brother rises from the tomb ------------------------------------
def crypt_backdrop():
    """the Barrows crypt: packed earth and old stone, six burial niches in the wall"""
    img = coursed(20, 36, 1101, (42, 40, 36), (22, 9, 3)) * 0.78
    niches = []
    for i, cx in enumerate((22, 58, 92, W - 92, W - 58, W - 22)):
        y0 = 36 + abs(i - 2.5) * 5
        niches.append((cx, y0))
        def paint(d):
            d.ellipse((cx - 13, y0, cx + 13, y0 + 26), fill=255); d.rectangle((cx - 13, y0 + 13, cx + 13, y0 + 66), fill=255)
        im = Image.new("L", (W, H), 0); paint(ImageDraw.Draw(im))
        m = np.asarray(im, float)[..., None] / 255
        img = img * (1 - m) + np.array([16.0, 15, 15]) * (0.8 + 0.4 * fbm(1103, (8, 3))[..., None]) * m
        coffin = ((np.abs(XX - cx) < 8 - np.abs(YY - y0 - 34) * 0.08) & (YY > y0 + 8) & (YY < y0 + 64))[..., None]
        img = img * (1 - coffin) + np.array([46.0, 42, 38]) * (0.7 + 0.6 * fbm(1105, (5, 2))[..., None]) * coffin
    floor = (YY > 176)[..., None]
    flags = 0.72 + 0.4 * (((XX + (YY // 11) * 17) % 34 < 32) & (YY % 11 < 10))[..., None]
    img = img * (1 - floor) + np.array([44.0, 40, 36]) * flags * (0.7 + 0.5 * fbm(1107, (12, 5))[..., None]) * floor
    return img, niches

def barrows_rise():
    H_TOMB = 198
    def load(n, wide, tall): return fit(cut_sprite(n, 30), wide, tall, exposure=1.0, gamma=0.8, sat=0.4, grain=0.4)
    brothers = []
    for n, wide, ramp_, tone in (("Ahrim_the_Blighted.png", 100, [(10, 16, 58), (30, 56, 140), (86, 134, 232), (226, 240, 255)], (120, 170, 255)),
                                 ("Dharok_the_Wretched.png", 100, [(50, 6, 8), (136, 20, 18), (232, 66, 40), (255, 208, 170)], (255, 80, 50)),
                                 ("Karil_the_Tainted.png", 130, [(8, 40, 22), (24, 106, 52), (92, 208, 96), (226, 255, 200)], (110, 235, 110))):
        rgb, al = load(n, wide, 194)
        rgb = heal(rgb, 5)
        rig = Rig(rgb, al, pad=40)
        l = lum_of(rig, 12, 88)                                                 # the ghost sprites are low in contrast
        l = np.clip(0.3 + 0.58 * l, 0, 1)
        col = ramp(ramp_, l)[..., :3]
        brothers.append(dict(rig=rig, col=col, rim=rim_of(rig, 3), tone=np.array(tone, float), size=al.shape[:2]))
    wall, niches = crypt_backdrop()
    # the tomb, front panel and open lid, drawn as flat shapes that never move with him
    x0, x1, y_top, y_low = 34, W - 34, H_TOMB, H - 10
    panel = poly([(x0, y_top), (x1, y_top), (x1 + 6, y_low), (x0 - 6, y_low)])
    tomb_tex = fbm(1111, (10, 4, 2))[..., None]
    panel_rgb = np.array([74.0, 66, 56]) * (0.65 + 0.6 * tomb_tex) * (0.8 + 0.4 * np.clip((YY - y_top) / 40, 0, 1))[..., None]
    carve = np.clip(1 - np.abs(np.abs(XX - W / 2) - 48) / 1.5, 0, 1) * (YY > y_top + 6) * (YY < y_low - 6) + np.clip(1 - np.abs(YY - y_top - 7) / 1.5, 0, 1) * (np.abs(XX - W / 2) < 48)
    panel_rgb = panel_rgb * (1 - 0.5 * carve[..., None])
    pit = poly([(x0 + 4, y_top - 12), (x1 - 4, y_top - 12), (x1, y_top + 1), (x0, y_top + 1)])
    prayer = Motes(1115, 26, lambda r: (r.rand() * 2 * math.pi, 50 + r.rand() * 70, 0, 0, 0))
    dust = Motes(1116, 24, lambda r: (W / 2 + (r.rand() - 0.5) * 120, y_top - 6, (r.rand() - 0.5) * 50, -50 - 50 * r.rand(), 0))
    motes = Motes(1117, 18, lambda r: (r.rand() * W, 30 + r.rand() * 140, 10 * (r.rand() - 0.5), -14 * r.rand(), 0))
    bolts = Motes(1118, 7, lambda r: (0, 0, 0, 0, 0))
    def scene(t):
        p = t * 3; k = int(p) % 3; u = p % 1
        b = brothers[k]; rig, tone = b["rig"], b["tone"]; h, w = b["size"]
        rise = sm(0.0, 0.17, u) * (1 - sm(0.88, 1.0, u))
        ready = sm(0.1, 0.32, u) * (1 - sm(0.72, 0.84, u))
        at = 0.42
        hit = math.exp(-max(0.0, u - at) * 13) if u >= at else 0.0
        go = sm(at - 0.02, at + 0.03, u) * (1 - sm(0.68, 0.82, u))
        wind = sm(0.17, at - 0.02, u) * (1 - sm(at - 0.02, at + 0.02, u))
        # the wall: lit from the tomb in his colour, and the niches light one at a time, two for each brother
        cv = wall * (0.92 + 0.14 * ready + 0.2 * hit)
        cv += (np.exp(-((XX - W / 2) / 90) ** 2 - ((YY - 170) / 80) ** 2))[..., None] * tone * (0.03 + 0.10 * rise + 0.14 * hit)
        n6 = int(t * 6) % 6
        for i, (cx, y0) in enumerate(niches):
            lit = 1.0 if i == (0, 5, 1, 4, 2, 3)[n6] else 0.0
            f = 0.75 + 0.25 * wave(t, 5, phi_phase(i))
            glow(cv, cx, y0 + 76, 9, (120, 74, 30), 0.22 * f + 0.4 * lit)
            stamp(cv, cx, y0 + 75, np.array((230.0, 160, 70)) * (0.25 * f + 0.6 * lit), 1.6)
        cv = feather(cv)
        motes.draw(cv, t, lambda v: np.array((70, 66, 58)) * math.sin(math.pi * v), 0.9)
        # the open tomb's dark, behind him
        cv = cv * (1 - pit) + np.array([12.0, 11, 12]) * pit
        cv += pit * tone * 0.22 * rise
        # the prayer drain: pale motes are pulled in to him
        cx_, cy_ = W / 2, 70
        for t0, (a, rad, _, _, _) in prayer.p:
            q = (t * 4 + t0) % 1.0
            d = rad * 1.5 * (1 - q)
            stamp(cv, cx_ + d * math.cos(a + 0.8 * q), cy_ + 20 + d * math.sin(a + 0.8 * q) * 0.9, np.array((180.0, 225, 255)) * q * ready, 1.9)
        left = (W - w) // 2 + (2 if k < 2 else -6); seat = H_TOMB + 18
        top = int(seat - h + (1 - rise) * (h - 10))
        breath = wave(t, 2, k * 0.2)
        lean = (5 * wind - 8 * go) * (k == 1) + 2 * wave(t, 1, 0.3)
        def body(x, y):
            u_ = np.clip(0.9 - y, 0, 1)
            ripple = np.sin(2 * math.pi * (t * 3 - y * 1.7)) * np.clip(y - 0.3, 0, 1)
            return -lean * u_ - (5 if k == 0 else 2.5) * ripple, -(4 * wind - 4 * go) * u_ * (k == 1)
        if k == 0:                       # Ahrim: the staff lifts, the far hand sweeps out, the hood turns
            parts = [dict(c=(0.11, 0.5), r=(0.17, 0.52), p=(0.17, 0.46), a=-4 * wave(t, 1, 0.1) + 6 * wind - 5 * go, d=(0, -0.03 * wind + 0.02 * go), soft=0.3),
                     dict(c=(0.84, 0.47), r=(0.17, 0.17), p=(0.76, 0.34), a=-9 * wave(t, 2, phi_phase(1)) - 8 * wind, soft=0.5),
                     dict(c=(0.5, 0.27), r=(0.18, 0.13), p=(0.5, 0.36), a=4 * wave(t, 1, phi_phase(2)), soft=0.5)]
            eyes = (0.46, 0.29); tip = (0.09, 0.05); ti = 0; he = 2
        elif k == 1:                     # Dharok: the greataxe comes down on a big lean
            parts = [dict(c=(0.42, 0.28), r=(0.47, 0.32), p=(0.26, 0.47), a=11 * wind - 11 * go, soft=0.25),
                     dict(c=(0.5, 0.31), r=(0.13, 0.08), p=(0.5, 0.37), a=3 * wave(t, 2, phi_phase(2)), soft=0.5),
                     dict(c=(0.85, 0.5), r=(0.16, 0.13), p=(0.76, 0.42), a=7 * wave(t, 2, phi_phase(1)), soft=0.5)]
            eyes = (0.5, 0.31); tip = (0.62, 0.05); ti = 0; he = 1
        else:                            # Karil: the crossbow kicks with each bolt
            kick = max(0.0, wave(u, 6)) * go
            parts = [dict(c=(0.16, 0.56), r=(0.24, 0.14), p=(0.36, 0.50), a=-4 * wave(t, 1, 0.2) - 5 * wind, d=(0.035 * kick, -0.01 * kick), soft=0.35),
                     dict(c=(0.86, 0.42), r=(0.16, 0.17), p=(0.76, 0.28), a=7 * wave(t, 2, phi_phase(1)), soft=0.5),
                     dict(c=(0.52, 0.11), r=(0.15, 0.11), p=(0.52, 0.2), a=3 * wave(t, 1, phi_phase(2)) + 3 * kick, soft=0.5)]
            eyes = (0.52, 0.125); tip = (0.03, 0.54); ti = 0; he = 2
        rig.rgb = np.clip(b["col"] * (0.88 + 0.2 * ready + 0.3 * hit) * (1 - b["rim"] * 0.6) + (tone * 0.55 + 110) * b["rim"] * 0.6, 0, 255)
        rgb, al = rig.pose(parts, light=(0.7 * wave(t, 1), -0.7), relief=0.3, bend=body)
        ox, oy = left - rig.pad, top - rig.pad
        darken_round(cv, al * ((np.arange(al.shape[0]) + oy) < H_TOMB)[:, None, None], ox, oy, 0.5, 4)
        over_above(cv, rgb, al, ox, oy, H_TOMB + 2)
        def canvas(part, fx, fy):
            x, y = rig.at(part, fx, fy); x, _ = bent(x, y, fy, lean, 0.0, 0.9)
            return left + x, top + y
        ex, ey = canvas(parts[he], *eyes)
        if rise > 0.5:
            glow(cv, ex, ey, 3.5 + 2 * ready, tone, 0.35 + 0.8 * ready)
            stamp(cv, ex, ey, (255, 255, 240), 1.3)
        tx, ty = canvas(parts[ti], *tip)
        v = u - at
        if k == 0:                       # a wave of air spells down off the staff head
            if wind > 0.02: glow(cv, tx, ty, 5 + 9 * wind, tone, 0.9 * wind); stamp(cv, tx, ty, (240, 248, 255), 2 + 2 * wind)
            if 0 <= v < 0.24:
                f = v / 0.24
                for i in range(3):
                    bolt(cv, tx, ty, W * (0.78 + 0.1 * i), y_top + 2 - 46 * i, 1120 + i + int(t * FRAMES), np.array((205.0, 225, 255)) * (1 - f * 0.6), jag=46, size=1.9)
                glow(cv, tx, ty, 14, tone, 1 - f)
        elif k == 1:                     # the axe's arc, and the blow lands on the flags
            if 0 <= v < 0.22:
                f = v / 0.22
                arc_slash(cv, W * 0.52, 122, 84, -1.1, -3.8, np.array((255.0, 110, 80)) * (1 - f * 0.6), 3.6, 1.0, min(1.0, f * 3.5))
                arc_slash(cv, W * 0.52, 122, 72, -1.2, -3.6, np.array((255.0, 210, 170)) * (1 - f * 0.7), 1.6, 1.0, min(1.0, f * 3.5))
            if 0 <= v < 0.3:
                ring(cv, 34, y_top - 8, 6 + 120 * v / 0.3, 0.3, 5, tone * (1 - v / 0.3))
            glow(cv, W / 2, 110, 60, (150, 18, 10), 0.3 * hit)
        else:                            # bolts in a quick string, across the crypt
            if 0 <= v < 0.3:
                for i in range(4):
                    q = (v - i * 0.05) / 0.09
                    if 0 <= q < 1:
                        bx0, by0 = tx - 6 - 110 * q, ty + 30 * q
                        line(cv, (bx0, by0), (bx0 + 20, by0 - 5), (235, 255, 215), 2.4)
                        line(cv, (bx0 + 20, by0 - 5), (bx0 + 52, by0 - 13), tone * 0.55, 1.5)
                    if q >= 0 and q < 0.25: glow(cv, tx, ty, 6, tone, 0.8)
        # the front of the tomb, always in front of him
        lit = (0.8 + 0.3 * ready + 0.5 * hit)
        cv = cv * (1 - panel) + panel_rgb * lit * panel
        glowline = np.clip(1 - np.abs(YY - y_top) / 2, 0, 1)[..., None] * (np.abs(XX - W / 2) < (x1 - x0) / 2)[..., None]
        cv += glowline * (tone * 0.5 * rise + 60)
        # the lid slides off in a grinding block at the left when he comes, and back as he goes
        lid = 66 * (1 - sm(0.0, 0.14, u) * (1 - sm(0.88, 1.0, u)))
        lx0, lx1 = x0 + 2 - 120 + lid * 2.3, x0 + 118 - 120 + lid * 2.3
        lm = poly([(lx0, y_top - 13), (lx1, y_top - 13), (lx1 + 8, y_top + 2), (lx0 - 8, y_top + 2)])
        cv = cv * (1 - lm) + np.array([70.0, 62, 54]) * (0.7 + 0.5 * tomb_tex) * lm
        if u < 0.2 or u > 0.86:
            f = (u / 0.2) if u < 0.5 else ((u - 0.86) / 0.14)
            dust.draw(cv, f * 0.8, lambda q: np.array((140, 126, 104)) * (1 - q), 1.3)
        cv = BG + (cv - BG) * (0.5 + 0.5 * EDGE)
        return np.clip(cv, 0, 255)
    return "barrows", "the risen", scene

# ---- the Perilous Moons: each takes the stage under her own sky -----------------
def neypotzli_backdrop():
    """Neypotzli: the Moons' cavern, a near-black round wall, a pale flagged floor"""
    rock = fbm(1201, (30, 12, 5))[..., None]
    img = np.array([30.0, 30, 36]) * (0.5 + 0.8 * rock)
    ang = np.arctan2(YY - 80, XX - W / 2); d = np.hypot(XX - W / 2, YY - 80)
    blocks = (np.sin(ang * 16) > 0.93) | (np.abs((d % 34) - 17) < 0.8)
    img = img * (1 - 0.35 * blocks[..., None])
    return img

def moons_phases():
    sprites = []
    for n, wide, tall in (("Blood_Moon.png", W - 8, 206), ("Blue_Moon.png", W - 2, 206), ("Eclipse_Moon.png", W - 8, 206)):
        rgb, al = fit(cut_sprite(n, 128, True), wide, tall, exposure=0.96, gamma=0.8, sat=1.15, grain=0.35)
        rig = Rig(rgb, al, pad=40)
        sprites.append(dict(rig=rig, base=rig.rgb.copy(), lum=lum_of(rig, 2, 98), rim=rim_of(rig, 3), size=al.shape[:2]))
    wall = neypotzli_backdrop()
    sc = (W / 2, 82)
    SK = [[(46, 4, 18), (150, 12, 40), (240, 50, 70), (255, 214, 190)],
          [(20, 52, 120), (66, 136, 220), (170, 226, 255), (255, 255, 255)],
          [(12, 8, 16), (40, 28, 30), (118, 84, 44), (246, 196, 96)]]
    for s_, ramp_ in zip(sprites, SK): s_["state"] = ramp(ramp_, s_["lum"])[..., :3]
    # the floor, in perspective
    FLOOR = 174
    z = np.clip((YY - FLOOR) / (H - FLOOR), 0, 1)
    across = (XX - W / 2) / (0.42 + z)
    row = np.floor(np.log(z + 0.16) * 4.2); col = np.floor(across / 34)
    tile_seed = np.random.RandomState(1211).rand(64, 64)
    tile = tile_seed[(row.astype(int)) % 64, col.astype(int) % 64]
    grout = (np.abs(across % 34 - 17) > 15.6) | ((np.log(z + 0.16) * 4.2) % 1 < 0.08)
    is_floor = (YY > FLOOR)[..., None]
    flags = np.array([44.0, 42, 46]) * (0.55 + 0.7 * tile[..., None]) * (0.55 + 0.6 * z[..., None]) * (1 - 0.5 * grout[..., None])
    disc_tex = fbm(1215, (22, 9, 4))
    disc_d = np.hypot(XX - sc[0], YY - sc[1])
    R = 62
    embers = Motes(1221, 30, lambda r: (r.rand() * W, 200 + r.rand() * 20, 10 * (r.rand() - 0.5), -130 - 70 * r.rand(), 0))
    snow = Motes(1222, 40, lambda r: (r.rand() * W * 1.5, r.rand() * 190, -90 - 50 * r.rand(), 26 + 40 * r.rand(), 0))
    shards = polygon_pts(W / 2, 206, 90, 6, rot=math.pi / 6, squash=0.27)
    order = (3, 1, 5, 0, 4, 2)
    fx = hex_mask(13, W / 2, 206, 0.0, 1.6, 0.34)
    clone_rgb, clone_al = fit(cut_sprite("Eclipse_Moon.png", 128, True), 90, 100, exposure=0.9, gamma=0.8, grain=0.2)
    clone_l = np.clip(clone_rgb.mean(2) / 230, 0, 1)
    clone_rgb = ramp([(10, 7, 14), (24, 18, 26), (110, 80, 40), (230, 180, 80)], clone_l)[..., :3]
    def sky(k, u, t, cv, state):
        """the moon on the wall, its rings, in the phase's colour"""
        if k == 0:
            disc = ramp([(52, 8, 16), (92, 16, 26), (140, 30, 34)], disc_tex)[..., :3]
            rc = np.array([190.0, 34, 44])
        elif k == 1:
            disc = ramp([(16, 28, 52), (24, 42, 74), (36, 60, 96)], disc_tex)[..., :3]
            rc = np.array([120.0, 190, 245])
        else:
            disc = ramp([(120, 88, 36), (168, 124, 48), (214, 170, 80)], disc_tex)[..., :3]
            rc = np.array([225.0, 170, 70])
        m = np.clip(R - disc_d, 0, 1)[..., None]
        cv[:] = cv * (1 - m) + disc * m
        breathe = 0.6 + 0.4 * wave(t, 3, k * 0.3)
        for i, rr in enumerate(phi_radii(R + 6, 3)):
            ring(cv, sc[0], sc[1], rr, 1.0, 1.8 + (2 - i) * 0.5, rc * (0.42 - 0.1 * i) * (breathe + state * 0.6), (YY < FLOOR))
        if k == 1:                                                              # a thin ice-lit crescent, turning full as her cold spreads
            off = 58 - 34 * state
            c = np.clip(R - disc_d, 0, 1) * np.clip(np.hypot(XX - sc[0] - off, YY - sc[1] + 8) - R * 0.98, 0, 1)
            cv += c[..., None] * np.array([120.0, 170, 215]) * (0.6 + 0.5 * state)
        if k == 2:                                                              # the black disc crosses: at totality only the corona is left
            ang = np.arctan2(YY - sc[1], XX - sc[0])
            off = 150 * (1 - sm(0.06, 0.42, u)) - 150 * sm(0.68, 0.84, u) if u < 0.9 else -150
            bk = np.clip(R + 1 - np.hypot(XX - sc[0] - off, YY - sc[1]), 0, 1)[..., None]
            cv[:] = cv * (1 - bk) + np.array([8.0, 6, 10]) * bk
            cor = np.exp(-np.maximum(disc_d - R, 0) / (7 + 9 * state)) * (0.55 + 0.25 * np.cos(ang * 7 + 2 * math.pi * t) + 0.2 * np.cos(ang * 13 - 4 * math.pi * t)) * (disc_d >= R - 1) * (YY < FLOOR)
            cv += (cor * state)[..., None] * np.array([240.0, 180, 80]) * 0.9
    def paint(k, u, t):
        s_ = sprites[k]; rig = s_["rig"]; h, w = s_["size"]
        at = (0.46, 0.46, 0.46)[k]
        wind = sm(0.1, at - 0.02, u) * (1 - sm(at - 0.02, at + 0.02, u))
        go = sm(at - 0.02, at + 0.03, u) * (1 - sm(0.66, 0.8, u))
        hit = math.exp(-max(0.0, u - at) * 10) if u >= at else 0.0
        state = sm(0.06, at, u) * (1 - sm(0.68, 0.8, u))
        cv = wall * (0.8 + 0.2 * hit)
        tints = (np.array([1.1, 0.68, 0.72]), np.array([0.72, 0.9, 1.2]), np.array([1.0, 0.86, 0.66]))
        cv = cv * tints[k]
        sky(k, u, t, cv, state)
        cv = cv * (1 - is_floor) + flags * tints[k] * (0.85 + 0.2 * hit) * is_floor
        tone = (np.array([255.0, 44, 66]), np.array([150.0, 215, 255]), np.array([245.0, 180, 70]))[k]
        cv = feather(cv)
        cy_ = FLOOR + 40
        shadow(cv, W / 2, cy_, 40, 7, 0.55)
        if k == 0:                                                              # the blood moon marks the tiles under you, in waves, then strikes them
            step = int(u * 5)
            lit = ((np.floor(tile * 5).astype(int) + step) % 3 == 0) * (~grout) * (YY > FLOOR) * np.clip(z * 2.2, 0.2, 1)
            warm = sm(0.04, 0.2, u) * (1 - sm(0.74, 0.8, u))
            cv += (lit * warm)[..., None] * np.array([96.0, 8, 18]) * (0.5 + 0.4 * state + 0.5 * hit)
            embers.draw(cv, t, lambda v: np.array((230, 50, 60)) * math.sin(math.pi * v) * warm, 1.0)
        if k == 1:                                                              # ice: a hexagon of frost spreads over the floor and six shards break through, one by one
            d = np.hypot(XX - W / 2, (YY - 206) / 0.34)
            grow = sm(0.05, 0.5, u) * (1 - sm(0.72, 0.8, u))
            frost = fx * (d < 150 * grow) * (YY > FLOOR)
            cv += frost[..., None] * np.array([70.0, 110, 150]) * 0.8
            cv += ((d < 150 * grow) * (YY > FLOOR))[..., None] * np.array([6.0, 12, 20])
            snow.draw(cv, t * 2 % 1, lambda v: (150, 195, 230), 1.0)
            for j, i in enumerate(order):
                x, y = shards[i]
                t0 = 0.18 + 0.05 * j
                tall = 96 * sm(t0, t0 + 0.08, u) * (1 - sm(0.72, 0.8, u)) * (0.6 + 0.6 * (y - 180) / 50)
                if y < 216: spike(cv, x, y, 9 + (y - 180) * 0.1, tall, (84, 140, 196), (225, 245, 255), lean=(x - W / 2) * 0.06)
        if k == 2:                                                              # the eclipse: shadow clones of her, on the hexagon's corners, rush in
            draw_c = sm(0.14, 0.34, u) * (1 - sm(0.46, 0.5, u))
            if draw_c > 0.02:
                for i, (sx, off) in enumerate(((-1, 0.0), (1, 0.03))):
                    q = sm(0.34, 0.46, u - off)
                    cx = W / 2 + sx * (82 - 54 * q)
                    a2 = clone_al * draw_c
                    r2, a3 = (clone_rgb, a2) if sx < 0 else (clone_rgb[:, ::-1], a2[:, ::-1])
                    over(cv, r2, a3, int(cx - r2.shape[1] / 2), 196 - r2.shape[0] - i * 4)
        # she floats: the tail ripples, the arms keep their own time
        breath = wave(t, 2, 0.1 * k)
        lean = 5 * wind - 9 * go
        def body(x, y):
            u_ = np.clip((y - 0.42) / 0.58, 0, 1)
            top_ = np.clip(0.55 - y, 0, 1) / 0.55
            return -(lean * top_) - (6 + 9 * state) * u_ ** 1.2 * math.sin(2 * math.pi * t * 2) * 0.5 - (5 + 6 * state) * u_ ** 2 * np.sin(2 * math.pi * (t * 3 - y * 1.8)), -(3 * breath + 5 * wind - 5 * go) * (1 - u_)
        if k == 0:
            weapon = dict(c=(0.16, 0.33), r=(0.26, 0.17), p=(0.38, 0.33), a=16 * wind - 15 * go + 2.5 * wave(t, 2, phi_phase(1)), soft=0.35)
            off = dict(c=(0.76, 0.46), r=(0.25, 0.2), p=(0.60, 0.34), a=-10 * wind + 9 * go + 3 * wave(t, 2, phi_phase(2)), soft=0.35)
            head = dict(c=(0.48, 0.14), r=(0.14, 0.14), p=(0.48, 0.25), a=3 * wave(t, 1, phi_phase(3)), soft=0.5)
            eye = (0.48, 0.15); tip = (0.03, 0.27)
        elif k == 1:
            weapon = dict(c=(0.17, 0.35), r=(0.25, 0.13), p=(0.38, 0.35), a=-7 * wind + 10 * go + 2.5 * wave(t, 2, phi_phase(1)), d=(0.04 * wind - 0.05 * go, 0), soft=0.35)
            off = dict(c=(0.80, 0.52), r=(0.2, 0.18), p=(0.60, 0.37), a=-9 * wind + 7 * go + 3 * wave(t, 2, phi_phase(2)), soft=0.35)
            head = dict(c=(0.42, 0.17), r=(0.13, 0.17), p=(0.42, 0.28), a=3 * wave(t, 1, phi_phase(3)), soft=0.5)
            eye = (0.42, 0.25); tip = (0.03, 0.31)
        else:
            weapon = dict(c=(0.17, 0.32), r=(0.25, 0.14), p=(0.38, 0.35), a=14 * wind - 14 * go + 2.5 * wave(t, 2, phi_phase(1)), soft=0.35)
            off = dict(c=(0.74, 0.44), r=(0.24, 0.17), p=(0.60, 0.36), a=-6 * wind + 6 * go + 3 * wave(t, 2, phi_phase(2)), soft=0.35)
            head = dict(c=(0.47, 0.14), r=(0.13, 0.14), p=(0.47, 0.25), a=3 * wave(t, 1, phi_phase(3)), soft=0.5)
            eye = (0.47, 0.17); tip = (0.03, 0.25)
        parts = [dict(c=(0.48, 0.38), r=(0.14, 0.1), p=(0.48, 0.38), d=(0, -0.008 * breath), soft=0.9), off, weapon, head]
        mix = state * (0.9 if k < 2 else 1.0)
        col = s_["base"] * (1 - mix) + s_["state"] * mix
        rc = tone if k < 2 else np.array([255.0, 205, 110])
        rig.rgb = np.clip(col * (1 - s_["rim"] * (0.25 + 0.5 * state)) + rc * s_["rim"] * (0.25 + 0.5 * state) + tone * 0.2 * hit, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        left = (W - w) // 2 + (0 if k != 1 else -2); top = 8
        ox, oy = left - rig.pad, top - rig.pad
        darken_round(cv, al, ox, oy, 0.5, 3, (YY < FLOOR + 8))
        over(cv, rgb, al, ox, oy)
        ex, ey = rig.at(head, *eye)
        ex += left; ey += top
        glow(cv, ex, ey, 4 + 3 * state, tone, 0.25 + 0.8 * state)
        tx, ty = rig.at(weapon, *tip); tx += left - lean * 0.6; ty += top
        v = u - at
        if k == 0 and 0 <= v < 0.24:                                            # a crescent cut from the club's swing
            f = v / 0.24
            arc_slash(cv, W * 0.42, 118, 92 + 16 * f, -2.9, 1.5, np.array((255.0, 70, 80)) * (1 - f * 0.6), 4.5, 0.9, min(1.0, f * 3.2))
            arc_slash(cv, W * 0.42, 118, 78 + 16 * f, -2.8, 1.0, np.array((255.0, 170, 150)) * (1 - f * 0.7), 2.2, 0.9, min(1.0, f * 3.2))
        if k == 1:
            if wind > 0.02: glow(cv, tx, ty, 4 + 9 * wind, tone, 0.8 * wind)
            if 0 <= v < 0.22:                                                   # the spear drives; a cone of cold off its head, and the frost cracks
                f = v / 0.22
                plume(cv, tx, ty, -0.5, 0.86, 40 + 100 * min(1.0, f * 2.5), 15, (t * 5) % 1, [(255, 255, 255), (190, 230, 255), (90, 160, 225), (40, 80, 140)], 1231, 1.1 - f, wobble=0.1)
                ring(cv, W / 2, 206, 40 + 110 * f, 0.34, 4, np.array((205.0, 240, 255)) * (1 - f))
        if k == 2 and 0 <= v < 0.24:                                            # the clones arrive in her: a black ring and a gold one
            f = v / 0.24
            ring(cv, W / 2, 120, 16 + 150 * f, 1.0, 9, np.array((-90.0, -90, -90)) * (1 - f))
            ring(cv, W / 2, 120, 8 + 150 * f, 1.0, 3, np.array((255.0, 190, 90)) * (1 - f))
            arc_slash(cv, W * 0.42, 118, 92, -2.9, 1.2, np.array((255.0, 205, 110)) * (1 - f * 0.6), 3.4, 0.9, min(1.0, f * 3.2))
        return cv
    def scene(t):
        p = t * 3; k = int(p) % 3; u = p % 1
        cv = paint(k, u, t)
        if u > 0.8:                                                             # the next moon's sky opens out from the disc
            f = sm(0.8, 1.0, u)
            nx = paint((k + 1) % 3, 0.0, t)
            rad = f * 300
            jag = (noise(1241, 16, t) - 0.5) * 12
            m = np.clip((rad + jag - disc_d) / 4, 0, 1)[..., None] * (1.0 if f < 1 else 1.0)
            cv = cv * (1 - m) + nx * m
            edge = np.exp(-((rad + jag - disc_d) / 3) ** 2)[..., None]
            cv += edge * (np.array([255.0, 44, 66]), np.array([150.0, 215, 255]), np.array([245.0, 180, 70]))[(k + 1) % 3] * 0.7 * (f > 0.02)
        return np.clip(cv, 0, 255)
    return "moons", "three moons", scene

# ---- the demonic gorilla: prayer switching under falling boulders -----------------
def crash_backdrop():
    """the crash site caverns: mossy black rock, the ribs of the wrecked glider overhead"""
    img = ramp([(20, 26, 18), (30, 38, 24), (38, 42, 26)], np.clip(YY / 170, 0, 1)) * (0.6 + 0.8 * fbm(1301, (28, 12, 5))[..., None])
    moss = np.clip(fbm(1303, (14, 6)) * 3 - 1.45, 0, 1)[..., None]
    img = img + moss * np.array([4.0, 16, 2])
    for x0, x1, y0, y1 in ((-10, 66, 0, 58), (34, 96, -4, 70), (W + 6, W - 54, 4, 66), (W - 22, W - 96, -2, 54)):
        im = Image.new("L", (W, H), 0); ImageDraw.Draw(im).line([(x0, y0), ((x0 + x1) / 2, (y0 + y1) / 2 - 10), (x1, y1)], fill=255, width=7)
        m = np.asarray(im, float)[..., None] / 255
        img = img * (1 - m) + np.array([72.0, 56, 36]) * (0.7 + 0.5 * fbm(1305, (6, 2))[..., None]) * m
    floor = np.clip((YY - 170 + 8 * (fbm(1307, (28, 10)) - 0.5)) / 4, 0, 1)[..., None]
    return img * (1 - floor) + np.array([44.0, 42, 30]) * (0.6 + 0.7 * fbm(1309, (12, 5, 2))[..., None]) * (0.8 + 0.3 * np.clip((YY - 170) / 70, 0, 1))[..., None] * floor

def prayer_icon(cv, x, y, k, power, flash):
    """the overhead he is praying: a tilted sword, a bow and arrow, or a mage's star, in a disc"""
    tone = [(255, 140, 100), (160, 255, 120), (150, 205, 255)][k]
    disc = np.clip((15 - np.hypot(XX - x, YY - y)) / 1.5, 0, 1)[..., None] * power
    cv[:] = cv * (1 - 0.82 * disc) + np.array([18.0, 18, 22]) * 0.82 * disc
    ring(cv, x, y, 15, 1.0, 2.2, np.array(tone, float) * power * (1 + flash))
    ink = np.clip(np.array(tone, float) * (0.9 + 0.3 * flash), 0, 255) * power
    if k == 0:
        line(cv, (x - 7, y + 8), (x + 8, y - 9), ink, 2.2); line(cv, (x - 9, y + 1), (x, y + 10), ink, 2.0)
    elif k == 1:
        polar(cv, x + 3, y, lambda th: 10.0, ink, rot=1.9, size=1.5, upto=0.36)
        line(cv, (x - 9, y), (x + 9, y), ink, 1.7); line(cv, (x - 9, y), (x - 4, y - 5), ink, 1.6); line(cv, (x - 9, y), (x - 4, y + 5), ink, 1.6)
    else:
        for i in range(5):
            a = -math.pi / 2 + i * 2 * math.pi / 5
            line(cv, (x, y), (x + 10 * math.cos(a), y + 10 * math.sin(a)), ink, 1.7)
        stamp(cv, x, y, ink, 3.2)

def gorilla_prayers():
    rgb0, al0 = fit(cut_sprite("Demonic_gorilla.png"), W + 2, H - 40, exposure=0.92, gamma=0.8, sat=1.0, grain=0.5)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 2; ground = H - 10; top = ground - h
    rig = Rig(rgb0, al0, pad=44)
    base = rig.rgb.copy(); lum = lum_of(rig, 14, 90)
    states = [ramp([(44, 8, 6), (130, 28, 18), (225, 76, 44), (255, 220, 190)], lum)[..., :3],
              ramp([(8, 40, 12), (28, 104, 30), (106, 210, 70), (232, 255, 196)], lum)[..., :3],
              ramp([(10, 22, 70), (28, 66, 164), (88, 150, 245), (224, 240, 255)], lum)[..., :3]]
    # the skull and claws stay bone and black: the body changes, not the skull's reading
    yy_, xx_ = (rig.yy - rig.pad) / h, (rig.xx - rig.pad) / w
    skull_m = np.clip((1 - np.hypot((xx_ - 0.27) / 0.12, (yy_ - 0.36) / 0.15)) / 0.35, 0, 1)[..., None]
    tones = [np.array([255.0, 80, 50]), np.array([110.0, 240, 80]), np.array([90.0, 160, 255])]
    rim = rim_of(rig, 5)
    back = crash_backdrop()
    # boulders fall on golden-section spots of the floor
    falls = [(W * 0.84, 0.12, 20, H - 26), (W * 0.14, 0.45, 23, H - 18), (W * 0.62, 0.78, 21, H - 8), (W * 0.36, 0.28, 13, H - 34)]
    rock_tex = _layer(1311, 3)
    dust = Motes(1312, 26, lambda r: ((r.rand() - 0.5) * 16, 0, (r.rand() - 0.5) * 100, -30 * r.rand() - 6, 60))
    chips = Motes(1313, 14, lambda r: (0, 0, (r.rand() - 0.5) * 90, -40 - 60 * r.rand(), 150))
    motes = Motes(1314, 16, lambda r: (r.rand() * W, 30 + r.rand() * 130, 8 * (r.rand() - 0.5), 26 * r.rand(), 0))
    def boulder(cv, x, y, r):
        d = 1 - ((XX - x) / r) ** 2 - ((YY - y) / (r * 0.92)) ** 2
        m = np.clip(d * r * 0.6, 0, 1)[..., None]
        lit = np.clip(0.55 + 0.7 * ((x - XX) * 0.6 + (y - YY)) / r, 0.1, 1.2)
        col = np.array([140.0, 116, 88]) * lit[..., None] * (0.72 + 0.5 * rock_tex[(YY.astype(int)) % H, XX.astype(int) % W][..., None])
        cv[:] = cv * (1 - m) + col * m
    def scene(t):
        p = t * 3; k = int(p) % 3; u = p % 1
        tone = tones[k]
        flash = math.exp(-u * 13)
        at = 0.46
        wind = sm(0.1, at - 0.02, u) * (1 - sm(at - 0.02, at + 0.01, u))
        go = sm(at - 0.02, at + 0.03, u) * (1 - sm(0.68, 0.92, u))
        hit = math.exp(-max(0.0, u - at) * 11) if u >= at else 0.0
        # the room takes his colour; the last two beats the new one lands on top, with a pale blink
        cv = back * (0.74 + 0.1 * wind + 0.22 * hit + 0.24 * flash)
        cv += np.exp(-((XX - W * 0.42) / 110) ** 2 - ((YY - 120) / 90) ** 2)[..., None] * tone * (0.10 + 0.08 * wind + 0.14 * hit + 0.1 * flash)
        cv = feather(cv)
        motes.draw(cv, t, lambda v: np.array((96, 92, 70)) * math.sin(math.pi * v), 1.0)
        impact = 0.0
        late = []
        for x, t0, r, gy in falls:
            s = since(t, t0)
            if s < 0.16:                                                        # the telegraph: its shadow grows on the floor
                f = s / 0.16
                shadow(cv, x, gy, r * (0.5 + 0.9 * f), r * 0.32 * (0.5 + 0.9 * f), 0.65)
                ring(cv, x, gy, r * 1.3, 0.3, 2, np.array((240.0, 200, 110)) * (0.4 + 0.6 * f))
                late.append((x, gy - 6 - 230 * (1 - f) ** 1.6, r, 1))
            elif s < 0.4:
                f = (s - 0.16) / 0.24
                impact = max(impact, math.exp(-(s - 0.16) * 18) * (r / 15))
                ring(cv, x, gy, r + 62 * f, 0.3, 4, np.array((205.0, 170, 120)) * (1 - f))
                late.append((x, gy - 6, r, 0))
            else:
                # the broken rock sinks away over the rest of the lap
                f = (s - 0.4) / 0.14
                if f < 1: late.append((x, gy - 5 + 4 * f, r * (1 - 0.3 * f), 0))
        shadow(cv, left + w * 0.38, ground, w * 0.5, 7, 0.5)
        for x, y, r, flying in late:
            if not flying and y > 0: boulder(cv, x, y, r)
        slam = (k == 0)
        breath = wave(t, 2)
        lean = (13 * wind - 18 * go) * (1.0 if slam else 0.7)
        drop = (-18 * wind + 18 * go) * (1.0 if slam else 0.7) + 2.5 * breath
        head = dict(c=(0.27, 0.38), r=(0.13, 0.17), p=(0.32, 0.50), a=4 * wave(t, 2, phi_phase(1)) + 7 * wind - 6 * go, lit=0.1 * wind, soft=0.5)
        # the green vines lash at their tips, out of step
        def vine_bend(x, y):
            a = np.clip((0.35 - x) / 0.35, 0, 1) * (y < 0.32)
            b = np.clip((x - 0.45) / 0.55, 0, 1) * (y < 0.42)
            return 0, 7 * a * math.sin(2 * math.pi * (t * 2 + 0.1)) * 1.0 + 12 * b ** 1.5 * np.sin(2 * math.pi * (t * 2 - x * 1.3))
        arm_l = dict(c=(0.15, 0.70), r=(0.13, 0.26), p=(0.19, 0.50), a=9 * wind * (slam) - 7 * go * slam + 3 * wave(t, 1, phi_phase(2)), soft=0.45)
        arm_r = dict(c=(0.60, 0.72), r=(0.14, 0.27), p=(0.56, 0.48), a=-8 * wind * (not slam) * 1.0 + 7 * go * (not slam) + 3 * wave(t, 1, phi_phase(3)), soft=0.45)
        parts = [dict(c=(0.47, 0.47), r=(0.15, 0.14), p=(0.47, 0.47), d=(0, -0.016 * breath), soft=0.9), arm_r, arm_l, head]
        def body(x, y):
            up = up_of(y, 0.88)
            vx, vy = vine_bend(x, y)
            return -lean * up + vx, -drop * up + vy
        mix = 0.74 + 0.26 * wind
        skin = base * (1 - mix) + states[k] * mix
        skin = skin * (1 - skull_m) + base * 1.1 * skull_m
        rig.rgb = np.clip(skin * (1 - rim * 0.4) + (tone * 0.6 + 80) * rim * 0.4 + tone * 0.3 * (hit + 0.8 * flash), 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        def on(part, fx, fy):
            x, y = rig.at(part, fx, fy); x, y = bent(x, y, fy, lean, drop, 0.88); return left + x, top + y
        for fx in (0.235, 0.305):
            ex, ey = on(head, fx, 0.37)
            glow(cv, ex, ey, 3 + 2 * wind, tone, 0.5 + 0.7 * wind)
        ix, iy = on(head, 0.415, 0.09)
        prayer_icon(cv, ix, iy, k, 1.0, flash)
        v = u - at
        # his style: melee slams the ground, ranged hurls a green shot, magic an orb from the skull
        if k == 0 and 0 <= v < 0.28:
            f = v / 0.28; hx, hy = on(arm_l, 0.12, 0.92)
            for i, rr in enumerate((f * 120, f * 80)):
                ring(cv, hx, ground - 4, 8 + rr, 0.28, 5 - i, np.array((255.0, 130, 80)) * (1 - f))
            for t0, (_, _, vx, vy, g) in chips.p:
                stamp(cv, hx + vx * f, ground - 4 + vy * f + g * f * f, np.array((230.0, 150, 100)) * (1 - f), 1.6)
        if k == 1:
            hx, hy = on(arm_r, 0.60, 0.95)
            if 0 <= v < 0.26:
                f = v / 0.26
                sx, sy = hx - 10, hy - 16
                for j in range(6):
                    q = f - j * 0.035
                    if q > 0:
                        stamp(cv, sx - 150 * q, sy - 90 * q + 95 * q * q, np.array((160.0, 255, 110)) * (1 - j * 0.14), 4.4 - j * 0.6)
                glow(cv, sx - 150 * f, sy - 90 * f + 95 * f * f, 11, tone, 0.8)
        if k == 2:
            ex, ey = on(head, 0.27, 0.42)
            if wind > 0.02:
                glow(cv, ex, ey, 6 + 10 * wind, tone, wind)
                for i in range(9):
                    q = (t * 6 + phi_phase(i)) % 1.0; a = i * GOLDEN_ANGLE
                    stamp(cv, ex + 40 * (1 - q) * math.cos(a), ey + 40 * (1 - q) * math.sin(a), np.array((190.0, 225, 255)) * q * wind, 1.5)
            if 0 <= v < 0.24:
                f = v / 0.24
                gx, gy = ex - 80 * f, ey + 60 * f
                glow(cv, gx, gy, 12 + 14 * f, tone, 1.0)
                stamp(cv, gx, gy, (235, 245, 255), 6 * (1 - f * 0.5))
                for i in range(2):
                    bolt(cv, gx, gy, gx - 26 - i * 10, gy + 40 - i * 60, 1320 + i + int(t * FRAMES), np.array((170.0, 210, 255)) * (1 - f * 0.6), jag=26, size=1.1)
        # flying boulders pass in front of him; the landing throws dust in front too
        for x, y, r, flying in late:
            if flying: boulder(cv, x, y, r); glow(cv, x, y - r, r, (230, 180, 110), 0.12)
        for x, t0, r, gy in falls:
            s = since(t, t0)
            if 0.16 <= s < 0.36:
                f = (s - 0.16) / 0.2
                for t1, (dx, _, vx, vy, g) in dust.p:
                    stamp(cv, x + dx + vx * f, gy + vy * f + g * f * f * 0.5, np.array((156.0, 134, 102)) * (1 - f) * 0.85, 2.0)
        s_ = t * 40
        cv = shake(cv, impact * 4 * math.sin(s_ * 3), impact * 3 * math.cos(s_ * 4))
        return np.clip(cv, 0, 255)
    return "gorilla", "prayer switch", scene

# ---- the Hueycoatl: a wave runs along her body, then she lets go ------------------
def ruins_backdrop():
    """Tapoyauik at dusk: a plum-to-amber sky, a low sun, a stepped pyramid, jungle at the foot"""
    sky = ramp([(24, 20, 44), (44, 28, 58), (80, 38, 54), (104, 58, 40)], np.clip(YY / 118, 0, 1))
    img = sky * (0.86 + 0.28 * fbm(1401, (46, 20, 8))[..., None])
    glow(img, W * 0.76, 36, 40, (60, 34, 16), 0.9)
    sun = np.clip(22 - np.hypot(XX - W * 0.76, YY - 36), 0, 1)[..., None]
    img = img * (1 - sun) + np.array([128.0, 84, 44]) * sun
    def step(cx, base, hw, n, rise, colour, seed):
        nonlocal img
        for i in range(n):
            x0, x1 = cx - hw * (1 - i / n), cx + hw * (1 - i / n)
            y1, y0 = base - rise * i, base - rise * (i + 1)
            m = ((XX >= x0) & (XX <= x1) & (YY >= y0) & (YY <= y1))[..., None]
            img = img * (1 - m) + np.array(colour, float) * (0.7 + 0.5 * fbm(seed, (8, 3))[..., None]) * (1.15 - 0.3 * (i % 2)) * m
    step(W * 0.16, 122, 60, 8, 11, (46, 32, 40), 1403)
    far = ridge(1405, 118, 8, (22, 9, 4)); near = ridge(1407, 134, 12, (18, 8, 4))
    m = (YY > far[None, :])[..., None]
    img = img * (1 - m) + np.array([32.0, 34, 34]) * (0.6 + 0.7 * fbm(1409, (10, 4))[..., None]) * m
    m = (YY > near[None, :])[..., None]
    z = np.clip((YY - 134) / (H - 134), 0, 1)
    img = img * (1 - m) + np.array([58.0, 50, 40]) * (0.45 + 0.75 * fbm(1411, (16, 6, 3))[..., None]) * (0.6 + 0.5 * z[..., None]) * m
    return img

def huey_surge():
    rgb0, al0 = fit(cut_sprite("The_Hueycoatl.png"), W * 1.4, H, exposure=0.82, gamma=1.0, sat=1.6, grain=0.25)
    # the rock she bursts from goes down into shade, so the head and the wings are what reads
    hh_, ww_ = al0.shape[:2]; yy_, xx_ = np.mgrid[0:hh_, 0:ww_] / np.array([hh_, ww_])[:, None, None]
    rgb0 = rgb0 * (1 - 0.6 * np.clip((yy_ - 0.52) / 0.14, 0, 1))[..., None]
    h, w = al0.shape[:2]; left = 2; ground = H - 8; top = ground - h
    rig = Rig(rgb0, al0, pad=44)
    base = rig.rgb.copy(); lum = lum_of(rig)
    charged = ramp([(62, 18, 10), (178, 76, 18), (250, 176, 52), (255, 240, 190)], lum)[..., :3]
    yy2, xx2 = (rig.yy - rig.pad) / h, (rig.xx - rig.pad) / w
    face = np.clip((1 - np.hypot((xx2 - 0.30) / 0.10, (yy2 - 0.44) / 0.13)) / 0.4, 0, 1)[..., None]      # the face keeps its own colours
    # the body, in the rock it wears; each hump farther off, on the golden sections, S-curving away
    humps = []
    for cx, gy, wide, seed in ((W * 0.97, H + 8, 124, 3), (W * 0.88, 88, 86, 5), (W * 0.63, 68, 46, 7)):
        r_, a_ = fit(cut_sprite("Hueycoatl_body.png"), wide, wide, exposure=0.62, gamma=1.05, sat=1.2, grain=0.4, seed=seed)
        lv = np.clip(np.asarray(Image.fromarray(np.clip(heal(r_, 9).mean(2), 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1)), float) / 170, 0, 1)
        hump = ramp([(38, 30, 34), (74, 58, 54), (120, 98, 80), (168, 142, 110)], lv)[..., :3]
        # scale bands and a line of feathers along the top, so a rock hump reads as her back
        hy, hx = np.mgrid[0:a_.shape[0], 0:a_.shape[1]] / np.array(a_.shape[:2])[:, None, None]
        hump = hump * (0.74 + 0.3 * np.sin(hx * 24 + 3 * np.sin(hy * 6)))[..., None]
        rim_ = a_[..., 0] - np.asarray(Image.fromarray((a_[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(max(5, wide // 11 | 1))), float) / 255
        crest_ = (rim_ * np.clip(1.25 - hy * 1.6, 0, 1))[..., None]
        feather_ = np.where(np.sin(hx * 34) > 0, 1.0, 0.0)[..., None]
        hump = hump * (1 - crest_) + (np.array([84.0, 200, 186]) * feather_ + np.array([236.0, 104, 76]) * (1 - feather_)) * crest_
        humps.append(dict(rgb=hump, al=a_, cx=cx, gy=gy, wide=wide, rig=Rig(hump, a_, pad=12)))
    humps.sort(key=lambda d: d["gy"])                                           # far to near
    back = ruins_backdrop()
    dirt = Motes(1421, 26, lambda r: ((r.rand() - 0.5), 0, (r.rand() - 0.5) * 2.2, -1.0 - 1.1 * r.rand(), 1.5))
    embers = Motes(1422, 22, lambda r: (r.rand() * W, 130 + r.rand() * 60, 26 * (r.rand() - 0.5), -60 * r.rand(), 0))
    orbs = [((255, 80, 54), (-120, -14), 0.0, (28, 160)), ((96, 170, 255), (-60, 40), 0.05, (104, 226)), ((130, 245, 100), (-140, 70), 0.10, (190, 222))]
    # the surge: far hump, middle, near, then her
    AT = [0.06, 0.18, 0.30, 0.44]
    def scene(t):
        rear = sm(AT[3] - 0.1, AT[3] + 0.04, t) * (1 - sm(0.56, 0.72, t))
        fire = since(t, 0.5)
        fire_on = fire < 0.26
        hit = math.exp(-since(t, 0.5) * 10)
        wind = sm(0.32, 0.5, t) * (1 - sm(0.5, 0.53, t))
        charged_f = sm(0.34, 0.5, t) * (1 - sm(0.64, 0.86, t))
        cv = back * (0.8 + 0.25 * hit)
        cv += np.exp(-((XX - 70) / 100) ** 2 - ((YY - 110) / 90) ** 2)[..., None] * np.array([30.0, 44, 52]) * charged_f
        cv = feather(cv)
        embers.draw(cv, t, lambda v: np.array((180, 130, 70)) * math.sin(math.pi * v), 0.9)
        def draw_hump(i, d):
            s = since(t, AT[i])
            # surge: heaves up out of the ground, sinks back; the floor stays where it is
            up = sm(0.0, 0.1, s) * (1 - sm(0.16, 0.42, s))
            hh, ww = d["al"].shape[:2]
            base_up = 0.72 + 0.05 * wave(t, 2, phi_phase(i))
            rise = (base_up + 0.3 * up) * hh
            x0 = int(d["cx"] - ww / 2); y0 = int(d["gy"] - rise)
            shadow(cv, d["cx"], d["gy"], ww * 0.6, ww * 0.1, 0.5)
            rgb2, al2 = d["rig"].pose([dict(c=(0.5, 0.25), r=(0.45, 0.3), p=(0.5, 0.5), d=(0, -0.05 * up), soft=0.9)], light=(wave(t, 1), -0.7), relief=0.3)
            over_above(cv, rgb2 * (0.8 if d["gy"] < 200 else 0.74) + np.array([34.0, 26, 18]) * up, al2, x0 - 12, y0 - 12, d["gy"])
            if s < 0.26:                                                        # dirt thrown off where it broke the ground
                f = s / 0.26; sc_ = ww * 0.52
                for t0, (dx, _, vx, vy, g) in dirt.p:
                    q = f * (0.6 + 0.4 * t0)
                    stamp(cv, d["cx"] + dx * sc_ + vx * sc_ * q, d["gy"] + vy * sc_ * q + g * sc_ * q * q, np.array((150.0, 120, 86)) * (1 - f), max(1.0, ww / 40))
                ring(cv, d["cx"], d["gy"], sc_ * (0.9 + 1.2 * f), 0.26, 2 + ww / 30, np.array((170.0, 140, 100)) * (1 - f))
        draw_hump(0, humps[0]); draw_hump(1, humps[1])
        # the furrow: a ridge of disturbed ground running from the last hump to her, as the wave reaches her
        s4 = since(t, AT[2] + 0.04)
        if s4 < 0.16:
            f = s4 / 0.16
            fx, fy = W * 0.9 + (70 - W * 0.9) * f, 206 + 16 * f
            ring(cv, fx, fy, 16, 0.3, 5, np.array((190.0, 150, 100)) * (1 - f * 0.4))
            for j in range(10):
                g_ = f - j * 0.015
                if g_ > 0: stamp(cv, W * 0.9 + (70 - W * 0.9) * g_, 206 + 16 * g_ + 2 * math.sin(j * 2), np.array((120.0, 95, 70)), 2.4)
        breath = wave(t, 2)
        lift = 15 * rear - 7 * hit
        shadow(cv, left + w * 0.5, ground, w * 0.42, 7, 0.5)
        head = dict(c=(0.30, 0.42), r=(0.135, 0.14), p=(0.38, 0.52), a=-6 * wind + 7 * hit + 3 * wave(t, 2, phi_phase(1)), d=(-0.02 * hit, -0.03 * wind + 0.02 * hit), lit=0.12 * charged_f, soft=0.5)
        jaw = dict(c=(0.285, 0.50), r=(0.075, 0.05), p=(0.34, 0.47), a=head["a"] - 15 * max(wind, min(1.0, hit * 1.6)) + 3, d=head["d"], soft=0.5)
        crest = dict(c=(0.34, 0.15), r=(0.17, 0.16), p=(0.36, 0.32), a=4 * wave(t, 2, phi_phase(2)) - 9 * wind + 8 * hit, d=(0, -0.025 * wind), soft=0.5)
        wl = dict(c=(0.10, 0.30), r=(0.15, 0.27), p=(0.24, 0.27), a=-8 * wave(t, 2, phi_phase(3)) - 9 * rear + 10 * hit, soft=0.45)
        wr = dict(c=(0.82, 0.36), r=(0.26, 0.33), p=(0.58, 0.27), a=7 * wave(t, 2, phi_phase(4)) + 8 * rear - 9 * hit, soft=0.4)
        parts = [dict(c=(0.5, 0.65), r=(0.2, 0.15), p=(0.5, 0.65), d=(0, -0.01 * breath), soft=0.9), wl, wr, crest, head, jaw]
        def body(x, y):
            up = np.clip(0.96 - y, 0, 1) / 0.96
            return 8 * up * rear - 9 * hit * up, lift * up + 2 * breath * up
        mix = charged_f * (1 - 0.8 * face)
        rig.rgb = np.clip(base * (1 - mix) + charged * mix + np.array([70.0, 50, 20]) * hit * lum[..., None], 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.7), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        draw_hump(2, humps[2])                                                  # the near coil crosses in front of her
        def on(part, fx, fy):
            x, y = rig.at(part, fx, fy); u_ = float(np.clip((0.96 - fy) / 0.96, 0, 1))
            return left + x + (8 * rear - 9 * hit) * u_, top + y + lift * u_
        mx, my = on(jaw, 0.27, 0.495)
        ex, ey = on(head, 0.30, 0.39)
        glow(cv, ex, ey, 3 + 2 * charged_f, (255, 110, 60), 0.5 + 0.6 * charged_f)
        if wind > 0.02:
            glow(cv, mx, my, 5 + 12 * wind, (240, 225, 180), 0.75 * wind)
            for i, ((col), _, _, _) in enumerate(orbs):
                a = 2 * math.pi * (t * 3 + i / 3)
                stamp(cv, mx + 11 * wind * math.cos(a), my + 11 * wind * math.sin(a), col, 3.2 * wind)
        if fire_on:                                                             # all three of her styles at once, a telegraph already on the floor
            for col, _, delay, land in orbs:
                f = (fire - delay) / 0.2
                if 0 <= f < 1:
                    x_ = mx + (land[0] - mx) * f; y_ = my + (land[1] - my) * f - 50 * math.sin(math.pi * f)
                    for j in range(5):
                        g_ = max(0.0, f - j * 0.05)
                        stamp(cv, mx + (land[0] - mx) * g_, my + (land[1] - my) * g_ - 50 * math.sin(math.pi * g_), np.array(col, float) * (1 - j * 0.17), 8.0 - j * 1.1)
                    glow(cv, x_, y_, 14, col, 0.8)
                elif f >= 1:
                    q = min(1.0, (f - 1) * 3)
                    ring(cv, land[0], land[1], 6 + q * 46, 0.28, 4, np.array(col, float) * (1 - q))
        return np.clip(cv, 0, 255)
    return "huey", "the surge", scene

# ---- Vet'ion: enraged, under his own lightning ----------------------------------
def hall_backdrop():
    """his hall under the Wilderness: cold blue-violet stone, tall windows on a stormy sky"""
    img = coursed(26, 50, 1501, (32, 34, 56), (24, 10, 4)) * 0.82
    for cx in (34, W - 34):
        im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
        d.ellipse((cx - 17, 18, cx + 17, 52), fill=255); d.rectangle((cx - 17, 36, cx + 17, 140), fill=255)
        m = np.asarray(im, float)[..., None] / 255
        storm = ramp([(22, 26, 56), (40, 48, 92), (66, 76, 122)], fbm(1503, (20, 9, 4)))[..., :3]
        img = img * (1 - m) + storm * m
        bar = ((np.abs(XX - cx) < 1.2) | (YY % 24 < 1.5))[..., None] * m
        img = img * (1 - 0.8 * bar)
    z = np.clip((YY - 172) / (H - 172), 0, 1)
    floor = (YY > 172)[..., None]
    across = (XX - W / 2) / (0.4 + z)
    grout = ((np.abs(across % 44 - 22) > 20.8) | ((np.log(z + 0.2) * 4) % 1 < 0.07))
    flags = np.array([44.0, 44, 66]) * (0.55 + 0.6 * z[..., None]) * (0.75 + 0.5 * fbm(1505, (14, 6))[..., None]) * (1 - 0.55 * grout[..., None])
    return img * (1 - floor) + flags * floor

def hellhound(cv, x, y, s, face, t, ph, bone, eye, lunge=0.0):
    """a skeleton hellhound in bold strokes: a shrunk sprite would dissolve at this size and none exists"""
    step = 2 * math.pi * (t * 6 + ph)
    y0 = y - 3.6 * s + math.sin(step * 2) * 0.3 * s - lunge * 1.6 * s
    hip = (x - face * 3.2 * s, y0 + lunge * 1.2 * s); sho = (x + face * 2.8 * s, y0 - 0.2 * s - lunge * 0.8 * s)
    def leg(a, ph2):
        sw = math.sin(step + ph2)
        return [a, (a[0] + face * sw * 0.8 * s, a[1] + 1.8 * s), (a[0] + face * sw * 1.7 * s, y - max(0.0, math.cos(step + ph2)) * 0.8 * s)]
    strokes(cv, [leg(hip, 0.0), leg(sho, 2.0), leg((hip[0] + face * 0.8 * s, hip[1]), 3.6), leg((sho[0] - face * 0.8 * s, sho[1]), 5.4)], np.array(bone, float) * 0.72, max(2, int(s * 0.9)))
    # the spine and rib cage
    strokes(cv, [[hip, ((hip[0] + sho[0]) / 2, y0 - 0.9 * s), sho]], bone, max(2, int(s * 1.1)))
    ribs = [[(hip[0] + (sho[0] - hip[0]) * f, y0 - 0.6 * s), (hip[0] + (sho[0] - hip[0]) * f, y0 + 1.1 * s)] for f in (0.25, 0.5, 0.75)]
    strokes(cv, ribs, np.array(bone, float) * 0.86, max(1, int(s * 0.7)))
    strokes(cv, [[hip, (hip[0] - face * 1.8 * s, hip[1] - 1.8 * s + math.sin(step) * 0.5 * s)]], np.array(bone, float) * 0.8, max(1, int(s * 0.6)))
    hx, hy = sho[0] + face * 2.0 * s, sho[1] - 1.0 * s
    strokes(cv, [[sho, (hx, hy)]], bone, max(2, int(s)))
    snap = 0.5 + 0.5 * math.sin(step * 2)
    strokes(cv, [[(hx, hy), (hx + face * 2.4 * s, hy + 0.1 * s)]], bone, max(2, int(s * 1.0)))
    strokes(cv, [[(hx, hy + 0.6 * s), (hx + face * 2.0 * s, hy + (0.7 + 0.8 * snap + lunge) * s)]], np.array(bone, float) * 0.8, max(1, int(s * 0.7)))
    stamp(cv, hx + face * 0.6 * s, hy - 0.3 * s, eye, 1.5 + 0.3 * s)
    return hx, hy

def vetion_strikes():
    rgb0, al0 = fit(cut_sprite("Vet'ion_(enraged).png"), W, H - 32, exposure=0.8, gamma=1.02, sat=1.05, grain=0.3)
    rgb0 = heal(rgb0, 3)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 2; ground = H - 16; top = ground - h
    rig = Rig(rgb0, al0, pad=40)
    base = rig.rgb.copy(); lum = lum_of(rig)
    molten = ramp([(62, 14, 4), (176, 58, 10), (246, 140, 30), (255, 226, 150)], lum)[..., :3]
    storm = ramp([(22, 34, 96), (70, 110, 214), (160, 210, 255), (255, 255, 255)], lum)[..., :3]
    back = hall_backdrop()
    # strike sites: the corners of a flat pentagon around him, called in star order
    verts = polygon_pts(W / 2, 208, 92, 5, rot=math.pi / 2, squash=0.25)
    sites = [(verts[i], 0.04 + 0.12 * k) for k, i in enumerate((1, 3, 0, 2, 4))]
    FLY = 0.11
    slam = 0.74
    sparks = Motes(1511, 18, lambda r: (0, 0, (r.rand() - 0.5) * 80, -40 - 60 * r.rand(), 150))
    ash = Motes(1512, 26, lambda r: (r.rand() * W, r.rand() * 160, -24 - 18 * r.rand(), 34 + 20 * r.rand(), 0))
    def scene(t):
        def state_of(tt): return sm(0.0, 0.1, tt) * (1 - sm(0.13, 0.36, tt))
        flash = 0.0
        for (_, t0) in sites:
            s = since(t, t0 + FLY)
            flash = max(flash, math.exp(-s * 26))
        wind = sm(slam - 0.26, slam - 0.02, t) * (1 - sm(slam - 0.02, slam + 0.01, t))
        hit = math.exp(-since(t, slam) * 9)
        s_ = since(t, slam)
        go = sm(slam - 0.02, slam + 0.03, t) * (1 - sm(slam + 0.14, slam + 0.26, t)) if t > 0.3 else 0.0
        rage = sm(0.08, slam, t) * (1 - sm(slam + 0.05, 0.98, t))
        cv = back * (0.72 + 0.16 * rage + 0.46 * flash + 0.3 * hit)
        cv += np.exp(-((XX - W / 2) / 90) ** 2 - ((YY - 100) / 80) ** 2)[..., None] * np.array([28.0, 12, 2]) * rage
        cv = feather(cv)
        ash.draw(cv, t, lambda v: np.array((100, 100, 140)) * math.sin(math.pi * v) * 0.7, 0.9)
        pre = []
        # the telegraph: a rune lights on the flags while he calls it, then the bolt
        for (x, y), t0 in sites:
            s = since(t, t0)
            scale = 0.7 + 0.5 * (y - 190) / 40
            if s < FLY:
                f = s / FLY
                polygon(cv, x, y, 15 * scale, 5, np.array((255.0, 150, 50)) * (0.35 + 0.65 * f), rot=-math.pi / 2 + f, squash=0.34, size=1.1)
                ring(cv, x, y, 19 * scale * (1.5 - 0.5 * f), 0.34, 2.6, np.array((255.0, 110, 30)) * (0.4 + 0.6 * f))
                cv += (np.exp(-((XX - x) / (16 * scale)) ** 2 - ((YY - y) / (6 * scale)) ** 2) * f)[..., None] * np.array([90.0, 34, 6])
            elif s < FLY + 0.1:
                f = (s - FLY) / 0.1
                pre.append((x, y, scale, f, 1531 + int(t0 * 100) + int(t * FRAMES)))
                ring(cv, x, y, (8 + 44 * f) * scale, 0.32, 4, np.array((190.0, 225, 255)) * (1 - f))
                cv += (np.exp(-((XX - x) / (15 * scale)) ** 2 - ((YY - y) / (6 * scale)) ** 2) * (1 - f))[..., None] * np.array([170.0, 200, 255])
            elif s < FLY + 0.34:                                                # the flag it hit stays scorched a while
                f = (s - FLY - 0.1) / 0.24
                shadow(cv, x, y, 16 * scale, 5.5 * scale, 0.6 * (1 - f))
        shadow(cv, W / 2, ground, w * 0.52, 7, 0.55)
        # the hounds: come in on the near flags during the wind-up, lunge on the slam, and go to dust
        arrive = sm(0.22, 0.60, t) * (1 - sm(0.92, 0.99, t))
        lunge = sm(slam - 0.02, slam + 0.04, t) * (1 - sm(slam + 0.10, slam + 0.22, t))
        breath = wave(t, 2)
        lean = 7 * wind - 13 * go
        drop = -13 * wind + 13 * go + 2 * breath
        head = dict(c=(0.60, 0.14), r=(0.17, 0.14), p=(0.56, 0.25), a=3 * wave(t, 2, phi_phase(1)) + 7 * wind - 5 * go, lit=0.2 * rage, soft=0.5)
        mace = dict(c=(0.12, 0.56), r=(0.17, 0.36), p=(0.17, 0.20), a=13 * wind - 12 * go + 2.5 * wave(t, 2, phi_phase(2)), soft=0.3, d=(0.02 * wind, -0.05 * wind + 0.05 * go))
        shield = dict(c=(0.81, 0.33), r=(0.2, 0.28), p=(0.72, 0.17), a=-4 * wind + 5 * go + 2.5 * wave(t, 2, phi_phase(3)), soft=0.35, d=(0.012 * wind, -0.01 * wind))
        parts = [
            dict(c=(0.5, 0.42), r=(0.2, 0.11), p=(0.5, 0.42), d=(0, -0.012 * breath), soft=0.9),
            dict(c=(0.5, 0.64), r=(0.26, 0.09), p=(0.5, 0.56), a=3 * wave(t, 2, phi_phase(4)), soft=0.7),    # the cloth skirt swings late
            shield, mace, head]
        def body(x, y):
            u_ = up_of(y, 0.72)
            return -lean * u_, -drop * u_
        col = base * (1 - rage) + molten * rage
        fm = min(1.0, flash * 3)
        col = col * (1 - fm) + storm * fm
        rig.rgb = np.clip(col, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        darken_round(cv, al, left - rig.pad, top - rig.pad, 0.55, 4, (YY < 172))
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        def on(part, fx, fy):
            x, y = rig.at(part, fx, fy); return bent(left + x, top + y, fy, lean, drop, 0.72)
        ex, ey = on(head, 0.585, 0.135)
        glow(cv, ex, ey, 4 + 4 * rage, (255, 120, 30), 0.3 + 0.8 * rage)
        mx, my = on(mace, 0.125, 0.76)
        glow(cv, mx, my, 5 + 12 * wind, (255, 170, 60), 0.7 * wind)
        # hounds go in front of his feet, on the near flags
        for sx, t0, ph in ((-1, 0.0, 0.1), (1, 0.04, 0.6)):
            if arrive > 0.01:
                q = sm(0.22 + t0, 0.60 + t0, t)
                gone = sm(0.92, 0.98, t)
                xx = W / 2 + sx * (136 - 66 * q) - sx * 12 * lunge
                if gone < 0.9:
                    bonec = np.array((236.0, 228, 196)) * (1 - gone)
                    hellhound(cv, xx, H - 5, 4.3, -sx, t, ph, bonec, (255, 110, 30), lunge)
        for x, y, scale, f, seed in pre:                                        # the bolts come from the top of the frame, in front of everything
            power = (1 - f) ** 0.8
            bolt(cv, x + 10, -6, x, y, seed, np.array((215.0, 235, 255)) * power, jag=40, size=2.6 * scale + 0.5)
            bolt(cv, x + 10, -6, x, y, seed + 2, np.array((140.0, 180, 255)) * power * 0.6, jag=60, size=1.0)
            glow(cv, x, y, 24 * scale, (150, 190, 255), 0.75 * power)
            for t1, (_, _, vx, vy, g) in sparks.p[:9]:
                stamp(cv, x + vx * f * scale, y + vy * f * scale + g * f * f, np.array((205.0, 225, 255)) * (1 - f), 1.3)
        sx_, sy_ = 0, 0
        if s_ < 0.22:                                                           # the slam: an earth ring and one big shock
            f = s_ / 0.22
            ring(cv, mx, ground - 4, 10 + f * 170, 0.26, 6, np.array((255.0, 150, 50)) * (1 - f) ** 0.9)
            ring(cv, mx, ground - 4, 6 + f * 110, 0.26, 3, np.array((255.0, 225, 150)) * (1 - f))
            for t1, (_, _, vx, vy, g) in sparks.p:
                stamp(cv, mx + vx * f * 1.5, ground - 4 + vy * f * 0.9 + g * f * f * 0.9, np.array((255.0, 190, 90)) * (1 - f), 1.8)
            sx_, sy_ = hit * 4 * math.sin(s_ * 90), hit * 3 * math.cos(s_ * 120)
        cv = shake(cv, sx_, sy_)
        return np.clip(cv, 0, 255)
    return "vetion", "the storm", scene

RECIPES = [dks_tide, barrows_rise, moons_phases, gorilla_prayers, huey_surge, vetion_strikes]
