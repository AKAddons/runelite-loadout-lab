"""Hi-res recipes, batch 3a: Brutus, the Mad Angel, the Grotesque Guardians, the Royal Titans, the Maggot King, Obor."""
from hires_kit import *
from hires_batch2c import sm, cut_sprite, lum_of, rim_of, heal, up_of, darken_round, over_above, arc_slash, shadow, shake, coursed, spike, poly, strokes

# ---- shared helpers ------------------------------------------------------------
def figure(name, wide, tall, thr=128, healed=5, **kw):
    kw.setdefault("grain", 0.4)
    rgb, al = fit(cut_sprite(name, thr), wide, tall, **kw)
    if healed: rgb = heal(rgb, healed)
    return Rig(rgb, al, pad=40), al.shape[:2]

def stand(cv, rig, parts, left, top, light, bend=None, halo=0.45, relief=0.3):
    rgb, al = rig.pose(parts, light=light, relief=relief, bend=bend)
    ox, oy = int(left) - rig.pad, int(top) - rig.pad
    darken_round(cv, al, ox, oy, halo, 4)
    over(cv, rgb, al, ox, oy)

def spot(rig, part, fx, fy, left, top):
    x, y = rig.at(part, fx, fy)
    return left + x, top + y

def lean_bend(lean, drop=0.0, y0=0.9):
    return lambda x, y: (-lean * up_of(y, y0), -drop * up_of(y, y0))

def beat(u, at, lead=0.25, rest=0.25):
    """wind-up before the blow, the held follow-through, and the flash that decays from it"""
    wind = sm(at - lead, at - 0.02, u) * (1 - sm(at - 0.02, at + 0.02, u))
    go = sm(at - 0.02, at + 0.03, u) * (1 - sm(at + rest * 0.6, at + rest, u))
    hit = math.exp(-(u - at) * 11) if u >= at else 0.0
    return wind, go, hit

def ground(img, y, colour, seed, cells=(14, 6, 3)):
    """a textured floor from row y down, lighter toward the viewer"""
    m = (YY > y)[..., None]
    z = np.clip((YY - y) / max(1, H - y), 0, 1)[..., None]
    return img * (1 - m) + np.array(colour, float) * (0.55 + 0.7 * fbm(seed, cells)[..., None]) * (0.65 + 0.5 * z) * m

def debris(seed, n, x, y, spread=120, rise=120):
    return Motes(seed, n, lambda r: (x + (r.rand() - 0.5) * 30, y, (r.rand() - 0.5) * spread, -rise * (0.4 + r.rand()), 170))

