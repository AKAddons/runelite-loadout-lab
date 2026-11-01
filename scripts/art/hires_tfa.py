"""Hi-res recipe, the Fractured Archive, in the raid films' manner (hires_raids.py): a library that breaks like
glass, what has grown through it (roots, moss, glowing crystal), and the shards coming back together for the next lap."""
from hires_kit import *
from hires_batch2c import sm, cut_sprite, lum_of, up_of, shadow, shake
from hires_batch3a import figure, stand, spot
from hires_raids import FILM, BAR, letterbox, blackout, push, play, rays

def overgrowth(seed, roots=9, crystals=9):
    """what has taken the archive: roots down from the dark, moss, and clusters of glowing crystal. rgb, alpha, glow."""
    r = np.random.RandomState(seed)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    def grow(x, y, a, width, left):
        while left > 0 and width > 0.7:
            nx, ny = x + math.cos(a) * 9, y + math.sin(a) * 9
            d.line((x, y, nx, ny), fill=(50, 38, 22, 255), width=int(width + 2))
            d.line((x - width * 0.25, y, nx - width * 0.25, ny), fill=(122, 108, 56, 255), width=max(1, int(width * 0.4)))
            x, y, a = nx, ny, a + (r.rand() - 0.5) * 0.7
            width *= 0.95; left -= 1
            if r.rand() < 0.2: grow(x, y, a + (r.rand() - 0.5) * 1.6, width * 0.7, left * 0.6)
    for i in range(roots):
        k = i / max(1, roots - 1)
        if i % 3 == 2: grow(0 if i % 2 else W, 20 + 170 * r.rand(), (0.2 if i % 2 else math.pi - 0.2) + 0.5 * (r.rand() - 0.5), 4.5, 12)
        else: grow(W * k, -2, math.pi / 2 + 0.5 * (0.5 - k), 6 - 2 * r.rand(), 15)
    root = np.asarray(im, float)
    glowing = Image.new("L", (W, H), 0)
    g = ImageDraw.Draw(glowing)
    for i in range(crystals):                                                    # clusters low in the corners and along the walls, pointing in
        side = i % 2
        x = (6 + 54 * r.rand()) if side else (W - 6 - 54 * r.rand())
        y = H - BAR - 6 - 70 * r.rand() ** 1.5
        for j in range(4):
            lean = (0.75 if side else -0.75) * (0.2 + r.rand()) + (r.rand() - 0.5) * 0.5
            tall, wide = (20 + 34 * r.rand()) * (1 - j * 0.14), 2.5 + 2.5 * r.rand()
            bx, by = x + (j - 1.5) * 9, y
            tx, ty = bx + lean * tall, by - tall
            d.polygon([(bx - wide, by), (bx, by + 2), (tx, ty), (bx - wide * 0.9 + lean * tall * 0.7, by - tall * 0.7)], fill=(28, 112, 92, 255))
            d.polygon([(bx + wide, by), (bx, by + 2), (tx, ty), (bx + wide * 0.9 + lean * tall * 0.7, by - tall * 0.7)], fill=(130, 240, 190, 255))
            g.polygon([(bx - wide, by), (bx + wide, by), (tx, ty)], fill=255)
    a = np.asarray(im, float)
    moss = (fbm(seed + 1, (10, 5, 2)) > 0.56)[..., None] * (a[..., 3:] > 0) * (a[..., 1:2] < 120)
    rgb = a[..., :3] * (1 - moss) + np.array((62.0, 112, 44)) * moss
    halo = np.asarray(glowing.filter(ImageFilter.GaussianBlur(11)), float)[..., None] / 255
    return rgb, a[..., 3:] / 255, halo * np.array((70.0, 255, 170)) * 0.5

GROWTH = overgrowth(3503)

