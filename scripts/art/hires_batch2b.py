"""Hi-res recipes, batch 2b: Kree'arra, Zilyana, Vardorvis, Araxxor, Doom of Mokhaiotl, Tormented Demon."""
from hires_kit import *
from hires_kit import _layer

# ---- shared helpers ------------------------------------------------------------
def sm(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)

def since(t, t0): return (t - t0) % 1.0

def keys(t, pts):
    """A looping eased track through (time, value) pairs."""
    n = len(pts)
    for i in range(n):
        t0, v0 = pts[i]; t1, v1 = pts[(i + 1) % n]
        span = (t1 - t0) % 1.0 or 1.0
        u = ((t - t0) % 1.0) / span
        if u <= 1.0:
            return v0 + (v1 - v0) * u * u * (3 - 2 * u)
    return pts[0][1]

def solid_sprite(name):
    """No soft ground shadow: nothing of the floor can ride on the model."""
    a = np.asarray(Image.open(SPR / name).convert("RGBA"), float).copy()
    mask = a[..., 3] > 128
    ys, xs = np.where(mask)
    a[..., 3] = mask * 255.0
    return Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.uint8), "RGBA")

def ink(segs, width=1):
    """A float mask of polylines."""
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    for s in segs: d.line([tuple(p) for p in s], fill=255, width=width)
    return np.asarray(im, float) / 255

def fill(polys):
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    for p in polys: d.polygon([tuple(q) for q in p], fill=255)
    return np.asarray(im, float) / 255

def paint(cv, mask, colour, power=1.0):
    m = (mask * power)[..., None]
    cv[:] = cv * (1 - m) + np.array(colour, float) * m

def add(cv, mask, colour, power=1.0):
    cv += (mask * power)[..., None] * np.array(colour, float)

