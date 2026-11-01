"""Hi-res recipes, batch 2a: Kalphite Queen, Kraken, Leviathan, Sarachnis, Thermy, Abyssal Sire."""
from hires_kit import *
from hires_kit import _layer
from hires_batch1b import sm, since, mask_of, tendrils, soften, close_seams

# ---- shared helpers ------------------------------------------------------------
def opaque(name, thr=128):
    """The sprite cut at an alpha threshold: ground shadows and translucent wings stay out of the rig."""
    a = np.asarray(Image.open(SPR / name).convert("RGBA"), float).copy()
    mask = a[..., 3] > thr
    ys, xs = np.where(mask)
    a[..., 3] = mask * 255.0
    return Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.uint8), "RGBA")

def land(rig, pt, bend, n=5):
    x, y = pt
    if bend is None: return x, y
    px, py = x, y
    for _ in range(n):
        bx, by = bend(px / rig.w, py / rig.h)
        px, py = x - float(bx), y - float(by)
    return px, py

def own_lum(rig):
    """the sprite's own brightness, 0..1, for ramps that keep the form"""
    b = rig.rgb.mean(2); on = rig.al[..., 0] > 0
    lo, hi = np.percentile(b[on], [4, 96])
    return np.clip((b - lo) / max(hi - lo, 1), 0, 1)

