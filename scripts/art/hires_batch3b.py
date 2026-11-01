"""Hi-res recipes, batch 3b: Bryophyta, the Gemstone Crab, Artio, Spindel, the Chaos Elemental, the Chaos Fanatic."""
from hires_kit import *
from hires_batch2c import sm, cut_sprite, lum_of, rim_of, heal, up_of, darken_round, over_above, arc_slash, shadow, shake, coursed, spike, poly, strokes
from hires_batch3a import figure, stand, spot, lean_bend, beat, ground, debris

# ---- Bryophyta: the growthlings come up round her -----------------------------------
def moss_cave_backdrop():
    """a moss-hung vault: dark stone under a green mat, roots down from the roof"""
    stone = fbm(1901, (24, 10, 4))
    moss = np.clip(fbm(1903, (22, 9, 4)) * 2.4 - 0.9, 0, 1)
    img = ramp([(24, 26, 26), (36, 38, 36), (46, 46, 44)], stone) * (1 - moss[..., None]) + ramp([(20, 44, 18), (36, 68, 24), (58, 86, 30)], stone) * moss[..., None]
    for i in range(11):
        x = 8 + i * 20 + 7 * math.sin(i * 2.1); length = 34 + 44 * abs(math.sin(i * 1.7))
        m = (np.abs(XX - x - 5 * np.sin(YY * 0.09 + i)) < 2.4 * np.clip(1 - YY / length, 0, 1) + 0.4) & (YY < length)
        img = img * (1 - m[..., None]) + np.array([52.0, 66, 34]) * m[..., None]
    return ground(img, 180, (44, 54, 28), 1905)

def bryophyta_growth():
    rig, (h, w) = figure("Bryophyta.png", W - 60, H - 14, exposure=0.72, gamma=1.0, sat=1.0)
    sprout_rgb, sprout_al = fit(cut_sprite("Growthling.png", 128), 48, 62, exposure=0.95, gamma=0.8, grain=0.3)
    back = moss_cave_backdrop()
    # five growthlings on a pentagon around her, squashed onto the floor, the next one each beat
    spots = polygon_pts(W / 2, 208, 92, 5, math.pi * 0.1, 0.22)
    order = sorted(range(5), key=lambda i: spots[i][1])
    seeds = phyllotaxis(W / 2 + 6, 122, 60, 12)
    def scene(t):
        cv = feather(back * (0.92 + 0.08 * wave(t, 2)))
        for j, (x, y) in enumerate(seeds):                                      # spores on sunflower steps, each pulsing as the swell passes
            d = math.hypot(x - W / 2, y - 122)
            on = max(0.0, math.sin(2 * math.pi * (t * 2 - d / 90)))
            if 0 <= x < W and 0 <= y < 170: stamp(cv, x, y - 18 * ((t + j * 0.09) % 1), np.array((150.0, 200, 70)) * on ** 2 * 0.65, 1.1)
        wind, go, hit = beat((t * 2) % 1, 0.5, 0.3, 0.3)
        left, top = (W - w) // 2 + 6, H - 10 - h
        arm = dict(c=(0.14, 0.53), r=(0.26, 0.17), p=(0.32, 0.38), a=11 * wind - 11 * go, soft=0.4)
        off = dict(c=(0.86, 0.45), r=(0.16, 0.25), p=(0.78, 0.26), a=-5 * wind + 6 * go + 2.5 * wave(t, 2), soft=0.4)
        head = dict(c=(0.6, 0.11), r=(0.14, 0.11), p=(0.6, 0.2), a=3 * wave(t, 2, phi_phase(1)), soft=0.5)
        def sprouting(i):
            x, y = spots[i]; q = (t * 5 / 5 + order.index(i) / 5) % 1
            rise = sm(0.0, 0.2, q) * (1 - sm(0.8, 0.96, q))
            if rise < 0.02: return
            s = 0.62 + 0.38 * (y - 196) / 24
            hh = int(sprout_al.shape[0] * s); ww = int(sprout_al.shape[1] * s)
            rgb = np.asarray(Image.fromarray(sprout_rgb.astype(np.uint8)).resize((ww, hh)), float)
            al = (np.asarray(Image.fromarray((sprout_al[..., 0] * 255).astype(np.uint8)).resize((ww, hh)), float) / 255)[..., None]
            ring(cv, x, y, ww * 0.55, 0.28, 2.5, np.array((120.0, 170, 60)) * rise * 0.8)
            ox = int(x - ww / 2 + 3 * wave(t, 4, i * 0.2)); oy = int(y - hh * rise)
            over_above(cv, rgb, al, ox, oy, y)
        for i in order:
            if spots[i][1] < 204: sprouting(i)
        shadow(cv, left + w * 0.5, H - 11, w * 0.5, 5, 0.5)
        stand(cv, rig, [off, arm, head], left, top, (0.7 * wave(t, 1), -0.7), lean_bend(5 * wind - 8 * go, -3 * wind + 4 * go), 0.5)
        cx, cy = spot(rig, arm, 0.06, 0.53, left, top)
        glow(cv, cx, cy, 6 + 9 * wind, (150, 220, 60), 0.3 + 0.6 * wind + 0.4 * go)
        ex, ey = spot(rig, head, 0.62, 0.11, left, top)
        glow(cv, ex, ey, 3, (210, 240, 70), 0.6)
        for i in order:
            if spots[i][1] >= 204: sprouting(i)
        return np.clip(cv, 0, 255)
    return "bryophyta", "growthlings", scene

