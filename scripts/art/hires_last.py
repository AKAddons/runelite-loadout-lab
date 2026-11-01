"""Hi-res recipes, the last of the rollout: Zulrah, Demonic Brutus, the five dragon families, the sea, Halloween."""
from hires_kit import *
from hires_batch2c import sm, cut_sprite, heal, shadow, shake, poly
from hires_batch3a import figure, stand, spot, lean_bend, beat, ground, debris
from hires_theme import logo, outline

def tinted(name, wide, tall, stops, thr=60, exposure=0.85, keep=0.12):
    """a sprite recast through a colour ramp on its own light; the grey parts (horns, claws) stay"""
    rgb, al = fit(cut_sprite(name, thr), wide, tall, exposure=exposure, gamma=1.0, sat=1.0, grain=0.4)
    lum = rgb.mean(2) / 255
    tone = (rgb.max(2) - rgb.min(2)) / (rgb.max(2) + 1)
    cast = ramp(stops, lum)[..., :3]
    return Rig(heal(np.where((tone > keep)[..., None], cast, rgb), 3), al, pad=40), al.shape[:2]

def spun(rgb, al, deg):
    """arrays rotated about their middle, for something flung through the air"""
    a = Image.fromarray(np.clip(np.dstack([rgb, al * 255]), 0, 255).astype(np.uint8), "RGBA").rotate(deg, Image.BILINEAR, expand=True)
    arr = np.asarray(a, float)
    return arr[..., :3], (arr[..., 3:] > 128).astype(float)

def ink(cv, a, b, colour, size=1.2):
    """a line that covers, for silhouettes (the kit's line adds light, so a dark one barely shows)"""
    n = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1])))
    c = np.array(colour, float)
    for i in range(n + 1):
        x, y = a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n
        x0, x1, y0, y1 = max(0, int(x - size)), min(W, int(x + size) + 2), max(0, int(y - size)), min(H, int(y + size) + 2)
        if x0 < x1 and y0 < y1:
            m = (XX[y0:y1, x0:x1] - x) ** 2 + (YY[y0:y1, x0:x1] - y) ** 2 <= size * size
            cv[y0:y1, x0:x1][m] = c

def tube(cv, pts, width, stops, scales=0.3, light=(-0.45, -0.75)):
    """a round body along pts, tail first: each disc lit as a ball, bands and a scale hatch along the length"""
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        r = width(i / max(1, n - 1))
        x0, x1, y0, y1 = max(0, int(x - r)), min(W, int(x + r) + 2), max(0, int(y - r)), min(H, int(y + r) + 2)
        if x0 >= x1 or y0 >= y1: continue
        dx, dy = (XX[y0:y1, x0:x1] - x) / r, (YY[y0:y1, x0:x1] - y) / r
        d = dx * dx + dy * dy
        m = d < 1
        lit = np.sqrt(np.clip(1 - dx * dx, 0, 1)) * (0.68 + 0.5 * dx * light[0])
        band = 1 - scales * (i % 4 < 2) * 0.6
        hatch = 1 - scales * (np.sin((XX[y0:y1, x0:x1] + YY[y0:y1, x0:x1]) * 1.3) * np.sin((XX[y0:y1, x0:x1] - YY[y0:y1, x0:x1]) * 1.3) > 0.2)
        c = ramp(stops, np.clip(0.12 + 1.25 * lit * band * hatch, 0, 1))[..., :3]
        cv[y0:y1, x0:x1][m] = c[m]

# ---- Zulrah: up out of the swamp in one colour, down, and up elsewhere in another ------------------------
def swamp_backdrop():
    sky = ramp([(10, 22, 26), (24, 48, 44), (66, 86, 58)], np.clip((YY - 10) / 130, 0, 1))[..., :3]
    img = sky * (0.7 + 0.5 * fbm(3601, (40, 16, 6))[..., None])
    for seed, base, tall, tone in ((3603, 112, 18, (18, 34, 30)), (3605, 142, 13, (12, 22, 20))):
        y = ridge(seed, base, tall)[None, :]
        m = (YY > y)[..., None]
        img = img * (1 - m) + np.array(tone, float) * (0.7 + 0.5 * fbm(seed, (12, 5))[..., None]) * m
    for x, lean in ((22, 5), (194, -7), (168, 3)):                               # dead trunks on the far bank
        ink(img, (x, 158), (x + lean, 76), (9, 15, 14), 2.6)
        ink(img, (x + lean * 0.7, 102), (x + lean + 14, 84), (9, 15, 14), 1.4)
        ink(img, (x + lean * 0.8, 92), (x + lean - 13, 74), (9, 15, 14), 1.2)
    return img

