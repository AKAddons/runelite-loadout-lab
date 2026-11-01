"""Hi-res recipes, batch 3c: the Crazy Archaeologist, the Mimic, the lizardman shaman, Hespori, the Shellbane Gryphon, Skotizo."""
from hires_kit import *
from hires_batch2c import sm, cut_sprite, lum_of, rim_of, heal, up_of, darken_round, over_above, arc_slash, shadow, shake, coursed, spike, poly, strokes
from hires_batch3a import figure, stand, spot, lean_bend, beat, ground, debris

# ---- the Crazy Archaeologist: books rain down and go off ----------------------------
def dig_backdrop():
    """his dig in the Wilderness: strata in the trench wall, a shored beam, red dusk over the lip"""
    strata = 0.5 + 0.5 * np.sin(YY * 0.5 + 8 * fbm(2501, (40, 14)))
    img = np.array([62.0, 46, 32]) * (0.55 + 0.45 * strata[..., None]) * (0.7 + 0.5 * fbm(2503, (14, 6, 3))[..., None])
    sky = (YY < 44 + 12 * fbm(2505, (30, 12))[0, :][None, :])[..., None]
    img = img * (1 - sky) + ramp([(70, 30, 26), (86, 44, 30)], np.clip(YY / 50, 0, 1)) * sky
    for x in (24, W - 34):
        m = ((np.abs(XX - x) < 5) & (YY > 40))[..., None]
        img = img * (1 - m) + np.array([44.0, 30, 20]) * (0.7 + 0.5 * fbm(2507, (50, 3))[..., None]) * m
    beam = ((np.abs(YY - 56) < 5) & (XX > 16) & (XX < W - 26))[..., None]
    img = img * (1 - beam) + np.array([48.0, 34, 22]) * beam
    return ground(img, 186, (56, 42, 28), 2509)

def book(cv, x, y, flap, colour, size=7.0):
    """a book in flight: two boards hinged on the spine, flapping"""
    a = 0.7 * flap
    for s in (-1, 1):
        q = [(x, y - size * 0.6), (x, y + size * 0.6), (x + s * size * math.cos(a), y + size * 0.6 - size * math.sin(a)), (x + s * size * math.cos(a), y - size * 0.6 - size * math.sin(a))]
        cv[:] = cv * (1 - poly(q)) + np.array(colour, float) * (0.75 + 0.25 * s) * poly(q)
    line(cv, (x, y - size * 0.6), (x, y + size * 0.6), (240, 230, 200), 0.8)

def archaeologist_rain():
    rig, (h, w) = figure("Crazy_archaeologist.png", 100, H - 18, exposure=0.72, gamma=1.0, sat=1.05)
    back = dig_backdrop()
    left, top = 22, H - 10 - h
    r = np.random.RandomState(2511)
    # books land on a golden-angle scatter over the trench floor
    LAND = [(W * 0.5 + 92 * math.sqrt((i + 1) / 7) * math.cos(i * GOLDEN_ANGLE), 208 + 16 * math.sqrt((i + 1) / 7) * math.sin(i * GOLDEN_ANGLE)) for i in range(7)]
    TIMES = [(i * 0.143 + 0.03) % 1 for i in (0, 4, 1, 5, 2, 6, 3)]
    COL = [(200, 60, 40), (60, 110, 190), (70, 150, 80), (190, 150, 50), (150, 60, 170), (200, 90, 40), (60, 150, 170)]
    pages = Motes(2513, 16, lambda q: (0, 0, 80 * (q.rand() - 0.5), -100 * (0.3 + q.rand()), 150))
    motes = Motes(2515, 14, lambda q: (q.rand() * W, 60 + q.rand() * 120, 8 * (q.rand() - 0.5), 16 * q.rand(), 0))
    def scene(t):
        cv = feather(back * (0.86 + 0.06 * wave(t, 2)))
        motes.draw(cv, t, lambda v: np.array((100, 80, 60)) * math.sin(math.pi * v), 0.9)
        lift = wave(t, 4)
        arm = dict(c=(0.34, 0.45), r=(0.26, 0.17), p=(0.4, 0.34), a=-5 + 6 * lift, soft=0.45)
        far = dict(c=(0.82, 0.42), r=(0.18, 0.18), p=(0.72, 0.28), a=-9 * wave(t, 2), soft=0.45)
        head = dict(c=(0.5, 0.1), r=(0.3, 0.11), p=(0.5, 0.19), a=4 * wave(t, 2, 0.15), soft=0.5)
        shadow(cv, left + w * 0.5, H - 11, w * 0.5, 4.5, 0.5)
        stand(cv, rig, [far, arm, head], left, top, (0.7 * wave(t, 1), -0.7), lean_bend(3 * wave(t, 2, 0.3)), 0.5)
        bx, by = spot(rig, arm, 0.35, 0.45, left, top)
        glow(cv, bx, by, 8, (120, 190, 255), 0.45 + 0.25 * lift)
        for i, (lx, ly) in enumerate(LAND):
            q = (t - TIMES[i]) % 1 * 3.5
            if q < 1:                                                            # up out of the open book and over: a mark on the ground warns first
                ring(cv, lx, ly, 13, 0.34, 1.6, np.array((240.0, 190, 90)) * 0.7)
                x = bx + (lx - bx) * q; y = by + (ly - by) * q - 130 * math.sin(math.pi * q)
                book(cv, x, y, math.sin(2 * math.pi * q * 5), COL[i])
            elif q < 2:
                f = q - 1
                ring(cv, lx, ly, 5 + 40 * f, 0.34, 4, np.array((255.0, 170, 60)) * (1 - f))
                glow(cv, lx, ly - 6, 22 * (1 - f * 0.5), (255, 110, 30), (1 - f) * 1.0)
                glow(cv, lx, ly - 6, 7, (255, 240, 200), (1 - f) ** 2 * 1.3)
                for t0, (_, _, vx, vy, g) in pages.p:
                    x, y = lx + vx * f, ly - 4 + vy * f + g * f * f
                    line(cv, (x - 2, y), (x + 2, y - 1.6 * math.sin(40 * t0 + 8 * f)), np.array((236.0, 226, 196)) * (1 - f * 0.6), 0.9)
        return np.clip(cv, 0, 255)
    return "archaeologist", "rain of knowledge", scene