# ---- the Gemstone Crab: every gem takes its turn -----------------------------------
def crab_cave_backdrop():
    """the crab's shore cave: dark rock with crystal veins that catch the light from its back"""
    tex = fbm(2001, (30, 12, 5))
    img = ramp([(24, 22, 36), (36, 32, 48), (46, 42, 58)], tex).copy()
    img = ground(img, 176, (50, 46, 56), 2003)
    return img

def crab_glints(under=None):
    rig, (h, w) = figure("Gemstone_Crab.png", W - 2, H - 40, exposure=0.85, gamma=0.92, sat=1.35, healed=3)
    back = crab_cave_backdrop()
    left, top = 1, H - 12 - h
    # the crystals' tips, left to right, in their own colours
    GEMS = [(0.27, 0.27, (255, 70, 80)), (0.36, 0.14, (255, 70, 80)), (0.46, 0.09, (190, 90, 255)), (0.56, 0.17, (90, 230, 120)),
            (0.64, 0.04, (255, 80, 90)), (0.70, 0.13, (240, 240, 255)), (0.75, 0.04, (255, 80, 110)), (0.83, 0.13, (80, 120, 255)), (0.88, 0.3, (120, 240, 110))]
    facets = hex_mask(15, 108, 80, 0.3, 1.5)
    GX = [left + g[0] * w for g in GEMS]; GY = [top + g[1] * h for g in GEMS]
    lum = rig.rgb.copy()
    def scene(t):
        n = len(GEMS)
        pos = t * n * 1.0
        active = int(pos) % n; u = pos % 1
        col = np.array(GEMS[active][2], float); ax, ay = GX[active], GY[active]
        flash = math.sin(math.pi * u) ** 2
        cv = back * (0.85 + 0.15 * flash)
        # the lit gem throws its colour on the cave wall, as a hexagon lattice round it
        wash = np.exp(-(((XX - ax) / 84) ** 2 + ((YY - ay) / 74) ** 2))[..., None]
        cv = cv + wash * col * (0.14 + 0.22 * flash * facets[..., None])
        cv = feather(cv)
        # rays out of the gem, sweeping as it turns
        for i in range(10):
            a = i * 2 * math.pi / 10 + 2 * math.pi * t + (active * GOLDEN_ANGLE)
            ln = 62 + 34 * math.sin(i * 2.4)
            line(cv, (ax, ay), (ax + ln * math.cos(a), ay + ln * math.sin(a) * 0.9), col * 0.24 * flash, 0.9)
        wave_ = wave(t, 2)
        cl = dict(c=(0.13, 0.75), r=(0.16, 0.2), p=(0.22, 0.62), a=-7 * wave(t, 2) + 4 * flash, soft=0.45)
        cr = dict(c=(0.88, 0.8), r=(0.14, 0.2), p=(0.78, 0.65), a=7 * wave(t, 2, 0.2), soft=0.45)
        front = dict(c=(0.4, 0.62), r=(0.12, 0.1), p=(0.4, 0.62), d=(0, 0.012 * wave(t, 3)), soft=0.9)
        shadow(cv, W / 2, H - 16, 98, 7, 0.55)
        def body(x, y):
            v = np.clip((0.7 - y) / 0.7, 0, 1)
            return -1.0 * v * wave_, -2.5 * v * wave_
        rig.rgb = lum
        if under: under(cv, t)
        stand(cv, rig, [cr, cl, front], left, top, (math.cos(2 * math.pi * t), -0.7), body, 0.5, relief=0.34)
        # every gem has a low shine; the active one flares and stars
        for i, (x, y) in enumerate(zip(GX, GY)):
            c_ = np.array(GEMS[i][2], float)
            y2 = y - 2.5 * wave_
            glow(cv, x, y2, 5, c_, 0.28 + 0.1 * wave(t, 3, i * 0.17))
            if i == active:
                glow(cv, x, y2, 13, c_, 0.75 * flash)
                s = 16 * flash + 2
                line(cv, (x - s, y2), (x + s, y2), (255, 255, 255), 0.9 * flash + 0.2)
                line(cv, (x, y2 - s * 1.4), (x, y2 + s * 1.4), (255, 255, 255), 0.9 * flash + 0.2)
                stamp(cv, x, y2, (255, 255, 255), 2.2)
        return np.clip(cv, 0, 255)
    return "crab", "gem glints", scene

