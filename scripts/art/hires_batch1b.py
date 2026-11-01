"""Hi-res recipes: yama, duke, whisperer, nightmare, corp."""
from hires_kit import *
from hires_kit import _layer

# ---- shared helpers ------------------------------------------------------------
def sm(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)

def since(t, t0): return (t - t0) % 1.0

def mask_of(paint):
    """a float H x W mask from PIL drawing calls"""
    im = Image.new("L", (W, H), 0); paint(ImageDraw.Draw(im))
    return np.asarray(im, float) / 255

def column(cv, x, ground, height, width, scroll, n1, n2, stops, power=1.0, flare=0.0, sway=0.0):
    """A standing fluid: noise rushing up a column, hottest low down. n1, n2 are _layer arrays."""
    sh = int(scroll * H)
    n = 0.6 * n1[(YY.astype(int) + sh) % H, XX.astype(int)] + 0.4 * n2[(YY.astype(int) + 2 * sh) % H, XX.astype(int)]
    v = (ground - YY) / max(height, 1)
    off = sway * np.sin(v * 5 + scroll * 6.283) * width
    half = width * (1 - 0.35 * np.clip(v, 0, 1) + flare * np.clip(v, 0, 1))
    body = 1 - np.abs(XX - x - off) / half
    den = np.clip(body * 1.7 + (n - 0.5) * 2.4 - np.clip(v, 0, 1) ** 3 * 1.5, 0, 1) * (v > 0) * (v < 1) * power
    cv[:] = cv * (1 - den[..., None] * 0.86) + ramp(stops, 1 - den * (1.0 - 0.62 * np.clip(v, 0, 1)))[..., :3] * den[..., None] * 0.86
    return den

def tendrils(cv, specs, t, core, rim):
    """Writhing strokes. A spec is (x, y, length, heading deg, curl deg, width, phase, cycles); a travelling wave runs root to tip."""
    a, b = Image.new("L", (W, H), 0), Image.new("L", (W, H), 0)
    da, db = ImageDraw.Draw(a), ImageDraw.Draw(b)
    tips = []
    for x, y, length, head, curl, width, ph, n in specs:
        px, py = x, y; steps = 26
        for i in range(steps + 1):
            s = i / steps
            ang = math.radians(head + curl * s * math.sin(2 * math.pi * (t * n + ph - s * 1.3)))
            r = width * (1 - s) ** 0.8 + 0.6
            da.ellipse((px - r - 1.0, py - r - 1.0, px + r + 1.0, py + r + 1.0), fill=255)
            db.ellipse((px - r, py - r, px + r, py + r), fill=255)
            px += math.cos(ang) * length / steps; py += math.sin(ang) * length / steps
        tips.append((px, py))
    ma, mb = np.asarray(a, float)[..., None] / 255, np.asarray(b, float)[..., None] / 255
    cv[:] = cv * (1 - ma) + np.array(rim, float) * ma
    tex = 0.75 + 0.5 * _layer(77, 4)[..., None]
    cv[:] = cv * (1 - mb) + np.array(core, float) * tex * mb
    return tips

def solid_sprite(name):
    """The sprite without its soft ground shadow, which kit.sprite() could make solid so the floor moves with the feet."""
    a = np.asarray(Image.open(SPR / name).convert("RGBA"), float).copy()
    mask = a[..., 3] > 128
    ys, xs = np.where(mask)
    a[..., 3] = mask * 255.0
    return Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.uint8), "RGBA")

def from_green(rgb, lo=45, span=60):
    return np.clip((rgb[..., 1] - rgb[..., 0] - lo) / span, 0, 1)[..., None]

def soften(rgb, al, k=0.45, rad=2.2, floor=0.42):
    """Thin dark seams land in a 5 x 10 cell as black dashes: blend in a blur and lift the shadows towards the body's mean colour."""
    blur = np.asarray(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(rad)), float)
    out = rgb * (1 - k) + blur * k
    mean = out[al[..., 0] > 0].mean(0)
    lum = out.mean(2, keepdims=True); target = mean.mean() * floor
    lift = np.clip((target - lum) / max(target, 1), 0, 1)
    return out * (1 - lift) + mean * floor * lift, al

def close_seams(rgb, size=5):
    """Grey closing: removes dark lines thinner than size while keeping shapes and bright spikes."""
    im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.MaxFilter(size)).filter(ImageFilter.MinFilter(size)), float)

# ---- backdrops -----------------------------------------------------------------
def chasm_backdrop():
    """the Chasm of Fire: burnt rock walls, spires against a low red glow, a lava floor"""
    sky = ramp([(26, 13, 14), (48, 19, 16), (84, 30, 16)], np.clip(YY / 190, 0, 1))
    tex = fbm(401)[..., None]
    img = sky * (0.75 + 0.5 * tex)
    far = 150 - 70 * np.abs(np.sin(XX * 0.055 + 1)) ** 4 - 20 * (fbm(405, (22, 9))[0, :][None, :] - 0.5)
    m = (YY > far)[..., None]
    img = img * (1 - m) + (np.array([50.0, 28, 26]) * (0.6 + 0.8 * tex) + np.clip(1 - (YY - far) / 10, 0, 1)[..., None] * np.array([34.0, 12, 4])) * m
    near = ridge(408, 190, 14)
    m = (YY > near[None, :])[..., None]
    img = img * (1 - m) + (np.array([44.0, 26, 24]) * (0.6 + 0.8 * fbm(412, (18, 7, 3))[..., None])) * m
    return img

def lava_seams(seed):
    return np.clip(1 - np.abs(fbm(seed, (28, 12, 5)) - 0.5) / 0.022, 0, 1) * (YY > 192)