# ---- the Mimic: the chest was never a chest ----------------------------------------
def hoard_backdrop():
    """a treasure vault: dark stone, and heaps of coin banked up to either side"""
    img = coursed(20, 36, 2601, (36, 32, 34), (22, 9, 3)).copy()
    h1 = 118 + 60 * ((XX - W / 2) / (W / 2)) ** 2 * -1 + 62
    heap = (YY > 176 - 76 * (np.abs(XX - W / 2) / (W / 2)) ** 2)[..., None]
    r = np.random.RandomState(2603)
    ix = (XX // 5).astype(int); iy = (YY // 4).astype(int)
    tone = r.rand(60, 60)[iy % 60, (ix + iy % 2) % 60]
    coin = ramp([(52, 36, 12), (86, 62, 18), (112, 84, 26)], tone)
    return img * (1 - heap) + coin * heap, tone

def mimic_teeth():
    rig, (h, w) = figure("The_Mimic.png", W - 18, H - 14, exposure=0.95, gamma=0.82, sat=1.25, healed=3)
    back, tone = hoard_backdrop()
    left, top = (W - w) // 2, H - 8 - h
    coins = Motes(2611, 40, lambda q: (W / 2 + 40 * (q.rand() - 0.5), 118, 180 * (q.rand() - 0.5), -190 * (0.35 + q.rand()), 300))
    def scene(t):
        p = t * 2; u = p % 1
        wind, go, hit = beat(u, 0.5, 0.36, 0.32)
        cv = back * (0.85 + 0.1 * hit)
        # the coins twinkle, each on its own phase
        tw = np.clip(np.sin(tone * 40 + 2 * math.pi * t * 2), 0, 1) ** 30
        cv = feather(cv + (tw * (cv.mean(2) > 34))[..., None] * np.array([120.0, 96, 40]))
        # the lid gapes, then it snaps shut
        gape = wind * 1.0 + 0.08 * max(0.0, wave(t, 4))
        snap = go
        lid = dict(c=(0.5, 0.2), r=(0.46, 0.27), p=(0.5, 0.48), d=(0, -0.05 * gape + 0.018 * snap), soft=0.45)
        low = dict(c=(0.5, 0.74), r=(0.42, 0.19), p=(0.5, 0.56), d=(0, 0.018 * gape - 0.012 * snap), soft=0.5)
        clawl = dict(c=(0.14, 0.4), r=(0.15, 0.13), p=(0.26, 0.36), a=-10 * gape + 8 * snap, soft=0.5)
        clawr = dict(c=(0.86, 0.4), r=(0.15, 0.13), p=(0.74, 0.36), a=10 * gape - 8 * snap, soft=0.5)
        def body(x, y):
            v = np.clip((y - 0.86) / 0.14, 0, 1)                                 # the dark tendrils under it writhe
            return -5 * v * np.sin(2 * math.pi * (t * 3 + x * 3)), -2.5 * v * np.cos(2 * math.pi * (t * 3 + x * 4))
        shadow(cv, W / 2, H - 16, 96, 8, 0.6)
        stand(cv, rig, [low, lid, clawl, clawr], left, top, (0.6 * wave(t, 1), -0.7), body, 0.55)
        mx, my = left + 0.5 * w, top + 0.41 * h
        glow(cv, mx, my, 12 + 22 * wind, (255, 60, 40), 0.25 + 0.75 * wind)
        for dx in (-22, 22):
            ex, ey = spot(rig, lid, 0.5 + dx / w, 0.32, left, top)
            glow(cv, ex, ey, 3 + 2 * wind, (255, 150, 40), 0.7 + 0.5 * wind)
            stamp(cv, ex, ey, (255, 240, 200), 1.3)
        if u >= 0.5:                                                             # the snap throws the hoard into the air
            f = min(0.99, (u - 0.5) / 0.46)
            for t0, (x, y, vx, vy, g) in coins.p:
                cy_ = y + vy * f + g * f * f
                if cy_ < 226:
                    wob = abs(math.sin(2 * math.pi * (f * 4 + t0)))
                    stamp(cv, x + vx * f, cy_, np.array((255.0, 214, 90)) * (0.5 + 0.5 * wob), 1.0 + 0.8 * wob)
            ring(cv, mx, my, 8 + 80 * min(1.0, f * 2.4), 1.0, 3, np.array((255.0, 180, 100)) * max(0.0, 1 - f * 2.4))
        return np.clip(shake(cv, 2 * hit * wave(t, 18), 3 * hit * wave(t, 15)), 0, 255)
    return "mimic", "the teeth", scene

# ---- the lizardman shaman: up out of the frame, and down on you ----------------------
def temple_backdrop():
    """the Lizardman Temple: green-black stone, carved arches, a sump of acid on the floor"""
    img = coursed(16, 30, 2701, (36, 44, 34), (20, 8, 3)).copy()
    for cx in (40, W / 2, W - 40):
        m = ((np.abs(XX - cx) < 26) & (YY > 62 + 26 * (np.abs(XX - cx) / 26) ** 2) & (YY < 176))[..., None]
        img = img * (1 - m) + np.array([14.0, 20, 16]) * (0.8 + 0.4 * fbm(2703, (9, 4))[..., None]) * m
    return ground(img, 176, (36, 42, 30), 2705)

def shaman_jump():
    rig, (h, w) = figure("Lizardman_shaman_(Lizardman_Temple).png", W - 50, H - 22, exposure=0.8, gamma=0.92, sat=1.2)
    back = temple_backdrop()
    spray = debris(2711, 30, W / 2, 218, 210, 120)
    drips = Motes(2713, 10, lambda r: (r.rand() * W, 0, 0, 170, 0))
    fumes = Motes(2715, 18, lambda r: (W * r.rand(), 206 + 16 * r.rand(), 10 * (r.rand() - 0.5), -46 * r.rand(), 0))
    GROUND = H - 10
    acid = noise(2717, 8, 0.0)
    def scene(t):
        u = t % 1
        # crouch, leave the frame, the shadow narrows on you, land
        crouch = sm(0.04, 0.2, u) * (1 - sm(0.2, 0.25, u))
        air = sm(0.22, 0.33, u) * (1 - sm(0.62, 0.72, u))
        land = u >= 0.72
        f = (u - 0.72) / 0.28 if land else 0.0
        hit = math.exp(-f * 8) if land else 0.0
        cv = feather(back * (0.88 + 0.28 * hit))
        drips.draw(cv, (t * 3) % 1, lambda v: np.array((100, 200, 80)) * 0.45, 0.9)
        lift = -H * 1.05 * air
        squat = 22 * crouch + 26 * hit
        left, top = (W - w) // 2 + 2, GROUND - h + lift
        head = dict(c=(0.13, 0.12), r=(0.2, 0.13), p=(0.26, 0.24), a=3 * wave(t, 2) + 6 * crouch - 7 * hit, soft=0.5)
        arm = dict(c=(0.84, 0.62), r=(0.17, 0.2), p=(0.62, 0.42), a=4 * wave(t, 3) + 6 * air, soft=0.45)
        fore = dict(c=(0.16, 0.42), r=(0.16, 0.16), p=(0.27, 0.32), a=-6 * crouch + 10 * hit + 3 * wave(t, 2, 0.2), soft=0.45)
        def body(x, y):
            v = up_of(y, 1.0)
            return 0 * v, squat * v * -1 * -1 * -1
        # the shadow: wide when he is high, gathering as he drops
        q = air
        if q > 0.02:
            shadow(cv, W / 2, 212, 26 + 42 * q, 8, 0.7)
            if 0.4 < u < 0.72:
                ring(cv, W / 2, 212, 34 + 34 * (1 - (u - 0.4) / 0.32), 0.26, 3, np.array((200.0, 255, 110)) * 0.7)
        else:
            shadow(cv, left + w * 0.5, GROUND - 3, w * 0.45, 6, 0.55)
        if air < 0.98:
            stand(cv, rig, [arm, fore, head], left, top, (0.7 * wave(t, 1), -0.7), body, 0.5)
            ex, ey = spot(rig, head, 0.09, 0.1, left, top)
            glow(cv, ex, ey, 3, (255, 240, 90), 0.5 + 0.5 * crouch)
        # the sump, in front: acid that moves on its own
        n = noise(2717, 8, t)
        sump = np.clip(1 - np.hypot((XX - W / 2) / 100, (YY - 228) / 12), 0, 1)
        den = np.clip(sump * 3, 0, 1)[..., None]
        cv = cv * (1 - den) + ramp([(30, 66, 22), (70, 130, 34), (150, 210, 60)], n)[..., :3] * den
        fumes.draw(cv, t, lambda v: np.array((110, 180, 70)) * math.sin(math.pi * v) * 0.6, 1.1)
        if land:
            for i in range(3):
                ring(cv, W / 2, 214, 8 + (150 - 30 * i) * min(1.0, f * 1.5), 0.26, 4 - i, np.array((180.0, 245, 110)) * max(0.0, 1 - f * 1.5))
            spray.draw(cv, min(0.99, f), lambda v: np.array((150, 225, 80)) * (1 - v * 0.6), 1.9)
        return np.clip(shake(cv, 0, 7 * hit * wave(t, 17)), 0, 255)
    return "shaman", "the jump", scene

# ---- Hespori: the flowers open at the corners, and it sows --------------------------
def patch_backdrop():
    """the Farming Guild's patch: tall dark hedges, furrowed soil in perspective"""
    tex = fbm(2801, (10, 5, 3))
    img = ramp([(10, 22, 12), (14, 30, 15), (22, 40, 18)], tex).copy()
    img = img * (1 - 0.55 * (YY < 30)[..., None])
    z = np.clip((YY - 168) / 72, 0, 1)
    across = (XX - W / 2) / (0.3 + z)
    furrow = 0.72 + 0.4 * np.sin(across * 0.3)
    soil = np.array([58.0, 42, 26]) * (furrow * (0.65 + 0.55 * fbm(2803, (10, 5, 3))) * (0.6 + 0.6 * z))[..., None]
    m = (YY > 168)[..., None]
    return img * (1 - m) + soil * m

def flower(cv, x, y, r, opened, colour, spin):
    """five rounded petals on a stalk: a rose curve that opens from a bud"""
    col = np.array(colour, float)
    th = np.arctan2((YY - y) / 0.78, XX - x) - spin
    d = np.hypot(XX - x, (YY - y) / 0.78)
    bloom = r * opened * (0.55 + 0.45 * np.abs(np.cos(th * 2.5)) ** 0.6)
    m = np.clip(bloom - d, 0, 1)[..., None]
    shade = (0.55 + 0.45 * (d / np.maximum(bloom, 1)))[..., None]
    cv[:] = cv * (1 - m) + col * shade * m
    stamp(cv, x, y, (250, 230, 110), 1 + 2.5 * opened)

def hespori_seeds(under=None):
    rig, (h, w) = figure("Hespori.png", 118, H - 16, thr=128, exposure=0.72, gamma=1.0, sat=1.0)
    back = patch_backdrop()
    left, top = (W - w) // 2, H - 8 - h
    CORNERS = [(38, 176), (W - 38, 176), (18, 216), (W - 18, 216)]
    TONES = [(236, 70, 110), (240, 150, 40), (180, 80, 230), (70, 170, 240)]
    SEEDS = [(W / 2 + 96 * math.sqrt((i + 1) / 16) * math.cos(i * GOLDEN_ANGLE), 196 + 20 * math.sqrt((i + 1) / 16) * math.sin(i * GOLDEN_ANGLE)) for i in range(16)]
    pollen = Motes(2811, 22, lambda r: (W * r.rand(), 60 + r.rand() * 120, 22 * (r.rand() - 0.5), -40 * r.rand(), 0))
    def scene(t):
        u = t % 1
        # the four flowers open in turn; with all four open it sows, and they die back
        opens = [sm(0.05 + i * 0.12, 0.17 + i * 0.12, u) * (1 - sm(0.84, 0.95, u)) for i in range(4)]
        sow = sm(0.53, 0.6, u) * (1 - sm(0.8, 0.86, u))
        cv = feather(back * (0.86 + 0.14 * sow))
        pollen.draw(cv, t, lambda v: np.array((190, 200, 90)) * math.sin(math.pi * v) * 0.7, 1.0)
        for i, (x, y) in enumerate(CORNERS):
            if opens[i] > 0.02: glow(cv, W / 2, 26, 1, TONES[i], 0)
            line(cv, (x, y + 16), (x, y), (40, 96, 34), 1.5)
        def body(x, y):
            v = up_of(y, 0.9)
            return -(5 + 5 * sow) * v * math.sin(2 * math.pi * t * 3) - 3 * v ** 2 * np.sin(2 * math.pi * (t * 3 - y)), -7 * sow * v * math.sin(2 * math.pi * t * 6)
        crown = dict(c=(0.5, 0.17), r=(0.34, 0.18), p=(0.5, 0.33), a=4 * wave(t, 2) + 4 * sow * wave(t, 8), soft=0.5)
        arm_l = dict(c=(0.2, 0.47), r=(0.18, 0.14), p=(0.36, 0.42), a=6 * wave(t, 2, 0.2), soft=0.5)
        arm_r = dict(c=(0.8, 0.47), r=(0.18, 0.14), p=(0.64, 0.42), a=-6 * wave(t, 2, 0.4), soft=0.5)
        if under: under(cv, t)
        stand(cv, rig, [arm_l, arm_r, crown], left, top, (0.7 * wave(t, 1), -0.7), body, 0.45)
        for i, (x, y) in enumerate(CORNERS):                                     # lit from the crown, tethered while open
            if opens[i] > 0.02:
                line(cv, (x, y), (left + w / 2, top + h * 0.28), np.array(TONES[i], float) * 0.22 * opens[i], 0.8)
            flower(cv, x + 2 * wave(t, 2, i * 0.3), y, 17 + (y - 176) * 0.12, max(0.14, opens[i]), TONES[i] if opens[i] > 0.02 else (60, 110, 50), 2 * math.pi * t * (0.0 + 1) * 0.0 + i * 0.6)
        cx, cy = left + w * 0.5, top + h * 0.16
        glow(cv, cx, cy, 10 + 16 * sow, (170, 255, 70), 0.3 + 0.8 * sow)
        for i, (sx, sy) in enumerate(SEEDS):
            q = (u - 0.54 - i * 0.012) / 0.2
            if 0 <= q < 1:
                x = cx + (sx - cx) * q; y = cy + (sy - cy) * q - 90 * math.sin(math.pi * q)
                stamp(cv, x, y, (210, 255, 100), 2.2); stamp(cv, x, y, (255, 255, 200), 1.0)
            elif q >= 1:
                sprout = min(1.0, (q - 1) * 1.1) * (1 - sm(0.86, 0.95, u))
                if sprout > 0.02:
                    line(cv, (sx, sy), (sx + 1.5, sy - 11 * sprout), (90, 210, 60), 1.0)
                    stamp(cv, sx + 1.5, sy - 11 * sprout, (170, 255, 90), 1.6 * sprout)
        return np.clip(cv, 0, 255)
    return "hespori", "the four flowers", scene

# ---- the Shellbane Gryphon: it comes down out of the sun ---------------------------
def cliff_backdrop():
    """a high ledge: a pale sun in thin cloud, far crags, the bare rock it nests on"""
    img = ramp([(46, 70, 102), (60, 84, 108), (88, 100, 104)], np.clip(YY / 180, 0, 1)).copy()
    d = np.hypot(XX - 40, YY - 40)
    img += np.exp(-(d / 34) ** 2)[..., None] * np.array([60.0, 54, 32])
    far = ridge(2901, 136, 30)
    m = (YY > far[None, :])[..., None]
    img = img * (1 - m) + np.array([48.0, 58, 74]) * (0.8 + 0.4 * fbm(2903, (16, 7, 3))[..., None]) * m
    return ground(img, 182, (58, 52, 46), 2905)

def spread_wing(span):
    """the sprite's wing is folded flat to its side, so this one is its own layer: russet coverts, dark long primaries, in its colours. Root at the bottom right."""
    S = 3
    im = Image.new("RGBA", (span * S, span * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rx, ry = (span - 6) * S, (span - 6) * S
    def blade(angle, length, wide, colour, edge):
        a = math.radians(angle)
        ux, uy, vx, vy = math.cos(a), -math.sin(a), math.sin(a), math.cos(a)
        L, Wd = length * S, wide * S
        pts = [(rx, ry), (rx + ux * L * 0.5 + vx * Wd, ry + uy * L * 0.5 + vy * Wd), (rx + ux * L, ry + uy * L), (rx + ux * L * 0.5 - vx * Wd, ry + uy * L * 0.5 - vy * Wd)]
        d.polygon(pts, fill=colour)
        d.line([pts[0], pts[2]], fill=edge, width=S)
    for i in range(7):                                                          # primaries, longest towards the leading edge
        blade(112 + i * 11, span * (1.02 - i * 0.055), 6, (56, 56, 72, 255), (118, 118, 138, 255))
    for i in range(6):
        blade(118 + i * 11, span * (0.68 - i * 0.03), 6.5, (150, 88, 30, 255), (214, 138, 58, 255))
    for i in range(5):
        blade(124 + i * 12, span * (0.4 - i * 0.012), 7, (196, 112, 36, 255), (240, 170, 84, 255))
    a = np.asarray(im.resize((span, span), Image.LANCZOS), float)
    return Image.fromarray(np.dstack([a[..., :3], (a[..., 3] > 128) * 255.0]).astype(np.uint8), "RGBA")

def gryphon_swoop():
    rgb, al = fit(cut_sprite("Shellbane_gryphon.png", 128), W + 2, H - 52, exposure=0.96, gamma=0.86, sat=1.2, grain=0.4)
    h, w = al.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    # off with the folded wing's dark feather tips, or it reads as a third wing under the spread ones
    tips = (xx < 0.5 * w) & (yy < 0.68 * h) & (rgb.max(2) < 120) & (rgb.max(2) - rgb.min(2) < 34)
    al = al * (1 - tips[..., None])
    rig = Rig(heal(rgb, 5), al, pad=40)
    near = spread_wing(int(0.66 * w)); far = spread_wing(int(0.58 * w))
    def wing(cv, img, x, y, deg, shade):
        """the root held at (x, y), turned about it"""
        turned = np.asarray(img.rotate(deg, Image.BILINEAR, center=(img.width - 6, img.height - 6)), float)
        over(cv, turned[..., :3] * shade, turned[..., 3:] / 255, int(x) - img.width + 6, int(y) - img.height + 6)
    back = cliff_backdrop()
    feathers = Motes(2911, 18, lambda r: (W * r.rand(), 40 + r.rand() * 130, -50 * r.rand(), 80 + 70 * r.rand(), 0))
    grit = debris(2913, 24, W * 0.6, 214, 170, 70)
    GROUND = H - 14
    def scene(t):
        u = t % 1
        # perched; it lifts away, passes out of the frame, and comes back hard from up left
        rear = sm(0.1, 0.25, u) * (1 - sm(0.25, 0.32, u))
        away = sm(0.28, 0.42, u) * (1 - sm(0.56, 0.74, u))
        land = u >= 0.74
        f = (u - 0.74) / 0.26 if land else 0.0
        hit = math.exp(-f * 9) if land else 0.0
        n = noise(2915, 26, t)
        cv = back.copy()
        haze = np.clip((n - 0.45) * 1.7, 0, 1)[..., None] * (YY < 130)[..., None]
        cv = cv * (1 - 0.45 * haze) + np.array([120.0, 128, 132]) * 0.45 * haze
        cv = feather(cv * (0.84 + 0.2 * hit))
        ox = -280 * away * (1 if u > 0.5 else -0.5) - 0.0
        oy = -220 * away
        left, top = (W - w) // 2 + ox, GROUND - h + oy
        flap = wave(t, 3) * (0.4 + 0.6 * away) + rear * 0.6
        tail = dict(c=(0.09, 0.56), r=(0.12, 0.12), p=(0.2, 0.55), a=6 * wave(t, 3, 0.3), soft=0.5)
        head = dict(c=(0.84, 0.17), r=(0.17, 0.18), p=(0.75, 0.33), a=3 * wave(t, 2, 0.1) - 6 * rear + 10 * hit, soft=0.5)
        talon = dict(c=(0.74, 0.86), r=(0.12, 0.14), p=(0.72, 0.68), a=-7 * rear + 5 * hit, soft=0.5)
        chest = dict(c=(0.62, 0.45), r=(0.14, 0.17), p=(0.62, 0.45), d=(0, -0.012 * wave(t, 4)), soft=0.9)
        lean = 7 * hit - 8 * rear
        drop = 12 * hit - 12 * rear
        # its shadow crosses the ledge ahead of it
        if away > 0.02:
            shadow(cv, W * 0.5 + 240 * (1 - away) * (1 if u > 0.5 else -1), 208, 70, 9, 0.55)
        else:
            shadow(cv, left + w * 0.55, GROUND - 2, w * 0.45, 6, 0.55)
        if away < 0.98:
            beat_ = -6 + 26 * flap - 26 * hit
            wing(cv, far, left + 0.67 * w + lean * 0.5, top + 0.4 * h + drop * 0.5, -beat_ * 0.8 - 14, 0.62)
            stand(cv, rig, [chest, tail, talon, head], left, top, (0.6 * math.cos(2 * math.pi * t), -0.8), lean_bend(lean, drop, 0.92), 0.5)
            wing(cv, near, left + 0.62 * w + lean * 0.5, top + 0.46 * h + drop * 0.5, -beat_, 1.0)
            ex, ey = spot(rig, head, 0.86, 0.11, left, top)
            glow(cv, ex + lean, ey, 2.2, (255, 220, 90), 0.5 + 0.5 * rear)
        if 0.56 < u < 0.74:                                                       # the rush of air before it lands
            for i in range(12):
                a = 0.7 + 0.05 * i; x0 = W * (i * 0.09); y0 = (i * 37) % 170
                line(cv, (x0, y0), (x0 + 50 * math.cos(a), y0 + 50 * math.sin(a)), np.array((235.0, 235, 230)) * 0.5, 0.8)
        feathers.draw(cv, (t * 2) % 1, lambda v: np.array((230, 150, 70)) * math.sin(math.pi * v), 1.1)
        if land:
            ring(cv, W * 0.6, 214, 8 + 120 * min(1.0, f * 1.4), 0.26, 4, np.array((225.0, 205, 170)) * max(0.0, 1 - f * 1.4))
            grit.draw(cv, min(0.99, f), lambda v: np.array((165, 145, 118)) * (1 - v), 1.7)
        return np.clip(shake(cv, 0, 4 * hit * wave(t, 17)), 0, 255)
    return "gryphon", "the swoop", scene

# ---- Skotizo: four altars hold it ---------------------------------------------------
def catacomb_backdrop():
    """the Catacombs of Kourend: skull-coursed walls, dim, and a sigil cut in the floor"""
    img = coursed(14, 22, 3001, (34, 28, 40), (20, 8, 3)).copy()
    sk = (((XX % 44 - 22) ** 2 + ((YY % 28 - 14) * 1.3) ** 2 < 26) & (YY < 170))[..., None]
    img = img * (1 - 0.55 * sk) + np.array([56.0, 50, 60]) * 0.55 * sk
    return ground(img, 170, (34, 28, 42), 3003)

def skotizo_altars():
    rig, (h, w) = figure("Skotizo.png", W - 8, H - 24, 60, exposure=0.85, gamma=0.85, sat=1.2, healed=3)
    lum = lum_of(rig, 4, 96)
    dark = rig.rgb.copy()
    lit = ramp([(14, 6, 28), (66, 22, 130), (170, 70, 255), (250, 215, 255)], lum)[..., :3]
    back = catacomb_backdrop()
    left, top = (W - w) // 2, H - 12 - h
    # the four altars on the corners of a floor square, turned in perspective
    ALT = polygon_pts(W / 2, 194, 96, 4, math.pi / 4, 0.3)
    ALT = sorted(ALT, key=lambda p: p[1])
    wisps = Motes(3011, 24, lambda r: (W * r.rand(), 196 + 20 * r.rand(), 14 * (r.rand() - 0.5), -110 * r.rand(), 0))
    def altar(cv, x, y, on, s):
        c = np.array((60.0, 54, 70)) * (0.8 + 0.5 * on)
        m = poly([(x - 13 * s, y - 16 * s), (x + 13 * s, y - 16 * s), (x + 16 * s, y), (x - 16 * s, y)])
        cv[:] = cv * (1 - m) + c * m
        top_ = poly([(x - 15 * s, y - 21 * s), (x + 15 * s, y - 21 * s), (x + 13 * s, y - 15 * s), (x - 13 * s, y - 15 * s)])
        cv[:] = cv * (1 - top_) + (c * 1.3 + np.array((120.0, 60, 200)) * on) * top_
        if on > 0.02:
            flick = 0.8 + 0.2 * wave(on, 1)
            glow(cv, x, y - 24 * s, 7, (150, 80, 255), 0.9 * on)
            stamp(cv, x, y - 24 * s, (240, 220, 255), 2.2 * on + 0.4)
    def scene(t):
        p = t * 2; u = p % 1
        # altars wake one by one, and each one feeds it; the fourth throws it into the air of its own power
        ons = [sm(0.06 + i * 0.1, 0.14 + i * 0.1, u) * (1 - sm(0.84, 0.96, u)) for i in range(4)]
        power = sum(ons) / 4
        blast_u = u - 0.56
        hit = math.exp(-blast_u * 9) if blast_u >= 0 else 0.0
        cv = feather(back * (0.84 + 0.2 * power))
        mandala(cv, W / 2, 200, 94, np.array((130.0, 70, 220)) * (0.2 + 0.55 * power), t, petals=4, sides=4, inner=4, rot=math.pi / 4, squash=0.28, spin=1, size=1.0)
        wisps.draw(cv, t, lambda v: np.array((130, 70, 220)) * math.sin(math.pi * v) * (0.3 + power), 1.1)
        for i, (x, y) in enumerate(ALT[:2]):                                       # the far pair stand behind it
            altar(cv, x, y, ons[i], 0.8)
        wing = dict(c=(0.74, 0.2), r=(0.28, 0.26), p=(0.5, 0.28), a=-6 * wave(t, 2) - 7 * power + 9 * hit, soft=0.45)
        arm = dict(c=(0.15, 0.34), r=(0.17, 0.26), p=(0.28, 0.25), a=4 * wave(t, 2, 0.3) + 8 * power - 8 * hit, soft=0.4)
        head = dict(c=(0.35, 0.18), r=(0.14, 0.13), p=(0.38, 0.3), a=3 * wave(t, 3) - 4 * hit, soft=0.5)
        rim = rim_of(rig, 3)
        rig.rgb = np.clip(dark * (1 - power * 0.4) + lit * power * 0.4 + rim * np.array((170.0, 90, 255)) * (0.2 + 0.5 * power), 0, 255)
        shadow(cv, W / 2, H - 14, 90, 7, 0.55)
        stand(cv, rig, [wing, arm, head], left, top, (0.7 * wave(t, 1), -0.7), lean_bend(4 * power - 7 * hit, -5 * power + 7 * hit), 0.5, relief=0.34)
        ex, ey = spot(rig, head, 0.36, 0.2, left, top)
        glow(cv, ex, ey, 3 + 3 * power, (190, 110, 255), 0.5 + 0.8 * power)
        tx, ty = left + w * 0.45, top + h * 0.42
        for i, (x, y) in enumerate(ALT):                                          # each altar's thread into its chest
            if ons[i] > 0.02:
                bolt(cv, x, y - 22, tx, ty, 3021 + i + int(t * FRAMES / 2), np.array((170.0, 110, 255)) * (0.5 * ons[i]), jag=16, size=0.9)
        glow(cv, tx, ty, 9 + 16 * power, (140, 70, 255), 0.2 + 0.6 * power)
        for i, (x, y) in enumerate(ALT[2:]):
            altar(cv, x, y, ons[i + 2], 1.15)
        if blast_u >= 0:
            ring(cv, W / 2, 198, 12 + 150 * min(1.0, blast_u / 0.3), 0.3, 5, np.array((190.0, 120, 255)) * max(0.0, 1 - blast_u / 0.3))
        return np.clip(cv, 0, 255)
    return "skotizo", "the altars", scene

RECIPES = [archaeologist_rain, mimic_teeth, shaman_jump, hespori_seeds, gryphon_swoop, skotizo_altars]