def zulrah_forms():
    back = swamp_backdrop()
    WL = 186
    tall = 188
    def form(stops):
        return tinted("Zulrah.png", 190, tall, stops, thr=60, exposure=0.74, keep=0.3) if stops else None
    plain = fit(cut_sprite("Zulrah.png", 60), 190, tall, exposure=0.74, gamma=1.0, sat=1.0, grain=0.4)
    RIGS = [(Rig(heal(plain[0], 3), plain[1], pad=40), plain[1].shape[:2]),                 # serpentine, its own colours
            form([(30, 8, 6), (132, 26, 14), (240, 92, 30), (255, 222, 124)]),              # magma
            form([(8, 18, 52), (24, 70, 152), (60, 170, 236), (192, 240, 255)])]            # tanzanite
    GLOW = [(190, 235, 60), (255, 96, 30), (70, 180, 255)]
    SPOT = [18, 28, 4]
    venom = [(220, 255, 140), (120, 210, 70), (50, 120, 40), (24, 50, 26)]
    ripples = fbm(3607, (22, 9, 4))
    drops = Motes(3609, 14, lambda r: (r.rand() * W, WL + 4, 20 * (r.rand() - 0.5), -50 * r.rand(), 90))
    def scene(t):
        k = int(t * 3) % 3; u = (t * 3) % 1
        rig, (h, w) = RIGS[k]
        tone = np.array(GLOW[k], float)
        rise = sm(0.0, 0.22, u) * (1 - sm(0.82, 0.99, u))
        wind, go, hit = beat(u, 0.48, 0.2, 0.3)
        cv = feather(back.copy())
        lunge = {0: 0.3, 1: 1.0, 2: 0.25}[k]
        left = SPOT[k] + 10 * wind * lunge - 22 * go * lunge + 3 * wave(t, 3)
        top = WL + 8 - (h - 8) * rise
        # the frill opens on the wind-up, the head rears then strikes, and the tail is its own arc
        frill = dict(c=(0.22, 0.33), r=(0.26, 0.26), p=(0.45, 0.36), a=-5 * wave(t, 6) - 9 * wind + 7 * go, soft=0.45)
        back_frill = dict(c=(0.62, 0.42), r=(0.14, 0.16), p=(0.52, 0.34), a=4 * wave(t, 6, 0.2) + 7 * wind, soft=0.5)
        head = dict(c=(0.5, 0.28), r=(0.15, 0.11), p=(0.54, 0.36), a=-6 * wind + 10 * go * lunge + 2 * wave(t, 3), soft=0.5)
        tail = dict(c=(0.84, 0.3), r=(0.2, 0.32), p=(0.84, 0.72), a=6 * wave(t, 3, 0.15) - 5 * wind, soft=0.55)
        stand(cv, rig, [tail, back_frill, frill, head], left, top, (-0.5 + 0.3 * wave(t, 2), -0.8), lean_bend(4 * wind - 7 * go * lunge, 2 * wave(t, 3), 0.95), 0.45, 0.32)
        mx, my = spot(rig, head, 0.42, 0.32, left, top)
        # the pool over its base, its colour thrown across the water
        z = np.clip((YY - WL) / (H - WL), 0, 1)
        shim = 0.6 + 0.8 * noise(3611, 9, t)
        water = ramp([(34, 56, 44), (18, 40, 34), (10, 26, 24)], z)[..., :3] * (0.65 + 0.5 * ripples[..., None]) * shim[..., None]
        bx = left + w * 0.46
        water = water + np.exp(-((XX - bx) / 62) ** 2)[..., None] * tone * 0.24 * rise
        cv = np.where((YY > WL)[..., None], water, cv)
        for cx in (bx, left + w * 0.84):
            for i in range(2):
                q = (t * 6 + i * 0.5 + cx * 0.01) % 1
                ring(cv, cx, WL + 6, 14 + 26 * q, 0.3, 1.4, tone * 0.55 * (1 - q) * max(rise, 0.25))
        if rise > 0.3:
            glow(cv, mx + 6, my - 6, 2.5 + 3 * wind + 3 * go, tone, 0.3 + 0.5 * wind + 0.3 * go)
        # what each form throws
        if k == 0 and 0.42 < u < 0.82:
            q = sm(0.42, 0.56, u) * (1 - sm(0.72, 0.82, u))
            plume(cv, mx, my + 2, -0.8, 0.6, 118, 32, (t * 9) % 1, venom, 3613, q, wobble=0.25)
        if k == 1 and hit > 0.02:
            ring(cv, mx - 14, min(WL, my + 44), 12 + 80 * (1 - hit), 0.35, 3, tone * hit)
            debris(3615, 16, mx - 14, WL - 4, 150, 110).draw(cv, min(0.99, (u - 0.48) / 0.3), lambda v: np.array((120, 96, 70)) * (1 - v), 1.6)
        if k == 2 and 0.42 < u < 0.78:
            for i in range(4):
                q = ((u - 0.42) / 0.3 + i * 0.22) % 1
                x, y = mx + (18 + 42 * i - mx) * q, my + (WL - 6 - my) * q - 30 * math.sin(math.pi * q)
                glow(cv, x, y, 6, tone, 0.9); stamp(cv, x, y, (235, 245, 255), 2.2)
        # the colour change itself, a sheet of light under the water as it goes down
        if u > 0.86:
            cv += (YY > WL)[..., None] * np.array(GLOW[(k + 1) % 3], float) * 0.28 * math.sin(math.pi * (u - 0.86) / 0.14)
        drops.draw(cv, (t * 6) % 1, lambda v: np.array((120, 160, 130)) * (1 - v) * rise, 1.0)
        return np.clip(shake(cv, 3 * hit * wave(t, 40) * (k == 1), 3 * hit * wave(t, 34) * (k == 1)), 0, 255)
    return "zulrah", "three forms", scene, 48

# ---- Demonic Brutus: the bull again, out of a burning pit -------------------------------------------------
def pit_backdrop():
    n = fbm(3621, (30, 12, 5))
    img = ramp([(12, 8, 18), (30, 14, 32), (68, 24, 42)], np.clip(n * 1.3 - 0.15, 0, 1))[..., :3]
    return ground(img, 172, (40, 24, 34), 3623)