def rim_of(rig, size=5):
    e = Image.fromarray((rig.al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(size))
    return (rig.al[..., 0] - np.asarray(e, float) / 255)[..., None]

def layer(rgb, al, x, y):
    """a posed sprite as a canvas-sized colour and alpha"""
    C, A = np.zeros((H, W, 3)), np.zeros((H, W, 1))
    h, w = al.shape[:2]
    x0, y0, x1, y1 = max(0, x), max(0, y), min(W, x + w), min(H, y + h)
    if x0 < x1 and y0 < y1:
        s = (slice(y0 - y, y1 - y), slice(x0 - x, x1 - x))
        C[y0:y1, x0:x1] = rgb[s]; A[y0:y1, x0:x1] = al[s]
    return C, A

def shake(cv, ox, oy):
    return cv[np.clip(np.arange(H) - int(round(oy)), 0, H - 1)][:, np.clip(np.arange(W) - int(round(ox)), 0, W - 1)]

def climb(seed, cell, t, k=1, kx=0, warp=0.0, warp_seed=None):
    """noise scrolled up the frame, optionally dragged sideways by slower noise; k whole screens a lap"""
    n = _layer(seed, cell); yi, xi = YY.astype(int), XX.astype(int)
    if warp:
        w = noise(warp_seed or seed + 50, 30, t) - 0.5
        xi = xi + (w * warp).astype(int); yi = yi + (w * warp * 0.5).astype(int)
    return n[(yi + int(t * H * k)) % H, (xi + int(t * W * kx)) % W]

def blade(cv, root, deg, length, width, colour, alpha):
    """a translucent wing: a lens from the root, with a bright leading edge and veins"""
    a = math.radians(deg); ux, uy = math.cos(a), math.sin(a); nx, ny = -uy, ux
    pts = []
    for s in np.linspace(0, 1, 14): pts.append((root[0] + ux * length * s + nx * width * math.sin(math.pi * s ** 0.8), root[1] + uy * length * s + ny * width * math.sin(math.pi * s ** 0.8)))
    for s in np.linspace(1, 0, 14): pts.append((root[0] + ux * length * s - nx * width * 0.55 * math.sin(math.pi * s ** 0.8), root[1] + uy * length * s - ny * width * 0.55 * math.sin(math.pi * s ** 0.8)))
    m = mask_of(lambda d: d.polygon(pts, fill=255))[..., None]
    along = ((XX - root[0]) * ux + (YY - root[1]) * uy) / length
    col = ramp([(255, 150, 215), (214, 70, 170), (120, 40, 130)], np.clip(along, 0, 1))
    across = ((XX - root[0]) * nx + (YY - root[1]) * ny)
    vein = (np.abs(np.sin(across * 0.55 + along * 3)) < 0.14)[..., None]
    cv[:] = cv * (1 - m * alpha) + (col[..., :3] * (1 + 0.35 * vein) * np.array(colour, float)) * m * alpha

# ---- Kalphite Queen ------------------------------------------------------------
def hive_backdrop():
    """the Kalphite Lair: hexagonal chitin cells, cold and dim, so orange and green both stand off it"""
    edge = hex_mask(19, W * 0.47, 108, width=2.6, squash=0.92)
    tex = fbm(1101, (22, 9, 4))[..., None]
    depth = np.exp(-((XX - W * 0.47) / 112) ** 2 - ((YY - 108) / 96) ** 2)[..., None]
    img = np.array([28.0, 30, 48]) * (0.55 + 0.65 * tex) * (1.25 - 0.55 * depth)
    img = img * (1 - 0.55 * edge[..., None]) + edge[..., None] * np.array([60.0, 62, 86]) * (0.7 + 0.6 * tex)
    floor = np.clip((YY - 196 + 6 * (fbm(1110, (24, 9))[0, :][None, :] - 0.5)) / 4, 0, 1)[..., None]
    return img * (1 - floor) + floor * np.array([44.0, 40, 50]) * (0.6 + 0.7 * fbm(1115, (14, 5, 2))[..., None])

def kq_molt():
    # one idea: the armoured queen cracks along her seams, bursts, and the winged form tears out; the shell comes flying home to close the loop
    rA, aA = fit(opaque("Kalphite_Queen.png"), W - 2, H - 22, exposure=0.92, gamma=0.82, sat=1.1, grain=0.45)
    rB, aB = fit(opaque("Kalphite_Queen_2nd_form.png", 215), W - 4, H - 14, exposure=0.6, gamma=1.12, sat=1.0, grain=0.35)
    rB, aB = soften(close_seams(rB, 3), aB, 0.12, 1.4, 0.42)
    hA, wA = aA.shape[:2]; hB, wB = aB.shape[:2]
    gA = H - 12; lA, tA = (W - wA) // 2, gA - hA
    lB, tB = (W - wB) // 2 + 2, (H - hB) // 2 - 2
    A = Rig(rA, aA); B = Rig(rB, aB)
    lumA, lumB = own_lum(A), own_lum(B)
    baseA, baseB = A.rgb.copy(), B.rgb.copy()
    hotA = ramp([(90, 28, 6), (225, 92, 16), (255, 190, 70), (255, 245, 205)], lumA)[..., :3]
    seamsA = (np.clip((0.42 - lumA) / 0.3, 0, 1) * (A.al[..., 0] > 0))[..., None]
    freshB = ramp([(120, 46, 66), (235, 120, 110), (255, 200, 170), (255, 245, 230)], lumB)[..., :3]
    back = hive_backdrop()
    r = np.random.RandomState(1120)
    cx, cy = lA + 0.45 * wA, tA + 0.52 * hA
    # shards leave in golden-angle order, so they spread evenly without a visible pattern
    shards = []
    for i in range(30):
        a = i * GOLDEN_ANGLE
        shards.append((a, 46 + 96 * r.rand(), 3 + 4.5 * r.rand(), r.rand() * 6.28, (r.rand() - 0.5) * 16, 0.92 + 0.3 * r.rand()))
    field = noise(1125, 16, 0.0)
    dust = Motes(1130, 22, lambda r_: (r_.rand() * W, 200 - r_.rand() * 150, 12 * (r_.rand() - 0.5), -26 * r_.rand(), 0))
    sparks = Motes(1131, 40, lambda r_: (0, 0, (r_.rand() - 0.5) * 210, (r_.rand() - 0.65) * 190, 90))
    crack_at, burst, close_at, shut = 0.24, 0.42, 0.76, 0.93
    distA = np.hypot((XX - cx) / 90, (YY - cy) / 90)
    def scene(t):
        charge = sm(crack_at, burst, t) * (1 - sm(burst, burst + 0.05, t)) + sm(close_at, shut, t) * (1 - sm(shut, 0.985, t)) * 0.8
        m = sm(burst - 0.01, burst + 0.09, t) * (1 - sm(close_at + 0.09, shut, t))      # 0 armoured, 1 winged
        hit = math.exp(-since(t, burst) * 9)
        clang = math.exp(-since(t, shut) * 10)
        winged = sm(burst + 0.02, burst + 0.12, t) * (1 - sm(close_at, close_at + 0.1, t))
        # the wall: a lit front crosses the cells on every change of form
        cv = back * (0.72 + 0.16 * charge + 0.1 * winged)
        cells = hex_mask(19, W * 0.47, 108, width=2.6, squash=0.92)
        d_ = np.hypot(XX - cx, YY - cy)
        for s_, col, sp in ((since(t, burst), np.array([255.0, 170, 70]), 620), (since(t, shut), np.array([150.0, 215, 90]), 620)):
            if s_ < 0.32:
                front = np.exp(-((d_ - s_ * sp) / 26) ** 2) * (1 - s_ / 0.32)
                cv += (cells * front)[..., None] * col * 1.1 + (front * 0.12)[..., None] * col
        cv += (cells * charge * np.exp(-(d_ / 90) ** 2))[..., None] * np.array([120.0, 62, 14]) * (0.6 + 0.4 * wave(t, 8))
        cv = feather(cv)
        dust.draw(cv, t, lambda v: np.array((110, 110, 140)) * math.sin(math.pi * v) * 0.5, 0.9)
        shudder = charge * wave(t, 13)
        # ---- the armoured form: planted on its claws, curling in as it cracks ----
        curl = sm(crack_at - 0.1, burst, t) * (1 - sm(burst, burst + 0.03, t)) + sm(close_at + 0.04, shut, t) * (1 - sm(shut, shut + 0.05, t))
        def bendA(x, y):
            feet = np.clip((0.82 - y) / 0.5, 0, 1)
            near = np.exp(-((x - 0.46) / 0.28) ** 2)
            walk = 0.0
            for tx, ty, ph in ((0.06, 0.62, 0.0), (0.21, 0.66, 0.31), (0.86, 0.72, 0.62), (0.97, 0.56, 0.18)):
                walk = walk + 8 * max(0.0, wave(t, 2, ph)) ** 1.5 * (1 - curl) * np.exp(-((x - tx) / 0.1) ** 2 - ((y - ty) / 0.16) ** 2)
            return 2.5 * shudder * near * feet, -(16 * curl - 3 * wave(t, 2)) * near * feet + walk
        partsA = [
            dict(c=(0.55, 0.40), r=(0.20, 0.17), p=(0.55, 0.40), d=(0, -0.012 * wave(t, 2)), soft=0.9),
            dict(c=(0.56, 0.10), r=(0.20, 0.13), p=(0.53, 0.26), a=6 * wave(t, 1, phi_phase(1)) - 9 * curl, soft=0.5),             # abdomen
            dict(c=(0.47, 0.33), r=(0.13, 0.30), p=(0.40, 0.62), a=4 * wave(t, 2, phi_phase(2)) + 7 * curl, soft=0.4),              # antennae
            dict(c=(0.38, 0.70), r=(0.13, 0.14), p=(0.46, 0.60), a=3 * wave(t, 2, phi_phase(3)) + 6 * curl, lit=0.15 * charge),     # head
        ]
        A.rgb = np.clip(baseA * (1 - 0.55 * charge) + seamsA * hotA * 2.2 * charge + hotA * 0.4 * charge * charge + np.array([60.0, 30, 6]) * charge * 0.3 * (A.al > 0), 0, 255)
        ra, al_a = A.pose(partsA, light=(0.8 * wave(t, 1), -0.6), relief=0.3, bend=bendA)
        # ---- the winged form: hovering, so here the whole body may ride the beat ----
        hover = 5 * wave(t, 4) * winged
        def bendB(x, y):
            legs = np.clip((y - 0.55) / 0.4, 0, 1)
            spikes = np.clip((0.3 - y) / 0.25, 0, 1)
            return 9 * legs ** 1.5 * wave(t, 2, 0.2) + 3 * spikes * wave(t, 4, 0.1), 6 * legs * wave(t, 3, 0.4) - hover + 18 * (1 - winged)
        partsB = [
            dict(c=(0.26, 0.42), r=(0.20, 0.14), p=(0.38, 0.42), a=6 * wave(t, 2, phi_phase(1)), soft=0.5),                         # head and mane
            dict(c=(0.72, 0.42), r=(0.20, 0.15), p=(0.58, 0.45), a=-6 * wave(t, 4, phi_phase(2)), soft=0.5),                        # abdomen
            dict(c=(0.42, 0.78), r=(0.26, 0.20), p=(0.42, 0.58), a=5 * wave(t, 2, phi_phase(3)), soft=0.4),                         # hanging legs
        ]
        B.rgb = np.clip(baseB * m + freshB * (1 - m) + np.array([180.0, 90, 60]) * hit * 0.4 * (B.al > 0), 0, 255)
        rb, al_b = B.pose(partsB, light=(0.8 * wave(t, 1, 0.3), -0.6), relief=0.3, bend=bendB)
        if m < 0.99:
            sh = np.clip(1 - ((XX - W / 2) / (wA * 0.5)) ** 2 - ((YY - gA + 2) / 7) ** 2, 0, 1)[..., None]
            cv *= 1 - 0.5 * sh * (1 - m)
        if m > 0.01:
            sh = np.clip(1 - ((XX - W / 2) / 70) ** 2 - ((YY - 212) / 6) ** 2, 0, 1)[..., None]
            cv *= 1 - 0.4 * sh * m
        CA, MA = layer(ra, al_a, lA - A.pad, tA - A.pad)
        CB, MB = layer(rb, al_b, lB - B.pad, tB - B.pad)
        # the wings beat fast and smear; they sit behind the body
        if winged > 0.02:
            lr = land(B, (0.33 * wB + B.pad * 0 + 0, 0.31 * hB), bendB)
            rr = land(B, (0.68 * wB, 0.52 * hB), bendB)
            for off, al_ in ((-1, 0.26), (1, 0.26), (0, 0.5)):
                beat = wave(t, 8, 0.08 * off)
                blade(cv, (lB + lr[0], tB + lr[1]), -128 + 26 * beat, 124 * winged, 24, (1, 1, 1), al_ * winged * 0.62)
                blade(cv, (lB + rr[0], tB + rr[1]), 48 - 26 * beat, 110 * winged, 22, (1, 1, 1), al_ * winged * 0.62)
        # the change itself: a burning edge eats out from her middle through the shell, and back
        eat = m * 2.0 - 0.5
        n = noise(1125, 12, 0.0) * 0.7
        thresh = distA * 0.65 + n
        show_b = np.clip((eat - thresh) * 7, 0, 1)[..., None]
        edge = (np.exp(-((eat - thresh) / 0.09) ** 2) * (0.02 < m) * (m < 0.98))[..., None]
        al_now = MA * (1 - show_b) + MB * show_b
        col_now = CA * (1 - show_b) + CB * show_b
        cv = cv * (1 - al_now) + col_now * al_now
        cv += edge * np.maximum(MA, MB) * np.array([255.0, 190, 90]) * 1.1
        if charge > 0.02:
            glow(cv, cx, cy, 16 + 28 * charge, (255, 130, 30), 0.34 * charge)
        # shards: outward at the burst, home again along the same paths at the close
        out = since(t, burst); home = (t - close_at) / (shut - close_at) if close_at <= t < shut else None
        if out < 0.3 or home is not None:
            v = (out / 0.3) if home is None else 1 - home
            v = 1 - (1 - v) ** 2
            for a, rad, size, spin, vy, _ in shards:
                x, y = cx + math.cos(a) * rad * v, cy + math.sin(a) * rad * 0.8 * v + (60 * v * v if home is None else 0)
                ang = spin + v * 5
                p = [(x + size * 1.5 * math.cos(ang + k * 2.1) * (1 + 0.4 * (k == 0)), y + size * math.sin(ang + k * 2.1)) for k in range(3)]
                ma = mask_of(lambda d: d.polygon(p, fill=255))[..., None]
                f = 1.0 if home is not None else 1 - sm(0.2, 0.3, out)
                cv[:] = cv * (1 - ma * f) + np.array([138.0, 172, 66]) * (0.75 + 0.5 * math.sin(ang)) * ma * f
        if hit > 0.05:
            glow(cv, cx, cy, 22 + 70 * (1 - hit), (255, 205, 120), 1.0 * hit)
            sparks.draw(cv, min(0.99, since(t, burst) * 3), lambda v: np.array((255, 190, 90)) * (1 - v), 1.6)
        if clang > 0.05:
            glow(cv, cx, cy, 44, (150, 215, 90), 0.5 * clang)
        s_ = hit * 3.5 + clang * 2.5
        return np.clip(shake(cv, s_ * math.sin(t * 480), s_ * 0.6 * math.cos(t * 610)), 0, 255)
    return "kq", "the molt", scene

# ---- Kraken --------------------------------------------------------------------
def kraken_backdrop(wl):
    """the Kraken Cove: a black lake, cold rock closing in overhead"""
    sky = ramp([(14, 20, 34), (22, 34, 52), (30, 48, 66)], np.clip(YY / wl, 0, 1)) * (0.7 + 0.6 * fbm(1201, (34, 14, 6))[..., None])
    drip = 10 + 26 * np.abs(np.sin(XX * 0.14 + 2.1 * np.sin(XX * 0.043))) ** 3 + 14 * fbm(1205, (14, 6))[0, :][None, :]
    top = (YY < drip)[..., None]
    img = sky * (1 - top) + np.array([6.0, 8, 14]) * top
    for side, x0 in ((-1, -8), (1, W + 8)):
        edge = 64 + 34 * fbm(1210 + side, (20, 8))[:, 0][:, None]
        m = np.clip((edge - np.abs(XX - x0) + 14 * np.clip(YY / wl, 0, 1) - 14) / 2, 0, 1)[..., None]
        img = img * (1 - m) + np.array([30.0, 38, 52]) * (0.5 + 0.9 * fbm(1215, (10, 4, 2))[..., None]) * m
    return img

def kraken_rise():
    # one idea: black water. Four tentacles break it one by one, then the whole body heaves up through the surface; water does the composing
    rgb0, al0 = fit(sprite("Kraken.png"), W - 4, 170, exposure=0.86, gamma=0.95, sat=1.1, grain=0.5)
    h, w = al0.shape[:2]; left = (W - w) // 2
    wl = 152                                                                  # the waterline never moves
    rig = Rig(rgb0, al0, pad=40)
    lum = own_lum(rig)
    dry = ramp([(70, 50, 8), (150, 116, 16), (222, 186, 40), (255, 240, 150)], lum)[..., :3] * 0.72 + rig.rgb * 0.28
    wet = ramp([(6, 28, 40), (14, 70, 86), (70, 150, 150), (214, 240, 214)], lum)[..., :3]
    back = kraken_backdrop(wl)
    water = (YY > wl)[..., None]
    depth = np.clip((YY - wl) / (H - wl), 0, 1)
    pools = [(26, wl + 10, 0.06, 0.8), (W - 26, wl + 7, 0.13, 0.75), (58, wl + 36, 0.20, 1.1), (W - 54, wl + 40, 0.27, 1.2)]
    cx = W / 2
    fx, fy = 0.42, 0.61                                                       # the mouth
    spray = Motes(1230, 46, lambda r: (cx + (r.rand() - 0.5) * 120, wl, (r.rand() - 0.5) * 90, -70 - 130 * r.rand(), 240))
    falling = Motes(1231, 22, lambda r: (r.rand() * W, 40 + r.rand() * 20, 0, 90, 60))
    surface_at, blast_at, sink_at = 0.32, 0.60, 0.80
    def scene(t):
        rise = sm(surface_at, surface_at + 0.13, t) * (1 - sm(sink_at, 0.97, t))
        overshoot = math.exp(-since(t, surface_at + 0.13) * 11) * math.sin(since(t, surface_at + 0.13) * 30) if t >= surface_at else 0.0
        burst = math.exp(-since(t, surface_at + 0.03) * 7) if surface_at + 0.03 <= t < 0.95 else 0.0
        heave = wave(t, 3, 0.1) * rise
        fire = math.exp(-since(t, blast_at) * 9) if t >= blast_at - 0.0 else 0.0
        charge = sm(blast_at - 0.14, blast_at, t) * (t < blast_at)
        y_top = 12 + (1 - rise) * (wl - 12 + 8) - 7 * overshoot - 2.5 * heave
        top = int(y_top)
        # ---- behind the surface ----
        lit = 0.7 + 0.25 * rise + 0.45 * fire
        cv = back * lit
        n = noise(1240, 16, t)
        # a cold shimmer on the roof, thrown up off the water
        caust = np.clip(1 - np.abs(noise(1245, 9, t) - 0.5) / 0.05, 0, 1) * np.clip(1 - np.abs(YY - 70) / 70, 0, 1) * (YY < wl)
        cv += caust[..., None] * np.array([12.0, 26, 32]) * (0.6 + 0.6 * rise + 1.2 * fire)
        # the water plane: black, with long horizontal glints
        planes = wl + (H - wl) * np.clip(depth, 0, 1)
        glint = np.clip(np.sin(np.maximum(YY - wl, 0) ** 1.35 * 0.55 + (noise(1250, 22, t) - 0.5) * 9 + 2 * math.pi * t * 2), 0, 1) ** 6
        lake = np.array([6.0, 12, 20]) + np.array([24.0, 46, 58]) * glint[..., None] * (0.25 + 0.75 * depth[..., None]) * (0.5 + n[..., None])
        # rings spread out across it from wherever something breaks the surface
        for i in range(5):
            r_ = ((t * 3 + i / 5) % 1) * 150
            ring(lake, cx, wl + 26, r_, 0.2, 3.2, np.array([70.0, 110, 120]) * (1 - r_ / 150) * (0.25 + 0.75 * rise))
        # the whirlpool: only while he is below, a pale foam gyre lying flat on the water
        gyre = phi_field(cx, wl + 26, t, arms=3, spin=2, squash=0.22, sharp=3.0)
        gmask = np.clip(1 - np.hypot(XX - cx, (YY - wl - 26) / 0.22) / 96, 0, 1) * (1 - sm(surface_at - 0.1, surface_at + 0.05, t) + sm(0.88, 0.99, t))
        lake += (gyre * gmask)[..., None] * np.array([120.0, 178, 190])
        hole = np.clip(1 - np.hypot(XX - cx, (YY - wl - 26) / 0.22) / 22, 0, 1)[..., None] * (1 - rise)
        lake *= 1 - 0.8 * hole
        # ---- his body and arms ----
        arm_g = 0.5 + 0.5 * rise
        parts = [
            dict(c=(0.50, 0.40), r=(0.26, 0.20), p=(0.50, 0.50), d=(0, -0.012 * heave), soft=0.9),
            dict(c=(0.07, 0.25), r=(0.14, 0.26), p=(0.20, 0.50), a=-11 * wave(t, 2, phi_phase(1)) * arm_g, soft=0.35),          # four arms, each on its own clock
            dict(c=(0.30, 0.14), r=(0.13, 0.27), p=(0.36, 0.38), a=8 * wave(t, 3, phi_phase(2)) * arm_g, soft=0.35),
            dict(c=(0.68, 0.14), r=(0.13, 0.27), p=(0.62, 0.38), a=-8 * wave(t, 2, phi_phase(3)) * arm_g, soft=0.35),
            dict(c=(0.92, 0.30), r=(0.16, 0.30), p=(0.78, 0.60), a=11 * wave(t, 3, phi_phase(4)) * arm_g, soft=0.35),
            dict(c=(0.72, 0.50), r=(0.12, 0.25), p=(0.68, 0.68), a=7 * wave(t, 2, phi_phase(5)) * arm_g, soft=0.4),
            dict(c=(fx, fy), r=(0.10, 0.12), p=(fx, fy), soft=0.8, d=(0, 0.008 * charge - 0.012 * fire)),
        ]
        # the water runs off him from the top down
        ytop_s = (rig.yy - rig.pad) / h
        dried = np.clip((sm(surface_at + 0.03, 0.52, t) * (1 - sm(sink_at, sink_at + 0.08, t)) * 1.5 - ytop_s * 0.5) * 2.2, 0, 1)[..., None]
        rig.rgb = wet * (1 - dried) + dry * dried
        # the blast lights him from inside his own mouth
        rgb, al = rig.pose(parts, light=(0.7 * wave(t, 1), -0.75), relief=0.3)
        mx, my = rig.at(parts[-1], fx, fy)
        d_ = np.exp(-((rig.xx - rig.pad - mx) / (w * 0.26)) ** 2 - ((rig.yy - rig.pad - my) / (h * 0.28)) ** 2)[..., None]
        rgb = rgb + d_ * np.array([50.0, 120, 190]) * (0.5 * charge + 0.6 * fire)
        C, A = layer(rgb, al, left - rig.pad, top - rig.pad)
        # his reflection, broken up by the glints
        off = ((noise(1260, 14, t) - 0.5) * 18).astype(int)
        my_ = np.clip(2 * (wl + 3) - YY.astype(int), 0, H - 1).astype(int)
        mx_ = np.clip(XX.astype(int) + off, 0, W - 1)
        refl = (A[my_, mx_] * (1 - glint[..., None] * 0.6) * (0.5 - 0.35 * depth[..., None]))
        lake = lake * (1 - refl) + C[my_, mx_] * 0.55 * refl * np.array([0.7, 0.85, 1.0])
        cv = cv * (1 - water) + lake * water
        # tentacles behind the line: the two far pools
        waking = [sm(t0, t0 + 0.07, t) * (1 - sm(sink_at + 0.02 + t0 * 0.2, sink_at + 0.12 + t0 * 0.2, t)) for _, _, t0, _ in pools]
        for (x, y, t0, sc), wk in list(zip(pools, waking))[:2]:
            if wk > 0.02:
                tendrils(cv, [(x, y + 2, (64 + 34 * wk) * sc * wk, -90 + (x < cx) * 14 - (x > cx) * 14, 80, 5.5 * sc, phi_phase(int(t0 * 50)), 2)], t, (126, 116, 40), (190, 190, 150))
        # the water cuts him off at the line
        above = np.clip((wl + 1.5 * np.sin(XX * 0.12 + 2 * math.pi * t * 3) - YY + 0.5), 0, 1)[..., None]
        a_ = A * above
        cv = cv * (1 - a_) + C * a_
        # the skirt of foam where his body meets the water
        foam_w = np.clip(1 - ((XX - cx) / (w * 0.42)) ** 2, 0, 1) * rise
        foam = np.exp(-((YY - wl - 1) / 3.5) ** 2) * foam_w * np.clip(n * 1.8 - 0.25, 0, 1)
        cv += foam[..., None] * np.array([190.0, 215, 215])
        cv = feather(cv)
        # near tentacles, in front of everything
        for (x, y, t0, sc), wk in list(zip(pools, waking))[2:]:
            if wk > 0.02:
                tendrils(cv, [(x, y + 2, (78 + 40 * wk) * sc * wk, -90 + (x < cx) * 18 - (x > cx) * 18, 95, 6 * sc, phi_phase(int(t0 * 50)), 2)], t, (150, 124, 30), (205, 190, 110))
        for (x, y, t0, sc), wk in zip(pools, waking):
            s_ = since(t, t0)
            if s_ < 0.2:
                ring(cv, x, y, 3 + s_ * 150 * sc, 0.25, 3, np.array([170.0, 215, 225]) * (1 - s_ / 0.2))
            if wk > 0.02:
                ring(cv, x, y, 8 * sc + 2, 0.25, 2.4, np.array([120.0, 170, 180]) * wk * 0.8)
        if t >= surface_at and t < surface_at + 0.4:
            spray.draw(cv, min(0.99, (t - surface_at) / 0.4), lambda v: np.array((200, 230, 235)) * (1 - v) ** 0.7, 1.6)
        if burst > 0.05:
            glow(cv, cx, wl, 54, (120, 170, 190), 0.4 * burst)
        px, py = left + rig.at(parts[-1], fx, fy)[0], top + rig.at(parts[-1], fx, fy)[1]
        if charge > 0.02:
            glow(cv, px, py, 6 + 9 * charge, (90, 190, 255), 0.5 * charge)
        # his magic is a wave of water: it leaves the mouth and comes straight at you
        s_ = since(t, blast_at)
        if s_ < 0.2 and rise > 0.3:
            v = s_ / 0.2
            bx, by = px + (W * 0.5 - px) * v, py + (H * 0.72 - py) * v
            rad = 6 + 72 * v * v
            ball = np.exp(-((XX - bx) ** 2 + (YY - by) ** 2) / (2 * rad * rad))
            tex = np.clip(noise(1270, 8, t * 3) * 1.6 - 0.2, 0, 1)
            den = np.clip(ball * 3.0 * (0.45 + 0.8 * tex), 0, 1)[..., None] * (1 - v ** 5)
            cv[:] = cv * (1 - 0.72 * den) + ramp([(24, 76, 150), (56, 140, 220), (205, 240, 255)], tex ** 1.6)[..., :3] * 0.72 * den
            ring(cv, bx, by, rad * 1.5, 0.9, 3.5, np.array([190.0, 240, 255]) * (1 - v))
        return np.clip(cv, 0, 255)
    return "kraken", "out of black water", scene

# ---- Leviathan -----------------------------------------------------------------
def scar_backdrop():
    """the Scar: a huge dark cavern, slate blue, with a broken rock shelf behind him"""
    img = ramp([(14, 16, 30), (24, 30, 50), (34, 42, 64)], np.clip(YY / H, 0, 1)) * (0.65 + 0.7 * fbm(1301, (40, 18, 7))[..., None])
    for seed, base, height, col in ((1305, 150, 70, (34, 42, 62)), (1309, 196, 26, (44, 52, 72))):
        rid = ridge(seed, base, height, (28, 10, 4))
        m = (YY > rid[None, :])[..., None]
        img = img * (1 - m) + np.array(col, float) * (0.55 + 0.9 * fbm(seed + 1, (12, 5, 2))[..., None]) * m
    drip = 16 + 34 * np.abs(np.sin(XX * 0.11 + 1.7 * np.sin(XX * 0.04))) ** 3 + 18 * fbm(1315, (10, 4))[0, :][None, :]
    return img * (1 - (YY < drip)[..., None]) + np.array([7.0, 8, 15]) * (YY < drip)[..., None]

def leviathan_stream():
    # one idea: a stream of orbs in three colours, faster and faster until you cannot keep up; his whole body and the cave relit by each. Then the roof comes down
    rgb0, al0 = fit(opaque("The_Leviathan.png", 200), W * 1.12, H * 1.22, exposure=0.82, gamma=0.96, sat=1.1, grain=0.45)
    h, w = al0.shape[:2]; left = 8; top = 7
    rig = Rig(rgb0, al0, pad=40)
    lum = own_lum(rig)
    base = rig.rgb.copy()
    GREEN, BLUE, RED = np.array([80.0, 240, 100]), np.array([90.0, 170, 255]), np.array([255.0, 66, 50])
    skins = {0: [(8, 40, 26), (26, 120, 60), (110, 230, 120), (225, 255, 215)], 1: [(10, 26, 70), (30, 80, 190), (100, 170, 255), (225, 240, 255)], 2: [(60, 8, 10), (170, 28, 26), (255, 90, 66), (255, 225, 195)]}
    dyed = {k: ramp(v, lum)[..., :3] for k, v in skins.items()}
    colours = [GREEN, BLUE, RED]
    back = scar_backdrop()
    # shots on an accelerating clock: each interval a golden step shorter than the last, then the barrage ends
    shots = []; at, gap = 0.02, 0.15
    pattern = [0, 1, 2, 2, 0, 1, 1, 2, 0, 2, 1, 0, 2, 1]
    for i in range(14):
        shots.append((at, pattern[i])); at += gap; gap = max(0.022, gap * 0.79)
    last_shot = shots[-1][0]
    fly = 0.17
    target = (34, 222)
    fx, fy = 0.215, 0.345                                                     # the mouth
    slam = 0.86
    rocks = [(W * 0.18, 0.00, 13, 0.9), (W * 0.46, 0.022, 17, 1.0), (W * 0.72, 0.01, 11, 0.7), (W * 0.30, 0.05, 9, 0.6), (W * 0.60, 0.065, 12, 0.8), (W * 0.08, 0.075, 10, 0.75)]
    dust = Motes(1320, 40, lambda r: (r.rand() * W, 205 + r.rand() * 14, (r.rand() - 0.5) * 110, -38 * r.rand(), 30))
    grit = Motes(1321, 22, lambda r: (r.rand() * W, 8, 6 * (r.rand() - 0.5), 200 + 80 * r.rand(), 0))
    motes_ = Motes(1322, 20, lambda r: (r.rand() * W, r.rand() * H, -8, -18, 0))
    def scene(t):
        fired = [(s_, k) for s_, k in shots if s_ <= t]
        s_last = since(t, fired[-1][0]) if fired else 1.0
        snap = math.exp(-s_last * 42) if fired else 0.0
        col_k = fired[-1][1] if fired and t < last_shot + 0.07 else None
        relit = (math.exp(-s_last * 12) * 0.5 + 0.5) * (1 - sm(last_shot + 0.02, last_shot + 0.07, t)) * sm(0.0, 0.03, t) if col_k is not None else 0.0
        col = colours[col_k] if col_k is not None else np.array([0.0, 0, 0])
        frenzy = sm(0.0, last_shot, t) * (t < slam)
        rear = sm(last_shot + 0.0, slam - 0.03, t) * (1 - sm(slam - 0.03, slam, t))
        s = since(t, slam); hit = math.exp(-s * 8)
        settle = hit * math.cos(s * 26)
        back_off = sm(0.0, 0.1, t) * (1 - sm(last_shot, last_shot + 0.05, t))
        cv = back * (0.75 + 0.28 * hit)
        if relit > 0:
            cv = cv * (1 + relit * 0.55) + (np.exp(-((XX - 70) / 130) ** 2 - ((YY - 120) / 120) ** 2) * relit)[..., None] * col * 0.17
        cv = feather(cv)
        motes_.draw(cv, t, lambda v: np.array((80, 90, 120)) * math.sin(math.pi * v) * 0.6, 0.9)
        # the roof falling behind him
        if s < 0.34 or hit > 0.2:
            for x, dl, sz, depth in rocks[3:]:
                v = (s - dl) / 0.13
                if 0 <= v < 1:
                    y = -14 + (196 - 0.12 * sz) * v * v
                    stamp(cv, x, y, np.array((64.0, 66, 84)), sz * 0.7)
        # the great neck: a slow S travels up it, the head leads and the base stays planted off-frame
        neck = 0.5 + 0.5 * wave(t, 1, 0.1)
        def body(x, y):
            up = np.clip(1.0 - y, 0, 1)
            s_wave = np.sin(2 * math.pi * (t * 2 - y * 1.1))
            amp = (2 + 7 * up) * (1 - 0.4 * frenzy)
            lean, drop = 9 * rear * up - 13 * hit * up - 5 * frenzy * up ** 2, -30 * rear * up ** 1.3 + 30 * settle * up ** 1.3 + 8 * frenzy * up
            return -lean + amp * s_wave * np.clip(y * 2.2, 0, 1), -drop + 2.5 * snap * up
        head_a = 6 * rear - 4 * frenzy * snap - 8 * settle + 2 * wave(t, 2, phi_phase(1))
        head = dict(c=(0.36, 0.23), r=(0.37, 0.25), p=(0.58, 0.34), a=head_a, soft=0.35, lit=0.12 * relit)
        jaw = dict(c=(0.22, 0.39), r=(0.19, 0.09), p=(0.42, 0.31), a=head_a + 7 * snap + 3 + 5 * rear + 10 * hit, soft=0.45)
        lure = dict(c=(0.045, 0.31), r=(0.075, 0.14), p=(0.14, 0.235), a=head_a + 13 * wave(t, 2, phi_phase(2)) - 10 * rear, soft=0.45)
        spines = dict(c=(0.42, 0.055), r=(0.25, 0.06), p=(0.42, 0.10), a=head_a + 3 * wave(t, 4, phi_phase(3)), soft=0.6)
        gill = dict(c=(0.77, 0.20), r=(0.14, 0.14), p=(0.72, 0.24), a=-5 * wave(t, 3, phi_phase(4)), soft=0.6, d=(0.008 * wave(t, 3, phi_phase(4)), 0))
        parts = [gill, spines, head, jaw, lure]
        mix = relit * 0.72
        rig.rgb = base * (1 - mix) + (dyed[col_k] if col_k is not None else base) * mix
        rgb, al = rig.pose(parts, light=(-0.7 + 0.5 * wave(t, 1), -0.5), relief=0.3, bend=body)
        rgb = rgb * (1 + 0.18 * hit)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        mp = land(rig, rig.at(jaw, fx, fy), body)
        m = (left + mp[0], top + mp[1])
        ep = land(rig, rig.at(head, 0.365, 0.2), body)
        glow(cv, left + ep[0], top + ep[1], 4 + 3 * frenzy, (120, 255, 160), 0.4 + 0.6 * frenzy)
        lp = land(rig, rig.at(lure, 0.015, 0.36), body)
        glow(cv, left + lp[0], top + lp[1], 6, (90, 255, 130), 0.45 + 0.25 * wave(t, 4))
        # ---- the stream: orbs grow as they come at you, the last ones overlap into a ribbon ----
        for s0, k in shots:
            u = since(t, s0) / fly
            if u < 1:
                x = m[0] + (target[0] - m[0]) * u; y = m[1] + (target[1] - m[1]) * u + 16 * math.sin(math.pi * u)
                r_ = 3 + 13 * u
                c_ = colours[k]
                glow(cv, x, y, r_ * 1.5, c_, 0.18)
                disc = np.clip(r_ + 0.5 - np.hypot(XX - x, YY - y), 0, 1)[..., None]
                cv[:] = cv * (1 - disc) + c_ * disc
                stamp(cv, x - r_ * 0.25, y - r_ * 0.25, np.array(c_) * 0.3 + 90, r_ * 0.45)
            elif u < 1.5:
                glow(cv, target[0], target[1], 14, colours[k], 0.5 * (1.5 - u))
        if snap > 0.05 and col_k is not None:
            glow(cv, m[0], m[1], 7, colours[col_k], 0.9 * snap)
        # ---- the slam: rocks and lightning ----
        if s < 0.34:
            for x, dl, sz, depth in rocks[:3]:
                v = (s - dl) / 0.1
                if 0 <= v < 1:
                    y = -16 + 226 * v * v
                    stamp(cv, x + 1, y + 1, (-40, -40, -40), sz)
                    stamp(cv, x, y, (120, 122, 136), sz * 0.85)
                    stamp(cv, x - 2, y - 3, (150, 150, 165), sz * 0.45)
                elif 1 <= v < 2.8:
                    w_ = (v - 1) / 1.8
                    ring(cv, x, 210, 6 + 36 * w_, 0.3, 4, np.array([150.0, 150, 170]) * (1 - w_))
            grit.draw(cv, min(0.99, s / 0.34), lambda v: np.array((120, 120, 140)) * (1 - v * 0.5), 1.0)
        if s < 0.18:
            for i, (bx0, bx1) in enumerate(((W * 0.82, W * 0.62), (W * 0.1, W * 0.26), (W * 0.48, W * 0.40))):
                if s > i * 0.018:
                    bolt(cv, bx0, 0, bx1, 216, 1330 + i * 7 + int(s * FRAMES), np.array((190, 215, 255)) * (1 - s / 0.18), jag=46, size=1.6)
            cv += ((1 - s / 0.18) * 0.1) * np.array([120.0, 150, 200])
        if s < 0.42:
            dust.draw(cv, min(0.99, s / 0.42), lambda v: np.array((120, 125, 145)) * (1 - v) * 0.7, 2.0)
        e = hit * 5
        return np.clip(shake(cv, e * math.sin(s * 80), e * 0.8 * math.cos(s * 110)), 0, 255)
    return "leviathan", "the orb stream", scene

# ---- Sarachnis -----------------------------------------------------------------
def forthos_backdrop():
    """the Forthos ruin: mossed, sunken temple stone, greenish, so her crimson burns against it"""
    course, brick = 26, 48
    row = (YY // course).astype(int); off = (row % 2) * brick // 2
    col = ((XX + off) // brick).astype(int)
    shade = np.random.RandomState(1401).rand(40, 40)[row % 40, col % 40]
    joint = (YY % course < 2) | ((XX + off) % brick < 2)
    stone = np.array([36.0, 44, 38]) * ((0.6 + 0.6 * shade) * (0.65 + 0.7 * fbm(1405, (18, 7, 3))))[..., None]
    img = stone * (1 - 0.55 * joint[..., None])
    moss = np.clip(fbm(1410, (20, 9, 4)) * 3.2 - 1.45, 0, 1)[..., None] * np.clip(1 - YY / 170, 0, 1)[..., None]
    img = img + moss * np.array([-4.0, 20, 2])
    glowhole = np.exp(-((XX - W * 0.54) / 64) ** 2 - ((YY - 84) / 66) ** 2)[..., None]
    img = img * (1 - 0.5 * glowhole) + glowhole * np.array([8.0, 14, 12])
    z = np.clip((YY - 170) / 70, 0, 1)
    floor = np.clip((YY - 170) / 3, 0, 1)[..., None]
    flag = (0.75 + 0.4 * (np.sin((XX - W / 2) / (z + 0.3) * 0.16) > -0.92)[..., None]) * (0.6 + 0.8 * fbm(1415, (14, 5, 2))[..., None])
    return img * (1 - floor) + floor * np.array([44.0, 50, 44]) * flag * (0.75 + 0.45 * z[..., None])

def spiderling(cv, x, y, s, face, t, ph, colour):
    """a spawn in strokes: a round body, eight scuttling legs; a shrunk sprite would dissolve at this size"""
    step = 2 * math.pi * (t * 6 + ph)
    segs = []
    for i in range(4):
        for side in (-1, 1):
            a = (i - 1.5) * 0.62 + (side > 0) * math.pi
            sw = math.sin(step + i * 1.7 + (side > 0) * 3.14) * 0.3
            kx, ky = x + math.cos(a + sw) * 2.2 * s, y - 0.7 * s + math.sin(a + sw) * 0.5 * s
            fx_, fy_ = x + math.cos(a + sw) * 3.8 * s, y + 1.8 * s + math.sin(a + sw) * 0.8 * s
            segs.append([(x, y), (kx, ky), (fx_, fy_)])
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    for sg in segs: d.line(sg, fill=255, width=max(2, int(round(s * 0.5))))
    m = (np.asarray(im, float) / 255)[..., None]
    cv[:] = cv * (1 - m) + np.array(colour, float) * 0.7 * m
    bd = np.clip((1 - ((XX - x - face * 0.4 * s) / (1.9 * s)) ** 2 - ((YY - y) / (1.45 * s)) ** 2) * 4, 0, 1)[..., None]
    cv[:] = cv * (1 - bd) + (np.array(colour, float) * (0.75 + 0.5 * np.clip((y - YY) / (1.5 * s), -1, 1))[..., None]) * bd

def cocoon(cv, x, y, s, tight, shake_, glow_c, heat, torn):
    """a silk cocoon: pale wrapped bands, what is inside lit through them. torn (0..1) splits it from the top"""
    ax, ay = 0.74 * s, 1.0 * s
    d = np.sqrt(((XX - x - shake_) / ax) ** 2 + ((YY - y + ay) / ay) ** 2)
    if torn > 0:
        split_ = np.abs(XX - x - shake_) < torn * ax * 0.8 * np.clip((y - ay * 0.5 - YY) / (ay * 1.2) + 0.3, 0, 1)
    else:
        split_ = np.zeros_like(d, bool)
    m = (np.clip((1 - d) * 5, 0, 1) * tight * (~split_))[..., None]
    bands = 0.5 + 0.62 * (np.sin((YY - y) * 0.85 + (XX - x) * 0.5) > -0.1)      # wide wraps of silk: a thin cross-hatch would vanish in a 5 x 10 cell
    silk = np.array([232.0, 214, 160]) * bands[..., None] * (0.55 + 0.6 * np.clip((x - XX) / (ax * 1.4) + 0.5, 0, 1))[..., None]
    rim_ = np.clip(1 - np.abs(d - 1.08) / 0.1, 0, 1)[..., None] * tight
    cv[:] = cv * (1 - 0.8 * rim_)
    silk = silk + (np.exp(-d ** 2 * 2.2) * heat)[..., None] * np.array(glow_c, float)
    cv[:] = cv * (1 - m) + silk * m

def sarachnis_brood():
    # one idea: she drives a leg into the floor and the shock runs out through the cocoons she has laid, which split and let their spawn run. Her body flushes outward from the middle with each blow
    rgb0, al0 = fit(sprite("Sarachnis.png"), W + 2, H - 40, exposure=1.08, gamma=0.72, sat=1.05, grain=0.4)
    h, w = al0.shape[:2]; left = (W - w) // 2; ground = H - 24; top = ground - h
    rig = Rig(rgb0, al0, pad=40)
    lum = own_lum(rig)
    base = rig.rgb.copy()
    hot = ramp([(150, 20, 22), (255, 70, 36), (255, 170, 80), (255, 240, 190)], lum)[..., :3]
    back = feather(forthos_backdrop())
    cen = (0.57, 0.36)
    dist_s = np.hypot((rig.xx - rig.pad) / w - cen[0], ((rig.yy - rig.pad) / h - cen[1]) * 0.82)
    GREEN, BLUE = (70, 235, 110), (90, 165, 255)
    # five cocoons on a pentagon lying on the floor: far three behind her legs, near two in front
    verts = polygon_pts(W * 0.5, 192, 88, 5, rot=math.pi / 2, squash=0.30)
    order = (0, 3, 1, 4, 2)
    cocoons = []
    for i, k in enumerate(order):
        x, y = verts[k]
        sc = 12 + (y - 165) * 0.28
        cocoons.append(dict(x=x, y=y, s=sc, hatch=0.40 + 0.055 * i, col=GREEN if i % 2 else BLUE, near=y > 200, ph=phi_phase(i), run=(-40 if x < W / 2 else W + 40, y + (10 if y > 200 else -6))))
    stabs = [0.36, 0.70]
    tip0 = (0.255, 0.885)                                                     # the leg that stabs
    tip1 = (0.94, 0.90)
    shreds = Motes(1425, 26, lambda r: (0, 0, (r.rand() - 0.5) * 70, -60 * r.rand() - 12, 110))
    motes_ = Motes(1426, 16, lambda r: (r.rand() * W, 40 + r.rand() * 130, 8 * (r.rand() - 0.5), 26, 0))
    def scene(t):
        wind = [sm(s0 - 0.17, s0 - 0.015, t) * (1 - sm(s0 - 0.015, s0, t)) for s0 in stabs]
        hits = [math.exp(-since(t, s0) * 7.5) for s0 in stabs]
        ages = [since(t, s0) for s0 in stabs]
        hit = max(hits)
        s_near = ages[0] if hits[0] >= hits[1] else ages[1]
        settle = hit * math.cos(s_near * 26)
        crouch = 10 * max(wind) * -1 + 14 * settle + 4 * wave(t, 2)
        lift0, lift1 = wind[0], wind[1]
        # the flush: a band of heat runs from her middle out along her legs, then cools
        front = min(1.1, s_near * 5.2)
        flush = np.exp(-((dist_s - front) / 0.16) ** 2) * (1 - sm(0.12, 0.3, s_near)) + np.clip(0.8 - dist_s * 3.2, 0, 1) * (0.25 * max(wind) + 0.5 * hit)
        rig.rgb = base * (1 - np.clip(flush, 0, 1)[..., None]) + hot * np.clip(flush, 0, 1)[..., None]
        def body(x, y):
            near = np.exp(-((x - cen[0]) / 0.3) ** 2 - ((y - cen[1]) / 0.34) ** 2)
            bx = 3 * wave(t, 1, 0.15) * near - 5 * max(wind) * near + 6 * settle * near
            by = crouch * near
            # each of the other legs shifts its weight in turn; tips touch down where they were
            for tx, ty, ph in ((0.05, 0.80, phi_phase(1)), (0.57, 0.92, phi_phase(2)), (0.80, 0.80, phi_phase(3))):
                by = by + 6 * max(0.0, wave(t, 1, ph)) ** 2 * np.exp(-((x - tx) / 0.09) ** 2 - ((y - ty) / 0.14) ** 2)
            # the stabbing legs draw up through their knees and drive down
            g0 = np.exp(-((x - 0.27) / 0.11) ** 2) * np.clip((y - 0.20) / 0.5, 0, 1) ** 1.3
            g1 = np.exp(-((x - 0.92) / 0.11) ** 2) * np.clip((y - 0.25) / 0.5, 0, 1) ** 1.3
            by = by + 62 * lift0 * g0 + 56 * lift1 * g1
            bx = bx - 16 * lift0 * g0 + 14 * lift1 * g1
            return bx, by
        cv = back * (0.74 + 0.22 * hit)
        d_ = np.hypot(XX - left - w * tip0[0], (YY - ground) / 0.3)
        cv = cv + (hit * np.exp(-(np.hypot(XX - W / 2, (YY - 110)) / 110) ** 2))[..., None] * np.array([34.0, 4, 6])
        motes_.draw(cv, t, lambda v: np.array((110, 120, 100)) * math.sin(math.pi * v) * 0.45, 0.9)
        # cracks of the shock run across the floor to each cocoon in turn
        strike = stabs[0] if s_near == ages[0] else stabs[1]
        for s0, tp in zip(stabs, (tip0, tip1)):
            age = since(t, s0)
            if age < 0.2:
                ring(cv, left + tp[0] * w, ground, age * 560, 0.28, 3, np.array([200.0, 70, 40]) * (1 - age / 0.2), (YY > 170))
        # shadow under her
        sh = np.clip(1 - ((XX - W * 0.55) / 92) ** 2 - ((YY - ground + 6) / 11) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.42 * sh
        def draw_cocoon(c):
            age = since(t, c["hatch"])
            grown = sm(0.0, 0.22, t) if t < c["hatch"] else 1.0
            if t >= c["hatch"]: grown = 1.0 - sm(0.3, 0.4, age)
            torn = sm(0.0, 0.03, age) if t >= c["hatch"] else 0.0
            before = sm(c["hatch"] - 0.2, c["hatch"], t) if t < c["hatch"] else 0.0
            sh_ = 1.4 * before * wave(t, 12, c["ph"])
            if grown > 0.02:
                glow(cv, c["x"], c["y"] - c["s"], c["s"] * 0.95, c["col"], (0.08 + 0.28 * before + 0.5 * math.exp(-age * 24) * (t >= c["hatch"])))
                cocoon(cv, c["x"], c["y"], c["s"] * (0.3 + 0.7 * grown), grown, sh_, c["col"], 0.12 + 0.8 * before, torn)
                # silk thread up into the dark, so the eggs are hung as much as laid
        def draw_spawn(c):
            age = since(t, c["hatch"]) if t >= c["hatch"] else 9
            if age < 0.42:
                v = age / 0.42
                x = c["x"] + (c["run"][0] - c["x"]) * v ** 1.25; y = c["y"] - 2 + (c["run"][1] - c["y"]) * v + 2 * wave(t, 7, c["ph"])
                spiderling(cv, x, y, 4.2 + c["s"] * 0.1, 1 if c["run"][0] > c["x"] else -1, t, c["ph"], c["col"])
            if age < 0.14:
                shreds_v = age / 0.14
                for t0, (_, _, vx, vy, g) in shreds.p[:9]:
                    stamp(cv, c["x"] + vx * shreds_v, c["y"] - c["s"] + vy * shreds_v + g * shreds_v ** 2, np.array((205, 200, 176)) * (1 - shreds_v), 1.5)
        for c in cocoons:
            if not c["near"]: draw_cocoon(c)
        for c in cocoons:
            if not c["near"]: draw_spawn(c)
        head = dict(c=(0.50, 0.41), r=(0.07, 0.07), p=(0.54, 0.38), a=5 * wave(t, 2, phi_phase(4)) - 8 * max(wind) + 8 * settle, lit=0.2 * hit, soft=0.5)
        parts = [dict(c=(0.60, 0.36), r=(0.08, 0.07), p=(0.60, 0.36), d=(0, -0.008 * wave(t, 2, 0.2)), soft=0.9), head]
        rgb, al = rig.pose(parts, light=(0.85 * wave(t, 1), -0.45), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ep = land(rig, rig.at(head, 0.495, 0.41), body)
        glow(cv, left + ep[0], top + ep[1], 4 + 3 * hit, (255, 70, 30), 0.3 + 0.9 * max(max(wind), hit))
        for c in cocoons:
            if c["near"]: draw_cocoon(c)
        for c in cocoons:
            if c["near"]: draw_spawn(c)
        for s0, tp in zip(stabs, (tip0, tip1)):
            age = since(t, s0)
            if age < 0.12:
                glow(cv, left + tp[0] * w, ground - 2, 7, (255, 110, 60), 0.6 * (1 - age / 0.12))
        e = hit * 2.5
        return np.clip(shake(cv, e * math.sin(s_near * 80), e * math.cos(s_near * 110)), 0, 255)
    return "sarachnis", "the brood", scene

# ---- Thermonuclear smoke devil ---------------------------------------------------
SMOKE = [(30, 29, 29), (42, 41, 40), (56, 54, 51), (74, 71, 65), (98, 93, 82)]

def thermy_core():
    # one idea: the frame is smoke, and the devil is only a darker thought in it, an eye and horn tips. Then the core goes critical: the smoke burns off in a ring and for a moment you see all of him
    rgb0, al0 = fit(sprite("Thermonuclear_smoke_devil.png"), W - 30, H - 8, exposure=0.92, gamma=0.86, sat=1.15, grain=0.4)
    h, w = al0.shape[:2]; left = (W - w) // 2 - 2; top = 5
    rig = Rig(rgb0, al0, pad=40)
    lum = own_lum(rig)
    base = rig.rgb.copy()
    yellow = np.clip((rig.rgb[..., 0] + rig.rgb[..., 1]) / 2 - rig.rgb[..., 2] - 18, 0, 60)[..., None] / 60
    ash = ramp([(14, 14, 16), (36, 36, 38), (70, 68, 66), (112, 108, 100)], lum)[..., :3]
    white = ramp([(120, 48, 10), (235, 124, 26), (255, 210, 110), (255, 250, 225)], lum)[..., :3]
    fx, fy = 0.45, 0.285                                                      # the eye
    crit = 0.56
    embers = Motes(1520, 34, lambda r: (W / 2 + (r.rand() - 0.5) * 30, 76 + (r.rand() - 0.5) * 30, (r.rand() - 0.5) * 300, (r.rand() - 0.5) * 300, 0))
    fine = Motes(1521, 28, lambda r: (r.rand() * W, H, 26 * (r.rand() - 0.5), -H - 20, 0))
    def smoke(t, seed, cell, k, kx, warp):
        a = climb(seed, cell, t, k, kx, warp, seed + 40)
        b = climb(seed + 2, max(4, cell // 2), t, k * 2, -kx, warp * 0.6, seed + 40)
        return np.asarray(Image.fromarray(np.clip((0.66 * a + 0.34 * b) * 255, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)), float) / 255
    def scene(t):
        s = since(t, crit); fire = math.exp(-s * 5.5)
        flash = math.exp(-s * 17)
        charge = sm(0.2, crit, t) * (t < crit) * 1.0
        pulse = 0.5 + 0.5 * wave(t, 6)
        ex0, ey0 = left + fx * w, top + fy * h
        # the far smoke, lit from behind him as the core wakes
        far = smoke(t, 1500, 42, 1, 0, 60)
        lit = (0.62 + 0.3 * wave(t, 1)) * 0.75
        d = np.hypot(XX - ex0, YY - ey0)
        back_glow = np.exp(-(d / (42 + 70 * charge + 180 * fire)) ** 2)
        cv = ramp(SMOKE, np.clip((far - 0.25) * 1.6, 0, 1) * lit)[..., :3]
        cv = cv + back_glow[..., None] * np.array([120.0, 62, 18]) * (0.1 + 0.55 * charge + 1.1 * fire)
        cv = feather(cv)
        # the ring: burns clear, in a torn, glowing front
        rr = s * 520 if s < 0.6 else 999.0
        tear = (noise(1540, 14, t) - 0.5) * 44
        clear = np.clip((rr - d + tear) / 18, 0, 1) * (1 - sm(0.2, 0.62, s))         # how open the smoke is: it rolls back in
        # his body: stem sways, each horn on its own clock, tentacles trail
        sway = wave(t, 1, 0.1)
        def body(x, y):
            yy_ = np.clip(1 - y, 0, 1)
            feet = np.clip((0.86 - y) / 0.2, 0, 1)
            s_ = np.sin(2 * math.pi * (t - y * 1.0))
            return -(6 * s_ * yy_ * feet + 2 * sway), -(2.5 * wave(t, 2)) * yy_ + 7 * charge * yy_ ** 2 - 12 * fire * yy_ ** 2
        recoil = fire * math.cos(s * 22)
        parts = [
            dict(c=(0.47, 0.11), r=(0.17, 0.14), p=(0.47, 0.22), a=5 * wave(t, 2, phi_phase(1)) + 6 * recoil, soft=0.5),              # crown
            dict(c=(0.22, 0.27), r=(0.14, 0.09), p=(0.36, 0.30), a=9 * wave(t, 2, phi_phase(2)) - 10 * charge + 8 * recoil, soft=0.5),  # left horn
            dict(c=(0.72, 0.28), r=(0.14, 0.09), p=(0.58, 0.30), a=-9 * wave(t, 2, phi_phase(3)) + 10 * charge - 8 * recoil, soft=0.5),
            dict(c=(0.26, 0.56), r=(0.13, 0.12), p=(0.40, 0.66), a=6 * wave(t, 3, phi_phase(4)) - 7 * recoil, soft=0.5),                # thorns round the bulb
            dict(c=(0.68, 0.55), r=(0.14, 0.12), p=(0.55, 0.65), a=-6 * wave(t, 3, phi_phase(5)) + 7 * recoil, soft=0.5),
            dict(c=(0.12, 0.85), r=(0.16, 0.12), p=(0.28, 0.80), a=8 * wave(t, 1, phi_phase(6)), soft=0.45),                            # tentacles
            dict(c=(0.88, 0.85), r=(0.16, 0.12), p=(0.72, 0.80), a=-8 * wave(t, 1, phi_phase(7)), soft=0.45),
            dict(c=(0.66, 0.95), r=(0.09, 0.10), p=(0.62, 0.82), a=7 * wave(t, 2, phi_phase(8)), soft=0.45),
        ]
        heat = np.clip(fire * 1.1 + 0.5 * charge * np.clip(1 - np.hypot(((rig.xx - rig.pad) / w - fx), ((rig.yy - rig.pad) / h - fy)) * 2.6, 0, 1), 0, 1)
        heat = heat[..., None] if hasattr(heat, "ndim") else heat
        body_col = ash * (1 - heat) + white * heat
        body_col = body_col * (1 - yellow * 0.75) + yellow * 0.75 * np.array([255.0, 225, 90]) * (0.62 + 0.38 * pulse + 0.5 * charge)
        rig.rgb = np.clip(body_col, 0, 255)
        rgb, al = rig.pose(parts, light=(0.7 * wave(t, 1), -0.7), relief=0.3, bend=body)
        C, A = layer(rgb, al, left - rig.pad, top - rig.pad)
        # the near smoke: this is what he is seen through
        near = smoke(t, 1510, 30, 2, 0, 70)
        haze = np.clip((near - 0.34) * 2.6, 0, 1)
        veil = np.clip(0.52 + 0.44 * haze, 0, 0.94)                           # at rest the body shows through at about a quarter
        cv = cv * (1 - A) + C * A
        veil_now = (veil * (1 - clear))[..., None]
        smoke_col = ramp(SMOKE, np.clip((near - 0.2) * 1.7, 0, 1))[..., :3]
        smoke_col = smoke_col + back_glow[..., None] * np.array([100.0, 50, 12]) * (0.45 * charge + 1.0 * fire) * haze[..., None]
        # the front of the ring itself is on fire
        edge = (clear * (1 - clear) * 4)[..., None]
        cv = cv * (1 - veil_now * EDGE) + feather(smoke_col) * veil_now * EDGE
        cv += edge * np.array([255.0, 120, 26]) * 0.95 * (1 - sm(0.1, 0.42, s))
        if s < 0.4:
            embers.draw(cv, min(0.99, s / 0.4), lambda v: np.array((255, 170, 60)) * (1 - v), 1.5)
        fine.draw(cv, t, lambda v: np.array((150, 120, 70)) * math.sin(math.pi * v) * 0.35, 1.0)
        # the eye and his horn tips burn through whatever smoke is in front
        ep = land(rig, rig.at(dict(p=(fx, fy)), fx, fy), body)
        ex, ey = left + ep[0], top + ep[1]
        tipmask = (A[..., 0] > 0.5) * 1.0
        tipc = np.clip(C - 40, 0, 255) * (C[..., 0] > 175)[..., None] * (C[..., 2] < 130)[..., None]
        cv += tipc * tipmask[..., None] * (1 - clear)[..., None] * 0.62
        stamp(cv, ex, ey, (-90, -90, -90), 10)
        stamp(cv, ex, ey, (255, 225, 80), 7 + 2.5 * charge)
        stamp(cv, ex, ey, (10, 10, -30), 3.2 * (1 - charge))
        glow(cv, ex, ey, 10 + 15 * charge, (255, 190, 60), 0.42 + 0.3 * pulse * (1 - charge) + 0.85 * charge)
        if flash > 0.04:
            cv += flash * 0.85 * np.array([255.0, 225, 170]) * np.exp(-(d / 150) ** 2)[..., None]
        e = fire * 3.5
        return np.clip(shake(cv, e * math.sin(s * 80), e * math.cos(s * 110)), 0, 255)
    return "thermy", "critical", scene

def jet(cv, x, y, hh, wd, n, power, colour):
    """a gas vent as light, not as a solid: soft all round, additive, broken by smooth noise, gone at the top"""
    v = (y - YY) / max(hh, 1)
    den = np.exp(-((XX - x - (n - 0.5) * 22 * np.clip(v, 0, 1)) / (wd * (0.45 + 1.5 * np.clip(v, 0, 1)))) ** 2) * (v > 0) * np.clip(1 - v, 0, 1) ** 0.8
    cv += (den * np.clip(n * 2 - 0.25, 0, 1.2) * power)[..., None] * np.array(colour, float)

# ---- Abyssal Sire --------------------------------------------------------------
def sire_scene_backdrop():
    """static parts of the respiratory chamber: dark gut floor; the ribs are drawn each frame"""
    floor = np.clip((YY - 196 + 5 * (fbm(1610, (26, 9))[0, :][None, :] - 0.5)) / 4, 0, 1)[..., None]
    return floor, np.array([36.0, 24, 46]) * (0.55 + 0.9 * fbm(1615, (14, 5, 2))[..., None])

def sire_breath():
    # one idea: the chamber is a lung. One long inhale swells the walls and him together and draws the miasma in; one hard exhale throws it back out through the vents
    rgb0, al0 = fit(sprite("Abyssal_Sire_(phase_1).png"), W, H - 6, exposure=1.0, gamma=0.8, sat=0.95, grain=0.25)
    rgb0, al0 = soften(close_seams(rgb0, 5), al0, 0.25, 1.8, 0.55)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 6; ground = H - 4; top = ground - h
    rig = Rig(rgb0, al0, pad=40)
    lum = own_lum(rig)
    base = rig.rgb.copy()
    choked = ramp([(16, 8, 24), (46, 16, 50), (100, 26, 62), (190, 70, 90)], lum)[..., :3]
    # the maw is the sprite's yellow-green: it glows from within with the breath
    maw = np.clip((rig.rgb[..., 1] - rig.rgb[..., 2] - 30) / 70, 0, 1)[..., None]
    floor_m, floor_c = sire_scene_backdrop()
    n_wall = fbm(1620, (22, 9, 4))
    ang_w = np.arctan2(YY - 118, XX - W / 2)
    cx, cy = W * 0.5, 118
    # four vents at the corners of a square lying on the floor, rotated to a diamond
    vents = [(26, 210, 130, 15, 0.00), (W - 26, 210, 136, 15, 0.02), (58, 186, 86, 10, 0.045), (W - 58, 186, 92, 10, 0.06)]
    fx, fy = 0.47, 0.46
    inhale_end, exhale_end = 0.60, 0.74
    sucked = [(np.random.RandomState(1640 + i).rand() * W, 60 + np.random.RandomState(1660 + i).rand() * 150, phi_phase(i)) for i in range(34)]
    spat = Motes(1680, 26, lambda r: (W / 2 + (r.rand() - 0.5) * 20, 118, (r.rand() - 0.5) * 240, (r.rand() - 0.2) * 130, 30))
    def breath_at(t):
        if t < inhale_end: return sm(0.0, inhale_end, t)
        if t < exhale_end: return 1 - sm(inhale_end, exhale_end, t) ** 0.7
        return 0.0
    def scene(t):
        b = breath_at(t)
        drawing = (t < inhale_end) * sm(0.02, 0.1, t)
        blow = sm(inhale_end, inhale_end + 0.03, t) * (1 - sm(0.74, 0.9, t))
        s = since(t, inhale_end); hit = math.exp(-s * 8) if t >= inhale_end else 0.0
        # concentric ribs round him: the radius itself breathes, they crowd in as the lung fills
        r_ = np.hypot(XX - cx, (YY - cy) * 1.05) * (1 - 0.16 * b)
        ribs = np.sin(r_ * 0.235 + 2.6 * n_wall + 1.3 * np.sin(ang_w * 3))
        flesh = 0.5 + 0.5 * ribs
        wall = ramp([(14, 10, 36), (30, 22, 66), (52, 36, 92), (76, 54, 118)], flesh * (0.55 + 0.45 * n_wall) * (0.8 + 0.3 * b))[..., :3]
        sinew = np.clip(1 - np.abs(ribs) / 0.1, 0, 1)[..., None]
        wall = wall + sinew * np.array([26.0, 10, 22]) * (0.5 + 0.8 * b)
        wall = wall * (0.62 + 0.3 * b + 0.3 * hit)
        cv = wall * (1 - floor_m) + floor_c * (0.7 + 0.3 * b) * floor_m
        cv = feather(cv)
        # the vents in the floor
        for i, (x, y, hh, wd, dl) in enumerate(vents):
            ring(cv, x, y, wd * 0.9, 0.3, 3, np.array([70.0, 70, 40]) * (0.4 + 0.8 * b))
            ww = np.clip(1 - ((XX - x) / (wd * 0.8)) ** 2 - ((YY - y) / (wd * 0.26)) ** 2, 0, 1)[..., None] ** 0.5
            cv = cv * (1 - ww * 0.85)
        # the far vents blow behind him
        for i, (x, y, hh, wd, dl) in enumerate(vents):
            u = since(t, inhale_end + dl)
            pw = sm(0.0, 0.04, u) * (1 - sm(0.15, 0.32, u)) if t >= inhale_end else 0.0
            if pw > 0.02 and i >= 2:
                jet(cv, x, y, hh * (0.5 + 0.5 * pw), wd, climb(1631, 12, t, 3, 0, 18), pw * 0.9, (128, 150, 66))
        sh = np.clip(1 - ((XX - left - w * 0.45) / (w * 0.5)) ** 2 - ((YY - ground + 8) / 9) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        # the miasma is drawn in along straight lines to the maw while he breathes in
        mcx, mcy = left + fx * w, top + fy * h
        if drawing > 0.02:
            for x0, y0, ph in sucked:
                v = (t * 4 + ph) % 1
                stamp(cv, x0 + (mcx - x0) * v ** 1.6, y0 + (mcy - y0) * v ** 1.6, np.array((190.0, 205, 110)) * math.sin(math.pi * v) * 0.8 * drawing, 1.3)
        # he swells with the same breath, feet planted; the belly leads and the head lags
        lag = breath_at((t - 0.03) % 1)
        def body(x, y):
            g = np.exp(-((x - 0.5) / 0.38) ** 2 - ((y - 0.70) / 0.26) ** 2)
            planted = np.clip((0.98 - y) / 0.16, 0, 1)
            k = 0.14 * b * g * planted
            bx = -(x - 0.5) * w * k
            by = -(y - 0.70) * h * k
            up = np.clip(0.72 - y, 0, 0.72) / 0.72
            tall = 15 * lag * up ** 0.8 - 12 * hit * up ** 0.8
            return bx + 4 * math.sin(2 * math.pi * t) * up, by + tall
        cough = hit * math.cos(s * 24)
        head = dict(c=(0.47, 0.42), r=(0.14, 0.17), p=(0.50, 0.52), a=-5 * lag + 8 * cough + 2 * wave(t, 2, phi_phase(1)), d=(0, -0.02 * hit), lit=0.15 * hit, soft=0.5)
        parts = [
            dict(c=(0.34, 0.14), r=(0.12, 0.26), p=(0.36, 0.0), a=2.5 * wave(t, 2, phi_phase(2)) + 3 * (1 - lag), soft=0.5),                  # the left tendril hangs from the roof
            dict(c=(0.85, 0.24), r=(0.20, 0.27), p=(0.97, 0.05), a=-4 * wave(t, 2, phi_phase(3)) + 5 * lag, soft=0.5),                         # the right pulls taut
            dict(c=(0.13, 0.70), r=(0.17, 0.14), p=(0.30, 0.62), a=8 * lag * -1 + 8 * cough + 3 * wave(t, 1, phi_phase(4)), soft=0.45),         # his claw
            dict(c=(0.86, 0.75), r=(0.14, 0.14), p=(0.72, 0.68), a=7 * lag - 7 * cough + 3 * wave(t, 1, phi_phase(5)), soft=0.45),
            head]
        fill = np.clip(lag * 1.1, 0, 1)
        mawglow = maw * (0.25 + 1.5 * fill + 2.0 * hit)
        rig.rgb = np.clip((choked * (1 - fill) + base * fill) * (1 + 0.1 * fill) + mawglow * np.array([160.0, 170, 40]) + np.array([60.0, 30, 10]) * hit * (rig.al > 0), 0, 255)
        rgb, al = rig.pose(parts, light=(0.7 * wave(t, 1), -0.5), relief=0.3, bend=body)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        mp = land(rig, rig.at(head, fx, fy), body)
        mx, my = left + mp[0], top + mp[1]
        glow(cv, mx, my, 7 + 7 * fill + 16 * hit, (225, 235, 90), 0.18 + 0.4 * fill + 0.9 * hit)
        # exhale: the maw spits spores, the near vents roar in front of him
        if t >= inhale_end and s < 0.32:
            spat.draw(cv, min(0.99, s / 0.32), lambda v: np.array((215, 230, 120)) * (1 - v) ** 0.8, 1.7)
        for i, (x, y, hh, wd, dl) in enumerate(vents):
            u = since(t, inhale_end + dl)
            pw = sm(0.0, 0.04, u) * (1 - sm(0.15, 0.32, u)) if t >= inhale_end else 0.0
            if pw > 0.02 and i < 2:
                jet(cv, x, y, hh * (0.5 + 0.5 * pw), wd, climb(1633, 12, t, 3, 0, 18), pw, (180, 200, 90))
        # the miasma settles low and thins as he draws it back
        low = np.clip((YY - 176) / 40, 0, 1) * np.clip(noise(1690, 18, t) - 0.28, 0, 1) * 1.7 * (0.25 + 0.75 * (1 - b)) * EDGE[..., 0]
        cv = cv + low[..., None] * np.array([92.0, 112, 54]) * (0.45 + blow)
        return np.clip(cv, 0, 255)
    return "sire", "the lung", scene

RECIPES = [kq_molt, kraken_rise, leviathan_stream, sarachnis_brood, thermy_core, sire_breath]