def wrap_noise(seed, cw, ch):
    """Noise that tiles both ways, so it can be rolled round the frame for a whole-number loop."""
    r = np.random.RandomState(seed).rand(max(2, H // ch), max(2, W // cw))
    big = Image.fromarray((np.tile(r, (3, 3)) * 255).astype(np.uint8)).resize((3 * W, 3 * H), Image.BICUBIC)
    return np.asarray(big, float)[H:2 * H, W:2 * W] / 255

def lum_of(rig):
    b = rig.rgb.mean(2); lo, hi = np.percentile(b[rig.al[..., 0] > 0], [4, 96])
    return np.clip((b - lo) / max(hi - lo, 1), 0, 1)

def rim_of(rig, size=5):
    e = Image.fromarray((rig.al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(size))
    return (rig.al[..., 0] - np.asarray(e, float) / 255)[..., None]

def grain_of(rig, seed, cell=4):
    h, w = rig.al.shape[:2]
    r = np.random.RandomState(seed).rand(h // cell + 1, w // cell + 1)
    return np.asarray(Image.fromarray((r * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), float) / 255

def land(rig, pt, bend, n=5):
    """Where a Rig.at point ends up once the bend is applied (the bend samples backwards)."""
    x, y = pt
    px, py = x, y
    for _ in range(n):
        bx, by = bend(px / rig.w, py / rig.h)
        px, py = x - float(bx), y - float(by)
    return px, py

def shadow(cv, x, y, rx, ry, power=0.5):
    cv *= 1 - power * np.clip(1 - ((XX - x) / rx) ** 2 - ((YY - y) / ry) ** 2, 0, 1)[..., None]

def lift_shadows(rgb, al, k=0.3, rad=2.2, floor=0.5):
    """Dark seams land in a 5 x 10 cell as black dashes: blend in a blur and raise the shadows towards the body's mean."""
    blur = np.asarray(Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(rad)), float)
    out = rgb * (1 - k) + blur * k
    mean = out[al[..., 0] > 0].mean(0)
    lum = out.mean(2, keepdims=True); target = mean.mean() * floor
    lift = np.clip((target - lum) / max(target, 1), 0, 1)
    return out * (1 - lift) + mean * floor * lift, al

def shake(cv, sx, sy):
    return cv[np.clip(np.arange(H) - int(round(sy)), 0, H - 1)][:, np.clip(np.arange(W) - int(round(sx)), 0, W - 1)]

# ---- Kree'arra: the beat of his wings is the weather -----------------------------
def eyrie_static():
    """Armadyl's eyrie, open to the sky: two far pillars, a pale flagged platform."""
    sky = ramp([(22, 30, 46), (34, 48, 66), (58, 72, 84)], np.clip(YY / 190, 0, 1))
    tex = fbm(1101, (18, 7, 3))[..., None]
    img = sky.copy(); mask = np.ones((H, W))
    for x0, wd in ((24, 11), (W - 24, 11)):
        m = (np.abs(XX - x0) < wd) & (YY < 204)
        cap = (np.abs(XX - x0) < wd + 4) & (np.abs(YY - 44) < 5)
        form = 1 - 0.55 * ((XX - x0 + 4) / wd) ** 2
        col = np.array([46.0, 50, 62]) * form[..., None] * (0.7 + 0.6 * tex)
        img = np.where((m | cap)[..., None], col, img); mask = np.where(m | cap, 0, mask)
    z = np.clip((YY - 200) / (H - 200), 0, 1)
    across = (XX - W / 2) / (0.35 + z)
    joints = (np.abs((across + 22) % 44 - 22) < 1.2 + z) | (np.abs((np.log(z + 0.1) * 3) % 1 - 0.5) < 0.05)
    floor = np.array([58.0, 60, 66]) * (0.55 + 0.6 * z)[..., None] * (0.7 + 0.6 * tex) * (1 - 0.42 * joints[..., None])
    is_floor = YY > 200
    return np.where(is_floor[..., None], floor, img), mask * (~is_floor)

def twister(cv, x, base, tall, topw, t, power, stops, sway=0.0):
    """A whirlwind as a lathed column: bands wrap its surface and race round it; no flat spiral."""
    if tall < 4 or power < 0.02: return
    v = (base - YY) / tall
    cx = x + sway * np.sin(v * 4.2 + 2 * math.pi * t * 3) * np.clip(v, 0, 1)
    wd = 2.5 + topw * np.clip(v, 0, 1) ** 1.3
    u = (XX - cx) / wd
    on = (np.abs(u) < 1) & (v > 0) & (v < 1)
    th = np.arcsin(np.clip(u, -1, 1))
    bands = 0.5 + 0.5 * np.sin(th * 2.2 + v * tall * 0.20 - 2 * math.pi * t * 7)
    side = 0.5 + 0.5 * np.sin(th + 0.7)
    den = on * power * (0.8 + 0.2 * bands) * (1 - 0.5 * np.abs(u) ** 3) * np.clip((1 - v) * 7, 0.15, 1)
    col = ramp(stops, np.clip(0.18 + 0.42 * bands + 0.42 * side, 0, 1))[..., :3]
    cv[:] = cv * (1 - den[..., None]) + col * den[..., None]

def kree_storm():
    rgb0, al0 = fit(solid_sprite("Kree'arra.png"), W - 34, H - 42, exposure=1.02, gamma=0.74, sat=0.95, grain=0.45)
    rgb0, al0 = lift_shadows(rgb0, al0, 0.25, 2.0, 0.62)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 2; top = 17
    rig = Rig(rgb0, al0, pad=70)
    lum = lum_of(rig); rim = rim_of(rig); grain = grain_of(rig, 1111)
    base = np.clip(rig.rgb * np.array([1.04, 1.0, 0.92]) + np.array([10.0, 8, 4]), 0, 255)
    storm = ramp([(26, 40, 74), (66, 104, 160), (160, 208, 240), (250, 255, 255)], lum)[..., :3]
    spread = np.abs((rig.xx - rig.pad) / w - 0.5) * 2
    img, sky = eyrie_static()
    cloud_a, cloud_b = wrap_noise(1121, 74, 20), wrap_noise(1123, 32, 9)
    streak_a, streak_b = wrap_noise(1125, 96, 4), wrap_noise(1127, 50, 3)
    # three strokes: two working beats, then the one that brings the storm down
    beats = [(0.17, 0.5), (0.40, 0.6), (0.66, 1.0)]
    wing = [(0.03, 0.55), (0.17, -0.45), (0.27, 0.65), (0.40, -0.5), (0.54, 1.25), (0.66, -1.15), (0.80, -0.5)]
    feathers = []
    r = np.random.RandomState(1131)
    for sign in (-1, 1):
        for i in range(17):
            feathers.append((sign, 0.12 + 0.32 * r.rand(), 0.25 + 0.6 * r.rand(), r.rand() * 6.28, 6 + 7 * r.rand(), r.rand()))
    def scene(t):
        a = keys(t, wing)
        big = math.exp(-since(t, 0.66) * 7)
        gather = sm(0.42, 0.65, t) * (1 - sm(0.65, 0.68, t))
        gust = max(math.exp(-since(t, tb) * 13) * p for tb, p in beats)
        charge = max(gather, sm(0.66, 0.70, t) * (1 - sm(0.82, 1.0, t)))
        # wind: clouds and streaks are rolled by whole widths, faster on each stroke
        drift = t + 0.09 * sum(p * math.sin(2 * math.pi * (t - tb)) for tb, p in beats)
        ca, cb = np.roll(cloud_a, int(drift * W), 1), np.roll(cloud_b, int(drift * 2 * W), 1)
        cl = np.clip((0.66 * ca + 0.34 * cb - 0.40) * 3.0, 0, 1)
        dark = 1 - 0.45 * charge
        cv = img * (0.82 + 0.12 * gust) * (0.78 + 0.22 * dark)
        cv = cv + (cl * sky)[..., None] * np.array([58.0, 68, 76]) * (dark + 0.4 * big)
        s1, s2 = np.roll(streak_a, int(drift * 3 * W), 1), np.roll(streak_b, int(drift * 5 * W), 1)
        line_ = np.clip((s1 - 0.74) * 6, 0, 1) + 0.6 * np.clip((s2 - 0.76) * 6, 0, 1)
        add(cv, line_ * (YY < 205), (130, 165, 190), 0.05 + 0.5 * gust)
        cv = feather(cv)
        # the platform takes the wash of the big stroke
        u = since(t, 0.66)
        if u < 0.3:
            v = u / 0.3
            d = np.abs(np.sqrt(((XX - W / 2) / 1.0) ** 2 + ((YY - 222) / 0.22) ** 2) - (10 + 190 * v))
            add(cv, np.clip(1 - d / 13, 0, 1) * (YY > 204), (190, 200, 205), 0.55 * (1 - v))
        lift = 4 * math.exp(-since(t, 0.17) * 8) + 5 * math.exp(-since(t, 0.40) * 8) + 13 * big - 7 * gather
        shadow(cv, W / 2, 224, 66 - lift, 8 - lift * 0.1, 0.55)
        # two whirlwinds stand up off the wingtips after the big stroke
        def wind_layer(cv_, x0, x1, base_y, tall, topw, delay, power):
            up = since(t, 0.68 + delay)
            g = sm(0.0, 0.1, up) * (1 - sm(0.42, 0.56, up))
            if g < 0.02: return
            x = x0 + (x1 - x0) * (up / 0.56)
            stops = [(30, 42, 62), (70, 92, 116), (128, 152, 172), (196, 214, 226)]
            twister(cv_, x, base_y, tall * g, topw, t, power * g, stops, sway=9)
            ring(cv_, x, base_y - 1, 9 + 7 * wave(t, 6), 0.3, 4, np.array([150.0, 165, 175]) * g)
            for k in range(9):                                                # grit flung round its foot
                a_ = 2 * math.pi * (t * 7 + k / 9)
                rr = 14 + 8 * phi_phase(k)
                stamp(cv_, x + rr * math.cos(a_), base_y - 3 + rr * 0.3 * math.sin(a_) - 7 * phi_phase(k + 3), np.array((188, 198, 204)) * g, 1.4)
        wind_layer(cv, W + 22, -26, 208, 122, 25, 0.03, 0.72)
        # wings: tip swings wide and folds on the way up, drives past him on the way down
        fold = max(0.0, a)
        hit = big * math.cos(since(t, 0.66) * 22)
        la, ra = 8.5 * a, -8.5 * a
        head = dict(c=(0.41, 0.135), r=(0.135, 0.15), p=(0.44, 0.24), a=-5 * a + 3 * wave(t, 2, phi_phase(1)) + 3 * hit, d=(0, 0.012 * gather - 0.01 * big), lit=0.2 * big)
        parts = [
            dict(c=(0.50, 0.32), r=(0.17, 0.14), p=(0.50, 0.32), d=(0, -0.012 * wave(t, 2, 0.2)), soft=0.9),
            dict(c=(0.12, 0.50), r=(0.23, 0.56), p=(0.25, 0.14), a=la, soft=0.32),
            dict(c=(0.87, 0.52), r=(0.20, 0.56), p=(0.78, 0.22), a=ra, soft=0.32),
            dict(c=(0.415, 0.59), r=(0.125, 0.12), p=(0.44, 0.47), a=4 * wave(t, 2, phi_phase(3)) - 9 * big, soft=0.45),
            dict(c=(0.63, 0.60), r=(0.13, 0.13), p=(0.62, 0.47), a=-4 * wave(t, 2, phi_phase(4)) + 9 * big, soft=0.45),
            head]
        def body(x, y):
            feet = np.clip((y - 0.46) / 0.2, 0, 1) * np.exp(-((x - 0.52) / 0.22) ** 2)
            torso = np.exp(-((x - 0.5) / 0.34) ** 2)
            tip = np.clip((np.abs(x - 0.5) - 0.2) / 0.12, 0, 1) * np.clip((y - 0.28) / 0.5, 0, 1)
            return 0 * x, lift * torso * (1 - 0.6 * feet) + 34 * fold * tip * (y - 0.28)
        wash = np.clip((charge * 1.9 - (1 - spread) + (grain - 0.5) * 0.5) * 3, 0, 1)[..., None]
        skin = base * (1 - wash) + storm * wash
        skin = skin * (0.86 + 0.2 * gust)
        rc = np.array([230.0, 200, 150]) * (1 - charge) + np.array([225.0, 245, 255]) * charge
        rig.rgb = np.clip(skin * (1 - rim * 0.55) + rc * rim * 0.55 + big * np.array([70.0, 86, 100]) * 0.8, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.7 + 0.5 * a * 0.5), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ex, ey = land(rig, rig.at(head, 0.385, 0.115), body)
        glow(cv, left + ex, top + ey, 3 + 2.5 * charge, (150, 220, 255), 0.5 + 0.6 * charge)
        wind_layer(cv, -30, W + 30, 234, 158, 38, 0.0, 0.8)
        # feathers torn off the trailing edge on each stroke; they ride the wind off the frame
        segs_light, segs_dark = [], []
        for sign, tb_off, reach, spin, sz, ph in feathers:
            for j, (tb, p) in enumerate(beats):
                if (j == 2) or ph < 0.38:
                    u = since(t, tb + 0.015 * ph * 8)
                    if u > 0.42: continue
                    v = u / 0.42
                    x0 = W / 2 + sign * (24 + 62 * ph); y0 = top + h * tb_off + 96 * reach
                    x = x0 + sign * (22 + 90 * reach) * math.sqrt(v) * (0.5 + p) + 95 * v * v
                    y = y0 + 44 * math.sin(v * 3 + ph * 6) * reach + 30 * v
                    ang = spin + v * 9
                    dx, dy = math.cos(ang) * sz, math.sin(ang) * sz * 0.8
                    (segs_light if ph > 0.45 else segs_dark).append([(x - dx, y - dy), (x + dx, y + dy)])
        if segs_light: paint(cv, ink(segs_light, 3), (246, 232, 190))
        if segs_dark: paint(cv, ink(segs_dark, 3), (182, 156, 110))
        sx = big * 2.6 * math.sin(since(t, 0.66) * 80); sy = big * 2 * math.cos(since(t, 0.66) * 110)
        return np.clip(shake(cv, sx, sy), 0, 255)
    return "kree", "the storm", scene

# ---- Zilyana: she cuts Saradomin's figure in one unbroken line, too fast to see -----
def zily_static():
    """Saradomin's chamber by lamplight: warm dark stone, so the blue and white stand off it."""
    tex = fbm(1201, (22, 9, 4))[..., None]
    row = (YY // 26).astype(int); off = (row % 2) * 24
    joint = ((YY % 26) < 1.6) | (((XX + off) % 48) < 1.6)
    img = np.array([54.0, 42, 32]) * (0.55 + 0.75 * tex) * (1 - 0.45 * joint[..., None])
    for x0 in (14, W - 14):
        form = np.clip(1 - np.abs(XX - x0) / 13, 0, 1)[..., None]
        m = (np.abs(XX - x0) < 13)[..., None]
        img = img * (1 - m) + (np.array([66.0, 54, 40]) * (0.45 + 0.8 * form)) * (0.8 + 0.4 * tex) * m
    z = np.clip((YY - 204) / (H - 204), 0, 1)
    floor = np.array([52.0, 42, 36]) * (0.6 + 0.5 * z)[..., None] * (0.75 + 0.5 * fbm(1205, (26, 7))[..., None])
    return feather(np.where((YY > 204)[..., None], floor, img))

def zilyana_figure():
    rgb0, al0 = fit(solid_sprite("Commander_Zilyana.png"), 150, H - 22, exposure=0.74, gamma=1.04, sat=1.45, grain=0.5)
    h, w = al0.shape[:2]; ground = H - 10; top = ground - h
    rig = Rig(rgb0, al0, pad=40)
    lum = lum_of(rig); rim = rim_of(rig)
    bright = ramp([(46, 74, 170), (104, 156, 244), (205, 232, 255), (255, 255, 255)], lum)[..., :3]
    ghost_skin = ramp([(12, 26, 80), (24, 54, 130), (46, 92, 176), (84, 136, 214)], lum)[..., :3]
    back = zily_static()
    cx, cy, R = W / 2, 100.0, 100.0
    # a five-fold star ten chords long, walked in one stroke; its chords cut each other by phi
    verts = polygon_pts(cx, cy, R, 10, rot=-math.pi / 2)
    walk = [(i * 3) % 10 for i in range(11)]
    seg_t = 0.054; start = 0.02
    N = len(walk) - 1; end = start + N * seg_t
    chords = [ink([[verts[walk[i]], verts[walk[i + 1]]]], 3) for i in range(N)]
    star = np.clip(sum(chords), 0, 1)
    def four_star(rr, rot, inner=0.3):
        return [[(cx + (rr if i % 2 == 0 else rr * inner) * math.cos(rot + i * math.pi / 4), cy + (rr if i % 2 == 0 else rr * inner) * math.sin(rot + i * math.pi / 4)) for i in range(8)]]
    outer = ink([[(cx + R * 1.05 * math.cos(a), cy + R * 1.05 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 120)]], 1)
    inner_ring = ink([[(cx + R * 0.38 * math.cos(a), cy + R * 0.38 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 80)]], 1)
    disc = np.clip((R * 1.08 - np.hypot(XX - cx, YY - cy)) / 4, 0, 1)
    hatch = np.clip(1 - np.abs(((np.arctan2(YY - cy, XX - cx) / (2 * math.pi) * 40) % 1) - 0.5) * 7, 0, 1) * (np.abs(np.hypot(XX - cx, YY - cy) - R * 1.13) < 4)
    # where she stands for each chord: under the point it ends on, so the line seems to follow her
    stn = [float(np.clip(verts[walk[i + 1]][0], W * 0.28, W * 0.72)) for i in range(N)]
    home = W / 2 - 2
    strike_at = 0.66
    stars = Motes(1211, 34, lambda r: (cx + (r.rand() - 0.5) * 54, -6, (r.rand() - 0.5) * 14, 220 + 40 * r.rand(), 0))
    def where(t):
        """her centre, the frame's chord index, and how far along the chord"""
        if start <= t < end:
            f = (t - start) / seg_t; i = int(f); u = f - i
            a_ = stn[i - 1] if i > 0 else home
            return a_ + (stn[i] - a_) * sm(0.0, 0.42, u), i, u
        if end <= t < end + 0.05:
            return stn[-1] + (home - stn[-1]) * sm(0, 1, (t - end) / 0.05), N, 0.0
        return home, N if t >= end else -1, 0.0
    def scene(t):
        px, idx, u = where(t)
        running = start <= t < end
        s = since(t, strike_at); hit = math.exp(-s * 8)
        gather = sm(end, strike_at - 0.01, t) * (t < strike_at)
        hold = sm(strike_at - 0.01, strike_at + 0.02, t) * (1 - sm(0.80, 0.98, t))
        fade = 1 - sm(0.80, 0.98, t)
        cv = back * (0.66 + 0.26 * hit + 0.1 * gather)
        cv = cv * (1 - 0.6 * (disc * (0.25 + 0.75 * max(gather, hold)))[..., None]) if idx >= 0 else cv
        # the figure: only as much of it as she has cut, then all of it, then it goes out from the rim
        c_line = np.array([120.0, 180, 255]) + np.array([135.0, 75, 0]) * max(hit, gather * 0.6)
        if idx >= 0 and fade > 0.01:
            done = (sum(chords[:idx]) if idx > 0 else np.zeros((H, W)))
            done = np.clip(done, 0, 1)
            add(cv, done, c_line, 0.85 * fade)
            if idx < N:
                a_, b_ = verts[walk[idx]], verts[walk[idx + 1]]
                tip = (a_[0] + (b_[0] - a_[0]) * sm(0, 0.8, u), a_[1] + (b_[1] - a_[1]) * sm(0, 0.8, u))
                add(cv, ink([[a_, tip]], 3), (255, 255, 255), 0.95)
                glow(cv, tip[0], tip[1], 7, (170, 215, 255), 1.0)
        if t >= end:
            turn = t * math.pi / 2                                            # a quarter turn a lap: the four-star lands on itself
            show = sm(end, strike_at, t) * fade
            add(cv, outer, (110, 170, 240), 0.8 * show)
            add(cv, hatch, (90, 150, 230), 0.8 * show)
            add(cv, inner_ring, (150, 200, 255), 0.7 * show)
            sq = [polygon_pts(cx, cy, R * 0.60, 4, rot=-turn + math.pi / 4 + j * math.pi / 4) for j in (0, 1)]
            add(cv, ink([p + p[:1] for p in sq], 1), (120, 180, 250), 0.75 * show)
            for x_, y_ in polygon_pts(cx, cy, R * 0.86, 20, rot=turn): stamp(cv, x_, y_, np.array((190.0, 225, 255)) * show, 1.6)
        # the strike: Saradomin's light falls straight down the figure's axis onto her
        fall = sm(strike_at - 0.05, strike_at, t) * (1 - sm(strike_at + 0.08, strike_at + 0.2, t))
        if fall > 0.01:
            wd = 14 + 34 * fall + 18 * hit
            add(cv, np.exp(-((XX - home) / wd) ** 2) * (YY < ground), (150, 195, 255), 0.6 * fall)
        if since(t, strike_at - 0.03) < 0.26:
            stars.draw(cv, since(t, strike_at - 0.03) / 0.26 * 0.9, lambda v: np.array((225, 240, 255)) * (1 - v * 0.6), 1.6)
        # reflection and shade on the floor, under wherever she is
        add(cv, np.exp(-((XX - px) / 44) ** 2 - ((YY - ground + 2) / 8) ** 2), (70, 110, 180), 0.45 + 0.7 * hit)
        shadow(cv, px, ground - 1, 28, 5, 0.55)
        bright_on = max(0.9 * hit, 0.6 * hold, 0.4 * gather, 0.22 if running else 0.0)
        side = 1 if idx % 2 == 0 else -1
        arm = (-12 * side * (1 - 2 * sm(0.1, 0.55, u))) if running else (10 * gather - 16 * hit)
        spread_ = 7 * gather + 11 * hold - 9 * hit * math.cos(s * 22)
        head = dict(c=(0.485, 0.13), r=(0.13, 0.095), p=(0.49, 0.20), a=(6 * side if running else 3 * wave(t, 1, phi_phase(1))) - 5 * gather, lit=0.2 * bright_on)
        parts = [
            dict(c=(0.47, 0.34), r=(0.13, 0.08), p=(0.47, 0.34), d=(0, -0.006 * wave(t, 2)), soft=0.9),
            dict(c=(0.13, 0.33), r=(0.20, 0.42), p=(0.34, 0.15), a=3 * wave(t, 2, phi_phase(2)) + spread_ + (6 if running else 0), soft=0.35),
            dict(c=(0.85, 0.32), r=(0.20, 0.40), p=(0.68, 0.17), a=-3 * wave(t, 2, phi_phase(3)) - spread_ - (6 if running else 0), soft=0.35),
            dict(c=(0.62, 0.48), r=(0.21, 0.18), p=(0.58, 0.32), a=-3 * wave(t, 1, phi_phase(4)) - 3 * gather + (5 * side if running else 0), soft=0.5),
            dict(c=(0.22, 0.62), r=(0.13, 0.32), p=(0.25, 0.33), a=arm, lit=0.3 * bright_on, soft=0.4),
            head]
        speed = (stn[idx] - (stn[idx - 1] if idx > 0 else home)) if running and idx < N else 0.0
        pull = -0.16 * speed * (1 - sm(0.1, 0.8, u)) if running else 0.0   # the feet get there first, the body arrives after
        def body(x, y):
            up = np.clip((0.96 - y) / 0.96, 0, 1)
            return pull * up ** 1.3, 7 * gather * up - 4 * hold * up + 8 * hit * up
        rw = (np.ones_like(lum) * bright_on)[..., None]
        skin = rgb0_full * (1 - rw) + bright * rw
        saved = rig.rgb
        rig.rgb = np.clip(skin * (1 - rim * 0.5) + np.array([210.0, 235, 255]) * rim * 0.5, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        rig.rgb = saved
        ox = int(px - w * 0.49) - rig.pad; oy = top - rig.pad
        if running:
            # afterimages at the last places she stood, and the air torn between them
            for back_k, alpha in ((1, 0.5),):
                j = idx - back_k
                gx = stn[j] if j >= 0 else home
                sub = 1 - sm(0, 1, u) * 0.45
                ga, gb = gx - w * 0.49, abs(gx - px)
                if gb > 3:
                    y0_ = top + h * 0.30; y1_ = top + h * 0.72
                    rr = (YY > y0_) * (YY < y1_) * (XX > min(gx, px)) * (XX < max(gx, px))
                    tear = np.clip((wrap_noise(1221 + j % 5, 80, 5) - 0.58) * 8, 0, 1) * rr
                    add(cv, tear, (150, 200, 255), 0.45 * alpha)
                over(cv, ghost_skin, al * alpha * sub, int(ga) - rig.pad, oy)
        over(cv, rgb, al, ox, oy)
        ex, ey = rig.at(head, 0.475, 0.11)
        glow(cv, ox + rig.pad + ex, oy + rig.pad + ey, 5 + 5 * hit, (190, 225, 255), 0.25 + 0.7 * max(hit, hold * 0.6))
        sx_, sy_ = rig.at(parts[4], 0.235, 0.86)
        glow(cv, ox + rig.pad + sx_, oy + rig.pad + sy_, 3, (225, 240, 255), 0.4 if running else 0.15)
        if hit > 0.02:
            ring(cv, home, ground - 2, 6 + s * 460, 0.24, 7, np.array([205.0, 230, 255]) * hit)
        return np.clip(cv, 0, 255)
    rgb0_full = rig.rgb.copy()
    return "zilyana", "the figure", scene

# ---- Vardorvis: the axes come up through the floor as his rage fills -----------------
def wood_static():
    """The Stranglewood temple: cold pale fog, black trees, grey flags. Nothing warm, so the red owns the frame."""
    fog = ramp([(46, 56, 56), (62, 74, 70), (74, 84, 76)], np.clip(YY / 170, 0, 1)) * (0.74 + 0.5 * fbm(1301, (46, 18, 7))[..., None])
    img = fog.copy()
    def tree(x, wd, seed):
        r = np.random.RandomState(seed); segs = []
        for s in range(5):
            px, py, ang = x + (s - 2) * wd * 0.3, 190.0, -math.pi / 2 + (s - 2) * 0.08
            pts = [(px, py)]
            for i in range(10):
                ang += (r.rand() - 0.5) * 0.7
                px += math.cos(ang) * 18; py += math.sin(ang) * 18; pts.append((px, py))
            segs.append(pts)
        return segs
    for x, wd, seed, col, wid in ((18, 14, 1311, (34, 38, 40), 8), (86, 9, 1313, (44, 50, 50), 5), (W - 26, 16, 1315, (32, 36, 38), 9), (W * 0.60, 8, 1317, (46, 52, 50), 4)):
        m = ink(tree(x, wd, seed), wid)[..., None]
        img = img * (1 - m) + np.array(col, float) * m
    z = np.clip((YY - 178) / (H - 178), 0, 1)
    across = (XX - W / 2) / (0.3 + z)
    joints = (np.abs((across + 28) % 56 - 28) < 1.2 + z) | (np.abs((np.log(z + 0.08) * 2.6) % 1 - 0.5) < 0.05)
    floor = np.array([52.0, 56, 54]) * (0.55 + 0.6 * z)[..., None] * (0.72 + 0.56 * fbm(1321, (16, 6, 3))[..., None]) * (1 - 0.45 * joints[..., None])
    return np.where((YY > 178)[..., None], floor, img)

def axe_polys(x, y, s, a, foreshorten=1.0):
    """A double-bitted axe turning in its own plane: shaft, two curved blades."""
    ca, sa = math.cos(a), math.sin(a)
    def P(u, v): return (x + s * (ca * u - sa * v) * foreshorten, y + s * (sa * u + ca * v))
    shaft = [P(-0.09, -0.75), P(0.09, -0.75), P(0.09, 1.45), P(-0.09, 1.45)]
    blades = []
    for k in (-1, 1):
        blades.append([P(k * 0.08, -0.64), P(k * 0.46, -0.50), P(k * 0.98, -0.98), P(k * 1.22, -0.5), P(k * 1.3, 0.0), P(k * 1.22, 0.5), P(k * 0.98, 0.98), P(k * 0.46, 0.12), P(k * 0.08, 0.02)])
    edges = [[P(k * 0.98, -0.98), P(k * 1.22, -0.5), P(k * 1.3, 0.0), P(k * 1.22, 0.5), P(k * 0.98, 0.98)] for k in (-1, 1)]
    return shaft, blades, edges

def draw_axe(cv, x, y, s, a, rage, clip_y=None):
    shaft, blades, edges = axe_polys(x, y, s, a)
    keep = 1.0 if clip_y is None else (YY < clip_y).astype(float)
    glow(cv, x, y, s * 0.9, (170, 26, 22), 0.08 + 0.2 * rage)
    paint(cv, fill([shaft]) * keep, (76, 54, 44))
    m = fill(blades) * keep
    tone = np.array([120.0, 132, 146]) * (1 - 0.3 * rage) + np.array([120.0, 44, 44]) * 0.3 * rage
    cv[:] = cv * (1 - m[..., None]) + tone * m[..., None]
    paint(cv, ink(edges, 3) * keep, np.array([250.0, 252, 255]) * (1 - 0.5 * rage) + np.array([255.0, 170, 140]) * 0.5 * rage)

def vardorvis_axes():
    rgb0, al0 = fit(solid_sprite("Vardorvis.png"), 150, H - 16, exposure=1.0, gamma=0.8, sat=1.15, grain=0.45)
    h, w = al0.shape[:2]; ground = H - 8; top = ground - h; left = 62
    rig = Rig(rgb0, al0, pad=50)
    lum = lum_of(rig); rim = rim_of(rig); grain = grain_of(rig, 1331)
    fx = (rig.xx - rig.pad) / w; fy = (rig.yy - rig.pad) / h
    base = np.clip(rig.rgb * 1.02, 0, 255)
    rage_skin = ramp([(44, 4, 10), (140, 12, 20), (240, 52, 40), (255, 216, 186)], lum)[..., :3]
    back = wood_static()
    # three slits on the floor at the frame's golden points; each stamp opens one, then all three axes go at once
    axes = [dict(x=34, y=204, s=21, at=0.08, to=(-40, 262), ph=0.0, hand=1), dict(x=GOLD_X - 44, y=232, s=19, at=0.26, to=(W * 0.30, 310), ph=0.3, hand=-1), dict(x=W - 22, y=206, s=19, at=0.44, to=(W + 42, 262), ph=0.6, hand=1)]
    sweep_at = 0.68
    motes = Motes(1341, 34, lambda r: (55 + r.rand() * 140, 170 + r.rand() * 50, 16 * (r.rand() - 0.5), -130 - 80 * r.rand(), 0))
    def scene(t):
        rage = min(sm(0.0, 0.60, t) * 0.9 + sm(0.60, sweep_at, t) * 0.1, 1.0) * (1 - sm(0.84, 0.995, t))
        stamps = [math.exp(-since(t, a["at"]) * 9) for a in axes]
        wind = [sm(a["at"] - 0.09, a["at"] - 0.005, t) * (t < a["at"]) for a in axes]
        loaded = max(wind); stamp_ = max(stamps)
        roar = sm(sweep_at - 0.1, sweep_at, t) * (1 - sm(sweep_at + 0.07, sweep_at + 0.22, t))
        release = math.exp(-since(t, sweep_at) * 7)
        cv = back * (0.86 - 0.5 * rage)
        red = np.exp(-((XX - left - 0.55 * w) / 110) ** 2 - ((YY - 120) / 110) ** 2)
        add(cv, red, (58, 4, 8), rage * 0.6)
        n = noise(1351, 16, t)
        add(cv, np.clip(n - 0.45, 0, 1) * np.clip((YY - 110) / 90, 0, 1), (40, 4, 6), rage)
        cv = feather(cv)
        motes.draw(cv, t, lambda v: np.array((240, 66, 44)) * math.sin(math.pi * v) * rage * 0.95, 1.1)
        shadow(cv, left + w * 0.55, ground, w * 0.55, 8, 0.55)
        # the far tendrils writhe; he never quite stands still
        behind, front = [], []
        for a, st_ in zip(axes, stamps):
            rise = sm(0, 0.09, since(t, a["at"]))
            if t < a["at"]: rise = 0.0
            if t >= sweep_at + 0.26: rise = 0.0
            slit = ink([[(a["x"] - 13, a["y"]), (a["x"] + 13, a["y"])]], 3)
            open_ = sm(a["at"] - 0.05, a["at"], t) * (1 - sm(sweep_at + 0.1, sweep_at + 0.3, t))
            if open_ > 0.01:
                paint(cv, slit, (10, 4, 6), 0.9 * open_)
                add(cv, slit, (230, 40, 26), (0.3 + 0.9 * st_) * open_)
                glow(cv, a["x"], a["y"] - 3, 11, (200, 30, 20), (0.12 + 0.7 * st_) * open_)
            if rise <= 0.0: continue
            spin = 2 * math.pi * (t * 3 + a["ph"]) * a["hand"]
            if t < sweep_at:
                y = a["y"] - a["s"] * 1.2 * rise - 2 * wave(t, 4, a["ph"])
                behind.append((a["x"], y, a["s"] * (0.9 + 0.1 * rise), spin, a["y"]))
            else:
                v = (t - sweep_at) / 0.26
                v = v * v * (1.6 - 0.6 * v)
                x = a["x"] + (a["to"][0] - a["x"]) * v
                y0 = a["y"] - a["s"] * 1.2
                y = y0 + (a["to"][1] - y0) * v
                front.append((x, y, a["s"] * (1 + 1.1 * v), spin, None))
        for x, y, s, spin, clip_y in behind:
            draw_axe(cv, x, y, s, spin, rage, clip_y)
        stomp = stamp_ * math.cos(min(since(t, axes[0]["at"]), since(t, axes[1]["at"]), since(t, axes[2]["at"])) * 24)
        lean = -7 * loaded + 6 * stamp_ + 7 * roar
        drop = -9 * loaded + 13 * stamp_ - 5 * roar + 4 * wave(t, 2) * (1 - rage * 0.5)
        head = dict(c=(0.665, 0.285), r=(0.14, 0.095), p=(0.68, 0.37), a=-6 * loaded + 7 * stamp_ - 8 * roar + 3 * wave(t, 3, phi_phase(1)), d=(-0.01 * roar, -0.006 * roar), lit=0.3 * rage)
        parts = [
            dict(c=(0.73, 0.44), r=(0.14, 0.09), p=(0.73, 0.44), d=(0, -0.008 * wave(t, 2, 0.2)), soft=0.9),
            dict(c=(0.94, 0.42), r=(0.10, 0.17), p=(0.90, 0.29), a=-10 * loaded + 8 * stomp - 8 * roar + 3 * wave(t, 2, phi_phase(2)), soft=0.45),
            dict(c=(0.58, 0.50), r=(0.09, 0.14), p=(0.63, 0.40), a=7 * loaded - 7 * stomp + 8 * roar + 3 * wave(t, 2, phi_phase(3)), soft=0.45),
            head]
        gain = 0.6 + 1.2 * rage + 0.8 * roar
        def body(x, y):
            up = np.clip((0.78 - y) / 0.78, 0, 1)
            torso = np.clip((x - 0.5) / 0.1, 0, 1)
            # the tendril trunk is a live thing: a wave runs from the root out to the crown
            arm = np.clip((0.62 - x) / 0.14, 0, 1)
            root = np.clip((0.98 - y) / 0.4, 0, 1)
            wv = np.sin(2 * math.pi * (t * 3 - y * 1.6 - x * 0.7))
            wv2 = np.sin(2 * math.pi * (t * 2 + x * 2.1 + y * 0.8 + 0.2))
            crown = np.clip((0.34 - y) / 0.3, 0, 1)
            bx = gain * arm * root * (5.5 * wv + 4 * crown * wv2) - lean * up * (0.35 + 0.65 * torso)
            by = gain * arm * crown * 5 * wv2 - drop * up * (0.35 + 0.65 * torso)
            return bx, by
        # rage soaks in along the tendrils first, up from the floor, the armour last
        reach = (fx * 0.75 + (1 - fy) * 0.25)
        soak = np.clip((rage * 1.65 - reach + (grain - 0.5) * 0.3) * 3.2, 0, 1)[..., None]
        skin = base * (1 - soak) + rage_skin * soak
        pulse = 0.5 + 0.5 * wave(t, 6)
        skin = skin * (1 + soak * 0.16 * pulse) * (1 + 0.2 * stamp_)
        rc = np.array([210.0, 225, 225]) * (1 - rage) + np.array([255.0, 186, 150]) * rage
        rig.rgb, saved = np.clip(skin * (1 - rim * 0.45) + rc * rim * 0.45, 0, 255), rig.rgb
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        rig.rgb = saved
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ex, ey = land(rig, rig.at(head, 0.62, 0.30), body)
        glow(cv, left + ex, top + ey, 3.5 + 4 * rage, (255, 50, 34), 0.35 + 0.9 * rage)
        if release > 0.03 and t >= sweep_at:
            s_ = since(t, sweep_at)
            ring(cv, left + w * 0.6, ground - 3, 6 + s_ * 520, 0.24, 7, np.array([255.0, 80, 50]) * release)
        for x, y, s, spin, clip_y in front:
            draw_axe(cv, x, y, s, spin, rage, clip_y)
        e = stamp_ * 0.6 + release * 0.8
        since_ = min(min(since(t, a["at"]) for a in axes), since(t, sweep_at))
        return np.clip(shake(cv, e * 2.6 * math.sin(since_ * 80), e * 2.4 * math.cos(since_ * 110)), 0, 255)
    return "vardorvis", "the axes", scene

# ---- Araxxor: the wall behind it is a brood, and the enrage hatches it ----------------
def hex_centres(side, cx, cy):
    out = []
    for q in range(-14, 15):
        for r_ in range(-14, 15):
            x = cx + side * 1.5 * q; y = cy + side * math.sqrt(3) * (r_ + q / 2)
            if -side < x < W + side and -side < y < 210: out.append((x, y))
    return out

def spiderling(segs_fn, x, y, s, t, ph):
    """eight scuttling legs round a body, in bold strokes: a shrunk sprite dissolves at this cell size"""
    legs = []
    for i in range(4):
        for k in (-1, 1):
            st = math.sin(2 * math.pi * (t * 6 + ph + i * 0.25 + (k > 0) * 0.5))
            hx, hy = x + k * s, y - s * 0.3 + i * s * 0.2
            kx, ky = hx + k * s * 1.3, hy - s * (0.9 - 0.3 * i) + st * s * 0.4
            fx, fy = kx + k * s * 1.1, y + s * (0.8 + 0.3 * i) + max(0.0, st) * -s * 0.5
            legs.append([(hx, hy), (kx, ky), (fx, fy)])
    return legs

def araxxor_brood():
    rgb0, al0 = fit(solid_sprite("Araxxor.png"), W * 1.34, H - 70, exposure=1.2, gamma=0.68, sat=0.95, grain=0.25)
    rgb0, al0 = lift_shadows(rgb0, al0, 0.4, 2.4, 0.72)
    h, w = al0.shape[:2]; left = (W - w) // 2 - 2; top = 42
    rig = Rig(rgb0, al0, pad=40)
    lum = lum_of(rig); rim = rim_of(rig); grain = grain_of(rig, 1401)
    fx = (rig.xx - rig.pad) / w; fy = (rig.yy - rig.pad) / h
    hide = ramp([(52, 38, 50), (82, 62, 76), (136, 106, 108), (226, 196, 160)], lum)[..., :3] * 0.5 + np.clip(rig.rgb * np.array([1.0, 0.72, 0.58]), 0, 255) * 0.5
    molten = ramp([(62, 8, 8), (168, 22, 12), (255, 96, 22), (255, 236, 150)], lum)[..., :3]
    venom = np.clip((rig.rgb[..., 1] - rig.rgb[..., 0] - 12) / 40, 0, 1)[..., None] * rig.al
    # the hive: a hex lattice of sealed sacs on a dim bark-brown wall
    side = 15.0; hcx, hcy = W * 0.5, 70.0
    cs = hex_centres(side, hcx, hcy)
    d2 = (XX[..., None] - np.array([c[0] for c in cs])) ** 2 + (YY[..., None] - np.array([c[1] for c in cs])) ** 2
    cell_id = d2.argmin(2); cell_d = np.sqrt(d2.min(2))
    sac = np.clip(1 - ((cell_d) / (side * 0.82)), 0, 1)
    hexl = hex_mask(side, hcx, hcy, width=2.0)
    tex = fbm(1411, (16, 6, 3))[..., None]
    wall = np.array([40.0, 34, 28]) * (0.6 + 0.7 * tex)
    wall = wall * (1 - 0.55 * hexl[..., None])
    sac_c = np.array([44.0, 44, 28]) * (0.5 + 1.1 * sac ** 0.6)[..., None]
    wall = wall * (1 - (sac > 0.05)[..., None]) + sac_c * (sac > 0.05)[..., None]
    z = np.clip((YY - 190) / (H - 190), 0, 1)
    floor_m = YY > 188 + 5 * (fbm(1413, (24, 8))[0, :][None, :] - 0.5)
    floor = np.array([40.0, 32, 26]) * (0.6 + 0.6 * z)[..., None] * (0.65 + 0.7 * fbm(1415, (14, 5, 2))[..., None])
    wall = np.where(floor_m[..., None], floor, wall)
    # order of hatching: out from behind it at golden steps, which is how a sunflower fills
    order = sorted(range(len(cs)), key=lambda i: math.hypot(cs[i][0] - W * 0.58, cs[i][1] - 66))
    lane = {i: k for k, i in enumerate(order)}
    rank = np.array([lane[i] for i in range(len(cs))], float) / len(cs)
    cell_ph = np.array([phi_phase(i) for i in range(len(cs))])
    enrage = 0.54
    pools = [dict(x=44, y=214, rx=30, at=0.12, from_=(0.47, 0.60)), dict(x=W - 52, y=220, rx=34, at=0.30, from_=(0.47, 0.60)), dict(x=W * 0.46, y=229, rx=40, at=0.46, from_=(0.47, 0.60))]
    acid = [(30, 50, 12), (108, 160, 20), (196, 232, 44), (246, 255, 150)]
    splash = Motes(1421, 34, lambda r: ((r.rand() - 0.5) * 8, 0, (r.rand() - 0.5) * 70, -30 - 70 * r.rand(), 150))
    # tip x, tip y, reach, gait phase: the planted points of the legs
    legs = [(0.03, 0.74, 0.09, phi_phase(1)), (0.21, 0.66, 0.08, phi_phase(2)), (0.37, 0.72, 0.05, phi_phase(3)), (0.53, 0.72, 0.05, phi_phase(4)), (0.72, 0.90, 0.07, phi_phase(5)), (0.83, 0.74, 0.06, phi_phase(6)), (0.97, 0.62, 0.07, phi_phase(7))]
    brood = [dict(cell=order[int(len(cs) * f)], fx=W * g, ph=phi_phase(i + 3)) for i, (f, g) in enumerate(((0.32, 0.06), (0.44, 0.94), (0.60, 0.20), (0.72, 0.80), (0.84, 0.36)))]
    def scene(t):
        s = since(t, enrage); hit = math.exp(-s * 7)
        rage = sm(enrage - 0.02, enrage + 0.05, t) * (1 - sm(0.84, 0.99, t))
        rear = sm(enrage - 0.18, enrage - 0.01, t) * (1 - sm(enrage + 0.02, enrage + 0.18, t))
        settle = hit * math.cos(s * 24)
        # the sacs: dormant breath, a slow swell before the enrage, then each bursts as the wave reaches it
        wave_r = np.clip((t - enrage) / 0.30, 0, 1.2) if t >= enrage else 0.0
        burst_t = (wave_r - rank) * 6
        ember = np.clip(1 - np.abs(burst_t - 0.25) * 2.2, 0, 1) * (t >= enrage) * (t < 0.92)
        empty = np.clip(burst_t, 0, 1) * (1 - sm(0.84, 0.97, t)) * (t >= enrage)
        swell = 0.26 + 0.2 * np.sin(2 * math.pi * (t * 2 + cell_ph)) + 0.75 * sm(0.2, enrage, t) * (t < enrage + 0.02) * (0.5 + 0.5 * np.sin(2 * math.pi * (t * 4 - rank * 2)))
        lit = (swell * (1 - empty) + 1.8 * ember)[cell_id] * sac ** 0.7
        cv = wall * (1 - 0.72 * (empty[cell_id] * (sac > 0.05))[..., None])
        cv = cv * (0.80 - 0.12 * rage) + (ramp(acid, np.clip(lit * 0.5, 0, 1))[..., :3] * np.clip(lit * 1.1, 0, 1)[..., None] * 0.34) * (~floor_m)[..., None]
        cv += (ember[cell_id] * sac ** 0.5)[..., None] * np.array([200.0, 240, 70]) * (~floor_m)[..., None] * 0.55    # each cell flares as it splits
        add(cv, np.exp(-((XX - W * 0.5) / 120) ** 2 - ((YY - 90) / 60) ** 2), (70, 16, 6), rage * 0.55)
        cv = feather(cv)
        # acid: each spit lands, spreads and seethes on the floor; the pool stays
        for pool in pools:
            u = since(t, pool["at"] + 0.08)
            alive = u < 0.80
            if not alive: continue
            sz = sm(0, 0.14, u) * (1 - sm(0.62, 0.80, u))
            if sz < 0.01: continue
            n_ = noise(1431 + int(pool["x"]), 8, t)
            rr = ((XX - pool["x"]) / (pool["rx"] * sz + 0.1)) ** 2 + ((YY - pool["y"]) / (pool["rx"] * 0.22 * sz + 0.1)) ** 2
            den = np.clip((1 - rr + (n_ - 0.5) * 1.1) * 3.2, 0, 1)
            cv[:] = cv * (1 - den[..., None] * 0.92) + ramp(acid, np.clip(n_ * 0.9, 0, 1))[..., :3] * den[..., None] * 0.92
            glow(cv, pool["x"], pool["y"] - 3, pool["rx"] * 0.7, (120, 170, 20), 0.3 * sz)
            for k in range(5):
                bub = (t * 3 + phi_phase(k)) % 1
                bx = pool["x"] + pool["rx"] * 0.8 * sz * math.cos(k * GOLDEN_ANGLE)
                by = pool["y"] + pool["rx"] * 0.14 * sz * math.sin(k * GOLDEN_ANGLE) - 12 * bub
                stamp(cv, bx, by, np.array((215, 250, 90)) * (1 - bub) * sz, 1.4)
        shadow(cv, left + w * 0.5, 214, w * 0.30, 11, 0.5)
        ang = 2 * math.pi * t
        spit = max(math.exp(-since(t, p["at"]) * 16) for p in pools)
        gape = max(sm(p["at"] - 0.05, p["at"], t) * (t < p["at"]) for p in pools)
        crouch = 3.5 * wave(t, 2, phi_phase(8)) + 13 * rear - 8 * settle - 9 * spit
        head = dict(c=(0.49, 0.53), r=(0.10, 0.17), p=(0.50, 0.40), a=4 * wave(t, 2, phi_phase(1)) - 6 * gape + 8 * spit + 7 * settle, d=(0, -0.01 * gape + 0.012 * spit), lit=0.25 * rage)
        fang_l = dict(c=(0.45, 0.60), r=(0.05, 0.09), p=(0.46, 0.53), a=head["a"] + 9 * max(gape, rage * 0.5) + 3 * wave(t, 4), d=head["d"], soft=0.5)
        fang_r = dict(c=(0.56, 0.60), r=(0.05, 0.09), p=(0.55, 0.53), a=head["a"] - 9 * max(gape, rage * 0.5) - 3 * wave(t, 4), d=head["d"], soft=0.5)
        parts = [
            dict(c=(0.64, 0.25), r=(0.15, 0.24), p=(0.56, 0.40), a=-3 * wave(t, 1, phi_phase(9)) - 7 * rear + 9 * settle, d=(0, 0.006 * wave(t, 2)), lit=0.15 * rage, soft=0.45),
            dict(c=(0.56, 0.07), r=(0.17, 0.10), p=(0.56, 0.22), a=2.5 * wave(t, 3, phi_phase(10)) - 5 * rear + 9 * settle, d=(0, -0.03 * rage), soft=0.45),
            head, fang_l, fang_r]
        speed = 2 + (rage > 0.5) * 1                                           # the enrage quickens its step
        def body(x, y):
            mid = np.exp(-((x - 0.52) / 0.24) ** 2 - ((y - 0.40) / 0.46) ** 2)
            bx, by = 0.0 * x, crouch * mid
            for tx, ty, rx, ph in legs:
                g = np.exp(-((x - tx) / rx) ** 2 - ((y - ty) / 0.22) ** 2)
                lift = max(0.0, math.sin(2 * math.pi * (t * 2 + ph))) ** 2
                by = by + (10 + 12 * rage) * lift * g
                bx = bx + 6 * math.cos(2 * math.pi * (t * 2 + ph)) * g * (1 if tx < 0.5 else -1)
            # the front legs rise for the enrage
            front = np.exp(-((x - 0.28) / 0.17) ** 2 - ((y - 0.45) / 0.26) ** 2) + np.exp(-((x - 0.76) / 0.17) ** 2 - ((y - 0.5) / 0.28) ** 2)
            by = by + 36 * rear * front
            return bx, by
        reach = np.abs(fx - 0.56) * 1.4 + np.abs(fy - 0.25) * 0.6
        soak = np.clip((rage * 1.8 - reach + (grain - 0.5) * 0.28) * 3.0, 0, 1)[..., None]
        skin = hide * (1 - soak) + molten * soak
        skin = skin * (1 - venom) + np.array([120.0, 245, 70]) * venom
        rc = np.array([150.0, 165, 90]) * (1 - rage) + np.array([255.0, 180, 80]) * rage
        rig.rgb, saved = np.clip(skin * (1 - rim * 0.5) + rc * rim * 0.5, 0, 255), rig.rgb
        rgb, al = rig.pose(parts, light=(0.8 * math.cos(ang), -0.6), relief=0.3, bend=body)
        rig.rgb = saved
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        mp = land(rig, rig.at(head, 0.50, 0.61), body)
        mouth = (left + mp[0], top + mp[1])
        ex, ey = land(rig, rig.at(head, 0.50, 0.49), body)
        glow(cv, left + ex, top + ey, 3.5 + 3 * rage, (255, 60, 30) if rage > 0.3 else (190, 240, 70), 0.5 + 0.7 * rage)
        glow(cv, mouth[0], mouth[1], 5 + 7 * gape, (170, 235, 40), 0.3 + 0.8 * gape)
        # the spits themselves, leaving the moving mouth on a high arc
        for pool in pools:
            u = since(t, pool["at"])
            if u < 0.08:
                v = u / 0.08
                for k in range(6):
                    w_ = v - k * 0.06
                    if w_ < 0: break
                    x_ = mouth[0] + (pool["x"] - mouth[0]) * w_
                    y_ = mouth[1] + (pool["y"] - mouth[1]) * w_ - 58 * 4 * w_ * (1 - w_)
                    stamp(cv, x_, y_, ramp(acid[::-1], np.array(k / 6)), 4.2 - k * 0.5)
            elif u < 0.2:
                v = (u - 0.08) / 0.12
                for t0, (x0, _, vx, vy, g) in splash.p[:16]:
                    stamp(cv, pool["x"] + x0 + vx * v, pool["y"] + vy * v + g * v * v, np.array((205, 250, 80)) * (1 - v), 1.5)
                ring(cv, pool["x"], pool["y"], 4 + v * pool["rx"], 0.24, 4, np.array([200.0, 245, 70]) * (1 - v))
        # the brood: hatchlings drop from their cells and scuttle out across the floor
        segs = []
        tips = []
        for b in brood:
            x0, y0 = cs[b["cell"]]
            hatch_t = enrage + rank[b["cell"]] * 0.30
            u = (t - hatch_t) / 0.40
            if not (0 <= u < 1): continue
            if u < 0.3:
                v = u / 0.3
                x, y = x0, y0 + (206 - y0) * v * v
            else:
                v = (u - 0.3) / 0.7
                x, y = x0 + (b["fx"] - x0) * v, 206 + 44 * v
            sz = 4.6 + 5.0 * max(0.0, u - 0.3)
            segs += spiderling(None, x, y, sz, t, b["ph"])
            tips.append((x, y, sz))
        if segs: paint(cv, ink(segs, 3), (205, 92, 40))
        for x, y, sz in tips:
            d = np.clip((1 - ((XX - x) / (sz * 1.5)) ** 2 - ((YY - y) / (sz * 1.05)) ** 2) * 4, 0, 1)[..., None]
            cv[:] = cv * (1 - d) + np.array([246.0, 136, 44]) * d
            stamp(cv, x, y - 1, (255, 244, 130), 1.6)
        return np.clip(shake(cv, hit * 2.8 * math.sin(s * 80), hit * 2.2 * math.cos(s * 110)), 0, 255)
    return "araxxor", "the brood", scene

# ---- Doom of Mokhaiotl: down the shaft, a level at a time ------------------------------
def solid_edges(cx, cy, r, t, spin, tilt, solid="icosahedron"):
    pts, edge = SOLIDS[solid]
    a = 2 * math.pi * spin * t; ca, sa, ct, st = math.cos(a), math.sin(a), math.cos(tilt), math.sin(tilt)
    scale = r / max(math.sqrt(sum(c * c for c in p)) for p in pts)
    out = []
    for x, y, z in pts:
        x, z = x * ca + z * sa, -x * sa + z * ca
        y, z = y * ct - z * st, y * st + z * ct
        out.append((cx + x * scale, cy + y * scale, z * scale))
    edges = []
    for i in range(len(pts)):
        for j in range(i + 1, len(pts)):
            if abs(math.dist(pts[i], pts[j]) - edge) < 1e-6:
                edges.append((out[i], out[j]))
    return edges

def doom_delve():
    rgb0, al0 = fit(solid_sprite("Doom_of_Mokhaiotl.png"), W + 2, H - 70, exposure=0.92, gamma=0.86, sat=1.1, grain=0.45)
    h, w = al0.shape[:2]; left = (W - w) // 2; top = 40
    rig = Rig(rgb0, al0, pad=50)
    lum = lum_of(rig); rim = rim_of(rig); grain = grain_of(rig, 1501)
    raw = ramp([(58, 8, 14), (162, 24, 26), (248, 70, 48), (255, 214, 170)], lum)[..., :3]
    gild = ramp([(40, 26, 16), (112, 84, 34), (214, 176, 72), (255, 244, 190)], lum)[..., :3]
    keep = rig.rgb.copy()
    chest_f = (0.52, 0.30)
    fx, fy = (rig.xx - rig.pad) / w, (rig.yy - rig.pad) / h
    from_chest = np.hypot((fx - chest_f[0]) * 1.1, (fy - chest_f[1]) * 0.8)
    # the shaft seen straight down: ledges at golden radii, so one level's zoom lands on the next
    scx, scy = W * 0.5, 104.0
    th = np.arctan2(YY - scy, XX - scx); rad = np.maximum(np.hypot(XX - scx, YY - scy), 1.0)
    wob = 1 + 0.035 * np.sin(th * 3 + 1.2) + 0.025 * np.sin(th * 7 + 0.4) + 0.015 * np.sin(th * 13)
    logr = np.log(rad * wob) / math.log(PHI) * 2                               # two ledges to each power of phi
    pol = wrap_noise(1511, 12, 12)
    ia = ((th / (2 * math.pi) + 0.5) * (W - 1)).astype(int)
    shaft_dark = np.clip((rad - 4) / 60, 0, 1)[..., None]
    rocks = Motes(1521, 40, lambda r: (r.rand() * 2 * math.pi, r.rand(), 0, 0, 0))
    sparks = Motes(1523, 26, lambda r: (r.rand() * 6.283, 10 + 30 * r.rand(), 0, 0, 0))
    SHATTER, DELVE, LAND = 0.30, 0.60, 0.86
    def scene(t):
        # s is levels fallen: it moves a little all the time and rushes in the delve; two whole levels a lap
        rush = sm(DELVE, DELVE + 0.05, t) * (1 - sm(LAND - 0.04, LAND, t))
        s = 0.2 * t + 1.8 * ((t - DELVE) / (LAND - DELVE) if DELVE <= t < LAND else (1.0 if t >= LAND else 0.0))
        v = logr - s
        fr = v % 1.0; lvl = np.floor(v).astype(int) % 2
        ledge = np.clip(1 - fr / 0.20, 0, 1)
        lodge = 0.55 + 0.45 * fr
        tex = pol[((fr * 60).astype(int) + lvl * 80) % H, ia]
        rock = np.array([34.0, 74, 86]) * ((0.30 + 0.62 * lodge)[..., None]) * (0.62 + 0.8 * tex)[..., None]
        rock = rock * (1 + 0.28 * (lvl == 0))[..., None]
        rock = rock + ledge[..., None] * np.array([34.0, 84, 92])
        # abyssal hue climbs out of the deep during the fall
        deep = np.exp(-(rad / (46 + 40 * rush)) ** 2)[..., None]
        rock = rock * (1 - deep) + np.array([24.0, 8, 54]) * deep
        rock = rock * (0.25 + 0.75 * shaft_dark) * (0.78 + 0.34 * rush)
        cv = feather(rock)
        if rush > 0.01:
            for t0, (a, rr, _, _, _) in rocks.p:
                u = (t * 4 + t0) % 1.0
                r_ = 7 * PHI ** (u * 7)
                x, y = scx + r_ * math.cos(a), scy + r_ * math.sin(a)
                stamp(cv, x, y, np.array((170, 225, 225)) * u * rush, 1.0 + 3.2 * u)
                if u > 0.25:
                    rb = 7 * PHI ** ((u - 0.1) * 7)
                    add(cv, ink([[(x, y), (scx + rb * math.cos(a), scy + rb * math.sin(a))]], 1), (120, 190, 200), rush * u)
        # the states
        shield = (1 - sm(SHATTER - 0.005, SHATTER + 0.03, t)) if t < DELVE else sm(LAND - 0.05, LAND + 0.04, t)
        grew = sm(DELVE + 0.02, LAND + 0.02, t) if t >= DELVE else 0.0
        hit = math.exp(-since(t, SHATTER) * 7)
        land_ = math.exp(-since(t, LAND) * 8)
        exposed = 1 - shield
        roar = sm(SHATTER, SHATTER + 0.05, t) * (1 - sm(DELVE - 0.04, DELVE + 0.03, t))
        fold = sm(DELVE - 0.03, DELVE + 0.05, t) * (1 - sm(LAND - 0.03, LAND + 0.05, t))
        settle = land_ * math.cos(since(t, LAND) * 22)
        # wings: spread wide when raw, snap up and in to fall; jaws gape
        wa = -7 * roar + 9 * fold - 6 * settle + 2.5 * wave(t, 2, phi_phase(1)) * (1 - fold)
        gape = roar * (0.65 + 0.35 * wave(t, 6)) + hit * 0.8
        parts = [
            dict(c=(0.50, 0.34), r=(0.12, 0.13), p=(0.50, 0.34), d=(0, -0.008 * wave(t, 2)), soft=0.9),
            dict(c=(0.14, 0.15), r=(0.17, 0.14), p=(0.30, 0.22), a=wa, soft=0.4),
            dict(c=(0.87, 0.27), r=(0.17, 0.12), p=(0.72, 0.29), a=-wa, soft=0.4),
            dict(c=(0.25, 0.56), r=(0.17, 0.32), p=(0.26, 0.30), a=-3 * wave(t, 2, phi_phase(2)) - 3 * roar + 6 * fold, soft=0.45),
            dict(c=(0.70, 0.60), r=(0.22, 0.34), p=(0.70, 0.30), a=2.5 * wave(t, 2, phi_phase(3)) + 3 * roar - 6 * fold, soft=0.45),
            dict(c=(0.26, 0.76), r=(0.15, 0.14), p=(0.26, 0.60), a=-5 * gape, d=(0, 0.04 * gape), soft=0.5),
            dict(c=(0.70, 0.85), r=(0.19, 0.14), p=(0.70, 0.68), a=4 * gape, d=(0, 0.04 * gape), soft=0.5),
            dict(c=(0.44, 0.62), r=(0.05, 0.22), p=(0.44, 0.42), a=6 * wave(t, 2, phi_phase(4)), soft=0.6),
            dict(c=(0.545, 0.09), r=(0.10, 0.12), p=(0.55, 0.22), a=4 * wave(t, 2, phi_phase(5)) - 6 * roar + 5 * fold, d=(0, -0.012 * roar + 0.018 * fold), soft=0.45),
        ]
        sink = 12 * fold * (0.8 + 0.2 * wave(t, 8)) - 7 * land_ * math.cos(since(t, LAND) * 14) - 4 * hit
        trail = fold
        def body(x, y):
            down = np.clip(y, 0, 1)
            # falling: the top leads, flesh lags up behind, squeezed; a big body feels the stop
            return 0 * x, -sink - 22 * trail * down ** 2 + 11 * settle * down ** 2
        reach = from_chest * 1.8
        w_gold = np.clip((shield * 2.4 - reach + (grain - 0.5) * 0.35) * 3, 0, 1)[..., None] if t < DELVE else np.clip((grew * 2.5 - reach + (grain - 0.5) * 0.35) * 3, 0, 1)[..., None]
        skin = raw * (1 - w_gold) + gild * w_gold
        heat = 0.92 + 0.14 * wave(t, 6) * exposed + 0.25 * hit
        rc = np.array([255.0, 225, 150]) * shield + np.array([255.0, 130, 90]) * exposed
        rig.rgb = np.clip(skin * heat * (1 - rim * 0.5) + rc * rim * 0.5, 0, 255)
        rgb, al = rig.pose(parts, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        cx_, cy_ = land(rig, (chest_f[0] * w, chest_f[1] * h), body)
        c = (left + cx_, top + cy_)
        pulse = 0.5 + 0.5 * wave(t, 4)
        glow(cv, c[0], c[1], 6 + 5 * pulse + 6 * shield, (255, 200, 70), 0.55 + 0.6 * shield)
        # the shield, a turning icosahedron in the chest's light; whole, then blown apart, then drawn back in
        R = 84.0
        lines_near, lines_far = [], []
        if t < SHATTER:
            k_off, show = 0.0, 1.0
        elif t < SHATTER + 0.16:
            k_off, show = (t - SHATTER) / 0.16, 1 - (t - SHATTER) / 0.16
        elif t >= LAND - 0.14:
            v_ = (t - (LAND - 0.14)) / 0.14
            k_off, show = max(0.0, 1 - v_), min(1.0, v_ * 1.3)
        else:
            k_off, show = 1.0, 0.0
        if show > 0.01:
            for a, b in solid_edges(c[0], c[1], R, t, 1, 0.45):
                mx, my = (a[0] + b[0]) / 2 - c[0], (a[1] + b[1]) / 2 - c[1]
                off = k_off ** 1.4 * 120 / max(math.hypot(mx, my), 12) * 1.0
                ax, ay = a[0] + mx * off, a[1] + my * off
                bx, by = b[0] + mx * off, b[1] + my * off
                (lines_near if a[2] + b[2] > 0 else lines_far).append([(ax, ay), (bx, by)])
            paint(cv, ink(lines_far, 1), (128, 210, 220), 0.55 * show)
            paint(cv, ink(lines_near, 2), (225, 255, 255), show)
            if t < SHATTER:
                # blows landing on it: the facet that takes one rings out
                for k, ht in enumerate((0.08, 0.17, 0.25)):
                    u = since(t, ht)
                    if u < 0.05:
                        a_ = k * GOLDEN_ANGLE + 0.4
                        px, py = c[0] + R * 0.95 * math.cos(a_), c[1] + R * 0.95 * math.sin(a_)
                        glow(cv, px, py, 5, (210, 250, 255), 1.1 * (1 - u / 0.05))
        if hit > 0.03:
            ss = since(t, SHATTER)
            ring(cv, c[0], c[1], 14 + ss * 620, 0.95, 7, np.array([255.0, 214, 120]) * hit)
            add(cv, np.exp(-((XX - c[0]) / 90) ** 2 - ((YY - c[1]) / 70) ** 2), (120, 60, 20), hit)
            if ss < 0.18:
                for t0, (a, sp, _, _, _) in sparks.p:
                    d = (0.5 + t0) * ss * 780
                    stamp(cv, c[0] + d * math.cos(a), c[1] + d * math.sin(a) * 0.86, np.array((255, 214, 110)) * (1 - ss / 0.18), 1.7)
        if roar > 0.02:
            for fxy, off in (((0.24, 0.74), 0.0), ((0.70, 0.82), 0.3)):
                mx, my = rig.at(parts[5 if off == 0 else 6], *fxy); mx, my = land(rig, (mx, my), body)
                glow(cv, left + mx, top + my, 14, (255, 74, 40), 0.5 * gape)
        return np.clip(shake(cv, land_ * 3 * math.sin(since(t, LAND) * 70) + hit * 2 * math.cos(since(t, SHATTER) * 90), land_ * 3.4 * math.cos(since(t, LAND) * 90)), 0, 255)
    return "doom", "the delve", scene

# ---- Tormented Demon: three altars, three styles, a shield of fire between them --------
def tormented_static():
    """A Guthixian temple gone cold: grey stone, three alcoves in a shallow arc, a neutral room each style can relight."""
    tex = fbm(1601, (20, 8, 3))[..., None]
    img = np.array([50.0, 50, 50]) * (0.6 + 0.65 * tex)
    row = (YY // 20).astype(int); off = (row % 2) * 17
    joint = ((YY % 20) < 1.5) | (((XX + off) % 34) < 1.5)
    img = img * (1 - 0.34 * joint[..., None])
    alcoves = [(28, 134, 19, 70), (W / 2, 112, 40, 96), (W - 28, 134, 19, 70)]
    voids = np.zeros((H, W)); arches = np.zeros((H, W))
    for ax, base, hw, tall in alcoves:
        yy0 = base - tall
        inside = ((np.abs(XX - ax) < hw) & (YY < base) & (YY > yy0 + hw)) | (((XX - ax) ** 2 + (YY - (yy0 + hw)) ** 2 < hw ** 2) & (YY <= yy0 + hw))
        band = ((np.abs(XX - ax) < hw + 4) & (YY < base + 4) & (YY > yy0 + hw)) | (((XX - ax) ** 2 + (YY - (yy0 + hw)) ** 2 < (hw + 4) ** 2) & (YY <= yy0 + hw))
        arches = np.maximum(arches, band * 1.0); voids = np.maximum(voids, inside * 1.0)
    img = img * (1 - arches[..., None]) + np.array([66.0, 66, 62]) * (0.7 + 0.5 * tex) * arches[..., None]
    img = img * (1 - voids[..., None]) + np.array([54.0, 54, 56]) * (0.7 + 0.5 * tex) * voids[..., None]
    z = np.clip((YY - 180) / (H - 180), 0, 1)
    across = (XX - W / 2) / (0.28 + z)
    jt = (np.abs((across + 26) % 52 - 26) < 1.3 + z) | (np.abs((np.log(z + 0.08) * 2.6) % 1 - 0.5) < 0.05)
    floor = np.array([54.0, 54, 52]) * (0.55 + 0.55 * z)[..., None] * (0.72 + 0.56 * fbm(1605, (14, 5, 2))[..., None]) * (1 - 0.4 * jt[..., None])
    return feather(np.where((YY > 180)[..., None], floor, img)), alcoves, voids

def crescent(cv, cx, cy, r, a0, a1, thick, colour, power=1.0):
    pts_o, pts_i = [], []
    for i in range(19):
        u = i / 18; a = a0 + (a1 - a0) * u
        tw = thick * math.sin(math.pi * u)
        pts_o.append((cx + (r + tw) * math.cos(a), cy + (r + tw) * math.sin(a)))
        pts_i.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    m = fill([pts_o + pts_i[::-1]])
    paint(cv, m, colour, power)
    add(cv, ink([pts_o], 1), (255, 250, 220), 0.7 * power)

def overhead(k, cx, cy, s):
    """Protect from melee, missiles, magic: a sword, an arrow nocked on a bow's curve, a star."""
    if k == 0:
        return [[(cx - s * 0.7, cy + s * 0.7), (cx + s * 0.75, cy - s * 0.75)], [(cx - s * 0.75, cy + s * 0.15), (cx - s * 0.15, cy + s * 0.75)]]
    if k == 1:
        bow = [(cx - s * 0.35 + s * 0.9 * math.sin(a) * 0.55, cy - s * math.cos(a)) for a in np.linspace(0.1, math.pi - 0.1, 12)]
        return [bow, [(cx - s, cy), (cx + s, cy)], [(cx + s * 0.55, cy - s * 0.4), (cx + s, cy), (cx + s * 0.55, cy + s * 0.4)]]
    p = polygon_pts(cx, cy, s, 5, rot=-math.pi / 2)
    return [[p[i], p[(i + 2) % 5]] for i in range(5)]

def tormented_swap():
    rgb0, al0 = fit(solid_sprite("Tormented_Demon_(1).png"), 130, H - 34, exposure=1.0, gamma=0.82, sat=1.0, grain=0.4)
    h, w = al0.shape[:2]; ground = H - 8; top = ground - h; left = (W - w) // 2
    rig = Rig(rgb0, al0, pad=50)
    # his head is a dark red knot in the sprite: lift it so the face survives every ramp
    hd = np.exp(-(((rig.xx - rig.pad) / w - 0.495) / 0.14) ** 2 - (((rig.yy - rig.pad) / h - 0.265) / 0.075) ** 2)[..., None]
    rig.rgb = np.clip(rig.rgb * (1 + 1.2 * hd) + hd * 60, 0, 255)
    lum = lum_of(rig); rim = rim_of(rig)
    back, alcoves, voids = tormented_static()
    # melee ember red, ranged sulphur green, magic cobalt
    tones = [np.array([255.0, 64, 34]), np.array([150.0, 230, 40]), np.array([70.0, 130, 255])]
    skins = [[(58, 10, 8), (176, 34, 18), (255, 110, 44), (255, 228, 170)],
             [(24, 44, 10), (78, 134, 22), (168, 224, 54), (240, 255, 180)],
             [(14, 22, 90), (40, 78, 200), (104, 164, 255), (225, 240, 255)]]
    dyed = [ramp(s, lum)[..., :3] * 0.88 + rig.rgb * 0.12 for s in skins]
    n1, n2 = _layer(1611, 8), _layer(1613, 4)
    embers = Motes(1621, 40, lambda r: (r.rand() * 6.283, 26 + r.rand() * 18, 0, 0, 0))
    def scene(t):
        p = t * 3; k = int(p) % 3; u = p % 1; ton = tones[k]
        flash = math.exp(-u * 14)
        prev = dyed[(k + 2) % 3]
        mix = sm(0.0, 0.12, u)
        shield = 1 - sm(0.30, 0.37, u)
        reform = sm(0.86, 1.0, u)                                             # the fire comes back round him as he changes
        attack = sm(0.36, 0.46, u) * (1 - sm(0.76, 0.92, u))
        wind = sm(0.14, 0.36, u) * (1 - sm(0.36, 0.42, u))
        strike = sm(0.36, 0.42, u) * (1 - sm(0.70, 0.92, u))
        broke = math.exp(-((u - 0.33) % 1.0) * 26)
        ax, ay, _, _ = alcoves[k]
        face = (ax - W / 2) / 79
        cv = back * (0.62 + 0.2 * flash)
        # the altar in use burns in the style's colour and relights the room from its side
        add(cv, np.exp(-((XX - ax) / 88) ** 2 - ((YY - ay + 34) / 70) ** 2), ton, 0.13 + 0.09 * attack + 0.12 * flash)
        add(cv, np.clip((YY - 180) / 60, 0, 1) * np.exp(-((XX - ax) / 78) ** 2), ton, 0.11)
        for i, (x, base, hw_, tall_) in enumerate(alcoves):
            on = (sm(0.0, 0.08, u) if i == k else (1 - sm(0.0, 0.10, u) if i == (k + 2) % 3 else 0.0))
            if on > 0.01:
                sh = int(t * 9 * H)
                nn = 0.6 * n1[(YY.astype(int) + sh) % H, XX.astype(int)] + 0.4 * n2[(YY.astype(int) + 2 * sh) % H, XX.astype(int)]
                v = (base - YY) / (tall_ * 0.82)
                den = np.clip((1 - np.abs(XX - x) / (hw_ * 0.78 * (1.05 - 0.5 * np.clip(v, 0, 1)))) * 1.6 + (nn - 0.5) * 2.2 - np.clip(v, 0, 1) ** 2 * 1.5, 0, 1) * (v > 0) * (v < 1) * voids * on
                col = np.array(skins[i][1], float) * (1 - den[..., None]) + np.array(skins[i][2], float) * den[..., None]
                cv[:] = cv * (1 - den[..., None]) + col * den[..., None]
            stamp(cv, x, base - 2, np.array((86, 84, 80)), 7)
        shadow(cv, W / 2, ground, w * 0.62, 7, 0.55)
        # the fire shield, far half behind him: a tall ring of flame standing on the floor
        cx_, cy_ = W / 2, ground - 4
        sh_pow = max(shield, reform)
        def flame_shell(cv_, front):
            if sh_pow < 0.02: return
            rx = w * 0.74
            sh = int(t * 8 * H)
            nn = 0.55 * n1[(YY.astype(int) + sh) % H, XX.astype(int)] + 0.45 * n2[(YY.astype(int) + 2 * sh) % H, XX.astype(int)]
            ex = (XX - cx_) / rx
            # height of the wall of fire round the ring, seen at a low angle
            band = np.sqrt(np.clip(1 - ex ** 2, 0, 1)) * 11 * (1 if front else -1)
            base_y = cy_ + band
            tall = (34 + 100 * sh_pow * np.sqrt(np.clip(1 - ex ** 2, 0, 1)) ** 1.2)
            v = (base_y - YY) / tall
            den = np.clip((1 - v) * 1.5 + (nn - 0.5) * 2.4 - 0.4, 0, 1) * (v > 0) * (np.abs(ex) < 1) * sh_pow
            den = den * (0.42 if front else 0.95)
            cv_[:] = cv_ * (1 - den[..., None]) + ramp([(255, 236, 150), (255, 150, 40), (200, 60, 16), (80, 22, 10)], 1 - den * (1.05 - 0.5 * np.clip(v, 0, 1)))[..., :3] * den[..., None]
        flame_shell(cv, False)
        # his body language differs by style
        if k == 0:     # melee: both claws cock back, then scythe across
            la, ra = 11 * wind - 16 * strike, -11 * wind + 16 * strike
            lean, drop = -2 * wind + 7 * strike * face, -6 * wind + 10 * strike
        elif k == 1:   # ranged: one arm throws overhand
            la, ra = 2 * wave(t, 3), -14 * wind + 19 * strike
            lean, drop = 7 * wind - 7 * strike, -4 * wind + 6 * strike
        else:          # magic: arms open, rising, hands fill
            la, ra = 11 * wind + 13 * attack, -11 * wind - 13 * attack
            lean, drop = 0.0, -7 * wind - 5 * attack + 3 * strike
        lean = lean + 3 * face * mix - 7 * face * flash
        head = dict(c=(0.495, 0.25), r=(0.14, 0.085), p=(0.50, 0.32), a=-7 * face + 3 * wave(t, 3, phi_phase(1)), lit=0.2 * attack)
        parts = [
            dict(c=(0.49, 0.42), r=(0.20, 0.09), p=(0.49, 0.42), d=(0, -0.008 * wave(t, 2)), soft=0.9),
            dict(c=(0.13, 0.27), r=(0.20, 0.12), p=(0.30, 0.31), a=1.6 * wave(t, 3, phi_phase(2)) + la * 0.4, soft=0.45),
            dict(c=(0.87, 0.27), r=(0.20, 0.12), p=(0.70, 0.31), a=-1.6 * wave(t, 3, phi_phase(3)) + ra * 0.4, soft=0.45),
            dict(c=(0.14, 0.62), r=(0.17, 0.22), p=(0.22, 0.38), a=la + 2 * wave(t, 2, phi_phase(4)), soft=0.4),
            dict(c=(0.86, 0.62), r=(0.17, 0.22), p=(0.78, 0.38), a=ra - 2 * wave(t, 2, phi_phase(5)), soft=0.4),
            dict(c=(0.50, 0.53), r=(0.15, 0.09), p=(0.50, 0.46), a=4 * wave(t, 2, phi_phase(6)) + 4 * lean / 7, soft=0.6),   # the loincloth lags
            dict(c=(0.50, 0.09), r=(0.28, 0.10), p=(0.50, 0.20), a=3 * wave(t, 3, phi_phase(7)), soft=0.5),                     # his crest of flame
            head]
        def body(x, y):
            up = np.clip((0.72 - y) / 0.72, 0, 1)
            flick = np.clip((0.2 - y) / 0.2, 0, 1) * 3 * np.sin(2 * math.pi * (t * 5 + x * 3))
            return -lean * up + flick, -drop * up
        skin = prev * (1 - mix) + dyed[k] * mix
        rc = ton * 0.55 + 110
        rig.rgb, saved = np.clip((skin + flash * (ton * 0.3 + 50)) * (1 - rim * 0.55) + rc * rim * 0.55, 0, 255), rig.rgb
        rgb, al = rig.pose(parts, light=(face * 0.9, -0.5), relief=0.3, bend=body)
        rig.rgb = saved
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        fx_, fy_ = land(rig, rig.at(head, 0.475, 0.262), body)
        glow(cv, left + fx_, top + fy_, 5, ton, 0.7 + 0.5 * attack)
        stamp(cv, left + fx_ - 3, top + fy_, (255, 255, 220), 2.0)
        stamp(cv, left + fx_ + 3, top + fy_, (255, 255, 220), 2.0)
        lh = land(rig, rig.at(parts[3], 0.09, 0.72), body); rh = land(rig, rig.at(parts[4], 0.91, 0.72), body)
        lh, rh = (left + lh[0], top + lh[1]), (left + rh[0], top + rh[1])
        flame_shell(cv, True)
        if broke > 0.04 and u >= 0.3:                                         # the blow that drops the shield throws the fire off him
            v = (u - 0.33) / 0.2
            if 0 <= v < 1:
                for t0, (a, r_, _, _, _) in embers.p:
                    d = 40 + 120 * v * (0.5 + t0)
                    stamp(cv, cx_ + d * math.cos(a), cy_ - 20 + d * math.sin(a) * 0.5 - 40 * v, np.array((255, 170, 60)) * (1 - v), 1.6)
            ring(cv, cx_, cy_, w * 0.66 + (u - 0.33) * 700, 0.26, 6, np.array([255.0, 150, 50]) * broke)
        if k == 0 and strike > 0.02:
            arc = sm(0.36, 0.56, u)
            crescent(cv, W / 2, 128, 80, math.radians(232 - 34 * arc), math.radians(150 - 22 * arc), 13, np.array([255.0, 150, 70]), min(1.0, strike * 1.2))
            crescent(cv, W / 2, 128, 80, math.radians(-52 + 34 * arc), math.radians(30 + 22 * arc), 13, np.array([255.0, 150, 70]), min(1.0, strike * 1.2))
        if k == 1:
            for j, (tx, ty, delay) in enumerate(((W * 0.13, 220, 0.0), (W * 0.84, 214, 0.1), (W * 0.40, 234, 0.2))):
                v = (u - 0.38 - delay) / 0.26
                if 0 <= v < 1:
                    for q in range(6):
                        vv = v - q * 0.05
                        if vv < 0: break
                        x_ = rh[0] + (tx - rh[0]) * vv; y_ = rh[1] + (ty - rh[1]) * vv - 74 * 4 * vv * (1 - vv)
                        stamp(cv, x_, y_, ramp([(245, 255, 170), (160, 230, 40), (60, 110, 16)], np.array(q / 5)), 5.4 - q * 0.6)
                elif 1 <= v < 1.6:
                    w_ = (v - 1) / 0.6
                    ring(cv, tx, ty, 4 + 30 * w_, 0.28, 4, tones[1] * (1 - w_))
                    glow(cv, tx, ty - 3, 13, tones[1] * 0.6, 1 - w_)
        if k == 2 and attack > 0.02:
            for hx, hy in (lh, rh):
                glow(cv, hx, hy, 7 + 7 * attack, tones[2], 0.8 * attack)
                stamp(cv, hx, hy, (225, 240, 255), 1.6 + 2 * attack)
            if strike > 0.1:
                seed = 1631 + int(t * FRAMES) % 4
                bolt(cv, lh[0], lh[1], 12, 24, seed, (170, 205, 255), jag=38, size=1.4)
                bolt(cv, rh[0], rh[1], W - 12, 24, seed + 9, (170, 205, 255), jag=38, size=1.4)
                bolt(cv, lh[0], lh[1], 40, 226, seed + 17, (120, 170, 255), jag=30, size=1.1)
                bolt(cv, rh[0], rh[1], W - 40, 226, seed + 23, (120, 170, 255), jag=30, size=1.1)
        # his overhead: which style he protects from, snapped over on the change
        scale = 1 + 0.5 * flash
        ico = ink(overhead(k, W / 2 - lean * 0.9, 15 + drop * 0.9, 9 * scale), 2)
        add(cv, np.exp(-((XX - W / 2) / 16) ** 2 - ((YY - 15) / 12) ** 2), ton, -0.0)
        paint(cv, ico, np.clip(ton * 0.75 + 70, 0, 255))
        e = flash * 0.7 + broke * 0.7
        return np.clip(shake(cv, e * 2.2 * math.sin(u * 70), e * 2 * math.cos(u * 90)), 0, 255)
    return "tormented", "the swap", scene

RECIPES = [kree_storm, zilyana_figure, vardorvis_axes, araxxor_brood, doom_delve, tormented_swap]