# ---- Artio: the roar goes out through the pines ------------------------------------
def pine_backdrop():
    """dusk in the Wilderness: three depths of pines, a bruised sky, a snowless frozen floor"""
    img = ramp([(46, 30, 54), (60, 38, 58), (78, 50, 56)], np.clip(YY / 150, 0, 1)).copy()
    for depth, (base, gap, colour, seed) in enumerate(((150, 30, (40, 30, 50), 2101), (162, 42, (28, 24, 38), 2103), (176, 58, (16, 16, 24), 2105))):
        r = np.random.RandomState(seed)
        for i in range(int(W / gap) + 2):
            x = i * gap + r.rand() * gap * 0.6; tall = 70 + 60 * r.rand() + depth * 18
            v = np.clip((YY - (base - tall)) / tall, 0, 1)
            wide = (5 + 22 * v) * (0.7 + 0.3 * np.sin(YY * 0.45) ** 2)
            m = ((np.abs(XX - x) < wide) & (YY > base - tall) & (YY < base + 18))[..., None]
            img = img * (1 - m) + np.array(colour, float) * m
    return ground(img, 182, (42, 38, 48), 2107)

def artio_roar():
    rig, (h, w) = figure("Artio.png", W + 8, H - 62, 60, exposure=0.72, gamma=1.0, sat=1.1)
    back = pine_backdrop()
    needles = Motes(2111, 30, lambda r: (r.rand() * W, -6, -20 * r.rand(), H * 0.8, 0))
    shudder = Motes(2113, 22, lambda r: (W * 0.5 * r.rand(), 90 + 60 * r.rand(), -120 * r.rand(), 50 * (r.rand() - 0.5), 60))
    left, top = -2, H - 12 - h
    def scene(t):
        p = t * 2; u = p % 1
        wind, go, hit = beat(u, 0.42, 0.3, 0.36)
        # the pines shake with the shout
        sh = 2.5 * go * math.sin(2 * math.pi * t * 11)
        cv = feather(back * (0.86 + 0.12 * hit))
        cv = cv[:, np.clip(np.arange(W) - int(sh), 0, W - 1)]
        needles.draw(cv, (t * 2) % 1, lambda v: (110, 90, 110), 0.9)
        lean = 7 * wind - 12 * go
        head = dict(c=(0.16, 0.56), r=(0.2, 0.26), p=(0.34, 0.5), a=-8 * wind + 8 * go + 1.5 * wave(t, 2), soft=0.5)
        jaw = dict(c=(0.12, 0.74), r=(0.11, 0.08), p=(0.2, 0.68), a=3 * wind + 13 * go, soft=0.5)
        hump = dict(c=(0.62, 0.28), r=(0.28, 0.24), p=(0.62, 0.4), d=(0, -0.018 * wave(t, 2) - 0.03 * wind), soft=0.7)
        fore = dict(c=(0.24, 0.9), r=(0.1, 0.13), p=(0.26, 0.76), a=-5 * wind, soft=0.5)
        shadow(cv, left + w * 0.5, H - 14, w * 0.5, 6, 0.55)
        stand(cv, rig, [hump, fore, jaw, head], left, top, (0.6 * wave(t, 1), -0.8), lean_bend(lean, -4 * wind + 4 * go, 0.92), 0.55)
        mx, my = spot(rig, jaw, 0.08, 0.68, left, top); mx += lean * 0.6
        ex, ey = spot(rig, head, 0.13, 0.5, left, top); ex += lean * 0.6
        glow(cv, ex, ey, 2 + 2 * go, (255, 210, 120), 0.5 + 0.6 * go)
        if u >= 0.42:
            f = min(1.0, (u - 0.42) / 0.5)
            for i in range(5):                                                   # the shout: arcs in golden spacing, thrown out of the mouth
                q = f - i * 0.1
                if q <= 0: continue
                rad = 14 * PHI ** (i * 0.9) + 170 * q
                arc_slash(cv, mx, my, rad, 2.3, 3.95, np.array((200.0, 190, 225)) * (1 - min(1.0, q)) ** 1.2, 2.4, 1.05)
            plume(cv, mx, my, -0.9, 0.35, 76, 28, (t * 6) % 1, [(240, 240, 250), (170, 170, 195), (100, 96, 126), (66, 62, 86)], 2115, max(0.0, 1 - f * 1.1), wobble=0.16)
            shudder.draw(cv, min(0.99, f), lambda v: np.array((96, 80, 110)) * (1 - v), 1.1)
        return np.clip(cv, 0, 255)
    return "artio", "the roar", scene