# ---- Brutus: the bull paws, snorts and comes ---------------------------------------
def byre_backdrop():
    """a byre by night: vertical boards, a square of moon through the hatch, hay, trodden mud"""
    col = (XX // 25).astype(int)
    shade = np.random.RandomState(1301).rand(20)[col % 20]
    grain_ = 0.75 + 0.5 * fbm(1303, (60, 5, 2))
    img = np.array([58.0, 40, 28]) * (0.7 + 0.5 * shade)[..., None] * grain_[..., None]
    img = img * (1 - 0.7 * (XX % 25 < 2)[..., None])
    hatch = ((np.abs(XX - 160) < 20) & (np.abs(YY - 52) < 18))[..., None]
    img = img * (1 - hatch) + np.array([62.0, 72, 96]) * hatch
    img = img * (1 - 0.8 * (((np.abs(XX - 160) < 1.5) | (np.abs(YY - 52) < 1.5)) & (np.abs(XX - 160) < 21) & (np.abs(YY - 52) < 19))[..., None])
    return ground(img, 172, (60, 46, 32), 1305)

def brutus_charge():
    rig, (h, w) = figure("Brutus.png", W + 12, H - 52, 60, exposure=0.7, gamma=1.0, sat=1.0)
    back = byre_backdrop()
    dust = debris(1311, 26, 150, 212, 150, 70)
    motes = Motes(1313, 16, lambda r: (r.rand() * W, 20 + r.rand() * 140, 8 * (r.rand() - 0.5), 18 * r.rand(), 0))
    hay = Motes(1315, 18, lambda r: (r.rand() * W, 150 + 60 * r.rand(), 40 * (r.rand() - 0.5), -70 * r.rand(), 110))
    GROUND = 222
    def scene(t):
        p = t * 2; u = p % 1
        wind, go, hit = beat(u, 0.62, 0.42, 0.34)
        cv = feather(back * (0.8 + 0.3 * hit))
        motes.draw(cv, t, lambda v: np.array((80, 70, 56)) * math.sin(math.pi * v), 0.9)
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
        shadow(cv, left + w * 0.5, GROUND - 3, w * 0.5, 7, 0.55)
        stand(cv, rig, parts, left, top, (0.6 * wave(t, 1), -0.8), lean_bend(lean, -6 * wind + 3 * go, 0.95))
        nx, ny = spot(rig, head, 0.085, 0.45, left, top)
        ex, ey = spot(rig, head, 0.25, 0.33, left, top)
        glow(cv, ex + lean * 0.7, ey, 3 + 3 * wind + 3 * go, (255, 30, 20), 0.5 + 0.7 * wind + 0.5 * go)
        stamp(cv, ex + lean * 0.7, ey, (255, 170, 130), 1.4)
        for i, phase in enumerate((0.1, 0.36)):                                 # two hard snorts of steam before he goes
            q = (u - phase) / 0.16
            if 0 <= q < 1:
                plume(cv, nx + lean * 0.7, ny, -0.8, 0.5, 46 + 30 * q, 16, (t * 6) % 1, [(240, 240, 235), (170, 172, 170), (96, 96, 98), (60, 60, 62)], 1317 + i, math.sin(math.pi * q), wobble=0.2)
        if paw > 0.1:
            hay.draw(cv, (u * 3) % 1, lambda v: np.array((150, 116, 60)) * (1 - v), 1.0)
        if go > 0.02:                                                           # streaks behind the charge, the dust thrown up from the hooves
            for i in range(9):
                y = 120 + i * 11; x = (W - 40 + 50 * math.sin(i * 2.4)) - 60 * (1 - go)
                line(cv, (x, y), (x + 34 + 14 * math.sin(i), y), np.array((110, 96, 76)) * go * 0.7, 0.9)
        if u >= 0.62:
            dust.draw(cv, min(0.99, (u - 0.62) / 0.34), lambda v: np.array((150, 126, 92)) * (1 - v), 2.0)
        return np.clip(shake(cv, 2.5 * hit * wave(t, 16), 3 * hit * wave(t, 13)), 0, 255)
    return "brutus", "the charge", scene

# ---- the Mad Angel: holy and ruined by turns ----------------------------------------
def sanctum_backdrop():
    """a broken marble sanctum: columns either side, a cracked floor, grey light from above"""
    img = ramp([(30, 28, 38), (44, 42, 52), (54, 52, 60)], np.clip(YY / 180, 0, 1))
    img = img * (0.75 + 0.45 * fbm(1401, (26, 11, 5))[..., None])
    for x0, x1, broke in ((6, 34, 0), (W - 34, W - 6, 52)):
        m = ((XX > x0) & (XX < x1) & (YY > broke + 12 * fbm(1403, (5, 2))))[..., None]
        flute = 0.75 + 0.3 * np.sin((XX - x0) * 1.1)
        img = img * (1 - m) + np.array([62.0, 60, 66]) * flute[..., None] * (0.7 + 0.5 * fbm(1405, (9, 4))[..., None]) * m
    img = ground(img, 186, (58, 56, 62), 1407)
    crack = (np.abs(fbm(1409, (22, 11, 5)) - 0.5) < 0.012)[..., None] * (YY > 186)[..., None]
    return img * (1 - 0.7 * crack)

def madangel_hover():
    rig, (h, w) = figure("Mad_Angel.png", W - 70, H - 26, exposure=0.76, gamma=1.0, sat=1.1)
    lum = lum_of(rig, 4, 96)
    holy = rig.rgb.copy()
    ruin = ramp([(14, 6, 26), (58, 18, 92), (170, 50, 170), (255, 170, 230)], lum)[..., :3]
    rim = rim_of(rig, 3)
    back = sanctum_backdrop()
    feathers = Motes(1411, 18, lambda r: (20 + r.rand() * (W - 40), -6, 26 * (r.rand() - 0.5), H - 40, 0))
    ash = Motes(1412, 26, lambda r: (W / 2 + 90 * (r.rand() - 0.5), 90 + 60 * r.rand(), 60 * (r.rand() - 0.5), -130 * r.rand(), 0))
    cx = W / 2
    # the wings are cut out as layers of their own, so they can swing far past what a flat rig allows
    # (Andrew 2026-10-31: "raise its wings into a spread position")
    fx_, fy_ = (rig.xx - rig.pad) / w, (rig.yy - rig.pad) / h
    feathered = (rig.al[..., 0] > 0) & (fy_ > 0.17) & (fy_ < 0.76)
    def closed(m):
        im = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))
        return (np.asarray(im) > 128) & (rig.al[..., 0] > 0)
    WINGS = [(closed(feathered & (fx_ < 0.36)), rig.px(0.35, 0.27), -1), (closed(feathered & (fx_ > 0.64)), rig.px(0.67, 0.27), 1)]
    rig.al = rig.al * (1 - (WINGS[0][0] | WINGS[1][0]))[..., None]
    def wing(cv, rgb, mask, pivot, angle, left, top):
        """rotates one wing about its shoulder on a canvas big enough for the whole swing"""
        S = 2 * max(mask.shape)
        ox, oy = S // 2 - int(pivot[0]), S // 2 - int(pivot[1])
        layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        src = np.dstack([np.clip(rgb, 0, 255), mask * 255.0]).astype(np.uint8)
        layer.paste(Image.fromarray(src, "RGBA"), (ox, oy))
        a = np.asarray(layer.rotate(angle, Image.BICUBIC, center=(S // 2, S // 2)), float)
        al = (a[..., 3:] > 110).astype(float)
        x, y = int(left) - rig.pad - ox, int(top) - rig.pad - oy
        darken_round(cv, al, x, y, 0.4, 3)
        over(cv, a[..., :3], al, x, y)
    def scene(t):
        p = t * 2; u = p % 1; k = int(p) % 2
        # she is holy, then something breaks: each half of the loop starts with the swap flash
        turn = sm(0.0, 0.12, u) * (1 - sm(0.88, 1.0, u))
        mad = turn if k == 1 else 0.0
        snap = math.exp(-u * 14)
        cv = back * (0.86 + 0.3 * snap)
        tone = np.array((255.0, 214, 120)) * (1 - mad) + np.array((210.0, 70, 240)) * mad
        cv = cv * (1 - 0.3 * mad) + cv * np.array([0.8, 0.5, 1.1]) * 0.3 * mad
        shaft = np.exp(-((XX - cx) / (34 + 8 * wave(t, 2))) ** 2) * np.clip(1 - YY / 230, 0.1, 1)
        cv += (shaft * (1 - mad))[..., None] * np.array([46.0, 40, 22])
        cv = feather(cv)
        bob = 5 * wave(t, 2)
        my = 76 + bob
        mandala(cv, cx - 4, my, 66, tone * (0.34 + 0.14 * wave(t, 4)), t, petals=8, sides=6, inner=3, spin=1 if k == 0 else -1, disc=0.35, size=1.0)
        left = (W - w) // 2; top = 8 + bob
        beat_ = wave(t, 4)
        arm = dict(c=(0.24, 0.13), r=(0.18, 0.2), p=(0.32, 0.3), a=-4 * wave(t, 2, 0.2) + 3 * snap, soft=0.4)
        spread = sm(0.1, 0.32, u) * (1 - sm(0.74, 0.94, u))                     # up and out, held with slow beats, then folded
        head = dict(c=(0.48, 0.31), r=(0.12, 0.09), p=(0.48, 0.38), a=3.5 * wave(t, 2, phi_phase(1)) + (5 * mad * wave(t, 14)), soft=0.5)
        def body(x, y):
            v = np.clip((y - 0.55) / 0.45, 0, 1)
            return -(5 + 5 * mad) * v ** 1.5 * np.sin(2 * math.pi * (t * 2 - y * 1.6)), 0 * v
        rig.rgb = np.clip((holy * (1 - mad) + ruin * mad) * (1 - rim * 0.4) + tone * rim * 0.4 + tone * 0.25 * snap, 0, 255)
        for mask, pivot, side in WINGS:
            wing(cv, rig.rgb, mask, pivot, side * (104 * spread + 4 * beat_ + 7 * spread * beat_), left, top)
        if 0.3 <= u < 0.56:                                                     # the air the wings throw off as they lock open
            f = (u - 0.3) / 0.26
            ring(cv, cx, top + h * 0.3, 24 + 110 * f, 0.9, 3, tone * (1 - f) * 0.8)
        stand(cv, rig, [arm, head], left, top, (0.7 * wave(t, 1), -0.7), body, 0.5)
        ex, ey = spot(rig, head, 0.49, 0.31, left, top)
        glow(cv, ex, ey, 3 + 2 * mad, tone, 0.5 + 0.5 * mad)
        tx, ty = spot(rig, arm, 0.235, 0.02, left, top)
        glow(cv, tx, ty, 6, tone, 0.6 + 0.3 * wave(t, 6))
        if k == 0:                                                              # gold feathers come down, turning as they fall
            for t0, (x, y, vx, vy, _) in feathers.p:
                v = (t * 2 + t0) % 1
                fx, fy = x + vx * v + 10 * math.sin(2 * math.pi * (v * 3 + t0)), y + vy * v
                a = 1.2 * math.sin(2 * math.pi * (v * 3 + t0))
                line(cv, (fx - 5 * math.cos(a), fy - 5 * math.sin(a)), (fx + 5 * math.cos(a), fy + 5 * math.sin(a)), np.array((235.0, 205, 130)) * math.sin(math.pi * v), 1.2)
        else:
            ash.draw(cv, (t * 2) % 1, lambda v: np.array((190, 60, 220)) * math.sin(math.pi * v), 1.2)
            if u < 0.4:
                ring(cv, cx, my, 20 + 240 * u, 1.0, 5, np.array((170.0, 50, 210)) * (1 - u / 0.4))
        return np.clip(cv, 0, 255)
    return "madangel", "holy and ruined", scene

# ---- the Grotesque Guardians: Dusk on the roof, Dawn in the air -----------------------
def roof_backdrop():
    """the top of the Slayer Tower: a night sky, a low moon, a crenellated parapet, flagged roof"""
    img = ramp([(14, 16, 34), (22, 24, 46), (40, 36, 58)], np.clip(YY / 170, 0, 1)).copy()
    img += ((fbm(1501, (40, 20, 9)) - 0.42) * 30).clip(0, 30)[..., None]
    d = np.hypot(XX - 160, YY - 54)
    img = img + np.clip(30 - d, 0, 1)[..., None] * np.array([58.0, 58, 50]) * (0.75 + 0.5 * fbm(1503, (10, 4))[..., None])
    img += np.exp(-(d / 56) ** 2)[..., None] * np.array([18.0, 18, 22])
    merlon = ((XX % 44 < 28) & (YY > 132) & (YY < 172)) | ((YY >= 152) & (YY < 172))
    img = img * (1 - merlon[..., None]) + coursed(10, 20, 1505, (40, 38, 46), (12, 5)) * merlon[..., None]
    img = ground(img, 172, (48, 46, 52), 1507)
    z = np.clip((YY - 172) / 68, 0, 1)
    grout = (((XX - W / 2) / (0.5 + z)) % 42 < 2) | (np.log(z + 0.2) * 4 % 1 < 0.09)
    return img * (1 - (0.45 * grout * (YY > 172))[..., None])

def winged(name, wide, tall, wings, stops, hot):
    """a figure whose wing membranes carry its signature colour: cast through a ramp on their own light, stone
    at the root, full at the trailing edge, with bright veins fanning out and a lit rim. wings: (polygon, root) in fractions."""
    rgb, al = fit(cut_sprite(name, 128), wide, tall, exposure=0.68, gamma=1.05, sat=1.0, grain=0.4)
    rgb = heal(rgb, 5)
    h, w = al.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    rim = np.asarray(Image.fromarray((al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), float) / 255
    rim = np.clip((1 - rim) * 2.4, 0, 1) * al[..., 0]
    lum = np.clip(rgb.mean(2) / 150, 0, 1)
    for pts, (rx, ry) in wings:
        m = Image.new("L", (w, h), 0)
        ImageDraw.Draw(m).polygon([(x * w, y * h) for x, y in pts], fill=255)
        mask = np.asarray(m.filter(ImageFilter.GaussianBlur(1.2)), float) / 255
        dx, dy = xx - rx * w, yy - ry * h
        far = np.clip(np.hypot(dx, dy) / (0.62 * max(w, h)), 0, 1)
        vein = np.abs(np.sin(np.arctan2(dy, dx) * 6 + far * 1.5)) ** 7 * (0.35 + 0.65 * far)
        rel = lum / max(0.01, lum[mask > 0.5].max())                             # each wing on its own light, or the lit one washes out
        cast = ramp(stops, np.clip(0.1 + rel * 0.26 + 0.14 * far + 0.52 * vein, 0, 1))[..., :3]
        cast = cast + (rim * (0.2 + 0.5 * far))[..., None] * np.array(hot, float) * 0.6
        k = (mask * (0.45 + 0.55 * np.clip((far - 0.04) / 0.38, 0, 1)))[..., None]
        rgb = rgb * (1 - k) + cast * k
    return Rig(rgb, al, pad=40), al.shape[:2]

def guardians_dusk():
    dusk, (dh, dw) = winged("Dusk.png", W - 50, H - 22,
                            [([(0.5, 0.02), (0.7, 0.03), (0.86, 0.22), (1.0, 0.48), (1.0, 0.72), (0.9, 0.73), (0.66, 0.66), (0.66, 0.39), (0.54, 0.39), (0.5, 0.2)], (0.56, 0.3)),
                             ([(0.18, 0.0), (0.4, 0.11), (0.37, 0.2), (0.3, 0.13)], (0.36, 0.2))],
                            [(34, 18, 8), (122, 62, 14), (226, 138, 30), (255, 216, 120)], (255, 170, 50))
    dawn, (ah, aw) = winged("Dawn.png", W + 40, H - 54,
                            [([(0.0, 0.0), (0.32, 0.0), (0.495, 0.2), (0.47, 0.3), (0.35, 0.47), (0.08, 0.36), (0.0, 0.2)], (0.47, 0.27)),
                             ([(0.595, 0.34), (0.55, 0.06), (0.76, 0.18), (1.0, 0.68), (0.84, 0.67), (0.6, 0.41)], (0.59, 0.34))],
                            [(20, 10, 54), (64, 32, 158), (128, 82, 255), (218, 196, 255)], (170, 110, 255))
    back = roof_backdrop()
    tiles = polygon_pts(W / 2, 208, 92, 5, -math.pi / 2, 0.26)
    grit = debris(1511, 22, W / 2, 212, 170, 90)
    def paint_dusk(u, t, cv):
        wind, go, hit = beat(u, 0.55, 0.3, 0.28)
        left, top = (W - dw) // 2 + 8, H - 12 - dh
        wing = dict(c=(0.72, 0.33), r=(0.3, 0.34), p=(0.55, 0.3), a=-8 * wave(t, 2) - 10 * wind + 12 * go, soft=0.45)
        arm = dict(c=(0.22, 0.5), r=(0.2, 0.2), p=(0.3, 0.38), a=11 * wind - 13 * go, soft=0.4)
        head = dict(c=(0.22, 0.33), r=(0.16, 0.1), p=(0.3, 0.4), a=3 * wave(t, 2, phi_phase(1)) - 3 * go, soft=0.5)
        shadow(cv, left + dw * 0.45, H - 14, dw * 0.45, 6, 0.5)
        for i, (x, y) in enumerate(tiles):                                      # the blue lightning picks tiles on a pentagon, then drops
            on = sm(0.12 + i * 0.05, 0.2 + i * 0.05, u) * (1 - sm(0.8, 0.9, u))
            ring(cv, x, y, 12 + 2 * wave(t, 6), 0.3, 3, np.array((90.0, 130, 255)) * 0.6 * on)
            if on > 0.5 and 0.55 <= u < 0.8:
                bolt(cv, x + 8, 0, x, y, 1520 + i + int(t * FRAMES), (150, 190, 255), jag=30, size=1.3)
                glow(cv, x, y, 12, (90, 130, 255), 0.5)
        stand(cv, dusk, [wing, arm, head], left, top, (0.7 * wave(t, 1), -0.7), lean_bend(6 * wind - 12 * go, -5 * wind + 8 * go), 0.55)
        ex, ey = spot(dusk, head, 0.18, 0.31, left, top)
        glow(cv, ex + 6 * wind - 12 * go, ey, 3 + 2 * go, (255, 90, 30), 0.7 + 0.5 * go)
        if u >= 0.55:
            ring(cv, W / 2, 212, 10 + 130 * (u - 0.55) / 0.3, 0.28, 4, np.array((190.0, 200, 230)) * max(0.0, 1 - (u - 0.55) / 0.3))
            grit.draw(cv, min(0.99, (u - 0.55) / 0.4), lambda v: np.array((140, 136, 150)) * (1 - v), 1.5)
        return hit
    def paint_dawn(u, t, cv):
        wind, go, hit = beat(u, 0.5, 0.25, 0.2)
        beat_ = wave(t, 6)
        bob = 7 * wave(t, 2) - 5 * beat_
        left, top = (W - aw) // 2 + 2, 26 + bob
        wl = dict(c=(0.16, 0.25), r=(0.2, 0.3), p=(0.42, 0.28), a=-8 * beat_, soft=0.45)
        wr = dict(c=(0.84, 0.25), r=(0.2, 0.3), p=(0.58, 0.28), a=8 * beat_, soft=0.45)
        tail = dict(c=(0.5, 0.86), r=(0.1, 0.16), p=(0.5, 0.65), a=7 * wave(t, 2, 0.2), soft=0.5)
        shadow(cv, W / 2, 214, 70, 6, 0.4)
        stand(cv, dawn, [tail, wl, wr], left, top, (0.7 * wave(t, 1), -0.7), None, 0.5)
        cx, cy = left + aw * 0.5, top + ah * 0.42
        glow(cv, cx, cy, 6 + 10 * wind, (130, 80, 255), 0.7 + 0.9 * wind)
        stamp(cv, cx, cy, (235, 225, 255), 2 + 2 * wind)
        if u >= 0.5:                                                            # three orbs down to the roof, each landing with a ring
            for i in range(3):
                q = (u - 0.5 - i * 0.05) / 0.22
                if 0 <= q < 1:
                    x = cx + (-70 + 70 * i) * q; y = cy + (205 - cy) * q ** 1.4
                    glow(cv, x, y, 5, (150, 100, 255), 0.9); stamp(cv, x, y, (240, 230, 255), 2.2)
                elif 1 <= q < 1.9:
                    ring(cv, cx - 70 + 70 * i, 205, 6 + 32 * (q - 1), 0.3, 3, np.array((150.0, 110, 255)) * (1.9 - q))
        return hit
    def scene(t):
        p = t * 2; u = p % 1; k = int(p) % 2
        cv = feather(back * 0.92)
        hit = (paint_dawn if k else paint_dusk)(u, t, cv)
        cv += hit * 0.12 * np.array([120.0, 130, 170])
        if u < 0.05 or u > 0.95:                                                # stone dust covers the changeover
            f = 1 - min(u, 1 - u) / 0.05
            cv = cv * (1 - 0.65 * f) + np.array([90.0, 86, 96]) * (0.7 + 0.6 * noise(1531, 6, t)[..., None]) * 0.65 * f
        return np.clip(cv, 0, 255)
    return "guardians", "dusk and dawn", scene

# ---- the Royal Titans: fire pushes, ice pushes back ---------------------------------
def titans_fire_ice():
    fire, (fh, fw) = figure("Branda_the_Fire_Queen.png", 108, H - 24, exposure=0.8, gamma=0.95, sat=1.1)
    ice, (ih, iw) = figure("Eldric_the_Ice_King.png", 112, H - 30, exposure=0.95, gamma=0.9, sat=1.1)
    rock = fbm(1601, (26, 11, 5))
    lava = ramp([(44, 20, 20), (70, 30, 22), (92, 44, 26)], rock)
    frost = ramp([(22, 36, 54), (32, 52, 72), (50, 72, 92)], rock)
    seam = np.clip(1 - np.abs(fbm(1603, (24, 11, 5)) - 0.5) * 42, 0, 1)
    shards = [(x, y, 8 + 6 * ((i * 7) % 3), 44 + 22 * ((i * 5) % 4)) for i, (x, y) in enumerate(((130, 214), (150, 200), (176, 218), (200, 196), (118, 196)))]
    n1, n2 = noise(1605, 12, 0.0), noise(1607, 6, 0.0)
    embers = Motes(1611, 26, lambda r: (r.rand() * W, 212, 20 * (r.rand() - 0.5), -170 * (0.4 + r.rand()), 0))
    snow = Motes(1612, 26, lambda r: (r.rand() * W, -4, -20 * r.rand(), H * (0.5 + 0.5 * r.rand()), 0))
    steam = Motes(1613, 26, lambda r: (0, 0, 80 * (r.rand() - 0.5), -130 * (0.3 + r.rand()), 0))
    STOPS_F = [(255, 245, 200), (255, 170, 50), (220, 70, 20), (90, 24, 14)]
    STOPS_I = [(250, 255, 255), (180, 225, 255), (90, 160, 225), (34, 70, 120)]
    def scene(t):
        # the front between them sways: first her fire drives it right, then his cold drives it back
        front_x = W / 2 + 34 * wave(t, 1, 0.0)
        push_f, push_i = max(0.0, math.cos(2 * math.pi * t)), max(0.0, -math.cos(2 * math.pi * t))
        n = noise(1605, 12, t)
        edge = front_x + (n - 0.5) * 34 + 7 * np.sin(YY * 0.07 + 2 * math.pi * t)
        hot = np.clip((edge - XX) / 3, 0, 1)[..., None]
        lit_f = lava + (seam * (0.45 + 0.35 * wave(t, 3)))[..., None] * np.array([100.0, 34, 8])
        cv = feather(lit_f * hot + frost * (1 - hot))
        embers.draw(cv, t, lambda v: np.array((255, 130, 40)) * math.sin(math.pi * v) * 0.8, 1.0)
        snow.draw(cv, (t * 2) % 1, lambda v: (150, 190, 225), 0.9)
        for i, (x, y, wd, tall) in enumerate(shards):
            grown = sm(0.0, 0.25, 1 - abs(((t + 0.5 - i * 0.03) % 1) - 0.5) * 2) if x > front_x else 0.0
            spike(cv, x, y, wd, tall * grown, (70, 120, 170), (210, 240, 255))
        lf, tf = 2, H - 12 - fh
        li, ti = W - iw - 2, H - 12 - ih
        fa = dict(c=(0.2, 0.3), r=(0.26, 0.32), p=(0.35, 0.32), a=-6 * push_f + 3 * wave(t, 3), soft=0.4)
        fh_ = dict(c=(0.55, 0.13), r=(0.14, 0.13), p=(0.55, 0.26), a=3 * wave(t, 2, 0.3), soft=0.5)
        ia = dict(c=(0.12, 0.3), r=(0.2, 0.34), p=(0.28, 0.33), a=-7 * push_i + 3 * wave(t, 3, 0.4), soft=0.4)
        ih_ = dict(c=(0.57, 0.1), r=(0.17, 0.12), p=(0.57, 0.24), a=3 * wave(t, 2, 0.7), soft=0.5)
        ior = dict(c=(0.87, 0.55), r=(0.13, 0.22), p=(0.82, 0.3), a=4 * wave(t, 2), soft=0.5)
        fro = dict(c=(0.85, 0.5), r=(0.14, 0.2), p=(0.78, 0.33), a=-5 * wave(t, 2, 0.1), soft=0.5)
        shadow(cv, lf + fw * 0.5, H - 13, fw * 0.5, 5, 0.5); shadow(cv, li + iw * 0.5, H - 13, iw * 0.5, 5, 0.5)
        stand(cv, ice, [ior, ia, ih_], li, ti, (-0.7 * wave(t, 1), -0.7), lean_bend(-5 * push_i, 0))
        stand(cv, fire, [fro, fa, fh_], lf, tf, (0.7 * wave(t, 1), -0.7), lean_bend(5 * push_f, 0))
        fx, fy = spot(fire, fa, 0.14, 0.05, lf, tf); fx += 5 * push_f
        ix, iy = spot(ice, ia, 0.06, 0.05, li, ti); ix -= 5 * push_i
        glow(cv, fx, fy, 7 + 6 * push_f, (255, 130, 30), 0.7 + 0.6 * push_f)
        glow(cv, ix, iy, 7 + 6 * push_i, (120, 200, 255), 0.7 + 0.6 * push_i)
        ang_f = math.atan2(120 - fy, front_x - fx); ang_i = math.atan2(120 - iy, front_x - ix)
        # each breath reaches the front and stops there
        if push_f > 0.02: plume(cv, fx, fy, math.cos(ang_f), math.sin(ang_f), max(40, front_x - fx + 8), 26, (t * 5) % 1, STOPS_F, 1615, min(1.0, push_f * 1.6), wobble=0.15)
        if push_i > 0.02: plume(cv, ix, iy, math.cos(ang_i), math.sin(ang_i), max(40, ix - front_x + 8), 26, (t * 5) % 1, STOPS_I, 1617, min(1.0, push_i * 1.6), wobble=0.15)
        # steam where they meet
        for t0, (_, _, vx, vy, _) in steam.p:
            v = (t * 3 + t0) % 1
            stamp(cv, front_x + vx * v, 120 + 50 * math.sin(t0 * 19) + vy * v, np.array((225, 230, 235)) * (1 - v) * 0.85, 2.6)
        return np.clip(cv, 0, 255)
    return "titans", "fire and ice", scene

# ---- the Maggot King: the floor is alive --------------------------------------------
def rot_backdrop():
    """a rotting hollow: wet flesh-coloured walls with ribs across them, a sump in the floor"""
    tex = fbm(1701, (30, 12, 5))
    img = ramp([(30, 18, 18), (56, 28, 24), (76, 44, 30)], tex).copy()
    for i in range(7):
        x = 14 + i * 32
        arc = np.abs(XX - x - 10 * np.sin(YY * 0.03 + i)) < 5 - YY * 0.012
        img = img * (1 - (arc * 0.45 * (YY < 170))[..., None]) + (arc * (YY < 170) * 0.45)[..., None] * np.array([84.0, 72, 58]) * (0.6 + 0.6 * tex[..., None])
    return ground(img, 176, (54, 42, 26), 1703)

def maggot(cv, x, y, a, size, colour, phase):
    """a fat grub in segments, its body in a travelling wave"""
    for i in range(6):
        d = (i - 2.5) * size * 1.1
        off = math.sin(i * 1.1 + phase) * size * 0.6
        px, py = x + d * math.cos(a) - off * math.sin(a), y + d * math.sin(a) * 0.6 + off * math.cos(a) * 0.6
        stamp(cv, px, py, np.array(colour, float) * (0.78 + 0.22 * (i % 2)), size * (1.15 if 1 < i < 4 else 0.9))

def maggot_crawl():
    rig, (h, w) = figure("Maggot_King.png", W + 8, H - 34, exposure=0.8, gamma=0.92, sat=1.1)
    back = rot_backdrop()
    r = np.random.RandomState(1711)
    # the grubs come out on golden-angle steps from under him
    crawl = [(phyllotaxis(W / 2, 204, 34, 15, squash=0.3)[i], r.rand(), r.rand() * 6.28) for i in range(34)]
    falls = Motes(1713, 12, lambda q: (60 + q.rand() * 130, 80 + 50 * q.rand(), 16 * (q.rand() - 0.5), 0, 150))
    flies = [(r.rand() * W, 30 + r.rand() * 120, r.rand(), 10 + 24 * r.rand()) for _ in range(18)]
    def scene(t):
        cv = feather(back * (0.86 + 0.1 * wave(t, 2)))
        for x, y, ph, rad in flies:
            a = 2 * math.pi * (t * 2 + ph)
            stamp(cv, x + rad * math.cos(a), y + rad * 0.5 * math.sin(2 * a), (70, 66, 52), 0.9)
        heave = wave(t, 2)
        left, top = -6, H - 14 - h
        crown = dict(c=(0.1, 0.22), r=(0.13, 0.26), p=(0.14, 0.45), a=5 * wave(t, 2, 0.2), soft=0.5)
        wing_a = dict(c=(0.72, 0.28), r=(0.3, 0.3), p=(0.42, 0.45), a=-7 * wave(t, 3), soft=0.4)
        wing_b = dict(c=(0.72, 0.72), r=(0.3, 0.26), p=(0.42, 0.55), a=7 * wave(t, 3, 0.08), soft=0.4)
        gut = dict(c=(0.2, 0.55), r=(0.14, 0.18), p=(0.2, 0.55), d=(0, -0.012 * heave), soft=0.9)
        shadow(cv, W / 2, 210, 96, 12, 0.55)
        for i, ((x, y), ph, a) in enumerate(crawl):                             # behind him first, so he stands among them
            if y < 200:
                k = (t * 2 + ph) % 1
                maggot(cv, x + 5 * math.cos(a) * math.sin(2 * math.pi * t), y, a, 1.7, (205, 186, 130), 2 * math.pi * (t * 4 + ph))
        stand(cv, rig, [wing_b, wing_a, gut, crown], left, top, (0.7 * wave(t, 1), -0.6), lambda x, y: (-5 * heave * up_of(y, 0.8) * np.clip(1 - x, 0, 1), -3 * heave * up_of(y, 0.8)), 0.55)
        for i, ((x, y), ph, a) in enumerate(crawl):
            if y >= 200:
                maggot(cv, x + 5 * math.cos(a) * math.sin(2 * math.pi * t), y, a, 2.2, (232, 214, 152), 2 * math.pi * (t * 4 + ph))
        for t0, (x, y, vx, _, g) in falls.p:                                    # more drop out of him and burst on the floor
            v = (t * 2 + t0) % 1
            yy = y + g * v * v
            if yy < 204: maggot(cv, x + vx * v, yy, 1.57 + 4 * v, 1.4, (222, 204, 142), 8 * v)
            else: ring(cv, x + vx * v, 204, 3 + 10 * (v - 0.6), 0.35, 2, np.array((150, 170, 70)) * 0.5)
        return np.clip(cv, 0, 255)
    return "maggot", "maggots", scene

# ---- Obor: the club comes down -----------------------------------------------------
def hill_cave_backdrop():
    """Obor's lair under Edgeville: rough strata, a barred gate behind him, the torch that lights him"""
    tex = fbm(1801, (28, 11, 5))
    strata = 0.5 + 0.5 * np.sin(YY * 0.24 + fbm(1803, (30, 12)) * 10)
    img = np.array([56.0, 46, 36]) * (0.55 + 0.35 * strata[..., None] + 0.4 * tex[..., None])
    gate = ((np.abs(XX - W / 2) < 56) & (YY > 30 + (np.abs(XX - W / 2) / 56) ** 2 * 24))[..., None]
    img = img * (1 - gate) + np.array([20.0, 18, 18]) * gate
    bars = (((XX - W / 2 + 56) % 16 < 3) & (np.abs(XX - W / 2) < 56) & (YY > 30 + (np.abs(XX - W / 2) / 56) ** 2 * 24))[..., None]
    img = img * (1 - bars) + np.array([52.0, 50, 50]) * bars
    return ground(img, 188, (60, 50, 38), 1805)

def obor_slam():
    rig, (h, w) = figure("Obor.png", W - 60, H - 16, exposure=0.68, gamma=1.08, sat=1.0)
    back = hill_cave_backdrop()
    rocks = debris(1811, 26, W * 0.26, 214, 200, 130)
    dust = Motes(1813, 20, lambda r: (r.rand() * W, 8, 14 * (r.rand() - 0.5), 180 + 60 * r.rand(), 0))
    def crack_mask(x, y):
        im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
        r = np.random.RandomState(1815)
        for _ in range(9):
            px, py = x, y; a = r.rand() * 2 * math.pi
            for s in range(7):
                a += (r.rand() - 0.5) * 1.1
                nx, ny = px + 22 * math.cos(a), py + 5.5 * math.sin(a)
                d.line((px, py, nx, ny), fill=255, width=1); px, py = nx, ny
        return np.asarray(im, float)[..., None] / 255
    IMPACT = (W * 0.2, 216)
    cracks = crack_mask(*IMPACT)
    def scene(t):
        p = t * 2; u = p % 1
        wind, go, hit = beat(u, 0.55, 0.4, 0.3)
        cv = back * (0.84 + 0.1 * wave(t, 7, 0.2) * 0.2 + 0.3 * hit)
        cv += np.exp(-((XX - 180) / 70) ** 2 - ((YY - 60) / 60) ** 2)[..., None] * np.array([40.0, 22, 6]) * (0.8 + 0.2 * wave(t, 7))
        cv = feather(cv)
        glow(cv, 188, 58, 5, (255, 160, 60), 0.8 + 0.2 * wave(t, 9))
        lean = 12 * wind - 22 * go
        drop = -8 * wind + 14 * go
        left, top = (W - w) // 2 + 14, H - 10 - h
        club = dict(c=(0.12, 0.42), r=(0.22, 0.32), p=(0.17, 0.2), a=15 * wind - 14 * go, soft=0.35)
        off = dict(c=(0.88, 0.42), r=(0.15, 0.27), p=(0.82, 0.2), a=-7 * wind + 7 * go + 2 * wave(t, 2), soft=0.4)
        head = dict(c=(0.47, 0.08), r=(0.17, 0.1), p=(0.47, 0.17), a=3 * wave(t, 2, phi_phase(1)) - 5 * wind + 5 * go, soft=0.5)
        chest = dict(c=(0.5, 0.26), r=(0.2, 0.14), p=(0.5, 0.26), d=(0, -0.012 * wave(t, 2)), soft=0.9)
        def body(x, y):
            v = up_of(y, 0.6)
            return -lean * v, -drop * v
        shadow(cv, left + w * 0.5, H - 10, w * 0.55, 6, 0.55)
        if u >= 0.55:
            cv *= 1 - cracks * min(1.0, (1 - u) * 3) * 0.8
            cv += cracks * np.array([150.0, 76, 24]) * math.exp(-(u - 0.55) * 7)
        stand(cv, rig, [chest, off, club, head], left, top, (0.7 * wave(t, 1) + 0.3, -0.7), body, 0.5)
        if 0.53 <= u < 0.68:
            f = (u - 0.53) / 0.15
            arc_slash(cv, W * 0.5, 138, 92, -2.0, -3.3, np.array((255.0, 228, 180)) * (1 - f), 3.2, 1.0, min(1.0, f * 3))
        if u >= 0.55:
            f = min(1.0, (u - 0.55) / 0.32)
            for i in range(3):
                ring(cv, IMPACT[0], IMPACT[1], 8 + (150 - 34 * i) * f, 0.26, 4 - i, np.array((230.0, 190, 130)) * (1 - f) ** 1.3)
            rocks.draw(cv, min(0.99, f), lambda v: np.array((150, 130, 104)) * (1 - v * 0.6), 2.0)
            dust.draw(cv, f, lambda v: np.array((130, 110, 88)) * (1 - v), 1.3)
        return np.clip(shake(cv, 3 * hit * wave(t, 18), 4.5 * hit * wave(t, 15)), 0, 255)
    return "obor", "the slam", scene

RECIPES = [brutus_charge, madangel_hover, guardians_dusk, titans_fire_ice, maggot_crawl, obor_slam]