def dbrutus_charge():
    rig, (h, w) = tinted("Brutus.png", W + 12, H - 52, [(18, 8, 10), (78, 20, 18), (172, 52, 26), (252, 156, 74)], exposure=1.0)
    back = pit_backdrop()
    GROUND = 222
    star = polygon_pts(108, 212, 100, 5, -math.pi / 2, 0.2)
    embers = Motes(3625, 30, lambda r: (r.rand() * W, 234, 26 * (r.rand() - 0.5), -220 * (0.4 + r.rand()), 0))
    dust = debris(3627, 26, 150, 212, 170, 80)
    fire = [(255, 244, 170), (255, 148, 30), (196, 44, 12), (56, 16, 10)]
    def scene(t):
        u = (t * 2) % 1
        wind, go, hit = beat(u, 0.62, 0.42, 0.34)
        cv = feather(back * (0.75 + 0.3 * wind + 0.5 * hit))
        # the summoning star on the floor burns up with his temper
        burn = 0.25 + 0.75 * max(wind, go, hit)
        for i in range(5):
            line(cv, star[i], star[(i + 2) % 5], np.array((255.0, 96, 24)) * burn, 1.5)
        ring(cv, 108, 212, 102, 0.2, 2.0, np.array((255.0, 70, 20)) * burn)
        # flames along the back wall, from the seam of floor and wall
        wall = noise(3629, 8, t) * np.exp(-((YY - 172) / (32 + 22 * burn)) ** 2) * (YY < 176)
        cv += np.clip(wall * 1.5 - 0.3, 0, 1)[..., None] * np.array((230.0, 80, 16)) * burn
        embers.draw(cv, (t * 4) % 1, lambda v: np.array((255, 130, 40)) * (1 - v), 1.0)
        paw = max(0.0, wave(u, 3)) * sm(0.05, 0.2, u) * (1 - sm(0.45, 0.55, u))
        rush = -26 * go + 7 * wind
        lean = 10 * wind - 16 * go
        left = -10 + rush; top = GROUND - h
        head = dict(c=(0.2, 0.3), r=(0.25, 0.33), p=(0.42, 0.34), a=-6 * wind + 9 * go + 2.5 * wave(t, 3), soft=0.5)
        leg = dict(c=(0.2, 0.85), r=(0.1, 0.2), p=(0.22, 0.65), a=-14 * paw, d=(0, -0.05 * paw), soft=0.5)
        tail = dict(c=(0.95, 0.42), r=(0.08, 0.3), p=(0.93, 0.2), a=9 * wave(t, 4) + 10 * wind, soft=0.5)
        hind = dict(c=(0.82, 0.85), r=(0.13, 0.2), p=(0.8, 0.62), a=8 * go - 5 * wind, soft=0.5)
        ribs = dict(c=(0.58, 0.45), r=(0.2, 0.2), p=(0.58, 0.45), d=(0, -0.012 * wave(t, 4)), soft=0.9)
        parts = [ribs, tail, hind, leg, head]
        shadow(cv, left + w * 0.5, GROUND - 3, w * 0.5, 7, 0.6)
        if go > 0.02:                                                           # the wake he leaves is on fire
            plume(cv, left + w * 0.95, GROUND - 40, 1, -0.1, 110, 40, (t * 8) % 1, fire, 3631, go * 0.75, wobble=0.25)
        stand(cv, rig, parts, left, top, (0.6 * wave(t, 1), -0.8), lean_bend(lean, -6 * wind + 3 * go, 0.95))
        nx, ny = spot(rig, head, 0.085, 0.45, left, top)
        ex, ey = spot(rig, head, 0.25, 0.33, left, top)
        glow(cv, ex + lean * 0.7, ey, 5 + 4 * wind + 4 * go, (255, 90, 20), 0.8 + 0.7 * wind + 0.5 * go)
        stamp(cv, ex + lean * 0.7, ey, (255, 240, 170), 1.6)
        for i, phase in enumerate((0.1, 0.36)):                                 # he snorts fire, not steam
            q = (u - phase) / 0.16
            if 0 <= q < 1:
                plume(cv, nx + lean * 0.7, ny, -0.8, 0.5, 54 + 30 * q, 18, (t * 6) % 1, fire, 3633 + i, math.sin(math.pi * q), wobble=0.2)
        if u >= 0.62:
            dust.draw(cv, min(0.99, (u - 0.62) / 0.34), lambda v: np.array((255, 130, 50)) * (1 - v), 2.0)
        return np.clip(shake(cv, 3 * hit * wave(t, 16), 3 * hit * wave(t, 13)), 0, 255)
    return "dbrutus", "out of the pit", scene

# ---- The dragons: one body, a place and a trick each ------------------------------------------------------
DRAGON_W, DRAGON_LEFT, DRAGON_FOOT = 168, 76, 214

def posed(rig, h, t, wind, go, cv, flap=1.0, light=None, foot=DRAGON_FOOT):
    """the dragon stood on foot; head rears on the wind-up and lunges with the breath. Returns the mouth, eye."""
    left, top = DRAGON_LEFT + 5 * wind - 7 * go, foot - h
    head = dict(c=(0.14, 0.72), r=(0.22, 0.26), p=(0.32, 0.62), a=-7 * wind + 7 * go + 1.8 * wave(t, 3), d=(-0.01 * go, -0.03 * wind + 0.02 * go), soft=0.5)
    far = dict(c=(0.26, 0.25), r=(0.25, 0.28), p=(0.3, 0.5), a=7 * flap * wave(t, 4, 0.1), d=(0, -0.018 * flap * wave(t, 4, 0.1)), soft=0.5)
    near = dict(c=(0.6, 0.3), r=(0.3, 0.2), p=(0.42, 0.5), a=-7 * flap * wave(t, 4), lit=0.2 * wave(t, 4), soft=0.5)
    tail = dict(c=(0.88, 0.3), r=(0.16, 0.34), p=(0.9, 0.52), a=7 * wave(t, 2, 0.3), soft=0.5)
    flank = dict(c=(0.58, 0.55), r=(0.2, 0.17), p=(0.58, 0.55), d=(0, -0.012 * wave(t, 4)), soft=0.9)
    parts = [flank, tail, far, near, head]
    stand(cv, rig, parts, left, top, light or (0.6 * wave(t, 2), -0.8), lean_bend(5 * wind - 5 * go, 0, 0.95), 0.4)
    return spot(rig, head, 0.035, 0.87, left, top), spot(rig, head, 0.165, 0.7, left, top)

def eye(cv, e, colour, power=0.8):
    glow(cv, e[0], e[1], 4, colour, power)
    stamp(cv, e[0], e[1], (255, 244, 200), 1.2)