# ---- Spindel: the web carries every step ------------------------------------------
def spindel_web():
    rig, (h, w) = figure("Spindel.png", W + 10, H - 60, 70, exposure=0.8, gamma=0.95, sat=1.2, healed=3)
    cx, cy = W / 2, 98
    tex = fbm(2201, (26, 11, 5))
    back = ramp([(24, 16, 36), (36, 22, 50), (48, 30, 60)], tex)
    back = ground(back, 196, (40, 30, 48), 2203)
    # the web: straight radials and sagging rings, each ring phi out from the last
    SPOKES = 13
    angles = [i * 2 * math.pi / SPOKES + 0.11 * math.sin(i * 3.1) for i in range(SPOKES)]
    RADII = phi_radii(13, 6)
    def web(cv, t, u):
        col = np.array((180.0, 110, 230))
        for i, a in enumerate(angles):
            end = (cx + 240 * math.cos(a), cy + 240 * math.sin(a))
            line(cv, (cx, cy), end, col * 0.7, 1.3)
        for j, rad in enumerate(RADII):
            pulse = max(0.0, math.sin(2 * math.pi * (u * 1 - j / 6))) ** 4
            shade = col * (0.6 + 0.9 * pulse)
            for i, a in enumerate(angles):
                b = angles[(i + 1) % SPOKES]
                p0 = (cx + rad * math.cos(a), cy + rad * math.sin(a))
                p1 = (cx + rad * math.cos(b), cy + rad * math.sin(b))
                sag = rad * 0.13
                mid = ((p0[0] + p1[0]) / 2 + (cx - (p0[0] + p1[0]) / 2) * 0.13, (p0[1] + p1[1]) / 2 + (cy - (p0[1] + p1[1]) / 2) * 0.13)
                line(cv, p0, mid, shade, 1.3); line(cv, mid, p1, shade, 1.3)
    r = np.random.RandomState(2211)
    bundles = [(angles[i], RADII[3 + i % 3] * (0.85 + 0.1 * r.rand())) for i in (1, 4, 6, 9, 11)]
    spawn = [(r.rand() * W, r.rand(), 3 + 3 * r.rand()) for _ in range(9)]
    left, top = -4, (H - h) // 2 - 22
    def scene(t):
        u = (t * 2) % 1
        cv = feather(back * (0.9 + 0.1 * wave(t, 2)))
        web(cv, t, u)
        for a, rad in bundles:                                                   # cocooned prey, swaying on the threads
            bx, by = cx + rad * math.cos(a), cy + rad * math.sin(a) + 2.5 * wave(t, 2, a)
            stamp(cv, bx, by, (98, 80, 104), 5)
            stamp(cv, bx - 1, by - 2, (170, 150, 170), 2.2)
        for x, ph, s in spawn:                                                   # the young let down on silk
            v = (t * 2 + ph) % 1
            y = -4 + (190 * sm(0.0, 0.5, v)) * (1 - sm(0.8, 1.0, v))
            line(cv, (x, 0), (x, y), (120, 90, 150), 0.6)
            stamp(cv, x, y, (160, 40, 40), s * 0.8); stamp(cv, x, y, (40, 20, 30), s * 0.45)
        step = lambda i: wave(t, 4, i * 0.25)
        parts = [
            dict(c=(0.12, 0.35), r=(0.15, 0.3), p=(0.3, 0.4), a=-8 * step(0), soft=0.45),
            dict(c=(0.12, 0.68), r=(0.15, 0.3), p=(0.3, 0.56), a=8 * step(1), soft=0.45),
            dict(c=(0.88, 0.35), r=(0.15, 0.3), p=(0.7, 0.4), a=8 * step(2), soft=0.45),
            dict(c=(0.88, 0.68), r=(0.15, 0.3), p=(0.7, 0.56), a=-8 * step(3), soft=0.45),
            dict(c=(0.5, 0.56), r=(0.13, 0.16), p=(0.5, 0.45), a=3 * wave(t, 2), soft=0.5),
        ]
        stand(cv, rig, parts, left, top, (math.cos(2 * math.pi * t), -0.7), lambda x, y: (0 * x, -2.5 * wave(t, 2) * np.ones_like(y)), 0.5, relief=0.34)
        fx, fy = left + 0.5 * w, top + 0.6 * h - 2.5 * wave(t, 2)
        for dx in (-5, -2, 2, 5):
            stamp(cv, fx + dx, fy, (255, 90, 110), 1.2)
        q = (u * 1.0) % 1                                                        # a bead of venom gathers, falls and lands
        y = fy + 8 + 160 * sm(0.35, 0.7, q) ** 2
        if q < 0.72: stamp(cv, fx, y, (150, 240, 110), 1.6 + 1.2 * sm(0.0, 0.35, q))
        else: ring(cv, fx, fy + 170, 3 + 40 * (q - 0.72), 0.3, 2.5, np.array((120.0, 220, 90)) * (1 - (q - 0.72) / 0.28))
        return np.clip(cv, 0, 255)
    return "spindel", "the web", scene