def library(travel, light=1.0):
    """one aisle to a vanishing point: shelves of book spines both sides, flags below, a lit end"""
    cx, cy = W / 2, H / 2 + 2
    dx, dy = (XX - cx) / 70, (YY - cy) / 92
    side = np.abs(dx) > np.abs(dy)
    depth = 1 / np.maximum(np.maximum(np.abs(dx), np.abs(dy)), 0.05)
    z = depth + travel
    across = np.where(side, dy / np.maximum(np.abs(dx), 0.02), dx / np.maximum(np.abs(dy), 0.02))
    shelf = np.floor((across + 1) * 3)
    spine = np.floor(z * 6).astype(int)
    r = _BOOKS[(shelf.astype(int) * 7 + spine) % 64]
    wood = (((across + 1) * 3) % 1 < 0.14)[..., None]
    gap = (r[..., 3:] < 0.14)
    books = r[..., :3]
    wall = books * (1 - gap) + np.array([6.0, 12, 8]) * gap
    wall = wall * (1 - wood) + np.array([46.0, 40, 22]) * wood
    upright = (z * 0.5 % 1 < 0.07)[..., None]                                    # the bay posts
    wall = wall * (1 - upright) + np.array([52.0, 46, 26]) * upright
    tiles = (((np.floor(z * 1.2) + np.floor(across * 3)) % 2) * 0.35 + 0.65)[..., None]
    moss = np.clip(fbm(3505, (12, 6, 3)) * 2.2 - 0.7, 0, 1)[..., None]
    flat = (np.array([66.0, 62, 44]) * (1 - moss) + np.array([48.0, 104, 46]) * moss) * tiles
    flat = np.where((YY < cy)[..., None], np.array([8.0, 18, 14]), flat)
    img = np.where(side[..., None], wall, flat)
    fall = np.clip(1.7 / depth, 0.1, 1)[..., None]
    end = 9 + 3 * light
    glowed = np.exp(-(np.hypot(XX - cx, YY - cy) / (end * 2.1)) ** 2)[..., None] * np.array([110.0, 215, 150])
    rgb, al, halo = GROWTH
    return (img * fall + glowed) * (1 - al) * light + rgb * al * light + halo * light * (0.8 + 0.2 * math.sin(travel * 18))

_r = np.random.RandomState(3501)
_cols = np.array([(72, 58, 36), (38, 72, 54), (54, 100, 56), (100, 88, 46), (36, 84, 80), (82, 66, 44)], float)
_BOOKS = np.hstack([_cols[_r.randint(0, 6, 64)] * (0.6 + 0.6 * _r.rand(64, 1)), _r.rand(64, 1)])

def voronoi(n, seed):
    """shards: the frame cut by nearest of n points, more of them near the middle"""
    r = np.random.RandomState(seed)
    pts = [(W / 2 + 0.5 * W * math.sqrt((i + 0.5) / n) * math.cos(i * GOLDEN_ANGLE), H / 2 + 0.55 * H * math.sqrt((i + 0.5) / n) * math.sin(i * GOLDEN_ANGLE)) for i in range(n)]
    d = np.stack([(XX - x) ** 2 + (YY - y) ** 2 for x, y in pts])
    labels = d.argmin(0)
    spins = r.rand(n) * 2 - 1
    return pts, labels, spins

def shatter(cv, img, amt, labels, pts, spins, edge):
    """lays img over cv in shards, flown out by amt (0 whole, 1 scattered); each tilts, edges catch light"""
    for i, (x, y) in enumerate(pts):
        m = labels == i
        ys, xs = np.nonzero(m)
        if len(ys) == 0: continue
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        a = np.dstack([np.clip(img[y0:y1, x0:x1], 0, 255), m[y0:y1, x0:x1] * 255.0]).astype(np.uint8)
        pad = 24
        im = Image.new("RGBA", (x1 - x0 + 2 * pad, y1 - y0 + 2 * pad), (0, 0, 0, 0))
        im.paste(Image.fromarray(a, "RGBA"), (pad, pad))
        if amt > 0.001: im = im.rotate(38 * spins[i] * amt, Image.BILINEAR)
        arr = np.asarray(im, float)
        al = (arr[..., 3:] > 128).astype(float)
        vx, vy = x - W / 2, y - H / 2
        off = 0.1 + 0.9 * (math.hypot(vx, vy) / 150)
        px = int(x0 - pad + vx * 1.15 * amt * off); py = int(y0 - pad + vy * 1.15 * amt * off)
        over(cv, arr[..., :3], al, px, py)
        if edge > 0.01:
            e = np.asarray(Image.fromarray((al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)), float) / 255
            ring_ = (al[..., 0] - e)[..., None]
            over(cv, np.ones_like(arr[..., :3]) * np.array(edge_col) * edge, ring_, px, py)

edge_col = (190.0, 255, 215)

def void_backdrop():
    """what is behind the books: green dark, thin glow, spores on golden steps, the roots and crystals that came through"""
    n = fbm(3511, (46, 20, 8))
    img = ramp([(4, 14, 14), (8, 36, 30), (22, 84, 62)], np.clip((n - 0.3) * 2.0, 0, 1)).copy()
    for i, (x, y) in enumerate(phyllotaxis(W / 2, H / 2, 150, 11.5)):
        if 0 <= x < W and 0 <= y < H: stamp(img, x, y, np.array((170.0, 255, 200)) * (0.35 + 0.65 * ((i * 7) % 5) / 5), 1.0)
    rgb, al, halo = overgrowth(3507, 7, 7)
    return img * (1 - al) + rgb * 0.75 * al + halo * 0.7