def drgreen_grass():
    # the Wilderness at dusk: a broken wall, long grass in the wind, and the breath sets it alight
    rig, (h, w) = tinted("Green_dragon.png", DRAGON_W, 150, [(10, 26, 12), (34, 84, 32), (92, 160, 58), (190, 226, 110)])
    sky = ramp([(30, 26, 54), (94, 56, 72), (214, 118, 60)], np.clip(YY / 150, 0, 1))[..., :3] * (0.82 + 0.3 * fbm(3641, (60, 22, 8))[..., None])
    wall = ((np.abs(XX - 34) < 22) & (YY > 66 + 16 * (fbm(3643, (9, 4))[0, :][None, :] * 2 - 1)) & (YY < 180))[..., None]
    bricks = 0.65 + 0.5 * (((YY // 9 + (XX // 16 + (YY // 9) % 2) * 3) % 5) > 0)[..., None] * (0.5 + 0.8 * fbm(3645, (8, 3))[..., None])
    back = sky * (1 - wall) + np.array((46.0, 40, 48)) * bricks * wall
    back = ground(back, 176, (42, 56, 28), 3647)
    fire = [(255, 244, 170), (255, 158, 36), (204, 58, 16), (70, 26, 14)]
    blades = fbm(3649, (6, 3))[0, :]
    def scene(t):
        u = (t * 2) % 1
        wind, go, hit = beat(u, 0.5, 0.25, 0.42)
        cv = feather(back * (0.8 + 0.28 * go))
        (mx, my), e = posed(rig, h, t, wind, go, cv)
        eye(cv, e, (255, 60, 20))
        if go > 0.01:
            plume(cv, mx, my, -0.88, 0.42, 128, 36, (t * 9) % 1, fire, 3651, go, wobble=0.22)
        # the grass, two lengths, leaning with the wind; where the breath lands it burns and then is ash
        reach = 92 * max(go, sm(0.5, 0.6, u) * (1 - sm(0.9, 1.0, u)))
        for row, tone, length in ((206, (58, 96, 36), 26), (226, (84, 132, 44), 34)):
            for x in range(-6, W + 8, 5):
                hgt = length * (0.55 + 0.9 * blades[x % W])
                sway = (6 + 3 * math.sin(x * 0.2)) * math.sin(2 * math.pi * (t * 4 - x / 60)) + 4
                burning = 1 - np.clip((x - (40 - reach * 0 + reach * 0.9)) / 20, 0, 1)
                c = np.array(tone, float) * (1 - burning) + np.array((42.0, 28, 22)) * burning
                line(cv, (x, row + 14), (x + sway, row + 14 - hgt * (1 - 0.4 * burning)), c, 1.7)
                if burning > 0.3 and reach > 4 and (x * 7 + int(t * 64)) % 5 < 2:
                    line(cv, (x + sway, row + 2 - hgt * 0.4), (x + sway * 1.5, row - hgt * 0.9), (255, 140 + 60 * math.sin(x), 30), 1.4)
        return np.clip(cv, 0, 255)
    return "drgreen", "the long grass", scene

def drblue_pool():
    # a still cave pool: frost dragons and blue alike; the breath freezes the water in a walking line of ice, and it all mirrors
    rig, (h, w) = tinted("Green_dragon.png", DRAGON_W, 150, [(8, 14, 44), (28, 52, 130), (72, 130, 222), (196, 228, 255)])
    WL = 202
    n = fbm(3661, (34, 14, 6))
    back = ramp([(6, 10, 18), (14, 24, 36), (32, 50, 66)], np.clip(n * 1.3 - 0.15, 0, 1))[..., :3]
    for x in (12, 40, 92, 136, 188):                                             # hanging stone
        back = back * (1 - poly([(x - 9, 0), (x + 9, 0), (x, 30 + (x * 3) % 34)])) + np.array((12.0, 22, 38)) * poly([(x - 9, 0), (x + 9, 0), (x, 30 + (x * 3) % 34)])
    frost = [(246, 252, 255), (160, 216, 255), (70, 120, 220), (24, 40, 90)]
    SPIKES = [(52 - i * 10, 10 + ((i * 13) % 17)) for i in range(7)]
    snow = Motes(3663, 18, lambda r: (r.rand() * W, -4, 14 * (r.rand() - 0.5), 240, 0))
    def scene(t):
        u = (t * 2) % 1
        wind, go, hit = beat(u, 0.5, 0.25, 0.4)
        cv = feather(back * (0.85 + 0.3 * go))
        (mx, my), e = posed(rig, h, t, wind, go, cv, 0.8, foot=WL + 3)
        eye(cv, e, (120, 220, 255), 0.9)
        grow = sm(0.52, 0.72, u) * (1 - sm(0.9, 1.0, u))
        for i, (x, tall) in enumerate(SPIKES):                                    # the ice walks away from him, tallest first
            g = max(0.0, grow * 1.6 - i * 0.1)
            if g <= 0: continue
            s = poly([(x - 6, WL + 1), (x + 6, WL + 1), (x + 1, WL + 1 - tall * 1.6 * min(1, g))])
            shade = 170 + 70 * np.clip((XX - x) / 6, -1, 1)
            ice = np.dstack([shade * 0.72, shade * 0.9, shade + 15])
            cv[:] = cv * (1 - s) + ice * s
        if go > 0.01:
            plume(cv, mx, my, -0.86, 0.42, 112, 30, (t * 9) % 1, frost, 3665, go, wobble=0.2)
        snow.draw(cv, (t * 3) % 1, lambda v: (170, 200, 235), 0.9)
        # the mirror: everything above folds into the water, shaken by a ripple that travels out
        rows = np.arange(WL, H)
        src = np.clip(2 * WL - rows, 0, H - 1)
        wob = (2.2 * np.sin((rows - WL) * 0.9 - 2 * math.pi * t * 4) * (0.4 + (rows - WL) / 30)).astype(int)
        for r, s_, dx in zip(rows, src, wob):
            cv[r] = np.roll(cv[s_], dx, axis=0) * np.array((0.48, 0.6, 0.78)) * (0.9 - (r - WL) * 0.012) + np.array((4.0, 10, 22))
        cv[WL:WL + 1] = np.minimum(cv[WL:WL + 1] + 60, 255)
        return np.clip(cv, 0, 255)
    return "drblue", "the frozen pool", scene

def drred_lava():
    # standing in the lava: haze shakes the cave, each wingbeat stirs the fire, and the breath wakes the pool
    rig, (h, w) = tinted("Green_dragon.png", DRAGON_W, 150, [(26, 6, 6), (108, 18, 14), (216, 54, 24), (255, 164, 80)])
    WL = 188
    back = ramp([(24, 8, 8), (58, 20, 14), (112, 44, 20)], np.clip((YY - 10) / 180 + 0.5 * fbm(3671, (34, 14, 6)) - 0.2, 0, 1))[..., :3]
    falls = [(22, 7), (196, 5)]
    fire = [(255, 250, 190), (255, 170, 40), (222, 66, 14), (80, 26, 12)]
    lava = [(255, 242, 150), (255, 150, 26), (206, 50, 12), (64, 16, 10)]
    embers = Motes(3673, 30, lambda r: (r.rand() * W, WL, 24 * (r.rand() - 0.5), -200 * (0.3 + r.rand()), 0))
    def scene(t):
        u = (t * 2) % 1
        wind, go, hit = beat(u, 0.5, 0.25, 0.42)
        cv = back * (0.85 + 0.22 * go)
        for x, wide in falls:                                                     # falls of lava on the back wall
            n = noise(3675 + x, 7, (t * 2) % 1) + 0.25 * np.sin((YY * 0.2 - t * 2 * math.pi * 4))
            m = (np.abs(XX - x - 4 * np.sin(YY * 0.05)) < wide)[..., None] * (YY < WL)[..., None]
            cv = cv * (1 - m) + ramp(lava, np.clip(1 - n, 0, 1))[..., :3] * m * 0.7
        cv = feather(cv)
        cv = np.stack([np.roll(cv[y], int(1.6 * math.sin(y * 0.28 + 2 * math.pi * t * 6)), axis=0) for y in range(H)])
        (mx, my), e = posed(rig, h, t, wind, go, cv, 1.25, foot=WL + 16)
        eye(cv, e, (255, 190, 60), 1.0)
        # the pool, drawn over his feet, brightest under the breath
        n = noise(3677, 9, t)
        surf = WL + 3 * np.sin(XX * 0.08 + 2 * math.pi * t * 2)
        z = np.clip((YY - surf) / 40, 0, 1)
        pool = ramp(lava, np.clip(np.abs(n - 0.5) * 4.2 + 0.2 * z - 0.7 * go * np.exp(-((XX - 30) / 50) ** 2), 0, 1))[..., :3]
        m = (YY > surf)[..., None]
        cv = cv * (1 - m) + pool * m
        if go > 0.01:
            plume(cv, mx, my, -0.85, 0.42, 122, 34, (t * 9) % 1, fire, 3679, go, wobble=0.22)
            q = (u - 0.5) / 0.42                                                 # the pool answers: a spout where the fire lands
            plume(cv, 22, WL + 4, 0.05, -1, max(8, 110 * math.sin(math.pi * min(1, q * 1.2))), 16, (t * 7) % 1, lava, 3681, go, wobble=0.3)
        embers.draw(cv, (t * 3) % 1, lambda v: np.array((255, 150, 40)) * (1 - v), 1.0)
        return np.clip(cv, 0, 255)
    return "drred", "the lava pool", scene

def drblack_dark():
    # the dark: nothing but two eyes and the sound of it, until its own fire shows you what is there
    rig, (h, w) = tinted("Green_dragon.png", DRAGON_W, 150, [(10, 10, 14), (44, 40, 54), (100, 92, 116), (190, 178, 204)])
    n = fbm(3691, (26, 11, 5))
    back = ramp([(10, 8, 12), (34, 28, 34), (72, 60, 62)], np.clip(n * 1.4 - 0.2, 0, 1))[..., :3]
    back = ground(back, 180, (46, 38, 36), 3693)
    fire = [(255, 248, 200), (255, 176, 60), (218, 74, 22), (66, 26, 18)]
    def scene(t):
        u = t % 1
        wind, go, hit = beat(u, 0.58, 0.2, 0.36)
        cv = feather(back.copy())
        (mx, my), e = posed(rig, h, t, wind, go, cv, 0.7)
        flick = 0.82 + 0.18 * wave(t, 19) * wave(t, 13)
        dist = np.hypot(XX - (mx - 26), YY - (my + 18))
        light = 0.035 + 0.04 * wind + go * flick * (1.7 * np.exp(-(dist / 120) ** 2) + 0.3)
        cv = cv * light[..., None] * np.array((1.0, 0.86, 0.68))
        for dx in (0,):                                                          # the eye, only, in the black
            x, y = e[0] + dx, e[1] - dx * 0.2
            glow(cv, x, y, 3 + 4 * wind, (255, 70, 24), 0.55 + 0.7 * wind + 0.2 * wave(t, 6))
            stamp(cv, x, y, (255, 220, 170), 1.1)
        if go > 0.01:
            plume(cv, mx, my, -0.86, 0.44, 130, 38, (t * 12) % 1, fire, 3695, go, wobble=0.22)
        if wind > 0.02:                                                          # the breath drawn in: a coal in its throat
            glow(cv, mx + 8, my - 2, 4 + 9 * wind, (255, 110, 30), wind)
        return np.clip(cv, 0, 255)
    return "drblack", "its own fire", scene, 40

def drmetal_lair():
    # a dungeon lair: torches on the rock, and each metal in turn caught by its own breath
    METALS = [[(26, 14, 8), (98, 58, 26), (184, 118, 58), (246, 196, 130)],      # bronze
              [(14, 14, 16), (60, 60, 66), (120, 118, 122), (206, 206, 210)],    # iron
              [(18, 20, 24), (84, 90, 100), (164, 172, 184), (240, 244, 250)],   # steel
              [(10, 12, 36), (40, 56, 110), (92, 120, 196), (196, 216, 255)],    # mithril
              [(6, 24, 16), (24, 84, 56), (66, 152, 104), (176, 232, 196)],      # adamant
              [(4, 22, 30), (18, 78, 102), (54, 150, 178), (170, 232, 248)]]     # rune
    RIGS = [tinted("Green_dragon.png", DRAGON_W, 150, s) for s in METALS]
    h = RIGS[0][1][0]
    n = fbm(3701, (34, 14, 6, 3))
    rock = ramp([(14, 12, 14), (38, 32, 32), (70, 60, 54)], np.clip(n * 1.5 - 0.25, 0, 1))[..., :3]
    back = ground(rock, 182, (44, 38, 36), 3703)
    for x, long in ((18, 34), (58, 18), (96, 42), (136, 22), (172, 36), (204, 16)):   # the roof comes down in teeth
        tooth = poly([(x - 11, 0), (x + 11, 0), (x + 2, long), (x - 1, long)])
        back = back * (1 - tooth) + np.array((16.0, 14, 16)) * tooth
    TORCH = [(30, 104), (190, 92)]
    fire = [(255, 248, 200), (255, 176, 60), (218, 74, 22), (66, 26, 18)]
    embers = Motes(3705, 16, lambda r: (TORCH[int(r.rand() * 2)][0], 96, 20 * (r.rand() - 0.5), -70 * (0.4 + r.rand()), 120))
    def scene(t):
        k = int(t * 6) % 6; u = (t * 6) % 1
        wind, go, hit = beat(u, 0.42, 0.26, 0.26)
        cv = back.copy()
        for x, y in TORCH:
            ink(cv, (x, y + 22), (x, y + 2), (40, 28, 20), 1.6)
        cv = feather(cv)
        (mx, my), e = posed(RIGS[k][0], h, t, wind, go, cv, 0.7, (-0.7, -0.6 - 0.2 * wave(t, 3)))
        # the torchlight, then the breath, are the only light there is; the dark between hides the change of metal
        flick = 0.85 + 0.15 * wave(t, 19) * wave(t, 13)
        light = 0.52 + 0.0 * XX
        for x, y in TORCH:
            light = light + flick * 0.9 * np.exp(-(np.hypot(XX - x, YY - y) / 70) ** 2)
        light = light + go * 1.5 * np.exp(-(np.hypot(XX - (mx - 30), YY - (my + 14)) / 100) ** 2)
        fade = sm(0.0, 0.08, u) * (1 - sm(0.92, 1.0, u))
        cv = cv * (light * (0.25 + 0.75 * fade))[..., None] * np.array((1.0, 0.9, 0.76))
        for x, y in TORCH:
            plume(cv, x, y + 2, 0.0, -1, 13, 8, (t * 12) % 1, fire, 3707 + x, 1.0, wobble=0.3)
            glow(cv, x, y - 2, 13, (255, 130, 40), 0.3 * flick)
        eye(cv, e, (255, 170, 60), 0.5 + 0.5 * wind)
        if wind > 0.02:
            glow(cv, mx + 8, my - 2, 4 + 8 * wind, (255, 110, 30), wind)
        if go > 0.01:
            plume(cv, mx, my, -0.86, 0.42, 92, 30, (t * 12) % 1, fire, 3711, go, wobble=0.22)
        embers.draw(cv, (t * 4) % 1, lambda v: np.array((255, 150, 50)) * (1 - v), 1.0)
        return np.clip(shake(cv, 1.5 * hit * wave(t, 70), 1.5 * hit * wave(t, 54)), 0, 255)
    return "drmetal", "the lair", scene, 60

# ---- The sea: a ship at night, her broadside, what's under her -----------------------------------------------
def ship_sprite():
    S = 2                                                                         # drawn large and reduced, so the edges come out soft
    im = Image.new("RGBA", (120 * S, 110 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts: [(x * S, y * S) for x, y in pts]
    d.polygon(P([(8, 72), (112, 66), (102, 100), (24, 102)]), fill=(76, 48, 30, 255))
    for i, y in enumerate(range(76, 100, 5)):
        d.line(P([(14 + i, y), (108 - i, y - 3)]), fill=(46, 28, 18, 255), width=S)
    d.polygon(P([(8, 72), (2, 60), (24, 66), (24, 72)]), fill=(92, 58, 36, 255))
    d.polygon(P([(112, 66), (118, 50), (92, 52), (92, 68)]), fill=(92, 58, 36, 255))
    d.line(P([(18, 72), (112, 66)]), fill=(170, 130, 70, 255), width=2 * S)
    for x in (34, 54, 74, 94):
        d.rectangle(P([(x, 80), (x + 7, 85)]), fill=(20, 12, 8, 255))
    for x, tall in ((40, 6), (80, 2)):
        d.line(P([(x, 72), (x, tall)]), fill=(52, 34, 22, 255), width=2 * S)
        d.line(P([(x - 22, tall + 12), (x + 22, tall + 10)]), fill=(52, 34, 22, 255), width=S)
    a = np.asarray(im.resize((120, 110), Image.LANCZOS), float)
    return a[..., :3], (a[..., 3:] > 128).astype(float)

def sail_mask(cx, top, wide, tall, belly):
    return poly([(cx - wide, top), (cx + wide, top), (cx + wide + belly, top + tall * 0.5), (cx + wide, top + tall), (cx - wide, top + tall), (cx - wide + belly, top + tall * 0.5)])

def sea_voyage():
    hull, hull_al = ship_sprite()
    sh_rgb, sh_al = fit(cut_sprite("Bull_shark.png", 60), 132, 70, exposure=0.72, gamma=1.0, sat=0.8, grain=0.4)
    sky = ramp([(6, 10, 30), (16, 26, 58), (62, 64, 94)], np.clip(YY / 130, 0, 1))[..., :3]
    sky = sky * (0.86 + 0.28 * fbm(3721, (60, 24, 9))[..., None])
    for i, (x, y) in enumerate(phyllotaxis(W / 2, 40, 120, 9)):
        if 0 <= x < W and 0 <= y < 110: stamp(sky, x, y, np.array((200.0, 210, 235)) * (0.3 + 0.7 * ((i * 7) % 5) / 5), 0.7)
    glow(sky, 164, 40, 26, (120, 120, 100), 1.0)
    glow(sky, 164, 40, 12, (240, 236, 200), 1.6)
    foam_tex = fbm(3723, (14, 6, 3))
    smoke = [(226, 222, 210), (160, 158, 156), (92, 92, 98), (44, 46, 56)]
    splash = debris(3725, 22, 60, 150, 110, 130)
    spray = Motes(3727, 30, lambda r: (W * r.rand(), 200, 26 * (r.rand() - 0.5), -80 * r.rand(), 120))
    def height(base, k, s, a, t):
        return base + a * np.sin(XX[0] * k + 2 * math.pi * t * s) + a * 0.45 * np.sin(XX[0] * k * 2.3 - 2 * math.pi * t * (s + 1) + 1.3)
    def water(cv, line_, tone, foam):
        m = YY > line_[None, :]
        depth = np.clip((YY - line_[None, :]) / 60, 0, 1)
        c = np.array(tone, float) * (1.16 - 0.55 * depth)[..., None] * (0.7 + 0.6 * foam_tex[..., None])
        crest = (np.abs(YY - line_[None, :]) < 2.4) * (foam_tex > 0.45)
        c = c * (1 - crest[..., None]) + np.array(foam, float) * crest[..., None]
        cv[m] = c[m]
    def scene(t):
        cv = feather(sky.copy())
        # moon's road on the far water, then the far swell
        far = height(140, 0.05, 2, 4, t)
        water(cv, far, (24, 58, 86), (120, 150, 170))
        road = (np.abs(XX - 164 - 5 * np.sin(YY * 0.8)) < 12) & (YY > far[None, :]) & (((YY * 3 + (XX // 4) * 5 + int(t * 64)) % 7) < 2)
        cv[road] = (190, 196, 180)
        # the ship rides the near swell, up its slope and down the far side
        roll = 5 * wave(t, 2, 0.1); bob = 7 * wave(t, 2)
        body = np.zeros((110, 120, 3)); body[:] = hull
        rgb, al = spun(hull, hull_al, roll)
        sx, sy = 40, 76 + int(bob)
        over(cv, rgb, al, sx, sy)
        gust = 3 + 2 * wave(t, 4)
        cx0, cy0 = sx + 60, sy + 55
        tilt = math.radians(-roll)
        for mast_x, top, wide, tall in ((-20, -40, 19, 28), (-20, -8, 22, 26), (20, -44, 16, 26), (20, -14, 19, 24)):
            mx_ = cx0 + mast_x + math.sin(tilt) * top; my_ = cy0 + top
            s = sail_mask(mx_, my_, wide, tall, gust)
            fold = 212 + 22 * np.sin((XX - mx_) * 0.5) - 30 * np.clip((YY - my_) / tall, 0, 1)
            cv[:] = cv * (1 - s) + np.dstack([fold, fold * 0.95, fold * 0.82]) * s
        lantern = (cx0 + 54, cy0 - 5)
        glow(cv, lantern[0], lantern[1], 6, (255, 190, 90), 0.8 + 0.2 * wave(t, 8))
        # the broadside, a gun at a time, out of the near side, the shots falling short to the left
        for i, at in enumerate((0.1, 0.14, 0.18, 0.22)):
            q = (t - at) / 0.1
            if 0 <= q < 1:
                gx, gy = sx + 94 - 20 * i, sy + 86
                glow(cv, gx - 3, gy, 13 * (1 - q), (255, 214, 120), 2 * (1 - q))
            q2 = (t - at) / 0.26
            if 0 <= q2 < 1:
                gx, gy = sx + 94 - 20 * i, sy + 86
                plume(cv, gx - 2, gy, -1, -0.15, 44 + 30 * q2, 14, (t * 8) % 1, smoke, 3729 + i, (1 - q2) * 0.9, wobble=0.3)
                x, y = gx - 90 * q2 - 8, gy - 26 * math.sin(math.pi * q2) + 42 * q2
                ink(cv, (x, y), (x, y + 1), (20, 16, 16), 1.8)
        if 0.3 < t < 0.5:
            splash.draw(cv, (t - 0.3) / 0.2, lambda v: np.array((200, 220, 230)) * (1 - v), 1.5)
        # the near swell, over her waterline
        near = height(178, 0.038, 2, 8, t)
        water(cv, near, (16, 44, 68), (190, 208, 214))
        # the shark comes over it
        q = (t - 0.52) / 0.3
        if 0 <= q < 1:
            x = 196 - 200 * q; y = 214 - 158 * math.sin(math.pi * q)
            rgb, al = spun(sh_rgb, sh_al, -32 + 64 * q)
            over(cv, rgb, al, int(x - rgb.shape[1] / 2), int(y - rgb.shape[0] / 2))
            for v in (0.0, 0.08):
                p = q - v
                if p > 0:
                    stamp(cv, 196 - 200 * p, 214 - 158 * math.sin(math.pi * p) + 16, (210, 226, 232), 1.6)
        if 0.5 < t < 0.6: splash.draw(cv, (t - 0.5) / 0.1 * 0.6, lambda v: np.array((226, 238, 240)) * (1 - v), 1.8)
        # an arm of something much larger, up beyond the bow and back down
        rise = sm(0.78, 0.86, t) * (1 - sm(0.92, 1.0, t))
        if rise > 0.01:
            base = 26
            pts = [(base + 14 * math.sin(s * 5 + 2 * math.pi * t * 4) * s + 30 * s * s, 196 - 150 * rise * s) for s in np.linspace(0, 1, 34)]
            tube(cv, pts, lambda s: 14 * (1 - s) + 2.5, [(26, 12, 26), (84, 36, 78), (170, 90, 140), (240, 190, 210)], 0.2)
        foreground = height(216, 0.03, 2, 8, t + 0.2)
        water(cv, foreground, (8, 26, 44), (220, 232, 236))
        spray.draw(cv, (t * 4) % 1, lambda v: np.array((190, 210, 216)) * (1 - v), 0.9)
        return np.clip(cv, 0, 255)
    return "sea", "the voyage", scene, 72

# ---- Halloween: the logo's guy in a witch's hat, brewing in a graveyard --------------------------------------
def hallow_backdrop():
    sky = ramp([(30, 18, 60), (84, 34, 90), (190, 96, 50)], np.clip(YY / 180, 0, 1))[..., :3]
    sky = sky * (0.85 + 0.3 * fbm(3741, (60, 24, 9))[..., None])
    glow(sky, 162, 62, 46, (130, 70, 20), 0.7)
    disc = (np.hypot(XX - 162, YY - 62) < 33)[..., None]
    crater = 0.72 + 0.5 * fbm(3743, (12, 5, 2))[..., None]
    sky = sky * (1 - disc) + np.array((248.0, 196, 92)) * crater * disc
    hill = ridge(3745, 184, 12)[None, :]
    m = (YY > hill)[..., None]
    img = sky * (1 - m) + np.array((22.0, 18, 30)) * (0.7 + 0.6 * fbm(3747, (16, 7, 3))[..., None]) * m
    dark = (10.0, 7, 14)
    ink(img, (20, 192), (28, 74), dark, 4.0)                                     # the bare tree, branch by branch
    for a, b, s in (((26, 110), (4, 80), 1.8), ((27, 94), (56, 62), 1.8), ((42, 76), (64, 76), 1.0), ((42, 76), (48, 48), 1.0), ((14, 94), (10, 62), 1.0), ((28, 74), (24, 40), 1.4)):
        ink(img, a, b, dark, s * 1.2)
    for x, y, wide, tall in ((176, 186, 12, 24), (202, 192, 9, 20), (150, 192, 8, 14)):   # the stones
        s = poly([(x - wide, y + 14), (x - wide, y - tall + 8), (x - wide * 0.6, y - tall), (x + wide * 0.6, y - tall), (x + wide, y - tall + 8), (x + wide, y + 14)])
        img = img * (1 - s) + np.array((52.0, 48, 62)) * (0.75 + 0.5 * fbm(3749, (6, 3))[..., None]) * s
        ink(img, (x - wide * 0.5, y - tall + 10), (x + wide * 0.5, y - tall + 10), (24, 22, 30), 0.9)
    return img

def halloween_brew():
    sil, gold, cap = logo()
    SCALE = 0.72
    def small(m):
        return np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize((int(m.shape[1] * SCALE), int(m.shape[0] * SCALE)), Image.NEAREST)) > 128
    sil, gold = small(sil | cap), small(gold)
    edge = outline(sil, 2)
    bh, bw = sil.shape
    gx, gy = 58, 226 - bh
    S = np.pad(sil, ((gy, H - gy - bh), (gx, W - gx - bw)))[..., None]
    E = np.pad(edge, ((gy, H - gy - bh), (gx, W - gx - bw)))[..., None]
    G = np.pad(gold, ((gy, H - gy - bh), (gx, W - gx - bw)))[..., None]
    back = hallow_backdrop()
    ORANGE, DEEP, HOT = np.array((245.0, 120, 24)), np.array((150.0, 46, 10)), np.array((255.0, 214, 110))
    PURPLE = np.array((120.0, 60, 170))
    bub = Motes(3751, 22, lambda r: (gx + 14 + r.rand() * (bw - 28), 224, 8 * (r.rand() - 0.5), -bh * 0.8 * (0.5 + 0.5 * r.rand()), 0))
    bats = [(i * 0.33, 40 + i * 14) for i in range(3)]
    neck = (gx + bw * 0.46, gy + 4)
    ghost_t = np.linspace(0, 1, 16)
    def bat(cv, x, y, f, size):
        beat_ = 4 * size * math.sin(f)
        dark = (6, 4, 10)
        ink(cv, (x, y), (x - 6 * size, y - beat_), dark, 1.1); ink(cv, (x - 6 * size, y - beat_), (x - 11 * size, y + beat_ * 0.3), dark, 0.9)
        ink(cv, (x, y), (x + 6 * size, y - beat_), dark, 1.1); ink(cv, (x + 6 * size, y - beat_), (x + 11 * size, y + beat_ * 0.3), dark, 0.9)
        ink(cv, (x, y), (x, y + 1), dark, 1.6)
    def scene(t):
        u = t % 1
        cv = feather(back.copy())
        for ph, y in bats:                                                       # bats across the moon
            q = (t * 2 + ph) % 1
            bat(cv, 234 - 260 * q, y + 8 * math.sin(q * 15), t * 2 * math.pi * 14 + ph * 9, 0.9 + ph * 0.5)
        # a jack-o'-lantern by his foot, its light jumping
        fl = 0.72 + 0.28 * wave(t, 17) * wave(t, 7, 0.3)
        jx, jy = 180, 214
        glow(cv, jx, jy, 26, (240, 110, 20), 0.5 * fl)
        for r_, off in ((14, -11), (14, 11), (18, 0)):
            d = np.hypot((XX - jx - off) / r_, (YY - jy) / 15)
            m = (d < 1)
            cv[m] = (np.array((226.0, 104, 18)) * (0.55 + 0.55 * np.clip(1 - d, 0, 1) ** 0.5)[..., None])[m]
        line(cv, (jx, jy - 12), (jx + 2, jy - 17), (50, 70, 26), 1.6)
        for tri in ([(jx - 9, jy - 3), (jx - 3, jy - 3), (jx - 6, jy - 8)], [(jx + 3, jy - 3), (jx + 9, jy - 3), (jx + 6, jy - 8)]):
            s = poly(tri); cv[:] = cv * (1 - s) + HOT * fl * s
        grin = poly([(jx - 10, jy + 2), (jx + 10, jy + 2), (jx + 6, jy + 8), (jx + 2, jy + 5), (jx - 2, jy + 8), (jx - 6, jy + 5)])
        cv[:] = cv * (1 - grin) + HOT * fl * grin
        # the brew: a sway, a level that heaves, and the logo's body glowing from it
        lvl = 0.62 + 0.06 * wave(t, 3)
        top = gy + bh * (1 - lvl) + 2.5 * np.sin(XX * 0.17 + 2 * math.pi * t * 4)
        liquid = (YY > top)[..., None]
        n = noise(3753, 7, t)
        fluid = ramp([HOT, ORANGE, DEEP], np.clip(n * 0.9 + (YY - top) / 150, 0, 1))[..., :3]
        glass = np.array((64.0, 48, 92))
        body = glass * (1 - liquid) + fluid * liquid
        cv[:] = cv * (1 - S) + body * S
        tmp = np.zeros_like(cv)
        bub.draw(tmp, (t * 3) % 1, lambda v: (90, 60, 20), 2.0)
        cv += tmp * S * liquid
        cv[:] = cv * (1 - E) + np.array((236.0, 226, 196)) * E
        cv[:] = cv * (1 - G) + np.array((236.0, 190, 90)) * G
        # the hat, and two eyes under it
        hx, hy = gx + bw * 0.46 + 2 * wave(t, 3), gy + 12
        brim = poly([(hx - 26, hy + 2), (hx - 8, hy - 3), (hx + 8, hy - 3), (hx + 26, hy + 2), (hx + 8, hy + 6), (hx - 8, hy + 6)])
        cone = poly([(hx - 14, hy), (hx + 14, hy), (hx + 7 + 4 * wave(t, 3), hy - 24), (hx - 10 + 5 * wave(t, 3), hy - 46), (hx - 7, hy - 22)])
        cv[:] = cv * (1 - cone) + np.array((30.0, 20, 46)) * (1 + 0.5 * np.clip((XX - hx) / 14, -1, 1))[..., None] * cone
        cv[:] = cv * (1 - brim) + np.array((42.0, 28, 62)) * brim
        band = poly([(hx - 13, hy - 7), (hx + 13, hy - 7), (hx + 14, hy - 1), (hx - 14, hy - 1)])
        cv[:] = cv * (1 - band) + np.array((226.0, 150, 40)) * band
        # the ghost: it pours up out of the side of him, drifts over the moon and thins away
        q = (u - 0.25) / 0.65
        if 0 <= q < 1:
            on = sm(0.0, 0.14, q) * (1 - sm(0.76, 1.0, q))
            gh_x = 74 + 66 * q + 13 * math.sin(q * 11); gh_y = 136 - 92 * sm(0, 0.8, q)
            tmp = np.zeros_like(cv)
            pts = [(gh_x - 20 * s * (1 + 0.6 * math.sin(s * 9 - t * 40)), gh_y + 44 * s) for s in ghost_t[::-1]]
            tube(tmp, pts, lambda s: 14 * (0.45 + 0.55 * s), [(40, 46, 66), (130, 150, 180), (214, 228, 240), (250, 252, 255)], 0.0)
            alpha = (tmp.max(2, keepdims=True) > 0) * on * 0.86
            cv[:] = cv * (1 - alpha) + tmp * alpha
            for dx in (-5, 5):
                stamp(cv, gh_x + dx, gh_y - 2, np.array((20.0, 20, 30)) * on + cv[int(np.clip(gh_y, 0, H - 1)), int(np.clip(gh_x, 0, W - 1))] * (1 - on), 2.0)
            mo = poly([(gh_x - 3, gh_y + 4), (gh_x + 3, gh_y + 4), (gh_x + 2, gh_y + 10), (gh_x - 2, gh_y + 10)])
            cv[:] = cv * (1 - mo * on) + np.array((20.0, 20, 30)) * mo * on
        cv += np.clip(S[..., 0] * 0 + np.exp(-(np.hypot(XX - gx - bw / 2, YY - gy - bh * 0.6) / 70) ** 2), 0, 1)[..., None] * ORANGE * 0.1 * (0.8 + 0.2 * wave(t, 6))
        return np.clip(cv, 0, 255)
    return "halloween", "the brew", scene, 48

RECIPES = [zulrah_forms, dbrutus_charge, drgreen_grass, drblue_pool, drred_lava, drblack_dark, drmetal_lair, sea_voyage, halloween_brew]