# ---- the Chaos Elemental: nothing stays one thing -----------------------------------
def chaos_rift():
    rig, (h, w) = figure("Chaos_Elemental.png", W + 70, H - 70, exposure=0.98, gamma=0.8, sat=1.2)
    lum = lum_of(rig, 4, 96)
    STATES = [ramp([(20, 8, 44), (84, 30, 150), (180, 90, 230), (245, 215, 255)], lum)[..., :3],
              ramp([(46, 8, 10), (160, 26, 26), (250, 90, 50), (255, 230, 180)], lum)[..., :3],
              ramp([(8, 34, 28), (20, 120, 90), (70, 225, 150), (225, 255, 225)], lum)[..., :3]]
    TONES = [np.array((180.0, 90, 255)), np.array((255.0, 90, 50)), np.array((70.0, 240, 160))]
    tex = fbm(2301, (34, 14, 6))
    base = ramp([(26, 22, 34), (38, 30, 44), (50, 40, 52)], tex)
    base = ground(base, 184, (40, 32, 46), 2303)
    junk = [(math.cos(i * GOLDEN_ANGLE), math.sin(i * GOLDEN_ANGLE), 44 + 3.6 * i, i) for i in range(18)]
    left, top = (W - w) // 2, 82
    def scene(t):
        p = t * 3; k = int(p) % 3; u = p % 1; tone = TONES[k]; nxt = TONES[(k + 1) % 3]
        cv = feather(base * (0.86 + 0.1 * wave(t, 3)))
        cv += np.exp(-((XX - W / 2) / 110) ** 2 - ((YY - 120) / 100) ** 2)[..., None] * tone * 0.1
        wire_solid(cv, W / 2, 112, 76, t, tone * 0.5, "dodecahedron", spin=1, size=1.0)
        wire_solid(cv, W / 2, 112, 76 / PHI, -t, nxt * 0.42, "icosahedron", spin=-1, size=0.9)
        for dx, dy, rad, i in junk:                                              # gear torn off and turning on golden steps
            a = 2 * math.pi * t * (1 if i % 2 else -1)
            x = W / 2 + rad * (dx * math.cos(a) - dy * math.sin(a)) * 1.25
            y = 112 + rad * (dx * math.sin(a) + dy * math.cos(a)) * 0.85
            col = np.array((130.0, 120, 110)) * (0.6 + 0.4 * ((i * 3) % 2)) * (0.8 + 0.3 * wave(t, 2, i * 0.1))
            line(cv, (x - 3, y - 2), (x + 3, y + 2), col, 1.6)
        mix = sm(0.8, 1.0, u)
        rig.rgb = np.clip(STATES[k] * (1 - mix) + STATES[(k + 1) % 3] * mix, 0, 255)
        rise = 8 * wave(t, 2)
        top2 = top + rise
        tent_a = dict(c=(0.24, 0.17), r=(0.26, 0.26), p=(0.32, 0.4), a=7 * wave(t, 3), soft=0.45)
        tent_b = dict(c=(0.86, 0.5), r=(0.14, 0.26), p=(0.74, 0.45), a=-8 * wave(t, 3, 0.2), soft=0.45)
        def body(x, y):
            return 3 * np.sin(2 * math.pi * (t * 3 + y * 1.6 + x)), 3 * np.sin(2 * math.pi * (t * 3 + x * 2.2))
        stand(cv, rig, [tent_a, tent_b], left, top2, (math.cos(2 * math.pi * t), -0.7), body, 0.55)
        # the three coloured bolts: one each state, thrown at the frame's golden points
        q = (u - 0.3) / 0.28
        if 0 <= q < 1:
            ox, oy = left + w * 0.5, top2 + h * 0.3
            for i, (x, y) in enumerate(((GOLD_X, 26), (W - GOLD_X, 26), (W - 18, GOLD_Y - 40))):
                if q * 3 > i:
                    bolt(cv, ox, oy, x, y, 2311 + i + int(t * FRAMES), tone * (1.15 - q * 0.6), jag=34, size=1.3)
            glow(cv, ox, oy, 12, tone, 1 - q)
        # reality slips: a few rows of the picture jump sideways, whole frames at a time
        r = np.random.RandomState(2321 + int(t * FRAMES))
        out = cv.copy()
        for _ in range(3 if u > 0.72 else 1):
            y0 = r.randint(0, H - 30); hh = r.randint(5, 22); s = r.randint(-20, 21)
            out[y0:y0 + hh] = np.roll(cv[y0:y0 + hh], s, axis=1)
            if u > 0.72: out[y0:y0 + hh] += nxt * 0.1
        return np.clip(out, 0, 255)
    return "chaos", "chaos", scene