def stronghold_backdrop():
    """Ghorrock: big frozen blocks, two columns, hoar on every course"""
    course, brick = 34, 54
    row = (YY // course).astype(int); off = (row % 2) * brick // 2
    col = ((XX + off) // brick).astype(int)
    shade = np.random.RandomState(501).rand(40, 40)[row % 40, col % 40]
    joint = ((YY % course < 2) | ((XX + off) % brick < 2))
    frost = fbm(510, (26, 9, 4))
    stone = np.array([30.0, 40, 54]) * ((0.7 + 0.5 * shade) * (0.7 + 0.7 * frost))[..., None]
    img = stone * (1 - 0.55 * joint[..., None]) + np.clip(1 - (YY % course) / 7, 0, 1)[..., None] * np.array([8.0, 12, 18])
    for cx in (20, W - 20):
        m = (np.abs(XX - cx) < 15)[..., None]
        form = np.clip(1 - np.abs(XX - cx + 5) / 13, 0, 1)[..., None]
        img = img * (1 - m) + (np.array([26.0, 36, 50]) + form * np.array([22.0, 30, 38])) * (0.75 + 0.5 * frost[..., None]) * m
    floor = np.array([40.0, 52, 66]) * (0.7 + 0.6 * fbm(520, (20, 7))[..., None]) * (0.8 + 0.4 * np.clip((YY - 200) / 40, 0, 1)[..., None])
    m = (YY > 200)[..., None]
    return img * (1 - m) + floor * m

def lassar_backdrop():
    """drowned Lassar: arches and columns in murky water, falling shafts of light"""
    img = ramp([(30, 58, 66), (20, 44, 54), (12, 28, 36)], np.clip(YY / H, 0, 1))
    img = img * (0.8 + 0.4 * fbm(601, (30, 12, 5))[..., None])
    tex = fbm(610, (14, 6, 3))[..., None]
    def arches(d):
        for cx in (34, 107, 180):
            d.ellipse((cx - 30, 34, cx + 30, 94), fill=255)
            d.rectangle((cx - 30, 64, cx + 30, 196), fill=255)
    gaps = mask_of(arches)[..., None]
    wall = np.array([40.0, 66, 70]) * (0.6 + 0.8 * tex) * (1 - gaps) + gaps * img * 0.7
    img = wall
    for cx in (70, 144):
        m = (np.abs(XX - cx) < 6)[..., None]
        img = img * (1 - m) + (np.array([34.0, 56, 60]) * (0.6 + 0.8 * tex)) * m
    m = (YY > 196 + 6 * (fbm(615, (26, 9))[0, :][None, :] - 0.5))[..., None]
    return img * (1 - m) + np.array([34.0, 52, 54]) * (0.6 + 0.8 * tex) * m

def shadow_backdrop():
    """the shadow realm: the same ruin, drained, with a black and violet murk"""
    img = ramp([(14, 8, 26), (24, 10, 40), (10, 6, 18)], np.clip(YY / H, 0, 1))
    return img * (0.65 + 0.7 * fbm(620, (40, 16, 6))[..., None])

def gauntlet_backdrop():
    """the Gauntlet: cold tiled floor, walls grown over with crystal"""
    img = ramp([(14, 16, 28), (24, 26, 44), (30, 36, 56)], np.clip(YY / 180, 0, 1)) * (0.75 + 0.5 * fbm(701)[..., None])
    r = np.random.RandomState(705)
    for i in range(16):
        x = r.rand() * W; base = 172 + r.rand() * 12; hh = 44 + r.rand() * 96; ww = 7 + r.rand() * 12; lean = (r.rand() - 0.5) * 28
        lit = mask_of(lambda d: d.polygon([(x - ww, base), (x + lean, base - hh), (x, base)], fill=255))[..., None]
        drk = mask_of(lambda d: d.polygon([(x, base), (x + lean, base - hh), (x + ww, base)], fill=255))[..., None]
        k = 0.65 + 0.5 * r.rand()
        img = img * (1 - lit) + np.array([62.0, 84, 104]) * k * lit
        img = img * (1 - drk) + np.array([34.0, 44, 66]) * k * drk
    for i in range(11):
        x = r.rand() * W; hh = 14 + r.rand() * 28; ww = 6 + r.rand() * 9
        roof = mask_of(lambda d: d.polygon([(x - ww, 0), (x + ww, 0), (x, hh)], fill=255))[..., None]
        img = img * (1 - roof) + np.array([40.0, 50, 72]) * (0.7 + 0.5 * r.rand()) * roof
    floor = (YY > 180)[..., None]
    across = (XX - W / 2) * 150 / np.maximum(YY - 90, 1)
    grid = ((np.abs(across % 34 - 17) < 1.0) | (np.log(np.maximum(YY - 178, 1)) * 5 % 2.2 < 0.28))[..., None]
    tile = np.array([36.0, 42, 60]) * (0.75 + 0.5 * fbm(720, (24, 8))[..., None]) * (1 - 0.5 * grid)
    return img * (1 - floor) + tile * floor

def dream_backdrop():
    """the dream chamber: violet fog, a ring of old stone, a round dais"""
    img = ramp([(16, 12, 26), (28, 22, 44), (38, 30, 54)], np.clip(YY / 170, 0, 1)) * (0.7 + 0.6 * fbm(801, (44, 18, 7))[..., None])
    tex = fbm(810, (12, 5, 3))[..., None]
    for cx, wd in ((8, 18), (56, 12), (W - 56, 12), (W - 8, 18)):
        m = (np.abs(XX - cx) < wd)[..., None] * (YY < 160)[..., None]
        img = img * (1 - m) + np.array([30.0, 28, 44]) * (0.6 + 0.8 * tex) * m
    floor = (YY > 156)[..., None]
    dais = np.array([40.0, 36, 54]) * (0.7 + 0.5 * tex)
    d = np.sqrt(((XX - W / 2) / 110) ** 2 + ((YY - 200) / 42) ** 2)
    band = ((np.abs(d - 0.9) < 0.03) | (np.abs(d - 0.6) < 0.02))[..., None]
    return img * (1 - floor) + (dais + band * np.array([12.0, 26, 28])) * floor

def spirit_cave_backdrop():
    """the spirit beast's cave: rough cold rock, a hanging roof, a pale floor"""
    bend = fbm(901, (34, 14))
    strata = 0.5 + 0.5 * np.sin((YY + XX * 0.3) * 0.17 + bend * 11)
    rock = np.array([40.0, 48, 64]) * (0.55 + 0.45 * strata[..., None] * (0.4 + 1.1 * fbm(910, (16, 6, 3))[..., None]))
    drip = 8 + 34 * np.abs(np.sin(XX * 0.09 + 3 * np.sin(XX * 0.031))) ** 4 + 26 * fbm(915, (12, 5))[0, :][None, :]
    top = (YY < drip)[..., None]
    img = rock * (1 - top) + np.array([18.0, 20, 30]) * top
    floor = (YY > 188 + 5 * (fbm(920, (30, 10))[0, :][None, :] - 0.5))[..., None]
    return img * (1 - floor) + np.array([46.0, 48, 58]) * (0.6 + 0.8 * fbm(925, (18, 6))[..., None]) * floor

# ---- recipes -------------------------------------------------------------------
def yama_pillars():
    rgb0, al0 = fit(solid_sprite("Yama.png"), W * 1.06, H - 14, exposure=1.2, gamma=0.68, sat=1.1)
    h, w = al0.shape[:2]; left = -6; ground = H - 10; top = ground - h
    rig = Rig(rgb0, al0)
    back = chasm_backdrop(); seams = lava_seams(420)
    fire = [(255, 214, 110), (248, 138, 36), (196, 62, 18), (96, 26, 14)]
    # a pentagon flat on the floor around him, its point towards us, erupting in star order
    verts = polygon_pts(W * 0.47, 214, 92, 5, rot=math.pi / 2, squash=0.24)
    pillars = []
    for k, i in enumerate((0, 2, 4, 1, 3)):
        x, y = verts[i]; front = i == 0
        pillars.append((x, 0.56 + 0.055 * k, 46 if front else 150 - (y - 197) * 2.2, 10 + (y - 190) * 0.2, 1 if front else 0, y))
    # the pact seal hangs on the back wall, one point up, big enough that its pentagon shows around him
    scx, scy, sr = W * 0.5, 104, 106
    wallm = np.clip((ridge(408, 190, 14)[None, :] - YY) / 3, 0, 1)
    def to_mask(a):
        return np.clip(a[..., 0], 0, 1) * wallm
    def spike(cv_, a, b, tip):
        line(cv_, a, tip, (1, 1, 1), size=2.6); line(cv_, b, tip, (1, 1, 1), size=2.6)
    seal = np.zeros((H, W, 3))
    up = -math.pi / 2
    outer = polygon_pts(scx, scy, sr, 5, rot=up)
    polygon(seal, scx, scy, sr, 5, (1, 1, 1), rot=up, size=3.2)
    pentagram(seal, scx, scy, sr, (1, 1, 1), rot=up, size=2.6)
    for i in range(5):                                                       # a horned lobe on each of the five sides
        ax, ay = outer[i]; bx, by = outer[(i + 1) % 5]
        mx, my = (ax + bx) / 2, (ay + by) / 2
        nx, ny = mx - scx, my - scy; nl = math.hypot(nx, ny); nx, ny = nx / nl, ny / nl
        tip = lambda u, out: (ax + (bx - ax) * u + nx * out, ay + (by - ay) * u + ny * out)
        pa, pb, pc = tip(0.22, 0), tip(0.5, 13), tip(0.78, 0)
        spike(seal, pa, tip(0.36, 25), pb); spike(seal, pc, tip(0.64, 25), pb)
    seal = to_mask(seal)
    # his body in each nature: ramps over its own brightness keep the form
    bright_ = rig.rgb.mean(2); on_ = rig.al[..., 0] > 0
    lo_, hi_ = np.percentile(bright_[on_], [4, 96]); lum_ = np.clip((bright_ - lo_) / max(hi_ - lo_, 1), 0, 1)
    flesh = np.clip(rig.rgb * np.array([1.12, 0.96, 0.8]), 0, 255)
    shade = ramp([(34, 20, 60), (66, 38, 116), (136, 80, 204), (232, 200, 255)], lum_)[..., :3]
    C_FIRE, C_COLD = np.array([255.0, 138, 40]), np.array([178.0, 190, 255])
    n1, n2 = _layer(431, 12), _layer(433, 6)
    embers = Motes(435, 26, lambda r: (r.rand() * W, H - 16 - r.rand() * 30, 16 * (r.rand() - 0.5), -130 - 90 * r.rand(), 0))
    slam = 0.52
    def scene(t):
        lift = sm(0.1, slam - 0.04, t) * (1 - sm(slam - 0.04, slam, t)) if t < slam else 0.0
        hit = math.exp(-since(t, slam) * 7)
        beat = wave(t, 2)
        # who is winning: 1 fire (the slam on), 0 shadow; the seal always burns in the other nature, so it never merges with him
        f = sm(0.46, 0.56, t) if t >= 0.2 else 1 - sm(0.04, 0.16, t)
        # how much fire stands in the room right now
        heat = np.zeros((H, W)); total = 0.0
        strength = []
        for x, t0, _, _, _, _ in pillars:
            u = since(t, t0 - 0.06)
            s = sm(0.0, 0.1, u) * (1 - sm(0.26, 0.42, u))
            strength.append(s); total += s
            heat += s * np.exp(-((XX - x) / 34) ** 2)
        cv = back * (0.72 + 0.12 * min(total, 3))
        hotter = (seams * (0.45 + 0.55 * np.clip(noise(440, 8, t), 0, 1)) * (0.5 + 0.5 * min(total, 2)))[..., None]
        cv = cv * (1 - hotter) + np.array([230.0, 96, 24]) * hotter
        cv += (heat * np.clip((YY - 40) / 190, 0, 1))[..., None] * np.array([46.0, 15, 3])
        turning = np.zeros((H, W, 3))                                         # nested pentagons, each turned against the last; a fifth of a turn loops
        polygon(turning, scx, scy, sr * 0.62, 5, (1, 1, 1), rot=math.pi / 2 + 2 * math.pi / 5 * t, size=2.8)
        polygon(turning, scx, scy, sr * 0.36, 5, (1, 1, 1), rot=up - 4 * math.pi / 5 * t, size=2.6)
        ink = np.clip(seal + to_mask(turning), 0, 1)[..., None]
        cv = cv * (1 - 0.72 * wallm[..., None])                               # near-black wall: the silhouette and the seal both stand on it
        cv = cv * (1 - ink) + ink * (C_COLD * f + C_FIRE * (1 - f))
        cv = feather(cv)
        ember = np.array((220.0, 110, 30)) * f + np.array((140.0, 80, 220)) * (1 - f)
        embers.draw(cv, t, lambda v: ember * math.sin(math.pi * v) * 0.75, 1.0)
        for (x, t0, hh, wd, row, y), s in zip(pillars, strength):
            if row == 0 and s > 0.02:
                column(cv, x, y, hh * s, wd, t * 4, n1, n2, fire, min(1, s * 1.4), sway=0.25)
            warn = sm(-0.16, 0.0, since(t, t0 - 0.06) - 1) if since(t, t0 - 0.06) > 0.8 else 0
            if warn > 0.02: ring(cv, x, y, 11, 0.3, 4, np.array([255.0, 130, 30]) * warn)
        sh = np.clip(1 - ((XX - left - w * 0.46) / (w * 0.42)) ** 2 - ((YY - ground) / 7) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        settle = hit * math.cos(since(t, slam) * 26)
        glaive_a = 4 * lift - 3 * hit
        parts = [
            dict(c=(0.48, 0.45), r=(0.16, 0.13), p=(0.48, 0.45), d=(0, -0.008 * wave(t, 2)), soft=0.9),            # chest
            dict(c=(0.16, 0.24), r=(0.22, 0.27), p=(0.32, 0.30), a=-13 * wave(t, 2, phi_phase(1)) + 8 * settle, lit=0.2 * beat, soft=0.45),       # far wing
            dict(c=(0.83, 0.30), r=(0.22, 0.31), p=(0.66, 0.26), a=12 * wave(t, 2, phi_phase(2)) - 8 * settle, lit=-0.2 * beat, soft=0.45),       # near wing, late
            dict(c=(0.25, 0.46), r=(0.10, 0.16), p=(0.25, 0.34), a=-5 * lift + 5 * settle),                         # arm that holds the butt
            dict(c=(0.66, 0.77), r=(0.07, 0.09), p=(0.60, 0.70), a=9 * wave(t, 2, phi_phase(3))),                            # tail tip
            dict(c=(0.42, 0.575), r=(0.48, 0.085), p=(0.42, 0.57), a=glaive_a, d=(0, -0.03 * lift + 0.02 * hit), soft=0.75),       # the shaft
            dict(c=(0.75, 0.565), r=(0.23, 0.23), p=(0.42, 0.57), a=glaive_a, d=(0, -0.03 * lift + 0.02 * hit), lit=0.25 * hit, soft=0.3),
            dict(c=(0.41, 0.31), r=(0.155, 0.18), p=(0.43, 0.42), a=3 * wave(t, 1, phi_phase(4)) + 5 * lift - 7 * hit, d=(0.005 * lift, -0.012 * lift + 0.02 * hit), lit=0.22 + 0.2 * hit),
            dict(c=(0.39, 0.40), r=(0.05, 0.03), p=(0.40, 0.385), a=-10 * hit - 5 * lift, soft=0.6),
        ]
        lean, drop = 7 * lift - 5 * hit, 9 * hit - 8 * lift
        def body(x, y):
            up = np.clip(0.95 - y, 0, 1)
            return -lean * up, -drop * up
        rig.rgb = flesh * f + shade * (1 - f)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), 0.5), relief=0.3, bend=body)
        # a dark edge around him, on the wall only, so the seal's lines stop short of his outline
        a_ = al[..., 0] if al.ndim == 3 else al
        halo = np.zeros_like(a_)
        for dy_, dx_ in ((0, 0), (0, 5), (0, -5), (5, 0), (-5, 0), (4, 4), (4, -4), (-4, 4), (-4, -4)):
            halo = np.maximum(halo, np.roll(np.roll(a_, dy_, 0), dx_, 1))
        y0_, x0_ = top - rig.pad, left - rig.pad
        ys_, xs_ = np.clip(YY.astype(int) - y0_, 0, a_.shape[0] - 1), np.clip(XX.astype(int) - x0_, 0, a_.shape[1] - 1)
        cv *= 1 - (0.85 * halo[ys_, xs_] * wallm)[..., None]
        # fire underlights him where it stands near him
        gx = np.clip((rig.xx - rig.pad + left).astype(int), 0, W - 1)
        under = np.clip(np.arange(rig.al.shape[0]) / rig.al.shape[0], 0, 1)[:, None] ** 1.5
        rgb = rgb + (heat[H // 2, gx] * under)[..., None] * np.array([120.0, 50, 12])
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ex, ey = rig.at(parts[-2], 0.435, 0.29)
        glow(cv, left + ex + lean * 0.66, top + ey + drop * 0.66, 5, np.array((255.0, 90, 30)) * f + np.array((170.0, 90, 255)) * (1 - f), 0.35 + 0.7 * hit)
        bx, by = rig.at(parts[5], 0.74, 0.45)
        glow(cv, left + bx + lean * 0.5, top + by + drop * 0.5, 12, np.array((255.0, 120, 30)) * f + np.array((150.0, 80, 240)) * (1 - f), 0.45 * lift + 0.1)
        for (x, t0, hh, wd, row, y), s in zip(pillars, strength):
            if row == 1 and s > 0.02:
                column(cv, x, y, hh * s, wd, t * 4, n1, n2, fire, min(1, s * 1.4), sway=0.25)
        return np.clip(cv, 0, 255)
    return "yama", "fire pillars", scene

def duke_vents(under=None):
    rgb0, al0 = fit(sprite("Duke_Sucellus.png"), W - 26, H - 14, exposure=0.8, gamma=1.0, sat=1.1)
    h, w = al0.shape[:2]; left = (W - w) // 2; ground = H - 6; top = ground - h
    rig = Rig(rgb0, al0)
    wall = stronghold_backdrop()
    vents = [(16, 0.42, 165, 15), (W - 15, 0.48, 175, 15), (56, 0.56, 70, 12), (W - 44, 0.62, 80, 12)]
    gas = [(205, 240, 232), (140, 205, 205), (84, 150, 166), (44, 84, 100)]
    n1, n2 = _layer(531, 13), _layer(533, 6)
    flakes = Motes(535, 26, lambda r: (r.rand() * W * 1.2, r.rand() * H, -22 - 14 * r.rand(), 40 + 20 * r.rand(), 0))
    eyes = [(0.553, 0.636, 0.26), (0.175, 0.878, 0.30), (0.345, 0.905, 0.33), (0.78, 0.80, 0.36), (0.14, 0.755, 0.28)]
    grain = fbm(540, (9, 4, 2))
    def scene(t):
        wake = sm(0.2, 0.34, t) * (1 - sm(0.72, 0.96, t))
        vent = [sm(0, 0.08, since(t, t0)) * (1 - sm(0.22, 0.42, since(t, t0))) for _, t0, _, _ in vents]
        gust = max(vent)
        light = 0.62 + 0.12 * wave(t, 1) + 0.26 * gust
        cv = wall * light + (np.exp(-((XX - W / 2) / 90) ** 2) * wake)[..., None] * np.array([6.0, 8, 12])
        cv = feather(cv)
        flakes.draw(cv, t, lambda u: (120, 140, 160), 0.9)
        # the grates, and the gas that stands behind him
        for i, (x, t0, hh, wd) in enumerate(vents):
            stamp(cv, x, H - 9, (40, 50, 58), 7 if i < 2 else 5)
            glow(cv, x, H - 12, 10, (90, 170, 170), 0.1 + 0.8 * vent[i])
            if i < 2 and vent[i] > 0.02:
                column(cv, x, H - 8, hh * vent[i], wd, t * 3, n1, n2, gas, min(1, vent[i] * 1.2), flare=0.9, sway=0.35)
        sh = np.clip(1 - ((XX - W / 2) / (w * 0.55)) ** 2 - ((YY - ground) / 7) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        snore = wave(t, 2) * (1 - wake)
        stir = wake * wave(t, 2, 0.1)
        chew = wake * wave(t, 4, 0.2)
        parts = [
            dict(c=(0.55, 0.26), r=(0.5, 0.27), p=(0.55, 0.52), a=-2.2 * wake + 2.2 * stir, soft=0.8, d=(0, -0.018 * wake)),   # the ice hood rides his back
            dict(c=(0.60, 0.53), r=(0.28, 0.14), p=(0.60, 0.56), d=(0.004 * stir, -0.014 * wake + 0.004 * snore), soft=0.8),
            dict(c=(0.40, 0.635), r=(0.28, 0.105), p=(0.60, 0.70), a=-4 * wake + 2.5 * stir, d=(0.008 * stir, -0.022 * wake + 0.005 * snore - 0.006 * chew), soft=0.55),   # the brow and upper lip rise
            dict(c=(0.355, 0.79), r=(0.32, 0.075), p=(0.62, 0.77), a=6 * wake - 3 * (1 - wake) - 2.5 * stir, d=(0.008 * stir, 0.03 * wake - 0.026 * (1 - wake) + 0.004 * snore + 0.008 * chew), soft=0.55),    # the jaw drops
            dict(c=(0.48, 0.91), r=(0.42, 0.075), p=(0.48, 0.91), d=(0, 0.006 * wake - 0.006 * snore), soft=0.9),   # the belly sags
            dict(c=(0.05, 0.90), r=(0.09, 0.075), p=(0.12, 0.87), a=-7 * wave(t, 2, phi_phase(1)) * wake - 4 * wake),        # toes
            dict(c=(0.63, 0.96), r=(0.09, 0.055), p=(0.65, 0.90), a=6 * wave(t, 2, phi_phase(2)) * wake),
        ]
        def body(x, y):
            up = np.clip(0.95 - y, 0, 1)
            return 3 * wake * up + 5 * stir * up ** 2, 2.5 * snore * up
        rgb, al = rig.pose(parts, light=(0.7 * wave(t, 1), -0.6), relief=0.3, bend=body)
        # asleep he is rimed over, and the frost creeps up from the floor as the gas settles
        yy_ = (rig.yy - rig.pad) / h
        gr = np.pad(np.asarray(Image.fromarray((grain * 255).astype(np.uint8)).resize((w, h)), float) / 255, rig.pad, mode="edge")
        frost = np.clip((1 - wake) * 2.2 - 1.0 + (gr - 0.5) * 2.4 - yy_ * 0.2, 0, 1)[..., None]
        lum = rgb.mean(2, keepdims=True)
        rgb = rgb * (1 - frost) + (lum * 0.72 + 8) * np.array([0.9, 1.08, 1.3]) * frost
        rgb = rgb * (0.94 + 0.08 * wake) * (0.6 + 0.4 * np.clip((yy_ - 0.38) / 0.16, 0, 1))[..., None]
        if under: under(cv, t)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        for fx, fy, t0 in eyes:
            open_ = sm(t0, t0 + 0.05, t) * (1 - sm(0.72, 0.86, t))
            part = parts[2] if fy < 0.7 else parts[4]
            ex, ey = rig.at(part, fx, fy)
            if open_ > 0.02:
                stamp(cv, left + ex, top + ey, (-150, -150, -130), 5.5)
                stamp(cv, left + ex, top + ey, (255, 90, 40), 4.2 * open_ + 0.4)
                stamp(cv, left + ex, top + ey, (120, 130, 60), 1.6 * open_)
        mx, my = rig.at(parts[3], 0.36, 0.72)
        glow(cv, left + mx, top + my, 16, (120, 180, 120), 0.35 * wake)
        # cold mist from the two near grates rolls in front of his feet
        for i, (x, t0, hh, wd) in enumerate(vents):
            if i >= 2 and vent[i] > 0.02:
                column(cv, x, H - 4, hh * vent[i], wd, t * 3, n1, n2, gas, min(0.72, vent[i] * 0.9), flare=1.5, sway=0.3)
        mist = np.clip((YY - H + 22) / 22, 0, 1) * np.clip(noise(545, 16, t) - 0.3, 0, 1) * (0.5 + gust)
        cv += mist[..., None] * np.array([70.0, 90, 100])
        return np.clip(cv, 0, 255)
    return "duke", "the vents", scene

def whisperer_shadow():
    rgb0, al0 = fit(sprite("The_Whisperer.png"), W, H - 6, exposure=0.76, gamma=0.98, sat=1.5)
    h, w = al0.shape[:2]; left = (W - w) // 2 - 2; top = 10
    rig = Rig(rgb0, al0, pad=50)
    drowned, shadow = lassar_backdrop(), shadow_backdrop()
    hem = (left + 0.45 * w, top + 0.62 * h)
    specs = [(-62, 0.08, -172, 42, 0.0, 1), (-52, -0.02, -142, 40, 0.2, 1), (54, 0.03, -12, 42, 0.45, 1), (46, -0.04, -44, 42, 0.65, 1)]
    bubbles = Motes(635, 22, lambda r: (r.rand() * W, H + 4, 8 * (r.rand() - 0.5), -H - 20, 0))
    motes = Motes(637, 22, lambda r: (r.rand() * W, -4, 8 * (r.rand() - 0.5), H + 20, 0))
    def scene(t):
        # the shadow realm spreads from her, holds, and drains back
        fall = sm(0.14, 0.42, t) * (1 - sm(0.74, 0.98, t))
        ang = 2 * math.pi * t
        n = noise(640, 18, t)
        d = np.sqrt((XX - W / 2) ** 2 + ((YY - 110) * 0.9) ** 2)
        R = fall * 260 - 20
        m = np.clip((R - d + (n - 0.5) * 60) / 18, 0, 1)
        rays = np.clip(np.sin(XX * 0.05 + YY * 0.012 + 1.5 * math.sin(ang)) * 0.5 + 0.5, 0, 1) ** 3 * np.clip(1 - YY / 190, 0, 1)
        cv = drowned + rays[..., None] * np.array([14.0, 30, 30])
        cv = cv * (1 - m[..., None]) + shadow * m[..., None]
        edge = (m * (1 - m) * 4)[..., None]
        cv = cv + edge * np.array([44.0, 16, 66]) * (fall > 0.02)
        cv = feather(cv)
        bubbles.draw(cv, t, lambda v: np.array((110, 170, 170)) * (1 - fall), 1.0)
        motes.draw(cv, t, lambda v: np.array((150, 70, 190)) * fall, 1.0)
        # the far tentacles, behind her
        lash = 0.7 + 0.6 * fall
        far = [(hem[0] + xo * 0.2, hem[1] + 10 + yo * 40, 100, hd + 0, 60 * lash, 7, ph, n_) for xo, yo, hd, _, ph, n_ in specs]
        tendrils(cv, far, t, (44 + 26 * fall, 42, 72 + 30 * fall), (98 + 50 * fall, 112, 150))
        sway = wave(t, 1, 0.1)
        arm = wave(t, 1, 0.3); hand = wave(t, 2, 0.15)
        head = dict(c=(0.28, 0.065), r=(0.24, 0.08), p=(0.28, 0.125), a=9 * wave(t, 1, 0.22) - 5 * fall, d=(0, -0.004 * fall), lit=0.1 + 0.2 * fall)
        parts = [
            dict(c=(0.33, 0.21), r=(0.34, 0.09), p=(0.33, 0.21), d=(0, -0.005 * wave(t, 2)), soft=0.9),            # ribs
            dict(c=(0.10, 0.27), r=(0.14, 0.21), p=(0.17, 0.09), a=-6 * arm - 3 * fall, soft=0.7),                # the long drowned arm drifts out
            dict(c=(0.47, 0.25), r=(0.17, 0.15), p=(0.41, 0.12), a=4 * wave(t, 1, 0.55) + 2 * fall, soft=0.8),    # the near arm
            dict(c=(0.48, 0.35), r=(0.09, 0.05), p=(0.47, 0.31), a=12 * hand),
            head]
        def body(x, y):
            u = np.clip((y - 0.32) / 0.68, 0, 1)
            ripple = np.sin(2 * math.pi * (t * 2 - y * 1.8))
            return -(15 * sway * u + (6 + 12 * fall) * u ** 2 * ripple + 4 * wave(t, 1, 0.1) * (1 - np.abs(2 * y - 0.6))), 0
        rgb, al = rig.pose(parts, light=(0.8 * math.cos(ang), -0.5), relief=0.3, bend=body)
        rgb = rgb * (0.8 + 0.2 * np.clip(((rig.yy - rig.pad) / h - 0.04) / 0.14, 0, 1))[..., None]
        rgb = rgb * (1 + 0.15 * fall) + (np.clip((rig.yy - rig.pad) / h, 0, 1)[..., None]) * np.array([26.0, 6, 42]) * fall
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        fx, fy = rig.at(head, 0.27, 0.055)
        glow(cv, left + fx, top + fy, 8, (140, 190, 255), 0.25 + 0.65 * fall)
        # the near tentacles, across her hem
        near = [(hem[0] + xo * 0.35, hem[1] + 60 + yo * 90, 110, hd, 70 * lash, 8, ph + 0.4, n_) for xo, yo, hd, _, ph, n_ in specs]
        tendrils(cv, near, t, (70 + 30 * fall, 66, 100 + 40 * fall), (150 + 60 * fall, 170, 195))
        return np.clip(cv, 0, 255)
    return "whisperer", "the shadow", scene

def nightmare_sleep():
    rgb0, al0 = fit(sprite("The_Nightmare.png"), W - 2, H - 14, exposure=0.98, gamma=0.86, sat=0.85)
    h, w = al0.shape[:2]; left = (W - w) // 2; ground = H - 6; top = ground - h
    rig = Rig(rgb0, al0)
    hall = dream_backdrop()
    totems = [(26, 152, 76, 0.08), (W - 26, 152, 76, 0.22), (68, 120, 44, 0.36), (W - 68, 120, 44, 0.48)]
    def paint(d):
        for x, b, hh, _ in totems:
            hw = hh * 0.19
            d.polygon([(x - hw, b), (x - hw * 0.6, b - hh), (x + hw * 0.6, b - hh), (x + hw, b)], fill=255)
    tm = mask_of(paint)[..., None]
    stone = np.array([92.0, 92, 112]) * (0.6 + 0.8 * fbm(830, (8, 4, 2))[..., None])
    hall = hall * (1 - tm) + stone * tm
    wake_at = 0.60
    spots = Motes(835, 26, lambda r: (r.rand() * W, 40 + r.rand() * 170, 10 * (r.rand() - 0.5), -44 * r.rand(), 0))
    def scene(t):
        charge = [sm(t0, t0 + 0.12, t) * (1 - sm(wake_at + 0.06, wake_at + 0.14, t)) for _, _, _, t0 in totems]
        sum_c = sum(charge) / 4
        hit = math.exp(-since(t, wake_at) * 7.5) if t >= wake_at - 0.0 or t < 0.1 else 0.0
        waking = sm(wake_at, wake_at + 0.07, t) * (1 - sm(0.82, 0.98, t))
        cv = hall * (0.72 + 0.28 * sum_c + 0.25 * hit)
        cv = cv + (np.exp(-((XX - W / 2) / 110) ** 2 - ((YY - 110) / 70) ** 2) * waking)[..., None] * np.array([26.0, 6, 22])
        cv = feather(cv)
        spots.draw(cv, t, lambda v: np.array((130, 90, 170)) * math.sin(math.pi * v) * (0.4 + 0.4 * waking), 1.0)
        for (x, b, hh, _), c in zip(totems, charge):
            # asleep: dim runes crawl up the stone. Charged: the eye burns
            size = hh / 76
            for k in range(4):
                glow_y = b - hh * (0.18 + 0.22 * k)
                on = sm(k * 0.25, (k + 1) * 0.25, c)
                stamp(cv, x, glow_y, np.array((70, 235, 215)) * (0.15 + 0.8 * on), 2.6 * size)
            glow(cv, x, b - hh, 7 * size + 2, (60, 220, 200), c * 0.7)
            stamp(cv, x, b - hh, np.array((220, 255, 245)) * (0.2 + 0.8 * c), 4 * size)
        tendril_gain = 0.45 + 0.95 * waking
        far = [(10, 214, 118, -74, 120 * tendril_gain, 7, phi_phase(5), 1), (W - 8, 210, 124, -108, 120 * tendril_gain, 7, phi_phase(6), 1), (52, 204, 78, -98, 130 * tendril_gain, 5, phi_phase(7), 1), (W - 52, 200, 84, -84, 130 * tendril_gain, 5, phi_phase(8), 1)]
        tendrils(cv, far, t, (46, 26, 66), (84, 54, 112))
        d = np.sqrt(((XX - W / 2 + 6) / 96) ** 2 + ((YY - ground + 6) / 16) ** 2)
        cv *= 1 - 0.45 * np.clip(1 - d, 0, 1)[..., None]
        settle = hit * math.cos(since(t, wake_at) * 24)
        breath = wave(t, 1)
        head = dict(c=(0.415, 0.275), r=(0.11, 0.145), p=(0.49, 0.22), a=-2 * breath * (1 - waking) - 9 * waking + 5 * settle, d=(0.012 * waking, -0.032 * waking + 0.012 * (1 - waking)), lit=0.2 * waking)
        parts = [
            dict(c=(0.57, 0.28), r=(0.12, 0.11), p=(0.57, 0.28), d=(0, -0.012 * wave(t, 2)), soft=0.9),                   # cloak breathes
            dict(c=(0.48, 0.115), r=(0.20, 0.125), p=(0.52, 0.24), a=-5 * waking + 2.5 * wave(t, 2, phi_phase(1)) - 4 * settle, d=(0, -0.024 * waking), soft=0.5),  # the mane lags
            dict(c=(0.165, 0.37), r=(0.20, 0.30), p=(0.36, 0.15), a=6 * wave(t, 1, phi_phase(2)) * (1 - waking) - 13 * waking + 7 * settle, soft=0.4),               # the reaching arm
            dict(c=(0.75, 0.42), r=(0.11, 0.31), p=(0.67, 0.20), a=-4 * wave(t, 1, phi_phase(3)) * (1 - waking) + 11 * waking - 7 * settle, soft=0.4),
            dict(c=(0.75, 0.60), r=(0.07, 0.11), p=(0.74, 0.50), a=8 * wave(t, 2, phi_phase(4))),                                  # claws of the near hand
            head]
        rise, shove = 10 * waking - 3 * breath * (1 - waking), 7 * waking
        def body(x, y):
            up = np.clip(0.75 - y, 0, 0.75) / 0.75
            return -shove * up, rise * up ** 1.2 - 4 * hit * up
        rgb, al = rig.pose(parts, light=(0.7 * wave(t, 1), -0.5 + 0.8 * hit), relief=0.3, bend=body)
        # the floor runes wake with the totems; the shock lights her teal
        m = from_green(rgb, 50, 50)
        rgb = rgb * (1 - m) + rgb * m * (0.28 + 1.0 * sum_c + 0.5 * hit)
        rgb = rgb + (1 - m) * np.array([40.0, 100, 95]) * hit * 0.5
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ex, ey = rig.at(head, 0.435, 0.28)
        glow(cv, left + ex + shove * 0.63, top + ey - rise * 0.57, 4 + 5 * waking, (255, 40, 120), 0.45 + 0.9 * waking)
        for i, ((x, b, hh, _), c) in enumerate(zip(totems, charge)):
            if hit > 0.04 and t >= wake_at:
                bolt(cv, x, b - hh, left + 0.5 * w, top + 0.36 * h, 860 + i + int(t * FRAMES) // 2, np.array((110, 255, 230)) * min(1, hit * 1.4), jag=44, size=1.7)
        near = [(2, 238, 108, -58, 130 * tendril_gain, 9, 0.15, 1), (W - 2, 238, 114, -120, 130 * tendril_gain, 9, 0.65, 1)]
        tendrils(cv, near, t, (56, 30, 80), (104, 66, 136))
        return np.clip(cv, 0, 255)
    return "nightmare", "sleep", scene

def corp_core():
    # one idea: he charges with spirit energy, stomps, and the dark core tears out of him; while it is out he is drained, until it comes home
    rgb0, al0 = fit(solid_sprite("Corporeal_Beast.png"), W - 12, H - 66, exposure=0.86, gamma=0.92, sat=1.25, grain=0.2)
    fh, fw = al0.shape[:2]; fy, fx = np.mgrid[0:fh, 0:fw] / np.array([fh, fw])[:, None, None]
    face = np.clip((1 - np.hypot((fx - 0.22) / 0.24, (fy - 0.25) / 0.2)) / 0.35, 0, 1)[..., None]     # keep the skull's features
    healed, al0 = soften(close_seams(rgb0, 5), al0, 0.12, 1.4, 0.62)
    rgb0 = healed * (1 - face) + rgb0 * face
    h, w = al0.shape[:2]; left = W - w - 2; ground = H - 30; top = ground - h
    rig = Rig(rgb0, al0)
    # the body's states are ramps over its own brightness, so the form survives each one
    bright_ = rig.rgb.mean(2); on = rig.al[..., 0] > 0
    lo_, hi_ = np.percentile(bright_[on], [4, 96]); lum = np.clip((bright_ - lo_) / max(hi_ - lo_, 1), 0, 1)
    key = (0.72 + 0.5 * np.clip(1 - (rig.yy - rig.pad) / h, 0, 1))[..., None]                         # a strong key light from above
    bone = np.clip(rig.rgb * key, 0, 255)
    spirit = ramp([(30, 78, 150), (48, 128, 214), (120, 212, 255), (236, 250, 255)], lum)[..., :3] * key
    drained = ramp([(48, 46, 62), (78, 76, 96), (124, 122, 144), (196, 194, 212)], lum)[..., :3] * key
    # a rim round the silhouette, in sprite space so it moves with the body
    edge_ = Image.fromarray((rig.al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(5))
    rim = (rig.al[..., 0] - np.asarray(edge_, float) / 255)[..., None]
    grain_ = np.asarray(Image.fromarray((np.random.RandomState(931).rand(rig.al.shape[0] // 4 + 1, rig.al.shape[1] // 4 + 1) * 255).astype(np.uint8)).resize(rig.al.shape[1::-1], Image.BICUBIC), float) / 255
    from_feet = np.clip(1 - (rig.yy - rig.pad) / h, 0, 1)
    crgb, cal = fit(sprite("Dark_energy_core.png"), 26, 38, exposure=0.92, gamma=0.92, sat=1.5, grain=0.3)
    ch, cw = cal.shape[:2]
    core_rig = Rig(crgb, cal, pad=8)
    cave = spirit_cave_backdrop()
    stomp = 0.36
    floor_y = H - 6
    keys = [(0.38, None), (0.52, 26), (0.63, 84), (0.74, 158), (0.93, None)]
    seat = (0.46, 0.57)                                                       # the dark hollow under his chest
    wisps = Motes(935, 18, lambda r: (r.rand() * W, 200, 14 * (r.rand() - 0.5), -130 * (0.5 + r.rand()), 0))
    dust = Motes(937, 44, lambda r: (left + 0.2 * w + (r.rand() - 0.5) * 60, ground, (r.rand() - 0.5) * W * 0.9, -46 * r.rand(), 40))
    gather = Motes(939, 30, lambda r: (r.rand() * 2 * math.pi, 50 + r.rand() * 70, 0, 0, 0))
    drain = Motes(941, 14, lambda r: (10 * (r.rand() - 0.5), 0, 16 * (r.rand() - 0.5), -40 - 40 * r.rand(), 0))
    purple = np.array([165.0, 70, 230]); blue = np.array([96.0, 190, 255])
    def scene(t):
        lift = (t / stomp) ** 2 if t < stomp else 0.0
        s = since(t, stomp); hit = math.exp(-s * 8)
        charge = sm(0.04, stomp, t) * (1 - sm(stomp + 0.02, stomp + 0.13, t))
        out = sm(stomp + 0.02, stomp + 0.14, t) * (1 - sm(0.88, 0.95, t))
        home = math.exp(-since(t, 0.93) * 14)
        # the room follows his state
        light = 0.62 + 0.16 * charge + 0.22 * hit - 0.14 * out + 0.2 * home
        cv = cave * light
        fog = np.clip(noise(945, 22, t) - 0.35, 0, 1) * np.clip((YY - 110) / 90, 0, 1)
        cv += fog[..., None] * (np.array([16.0, 30, 42]) * (1 + 1.6 * charge) * (1 - out) + np.array([34.0, 12, 48]) * out)
        wash = np.exp(-((XX - W * 0.55) / 110) ** 2 - ((YY - 110) / 95) ** 2)[..., None]
        cv += wash * (blue * 0.14 * charge + purple * 0.09 * out)
        cv = feather(cv)
        wisps.draw(cv, t, lambda v: np.array((120, 170, 200)) * math.sin(math.pi * v) * 0.5 * (1 - 0.6 * out), 1.2)
        sc = (left + seat[0] * w, top + seat[1] * h)
        md = (W * 0.6, 64)
        show = charge + 0.9 * hit * (1 - out)
        if show > 0.02:
            mandala(cv, md[0], md[1], 58, blue * min(1.0, show) * 0.8, t, petals=5, sides=5, inner=3, rot=-math.pi / 2, reveal=min(1.0, sm(0.04, stomp - 0.03, t) + (t >= stomp)), disc=0.6 * min(1.0, show), spin=1)
            dx_, dy_ = sc[0] - md[0], sc[1] + 30
            off = (XX - md[0]) - dx_ * np.clip(YY + 30, 0, None) / dy_
            cv += (np.exp(-(off / 5) ** 2) * (YY < sc[1]))[..., None] * blue * 0.7 * min(1.0, show)
        if hit > 0.05:                                                        # the burst: spirit rays fan out behind him
            a = np.arctan2(YY - sc[1], XX - sc[0]); d = np.hypot(XX - sc[0], YY - sc[1])
            rays = np.clip(np.cos(a * 9 + 0.7) * 0.5 + np.cos(a * 5 - 1.1) * 0.5 - 0.35, 0, 1) * 2.2 * np.clip(1 - d / (70 + 190 * (1 - hit)), 0, 1)
            cv += rays[..., None] * blue * 0.85 * hit * EDGE
        shadow_ = np.clip(1 - ((XX - left - w * 0.5) / (w * 0.5)) ** 2 - ((YY - ground - 2) / 7) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * shadow_
        settle = hit * math.cos(s * 26)
        roar = max(charge, hit)
        slump = out
        head = dict(c=(0.24, 0.20), r=(0.24, 0.22), p=(0.37, 0.33), a=-8 * lift + 8 * hit + 3 * wave(t, 2, phi_phase(5)) + 6 * slump, d=(0.012 * lift - 0.015 * hit, -0.014 * lift + 0.024 * hit + 0.022 * slump), lit=0.15 * hit, soft=0.45)
        jaw = dict(c=(0.21, 0.31), r=(0.11, 0.05), p=(0.30, 0.26), a=head["a"] + 10 * roar + 2 * wave(t, 2), d=head["d"], soft=0.5)
        parts = [
            dict(c=(0.48, 0.48), r=(0.14, 0.14), p=(0.48, 0.48), d=(0, -0.012 * wave(t, 2) * (1 - 0.6 * slump)), soft=0.9),   # chest
            dict(c=(0.56, 0.17), r=(0.12, 0.17), p=(0.50, 0.33), a=-7 * wave(t, 2, phi_phase(1)) + 8 * settle + 6 * slump, soft=0.5),
            dict(c=(0.84, 0.28), r=(0.16, 0.13), p=(0.68, 0.37), a=7 * wave(t, 2, phi_phase(2)) - 8 * settle - 6 * slump, soft=0.5),
            dict(c=(0.80, 0.44), r=(0.12, 0.05), p=(0.67, 0.46), a=-6 * wave(t, 2, phi_phase(3)) + 6 * settle - 5 * slump, soft=0.5),
            dict(c=(0.92, 0.52), r=(0.06, 0.16), p=(0.87, 0.67), a=8 * wave(t, 2, phi_phase(4)) - 7 * settle, soft=0.5),
            head, jaw]
        lean, drop = 15 * lift - 7 * hit + 3 * slump, 14 * hit - 9 * lift + 7 * slump
        def body(x, y):
            up = np.clip(1 - y, 0, 1)
            leg = np.exp(-((x - 0.20) / 0.17) ** 2) * np.clip((y - 0.62) / 0.2, 0, 1)
            return -lean * up, -drop * up + 26 * lift * leg
        # the state: spirit climbs from the feet as he charges; drained while the core is out
        up_w = np.clip((charge * 1.7 - from_feet + (grain_ - 0.5) * 0.35) * 3, 0, 1)[..., None]
        skin = bone * (1 - up_w) + spirit * up_w
        skin = skin * (1 - out) + drained * out
        rim_c = np.array([255.0, 238, 205]) * (1 - charge) * (1 - out) + np.array([200.0, 240, 255]) * charge + np.array([168.0, 150, 215]) * out
        rig.rgb = np.clip(skin * (1 - rim * 0.65) + rim_c * rim * 0.65 + (np.array([150.0, 225, 255]) * hit + np.array([230.0, 200, 255]) * home) * 0.42, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.55), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        c_up = 1 - seat[1]
        chest = (sc[0] + lean * c_up, sc[1] + drop * c_up)
        ex, ey = rig.at(head, 0.31, 0.175)
        eye_c = blue if out < 0.5 else purple
        glow(cv, left + ex + lean * 0.8, top + ey + drop * 0.8, 5 + 3 * roar, eye_c, 0.35 + 0.6 * roar)
        if charge > 0.03 and t < stomp:                                       # spirit energy pours into the hollow
            for t0, (a, rad, _, _, _) in gather.p:
                v = (t * 5 + t0) % 1.0
                stamp(cv, md[0] + (chest[0] - md[0]) * v + 5 * math.sin(a + v * 9), md[1] + (chest[1] - md[1]) * v, np.array((225.0, 245, 255)) * (0.5 + 0.5 * v) * charge, 2.0)
            glow(cv, chest[0], chest[1], 8 + 12 * charge, blue, 0.7 * charge)
        if s < 0.4:
            u = s / 0.4
            dust.draw(cv, u * 0.8, lambda v: np.array((170, 185, 200)) * (1 - v) * 0.7, 2.0)
        if s < 0.3:                                                           # the shock crosses the floor, in the floor's own cracks
            cracks = (_layer(950, 7) > 0.45) * (YY > 192)
            ring(cv, left + 0.2 * w, ground + 4, s * 480, 0.26, 12, blue * (1 - s / 0.3) * 1.3, cracks)
        # the core: tears out of the hollow, hops the floor, leaps home
        squash = 1.0; pos = None; pulse = 0.5 + 0.5 * wave(t, 4)
        for (t0, a), (t1, b) in zip(keys, keys[1:]):
            if t0 <= t < t1:
                u = (t - t0) / (t1 - t0)
                x0, y0 = (chest[0], chest[1] + 12) if a is None else (a, floor_y)
                x1, y1 = (chest[0], chest[1] + 12) if b is None else (b, floor_y)
                arc = 30 if a is None else (46 if b is None else 40)
                pos = (x0 + (x1 - x0) * u, y0 + (y1 - y0) * u - arc * 4 * u * (1 - u))
                squash = 1 + 0.18 * math.sin(math.pi * u) - 0.28 * math.exp(-u * 14) - 0.3 * math.exp(-(1 - u) * 14) * (b is not None)
        for t0, x in keys[1:-1]:
            v = since(t, t0)
            if v < 0.13:
                w_ = v / 0.13
                ring(cv, x, floor_y - 3, 6 + w_ * 36, 0.3, 4, purple * (1 - w_) * 1.2)
                for t_, (dx, _, vx, vy, _g) in drain.p[:7]:
                    uu = (w_ + t_ * 0.4) % 1
                    stamp(cv, x + dx + vx * uu, floor_y - 6 + vy * uu, purple * (1 - uu), 1.3)
        glow(cv, chest[0], chest[1], 9 + 4 * pulse, purple, 0.75 * out)        # the wound it left
        if pos is not None:
            mid = pos[1] - ch / 2
            for i in range(16):                                               # his life runs down a sagging tether into the core
                v = (t * 7 + phi_phase(i)) % 1.0
                stamp(cv, chest[0] + (pos[0] - chest[0]) * v, chest[1] + (mid - chest[1]) * v + 16 * math.sin(math.pi * v), np.array((205.0, 120, 255)) * (0.45 + 0.55 * v) * out, 2.0)
            chew = 2.5 * wave(t, 6)
            crgb2, cal2 = core_rig.pose([dict(c=(0.35, 0.47), r=(0.35, 0.09), p=(0.6, 0.42), a=chew * 3), dict(c=(0.5, 0.13), r=(0.4, 0.14), p=(0.5, 0.3), a=chew)], light=(0.8, -0.5), relief=0.25)
            hh, ww = cal2.shape[:2]
            nh = max(4, int(hh * squash)); nw = max(4, int(ww * (2 - squash)))
            iy = np.minimum((np.arange(nh) / nh * hh).astype(int), hh - 1); ix = np.minimum((np.arange(nw) / nw * ww).astype(int), ww - 1)
            glow(cv, pos[0], mid, 15, purple, 0.22 + 0.2 * pulse)
            over(cv, crgb2[iy][:, ix], cal2[iy][:, ix], int(pos[0] - nw / 2), int(pos[1] - nh + 8))
        glow(cv, chest[0], chest[1], 26, (210, 170, 255), 0.75 * home)        # it lands home and he fills back up
        return np.clip(cv, 0, 255)
    return "corp", "the dark core", scene

RECIPES = [yama_pillars, duke_vents, whisperer_shadow, nightmare_sleep, corp_core]