def tfa_film():
    guard, (gh, gw) = figure("Archive_Guardian.png", 320, 480, 60, exposure=0.5, gamma=1.1, sat=1.2, healed=3)
    xarth, (xh, xw) = figure("Xarth.png", 170, H - 2 * BAR - 2, 60, exposure=0.82, gamma=0.95, sat=1.2, healed=3)
    PH = [figure(n, 150, 190, 60, exposure=0.68, gamma=1.0, sat=1.15, healed=3) for n in ("Ocellus.png", "Ocellus_(phase_2).png", "Ocellus_(phase_3).png")]
    last, (lh, lw) = figure("Ocellus_(phase_3).png", 240, H - 2 * BAR + 18, 60, exposure=0.85, gamma=0.92, sat=1.2, healed=3)
    void = void_backdrop()
    pts, labels, spins = voronoi(23, 3521)
    pages = Motes(3531, 22, lambda r: (W * r.rand(), H * 0.3 + 90 * r.rand(), 26 * (r.rand() - 0.5), -40 - 60 * r.rand(), 30))
    drift = [(W * ((i * 0.618) % 1), 30 + ((i * 37) % 170), i) for i in range(14)]
    burn = Motes(3533, 34, lambda r: (W * r.rand(), H - BAR - 8, 30 * (r.rand() - 0.5), -170 * (0.5 + r.rand()), 0))
    # the three panes for Ocellus: two slanted cuts
    def pane(i):
        a, b = 72 + 18 * np.clip((YY - 120) / 120, -1, 1), 144 + 18 * np.clip((YY - 120) / 120, -1, 1)
        return ((XX < a) if i == 0 else ((XX >= a) & (XX < b)) if i == 1 else (XX >= b))
    PANE = [pane(i) for i in range(3)]
    TONE = [np.array((170.0, 220, 110)), np.array((100.0, 230, 150)), np.array((140.0, 255, 215))]
    FLAME = np.array((180.0, 80, 255))
    def paper(cv, x, y, f, colour):
        a = 6.0 * f
        line(cv, (x - 3 * math.cos(a), y - 2 * math.sin(a)), (x + 3 * math.cos(a), y + 2 * math.sin(a)), colour, 1.3)

    def a_aisle(u, t):
        # a quiet walk down the aisle; pages stir, then a crack runs across the picture
        travel = 1.6 * u
        cv = library(travel) * (0.55 + 0.45 * sm(0.0, 0.2, u))
        for x, y, i in drift:
            f = (t * 4 + i * 0.13) % 1
            paper(cv, x + 8 * math.sin(2 * math.pi * f), y + 30 * f, f * 6 + i, np.array((170.0, 255, 190)) * math.sin(math.pi * f) * 0.9)
        crack = sm(0.68, 0.94, u)
        if crack > 0:
            e = (np.abs(labels - np.roll(labels, 1, 0)) + np.abs(labels - np.roll(labels, 1, 1))) > 0
            d = np.hypot(XX - W / 2, YY - H / 2)
            cv += (e * (d < 200 * crack))[..., None] * np.array(edge_col) * 0.85
        return blackout(cv, 1 - sm(0.0, 0.1, u))

    def b_shatter(u, t):
        # it goes: the shards fly out and hang there, and the void shows between them
        cv = void.copy()
        amt = sm(0.0, 0.55, u) ** 0.8 * 0.72 + 0.28 * sm(0.5, 1.0, u)
        pic = library(1.6)
        # something opens its eyes behind the books
        gx, gy = W / 2 - 0.44 * gw, H / 2 - 0.27 * gh
        wake = sm(0.3, 0.9, u)
        if wake > 0:
            rgb, al = guard.pose([dict(c=(0.44, 0.27), r=(0.3, 0.26), p=(0.44, 0.32), a=2 * wave(t, 6), soft=0.5)], light=(0.5 * wave(t, 4), -0.7), relief=0.3)
            over(cv, rgb, al * wake, int(gx) - guard.pad, int(gy) - guard.pad)
        for x_, y_ in ((W / 2 - 34, H / 2 - 7), (W / 2 + 31, H / 2 - 7)):
            glow(cv, x_, y_, 6 + 5 * wake, (255, 50, 30), 1.3 * wake)
        shatter(cv, pic, amt, labels, pts, spins, 0.6 * (1 - amt * 0.6))
        flash = math.exp(-u * 12)
        return cv + flash * np.array(edge_col) * 0.8

    def c_guard(u, t):
        # the skull, close, among the slow shards; the vines reach and the eyes fill the frame
        cv = void.copy()
        left = W / 2 - 0.44 * gw + 7 * wave(t, 3); top = H / 2 - 0.27 * gh - 18 * sm(0.0, 1.0, u)
        def vines(x, y):
            v = np.clip((y - 0.42) / 0.58, 0, 1) + np.clip(np.abs(x - 0.45) - 0.28, 0, 1)
            return 7 * v * np.sin(2 * math.pi * (t * 5 + y * 2.2 + x)), 5 * v * np.cos(2 * math.pi * (t * 5 + x * 2.6))
        jaw = dict(c=(0.42, 0.42), r=(0.17, 0.09), p=(0.42, 0.36), d=(0, 0.024 * sm(0.45, 0.75, u)), soft=0.5)
        stand(cv, guard, [jaw], left, top, (0.7 * wave(t, 3), -0.7), vines, 0.5)
        rage = sm(0.4, 0.9, u)
        for fx_ in (0.335, 0.525):
            x, y = spot(guard, jaw, fx_, 0.27, left, top)
            glow(cv, x, y, 8 + 12 * rage, (255, 44, 24), 1.0 + 0.7 * rage)
            stamp(cv, x, y, (255, 230, 170), 2.5)
        rays(cv, W / 2, top + 0.27 * gh, 13, 170, (20, 84, 52), t, 0.3 * rage)
        # two hanging shards drift across, carrying the aisle
        pic = library(1.6)
        for i, (px_, py_) in enumerate(((40 + 40 * u, 60 + 16 * u), (170 - 50 * u, 190 - 16 * u))):
            m = poly_shard(px_, py_, 28 + 6 * i, i + t * 1.0 * 0)
            cv = cv * (1 - m) + pic * m
            e = np.clip(m - np.asarray(Image.fromarray((m[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)), float)[..., None] / 255, 0, 1)
            cv += e * np.array(edge_col) * 0.7
        return push(cv, 1 + 0.1 * u, 0.5, 0.4) * 1.0 + math.exp(-u * 9) * 120

    def poly_shard(x, y, r, phase):
        from hires_batch2c import poly
        return poly([(x + r * math.cos(a + phase), y + r * 1.25 * math.sin(a + phase)) for a in (0.2, 1.5, 2.7, 3.9, 5.2)])

    def d_xarth(u, t):
        # the archive burns: Xarth stands in a storm of burning pages
        cv = void * 0.9
        n = noise(3541, 10, t)
        low = np.clip((YY - 90) / 150, 0, 1)
        near = np.exp(-((XX - W / 2) / 62) ** 2)
        cv = cv + (low * near * n * 1.2)[..., None] * np.array([210.0, 84, 14])
        left, top = (W - xw) // 2, BAR + 2
        arm_l = dict(c=(0.14, 0.58), r=(0.2, 0.2), p=(0.28, 0.42), a=-6 * wave(t, 8), soft=0.45)
        arm_r = dict(c=(0.84, 0.58), r=(0.2, 0.2), p=(0.72, 0.42), a=6 * wave(t, 8, 0.2), soft=0.45)
        def flames(x, y):
            v = np.clip((0.3 - y) / 0.3, 0, 1)
            return 6 * v * np.sin(2 * math.pi * (t * 14 + x * 3)), 5 * v * np.cos(2 * math.pi * (t * 14 + x * 5))
        stand(cv, xarth, [arm_l, arm_r], left, top, (0.7 * wave(t, 6), -0.5), flames, 0.45)
        glow(cv, left + xw * 0.5, top + xh * 0.16, 16, (255, 150, 30), 0.7 + 0.2 * wave(t, 12))
        ex, ey = left + xw * 0.5, top + xh * 0.29
        for dx in (-6, 6): stamp(cv, ex + dx, ey, (255, 250, 200), 1.6)
        for t0, (x, y, vx, vy, _) in burn.p:                                     # pages, each burning away as it rises
            v = (t * 6 + t0) % 1
            c = ramp([(245, 235, 200), (255, 170, 50), (200, 50, 14), (40, 10, 6)], np.array(v))[:3]
            paper(cv, x + vx * v + 7 * math.sin(v * 9), y + vy * v, v * 9 + t0 * 9, c)
        return push(cv, 1.06 - 0.06 * u, 0.5, 0.7)

    def e_triptych(u, t):
        # three panes, one each: the hooded scholar, the blade, the thing in the book
        cv = void.copy() * 0.6
        for i, (rig, (hh, ww)) in enumerate(PH):
            at = 0.05 + i * 0.28
            on = sm(at, at + 0.06, u)
            if on <= 0: continue
            pic = void.copy() * 0.7 + np.exp(-((XX - (36 + 72 * i)) / 60) ** 2)[..., None] * TONE[i] * 0.2
            left = 36 + 72 * i - ww * (0.7, 0.5, 0.42)[i]; top = H - BAR - 2 - hh + 6 * (1 - on)
            hood = dict(c=(0.52, 0.17), r=(0.25, 0.15), p=(0.5, 0.3), a=3 * wave(t, 6, i * 0.2), soft=0.5)
            def robe(x, y):
                v = np.clip((y - 0.55) / 0.45, 0, 1)
                return 4 * v * np.sin(2 * math.pi * (t * 8 - y * 2)), 0 * y
            stand(pic, rig, [hood], left, top, (0.7 * wave(t, 4), -0.7), robe, 0.45)
            flash_ = math.exp(-max(0.0, u - at) * 20)
            pic = pic + TONE[i] * flash_ * 0.8
            m = (PANE[i] * on)[..., None]
            cv = cv * (1 - m) + pic * m
        for b_ in (1, 2):
            at = 0.05 + b_ * 0.28
            on = sm(at - 0.03, at, u)
            xs = (72 if b_ == 1 else 144) + 18 * np.clip((YY - 120) / 120, -1, 1)
            cv += ((np.abs(XX - xs) < 1.3) * on)[..., None] * np.array(edge_col)
        return cv

    def f_last(u, t):
        # the panes close on the last form, the purple flame up, a shock through everything
        cv = void.copy() * (0.8 + 0.3 * sm(0.0, 0.2, u))
        hit = math.exp(-u * 9)
        left = (W - lw) // 2 - 6; top = BAR - 8 + 6 * (1 - sm(0.0, 0.3, u))
        hood = dict(c=(0.48, 0.2), r=(0.26, 0.16), p=(0.48, 0.32), a=3 * wave(t, 6) + 6 * hit, soft=0.5)
        hand = dict(c=(0.86, 0.86), r=(0.14, 0.16), p=(0.74, 0.7), a=8 * wave(t, 8), soft=0.45)
        arm = dict(c=(0.14, 0.42), r=(0.16, 0.14), p=(0.27, 0.34), a=-6 * wave(t, 6, 0.3), soft=0.45)
        def robe(x, y):
            v = np.clip((y - 0.55) / 0.45, 0, 1)
            return 6 * v * np.sin(2 * math.pi * (t * 8 - y * 2)), 0 * y
        fx_, fy_ = left + 0.86 * lw, top + 0.86 * lh
        for i, rr in enumerate(phi_radii(16, 3)):
            polygon(cv, fx_, fy_, rr + 10 * wave(t, 6, i * 0.2), 5, TONE[2] * (0.7 - i * 0.15), 2 * math.pi * t * 2 * (1 if i % 2 else -1), 1.0, 1.1)
        stand(cv, last, [arm, hood, hand], left, top, (0.7 * wave(t, 4), -0.7), robe, 0.5)
        glow(cv, fx_, fy_, 18, FLAME, 0.75 + 0.3 * wave(t, 8))
        ring(cv, W / 2, H / 2, 10 + 260 * sm(0.0, 0.45, u), 1.0, 7, TONE[2] * (1 - sm(0.0, 0.45, u)))
        cv += hit * np.array(edge_col) * 0.7
        return shake(cv, 3 * hit * wave(t, 40), 3 * hit * wave(t, 37))

    def g_mend(u, t):
        # the shards fly home; the crack lines fade; only the aisle is left
        cv = f_last(1.0, t) * (1 - sm(0.0, 0.3, u)) + void * sm(0.0, 0.3, u)
        amt = (1 - sm(0.05, 0.86, u)) ** 1.1
        shatter(cv, library(0.0, 0.8), amt, labels, pts, spins, 0.7 * amt)
        return blackout(cv, sm(0.86, 1.0, u))

    SHOTS = [(0.0, 0.2, a_aisle), (0.2, 0.32, b_shatter), (0.32, 0.46, c_guard), (0.46, 0.6, d_xarth), (0.6, 0.78, e_triptych), (0.78, 0.9, f_last), (0.9, 1.01, g_mend)]
    return "tfa", "the fracture", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

RECIPES = [tfa_film]