# ---- the Chaos Fanatic: he talks to it, and it answers in green ---------------------
def ruin_backdrop():
    """the Wilderness ruin he paces: fallen arches, lava-black ground, a green sky low down"""
    img = ramp([(26, 34, 30), (32, 40, 34), (46, 58, 40)], np.clip(YY / 180, 0, 1)) * (0.75 + 0.45 * fbm(2401, (30, 12, 5))[..., None])
    for x0, tall, wid in ((10, 118, 26), (W - 40, 88, 30), (W - 80, 50, 20)):
        m = ((XX > x0) & (XX < x0 + wid) & (YY > 180 - tall - 14 * fbm(2403, (6, 3))))[..., None]
        img = img * (1 - m) + coursed(12, 18, 2405, (44, 44, 42), (12, 5)) * m
    return ground(img, 184, (34, 34, 30), 2407)

def fanatic_bolt(under=None):
    rig, (h, w) = figure("Chaos_Fanatic.png", 130, H - 20, exposure=0.72, gamma=1.0, sat=1.2)
    back = ruin_backdrop()
    left, top = 40, H - 11 - h
    # three bombs land in a row of golden steps
    MARKS = [(W - 30, 218), (W - 62, 200), (W - 96, 224)]
    sparks = Motes(2411, 20, lambda r: (0, 0, 90 * (r.rand() - 0.5), -90 * r.rand(), 150))
    swirl = phyllotaxis(0, 0, 24, 3.6)
    def scene(t):
        p = t * 2; u = p % 1
        wind, go, hit = beat(u, 0.42, 0.3, 0.2)
        cv = feather(back * (0.86 + 0.25 * hit))
        cv += np.exp(-((YY - 170) / 40) ** 2)[..., None] * np.array([6.0, 26, 10]) * (0.6 + 0.3 * wave(t, 3))
        fit_ = 3.5 * math.sin(2 * math.pi * t * 7) * max(wave(t, 3), 0)          # he twitches
        staff = dict(c=(0.16, 0.25), r=(0.2, 0.25), p=(0.32, 0.26), a=-7 * wind + 12 * go + 2 * wave(t, 4), soft=0.4)
        far = dict(c=(0.84, 0.42), r=(0.14, 0.19), p=(0.78, 0.27), a=-5 * wave(t, 3), soft=0.45)
        head = dict(c=(0.52, 0.09), r=(0.2, 0.11), p=(0.52, 0.18), a=4 * wave(t, 3, 0.2) + fit_, soft=0.5)
        hem = dict(c=(0.5, 0.72), r=(0.32, 0.16), p=(0.5, 0.6), a=2 * wave(t, 4), soft=0.6)
        shadow(cv, left + w * 0.5, H - 12, w * 0.45, 5, 0.5)
        if under: under(cv, t)
        stand(cv, rig, [hem, far, staff, head], left, top, (0.7 * wave(t, 1), -0.7), lean_bend(4 * wind - 6 * go + fit_ * 0.25), 0.5)
        tx, ty = spot(rig, staff, 0.1, 0.19, left, top)
        ex, ey = spot(rig, head, 0.56, 0.1, left, top)
        glow(cv, ex, ey, 3, (130, 255, 110), 0.5 + 0.5 * wind)
        if wind > 0.01:
            for i, (dx, dy) in enumerate(swirl):                                 # green gathers in a seed-head at the staff
                a = 2 * math.pi * t * 3; d = 1 - wind * 0.5
                stamp(cv, tx + d * (dx * math.cos(a) - dy * math.sin(a)), ty + d * (dx * math.sin(a) + dy * math.cos(a)), np.array((130.0, 255, 110)) * wind, 1.1)
            glow(cv, tx, ty, 5 + 9 * wind, (90, 255, 90), 0.9 * wind)
        if u >= 0.42:
            for i, (mx, my) in enumerate(MARKS):
                q = (u - 0.42 - i * 0.07) / 0.17
                if 0 <= q < 1:                                                    # each bomb on a high arc, trailing
                    for b in range(5):
                        v = max(0.0, q - b * 0.05)
                        x = tx + (mx - tx) * v; y = ty + (my - ty) * v - 80 * math.sin(math.pi * v)
                        stamp(cv, x, y, np.array((140.0, 255, 110)) * (1 - b * 0.18), 3.0 - b * 0.4)
                elif q >= 1:
                    f = min(1.0, (q - 1) / 1.8)
                    ring(cv, mx, my, 4 + 34 * f, 0.34, 4, np.array((110.0, 255, 100)) * (1 - f))
                    glow(cv, mx, my - 4, 14, (70, 230, 70), (1 - f) * 0.8)
                    sparks.draw(cv, f, lambda v: np.array((170, 255, 130)) * (1 - v), 1.1) if i == 1 else None
        for i, (mx, my) in enumerate(MARKS):                                      # the marks on the ground that warn of them
            on = sm(0.05, 0.3, u) * (1 - sm(0.6, 0.72, u))
            ring(cv, mx, my, 16, 0.34, 1.8, np.array((70.0, 180, 70)) * on)
        return np.clip(cv, 0, 255)
    return "fanatic", "the green bolt", scene

RECIPES = [bryophyta_growth, crab_glints, artio_roar, spindel_web, chaos_rift, fanatic_bolt]
