"""Hi-res recipes, holiday films (HOLIDAYS.md): Christmas, New Year, Valentine. No boss sprites here, so the heroes are
modelled: shaded balls and faceted polygons, which give the same chunky lit look."""
from hires_holiday_card import celebrating
import hires_holiday_card as Wear
from hires_kit import *
from hires_batch2c import sm, shake, shadow, poly
from hires_last import ink
from hires_raids import FILM, BAR, letterbox, blackout, push, play, rays

# ---- the modelling kit --------------------------------------------------------------
def ball(cv, x, y, rx, ry, colour, light=(-0.5, -0.65), steps=6, spec=0.0, lo=0.34):
    """a posterised lit ellipsoid: PS2 facets rather than a smooth gradient"""
    x0, x1, y0, y1 = max(0, int(x - rx) - 1), min(W, int(x + rx) + 2), max(0, int(y - ry) - 1), min(H, int(y + ry) + 2)
    if x0 >= x1 or y0 >= y1 or rx <= 0 or ry <= 0: return
    nx, ny = (XX[y0:y1, x0:x1] - x) / rx, (YY[y0:y1, x0:x1] - y) / ry
    d2 = nx * nx + ny * ny; m = d2 <= 1
    nz = np.sqrt(np.clip(1 - d2, 0, 1))
    lz = math.sqrt(max(0.05, 1 - light[0] ** 2 - light[1] ** 2))
    v = np.clip(nx * light[0] + ny * light[1] + nz * lz, 0, 1)
    v = np.round(v * steps) / steps
    shade = lo + (1.12 - lo) * v
    col = np.array(colour, float) * shade[..., None]
    if spec: col = col + (np.clip(v - 0.9, 0, 1) * 10 * spec)[..., None] * 255
    cv[y0:y1, x0:x1][m] = np.clip(col, 0, 255)[m]

def fill(cv, pts, colour):
    m = poly([(float(a), float(b)) for a, b in pts])
    cv[:] = cv * (1 - m) + np.array(colour, float) * m

def rot_pts(pts, x, y, s, a=0.0, sy=None):
    ca, sa = math.cos(a), math.sin(a); sy = s if sy is None else sy
    return [(x + px * s * ca - py * sy * sa, y + px * s * sa + py * sy * ca) for px, py in pts]

def star_pts(x, y, r, n=5, rot=-math.pi / 2, inner=0.42):
    out = []
    for i in range(n * 2):
        rr = r if i % 2 == 0 else r * inner
        a = rot + math.pi * i / n
        out.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    return out

SNOW = lambda seed, n: Motes(seed, n, lambda r: (r.rand() * W * 1.15 - 12, -8, -10 + 20 * r.rand(), H + 16, 0))
def snowfall(cv, far, near, t):
    far.draw(cv, (t * 4) % 1, lambda v: (90, 100, 120), 1.5)
    near.draw(cv, (t * 7) % 1, lambda v: (215, 225, 245), 2.5)

def stars(seed, n, top=20, bottom=130):
    r = np.random.RandomState(seed)
    return [(r.rand() * W, top + r.rand() ** 1.5 * (bottom - top), 0.35 + 0.65 * r.rand()) for _ in range(n)]

# ---- Christmas: the tree lights, the goblin, the cracker ------------------------------
def snowy_village():
    """a village in snow at night, all well below the tree's brightness"""
    img = ramp([(10, 14, 32), (18, 24, 50), (34, 40, 70), (48, 52, 80)], np.clip((YY - 18) / 150, 0, 1)).copy()
    for x, y, b in stars(4101, 60, 22, 110):
        stamp(img, x, y, np.array((130.0, 136, 160)) * b, 1.1)
    far = ridge(4103, 128, 24, (60, 26))
    m = (YY > far[None, :])[..., None]
    cap = np.clip(1 - (YY - far[None, :]) / 12, 0, 1)[..., None]
    img = img * (1 - m) + (np.array([28.0, 34, 54]) + cap * np.array([38.0, 42, 48])) * m
    for x, base, wid, hi in ((-6, 170, 44, 28), (36, 176, 32, 22), (153, 176, 34, 24), (182, 170, 44, 30)):
        fill(img, [(x, base), (x, base - hi), (x + wid, base - hi), (x + wid, base)], (46, 34, 34))   # walls
        fill(img, [(x - 4, base - hi + 1), (x + wid / 2, base - hi - wid * 0.46), (x + wid + 4, base - hi + 1)], (84, 92, 116))   # the snowed roof
        fill(img, [(x + wid / 2, base - hi - wid * 0.46), (x + wid + 4, base - hi + 1), (x + wid / 2 + 3, base - hi + 1)], (62, 68, 92))
        cx, wy = x + wid / 2, base - hi * 0.6
        fill(img, [(cx - 6, wy - 5), (cx + 6, wy - 5), (cx + 6, wy + 6), (cx - 6, wy + 6)], (176, 120, 50))
        ink(img, (cx, wy - 5), (cx, wy + 6), (46, 30, 26), 0.7)
        glow(img, cx, wy, 13, (70, 44, 14), 0.6)
    snow = (np.array([58.0, 66, 92]) * (0.72 + 0.5 * fbm(4107, (24, 10, 4))[..., None]))
    ln = 172 + 5 * np.sin(XX * 0.05)
    m = (YY > ln)[..., None]
    img = img * (1 - m) + snow * m
    return img

def make_tree(cx, base, h, seed=4111):
    """the tree cached as a full-frame layer: four tiers of needles lit from the left, snow on each hem, and its lights"""
    rgb, cover = np.zeros((H, W, 3)), np.zeros((H, W, 1))
    tex = fbm(seed, (6, 3))
    wide = h * 0.40
    tiers = [(0.00, 0.42, 0.42), (0.20, 0.62, 0.64), (0.40, 0.82, 0.84), (0.60, 1.00, 1.00)]
    for top, bot, wf in tiers:
        ty, by, hw = base - h + h * top, base - h + h * bot, wide * wf
        teeth = [(cx - hw, by)]
        for i in range(1, 8):
            teeth.append((cx - hw + 2 * hw * i / 8, by + (5 if i % 2 else -1) * (h / 150)))
        m = poly([(cx, ty)] + [(cx + hw, by)] + teeth[::-1])
        side = np.clip(0.5 - (XX - cx) / (hw * 1.2), 0, 1)
        band = np.clip((YY - ty) / max(1, by - ty), 0, 1)
        shade = 0.42 + 0.75 * side + 0.25 * (tex - 0.5) + 0.18 * band
        shade = np.round(shade * 6) / 6
        col = np.array([40.0, 120, 66])[None, None] * shade[..., None]
        hem = np.clip(1 - (by + 4 - YY) / (7 * h / 150), 0, 1)[..., None]
        col = col * (1 - hem) + np.array([175.0, 190, 205]) * hem * (0.5 + 0.6 * side[..., None])
        rgb = rgb * (1 - m) + col * m; cover = np.maximum(cover, m)
    lights = []
    n = 56
    for i in range(n):
        s = i / n
        y = base - h * 0.05 - s * h * 0.84
        hw = wide * (1 - s * 0.8) * 0.9
        a = s * 2 * math.pi * 4.5
        if math.cos(a) > -0.25:
            lights.append((cx + hw * math.sin(a), y, i * 0.37 % 1, i % 3, 1 - s))
    return rgb, cover, lights, (cx, base - h, h)

BAUBLES = [(255, 70, 60), (255, 205, 80), (110, 190, 255)]
def tree(cv, made, lit, t, burn=1.0):
    """lit climbs 0..1 from the base, the star takes last; burn dims the whole thing for a dark start"""
    rgb, cover, lights, (cx, top, h) = made
    cv[:] = cv * (1 - cover) + rgb * (0.42 + 0.58 * burn) * cover
    k = h / 150
    for x, y, ph, c, up in lights:
        v = sm(up - 0.02, up + 0.1, lit)
        if v <= 0: continue
        tw = 0.72 + 0.28 * wave(t, 18, ph)
        glow(cv, x, y, 5 * k, BAUBLES[c], 0.4 * v * tw)
        ball(cv, x, y, 3.6 * k, 3.6 * k, np.array(BAUBLES[c], float) * (0.5 + 0.5 * v * tw) * 1.15, spec=0.6)
    s = sm(0.82, 1.0, lit)
    r = 13 * min(k, 1.4) * (1 + 0.5 * math.exp(-max(0.0, lit - 0.9) * 40) * s)
    fill(cv, star_pts(cx, top - 2, r), np.array((110.0, 90, 36)) + np.array((145.0, 140, 90)) * s)
    if s > 0:
        glow(cv, cx, top - 2, 13 * k, (255, 190, 90), 0.3 * s)
        rays(cv, cx, top - 2, 10, 60 * k, (34, 26, 10), t, 0.1)
        fill(cv, star_pts(cx, top - 2, r * 0.5), (255, 248, 210))

def present(cv, x, y, w, h, colour, ribbon, lid=0.0):
    """a box standing on (x, y): front, side, top, and a ribbon and bow. lid lifts the lid off"""
    c, rb = np.array(colour, float), np.array(ribbon, float)
    d = w * 0.25
    fill(cv, [(x, y), (x + w, y), (x + w, y - h), (x, y - h)], c)
    fill(cv, [(x + w, y), (x + w + d, y - d * 0.6), (x + w + d, y - h - d * 0.6), (x + w, y - h)], c * 0.58)
    fill(cv, [(x + w * 0.42, y), (x + w * 0.58, y), (x + w * 0.58, y - h), (x + w * 0.42, y - h)], rb)
    lf = lid * h * 0.9
    fill(cv, [(x - 1, y - h - lf), (x + w + 1, y - h - lf), (x + w + d + 1, y - h - d * 0.6 - lf), (x + d - 1, y - h - d * 0.6 - lf)], c * 1.22)
    fill(cv, [(x + w * 0.42, y - h - lf), (x + w * 0.58, y - h - lf), (x + w * 0.58 + d, y - h - d * 0.6 - lf), (x + w * 0.42 + d, y - h - d * 0.6 - lf)], rb)
    bx, by = x + w * 0.5 + d * 0.5, y - h - d * 0.3 - lf
    ball(cv, bx - w * 0.15, by - 3, w * 0.16, w * 0.11, rb); ball(cv, bx + w * 0.15, by - 3, w * 0.16, w * 0.11, rb)
    ball(cv, bx, by - 2, w * 0.08, w * 0.08, rb * 1.2)

def santa_hat(cv, x, y, s, flop):
    """brim on (x, y); flop leans the tail and its bobble"""
    tipx, tipy = x + (0.95 + 0.2 * flop) * s, y - (0.72 - 0.25 * flop) * s
    fill(cv, [(x - 0.72 * s, y), (x - 0.15 * s, y - 1.05 * s), (x + 0.35 * s, y - 1.0 * s), (tipx, tipy), (tipx - 0.1 * s, tipy + 0.22 * s), (x + 0.45 * s, y - 0.48 * s), (x + 0.72 * s, y)], (232, 48, 44))
    fill(cv, [(x + 0.35 * s, y - 1.0 * s), (tipx, tipy), (tipx - 0.1 * s, tipy + 0.22 * s), (x + 0.45 * s, y - 0.48 * s), (x + 0.72 * s, y), (x + 0.2 * s, y)], (168, 26, 34))
    ball(cv, x, y, 0.86 * s, 0.22 * s, (250, 250, 250), steps=4, lo=0.6)
    ball(cv, tipx, tipy + 0.08 * s, 0.24 * s, 0.24 * s, (255, 255, 255), steps=4, lo=0.6)

HATS = [(235, 50, 50), (250, 205, 50), (70, 215, 90), (70, 130, 250), (190, 80, 235), (245, 245, 245)]
PHAT = [(-1, 0.5), (-1.08, -0.55), (-0.52, -0.02), (0, -0.92), (0.52, -0.02), (1.08, -0.55), (1, 0.5)]
def party_hat(cv, x, y, s, colour, a=0.0):
    """the paper crown: three peaks, a folded band"""
    c = np.array(colour, float)
    fill(cv, rot_pts(PHAT, x, y, s, a), c)
    fill(cv, rot_pts([(-1, 0.5), (-1.02, 0.08), (1.02, 0.08), (1, 0.5)], x, y, s, a), c * 0.62)
    fill(cv, rot_pts([(-0.52, -0.02), (0, -0.92), (0.0, -0.02)], x, y, s, a), np.minimum(c * 1.25, 255))

def goblin(cv, x, y, s, t, hat="santa", grin=1.0, blink=0.0, look=0.0, ear=0.0, flop=0.0, body=True, hands=()):
    """A goblin of Gielinor by way of a toy: a big green head, huge ears, a toothy grin. (x, y) is the head's centre, s its radius"""
    G = np.array((104.0, 190, 76))
    ey = y + 0.92 * s
    if body:
        ball(cv, x, y + 1.35 * s, 0.72 * s, 0.72 * s, (150, 92, 44))
        ink(cv, (x - 0.65 * s, y + 1.45 * s), (x + 0.65 * s, y + 1.45 * s), (70, 40, 20), max(0.8, 0.07 * s))
        for sx in (-1, 1):
            ball(cv, x + sx * 0.38 * s, y + 2.15 * s, 0.34 * s, 0.2 * s, (80, 60, 44))
    for hx, hy in hands:
        ax, ay = x + (0.6 if hx > x else -0.6) * s, y + 1.15 * s
        ink(cv, (ax, ay), (hx, hy), G * 0.75, max(1.5, 0.15 * s))
        ball(cv, hx, hy, 0.25 * s, 0.25 * s, G)
    for sx in (-1, 1):
        w = ear * sx
        fill(cv, [(x + sx * 0.72 * s, y - 0.32 * s), (x + sx * 1.95 * s, y - (0.72 + 0.28 * ear) * s), (x + sx * 0.82 * s, y + 0.38 * s)], G * 0.78)
        fill(cv, [(x + sx * 0.85 * s, y - 0.12 * s), (x + sx * 1.62 * s, y - (0.56 + 0.22 * ear) * s), (x + sx * 0.88 * s, y + 0.2 * s)], (190, 110, 90))
    ball(cv, x, y, s, 0.92 * s, G, spec=0.15)
    for sx in (-1, 1):
        ex = x + sx * 0.38 * s
        ball(cv, ex, y - 0.14 * s, 0.25 * s, 0.3 * s * max(0.12, 1 - blink), (255, 250, 210), steps=3, lo=0.8)
        if blink < 0.6:
            ball(cv, ex + look * 0.09 * s, y - 0.1 * s, 0.11 * s, 0.15 * s, (16, 12, 10), steps=1, lo=1.0)
        ink(cv, (ex - 0.26 * s, y - (0.5 - 0.1 * sx * 0) * s - 0.08 * sx * (-1) * s * 0), (ex + 0.26 * s, y - 0.5 * s + (0.06 * s * sx)), (36, 70, 26), max(0.8, 0.07 * s))
    ball(cv, x + look * 0.08 * s, y + 0.2 * s, 0.17 * s, 0.14 * s, G * 0.72)
    wid, dep = (0.42 + 0.2 * grin) * s, (0.1 + 0.26 * grin) * s
    mouth = [(x + wid * math.cos(a), y + 0.38 * s + dep * math.sin(a) * (1 if math.sin(a) > 0 else 0.2)) for a in np.linspace(0, 2 * math.pi, 18)]
    fill(cv, mouth, (40, 12, 16))
    for f in (-0.55, 0.0, 0.55):
        tx = x + f * wid
        fill(cv, [(tx - 0.09 * s, y + 0.36 * s), (tx + 0.09 * s, y + 0.36 * s), (tx, y + 0.38 * s + 0.65 * dep)], (250, 250, 225))
    if hat == "santa": santa_hat(cv, x, y - 0.74 * s, s * 0.95, flop)
    elif hat is not None: party_hat(cv, x, y - 1.02 * s, s * 0.62, hat)

def cracker_half(cv, x, cy, sgn, length, torn=0.0):
    """x is the middle of the cracker, sgn which way this half runs: tube, gold bands, a pinched neck, a flared end"""
    r = 15
    bx = x + sgn * length * 0.62; nx = x + sgn * length * 0.74; fx = x + sgn * length
    def lit(c):
        v = np.clip(1 - (YY - cy + 5) ** 2 / (r * r * 1.1), 0, 1)
        return np.array(c, float) * (0.42 + 0.72 * (np.round(v * 5) / 5))[..., None]
    jag = (lambda i: (x + (torn * sgn * (-3 if i % 2 else 5)), cy - r + i * 2 * r / 6))
    edge = [jag(i) for i in range(7)]
    m = poly(edge + [(bx, cy + r), (bx, cy - r)])
    cv[:] = cv * (1 - m) + lit((240, 36, 44)) * m
    xa = x + sgn * length * 0.26
    m = poly([(xa, cy - r), (xa + sgn * 9, cy - r), (xa + sgn * 9, cy + r), (xa, cy + r)])
    cv[:] = cv * (1 - m) + lit((255, 250, 235)) * m
    xa = x + sgn * length * 0.54
    m = poly([(xa, cy - r), (xa + sgn * 5, cy - r), (xa + sgn * 5, cy + r), (xa, cy + r)])
    cv[:] = cv * (1 - m) + lit((255, 205, 70)) * m
    m = poly([(bx, cy - r), (nx, cy - 6), (nx, cy + 6), (bx, cy + r)])
    cv[:] = cv * (1 - m) + lit((180, 24, 34)) * m
    teeth = [(fx + sgn * (5 if i % 2 else 0), cy - 17 + i * 34 / 8) for i in range(9)]
    m = poly([(nx, cy - 6)] + teeth + [(nx, cy + 6)])
    cv[:] = cv * (1 - m) + lit((250, 215, 110)) * m
    return fx

def tree_bokeh(cv, seed, t, n=16):
    r = np.random.RandomState(seed)
    for i in range(n):
        x, y, c = r.rand() * W, 26 + r.rand() * 190, r.randint(3)
        glow(cv, x, y, 10 + 8 * r.rand(), BAUBLES[c], 0.2 + 0.08 * wave(t, 8, i * 0.17))

def christmas_film():
    night = snowy_village()
    wide = make_tree(W / 2, 192, 150)
    big = make_tree(W / 2 + 70, 330, 330, 4113)
    far, near = SNOW(4121, 34), SNOW(4123, 20)
    confetti_r = np.random.RandomState(4125)
    paper = [(confetti_r.rand() * 2 * math.pi, 60 + 170 * confetti_r.rand(), confetti_r.randint(6), confetti_r.rand()) for _ in range(90)]
    room = np.array([18.0, 22, 44]) * (0.8 + 0.5 * fbm(4127, (24, 10))[..., None])

    def floor_snow(cv, y):
        m = (YY > y + 3 * np.sin(XX * 0.06))[..., None]
        cv[:] = cv * (1 - m) + np.array([70.0, 80, 108]) * (0.8 + 0.5 * fbm(4129, (20, 8, 4))[..., None]) * m

    def pile(cv, y, lid=0.0):
        present(cv, 56, y + 8, 34, 26, (60, 110, 230), (250, 215, 90))
        present(cv, 124, y + 10, 40, 30, (225, 60, 70), (250, 245, 230), lid)
        present(cv, 92, y + 14, 28, 20, (60, 190, 110), (240, 70, 70))

    def a_village(u, t):
        # dark first. the lights climb the tree from the snow, and the star takes last
        lit = sm(0.26, 0.92, u)
        cv = night * (0.72 + 0.28 * lit)
        cv += np.exp(-((XX - W / 2) ** 2 + (YY - 120) ** 2) / (2 * 62.0 ** 2))[..., None] * np.array([58.0, 36, 14]) * lit
        tree(cv, wide, lit, t, 0.25 + 0.75 * lit)
        pile(cv, 186)
        snowfall(cv, far, near, t)
        return blackout(push(cv, 1 + 0.08 * u, 0.5, 0.7), 1 - sm(0.0, 0.14, u))

    def b_goblin(u, t):
        # up close at the foot of the tree. one blink, the head pops out behind a present, a look round, then the cracker comes up
        cv = room.copy()
        tree(cv, big, 1.0, t)
        cv *= 0.52
        floor_snow(cv, 186)
        pop = sm(0.1, 0.28, u)
        over = math.sin(min(1.0, sm(0.1, 0.34, u)) * math.pi) * 10
        gx = 84 + 3 * wave(t, 6)
        gy = 240 - 128 * pop - over
        look = 1.6 * math.sin(2 * math.pi * sm(0.34, 0.7, u)) if u < 0.7 else 0.0
        raise_ = sm(0.72, 0.9, u)
        shadow(cv, gx, 192, 52, 8, 0.5)
        glow(cv, gx, gy - 10, 58, (255, 150, 70), 0.3 * pop)
        goblin(cv, gx, gy, 38, t, "santa", grin=0.4 + 0.6 * sm(0.5, 0.75, u), blink=1.0 if 0.46 < u < 0.5 else 0.0, look=look, ear=0.6 * wave(t, 12) * pop, flop=wave(t, 8))
        if raise_ > 0:
            cy = gy + 88 - 34 * raise_
            cracker_half(cv, gx, cy, -1, 36); cracker_half(cv, gx, cy, 1, 36)
            for sg in (-1, 1):
                ball(cv, gx + sg * 42, cy, 11, 13, (112, 200, 82))
        present(cv, 22, 208, 58, 36, (225, 60, 70), (250, 245, 230))
        present(cv, 120, 212, 62, 34, (60, 110, 230), (250, 215, 90))
        snowfall(cv, far, near, t)
        return push(cv, 1 + 0.04 * u, 0.4, 0.6)

    def c_crack(u, t):
        # two goblins lean back, the strain, the snap, and six paper crowns go out on a hexagon
        snap = 0.46
        cv = room * 0.7
        tree_bokeh(cv, 4131, t)
        cy = 142
        strain = sm(0.04, snap, u)
        rattle = 3.2 * strain * (1 - sm(snap, snap + 0.02, u))
        q = max(0.0, (u - snap) / (1 - snap))
        away = (1 - (1 - min(1.0, q * 2.4)) ** 2) if u >= snap else 0.0
        spread = 2 * strain + 24 * away
        fall = 12 * math.sin(min(1.0, q * 2.4) * math.pi) if u >= snap else 0.0
        joy = sm(0.0, 0.3, q)
        for sg, hx0, hat, dir_ in ((-1, 40, "santa", 1), (1, 175, HATS[3], -1)):
            hx = W / 2 + sg * (46 + spread + 12)
            gx = hx0 + sg * (2 * strain + 8 * away) + (1 - strain) * dir_ * 3
            gy = 82 + fall
            goblin(cv, gx, gy, 26, t, hat, grin=0.25 + 0.75 * joy, blink=1.0 if 0.2 < strain < 0.28 else 0.0, look=dir_, ear=wave(t, 18) * (0.3 + strain), flop=wave(t, 12), body=True)
            ink(cv, (gx + dir_ * 15, gy + 40), (hx, cy), (84, 150, 60), 5)
        cracker_half(cv, W / 2 - spread, cy, -1, 46, 1.0 if u >= snap else 0.0); cracker_half(cv, W / 2 + spread, cy, 1, 46, 1.0 if u >= snap else 0.0)
        for sg in (-1, 1):
            hx = W / 2 + sg * (46 + spread + 12)
            ball(cv, hx, cy, 13, 16, (112, 200, 82), spec=0.2)
            for k in (-1, 0, 1):
                ink(cv, (hx - 9, cy + k * 6), (hx + 9, cy + k * 6), (64, 120, 46), 0.9)
        if strain > 0.5 and u < snap:
            for i in range(5):       # sparks of tension at the join
                a = i * GOLDEN_ANGLE + t * 40
                stamp(cv, W / 2 + 7 * math.cos(a), cy + 16 * math.sin(a), (255, 220, 120), 1.4)
        if u >= snap:
            flash = math.exp(-q * 11)
            bang = 1 - (1 - min(1.0, q * 3.2)) ** 3
            bcy = 104
            if q < 0.1:                                  # the POW: solid, then only its outline flying out
                fill(cv, star_pts(W / 2, cy - 6, 18 + 70 * bang, 12, 0.13, 0.55), (255, 205, 70))
                fill(cv, star_pts(W / 2, cy - 6, 10 + 40 * bang, 12, 0.4, 0.6), (255, 250, 225))
            elif q < 0.26:
                o = star_pts(W / 2, cy - 6, 18 + 76 * bang, 12, 0.13, 0.55)
                for i in range(len(o)): line(cv, o[i], o[(i + 1) % len(o)], np.array((255.0, 190, 70)) * (1 - (q - 0.1) / 0.16), 1.6)
            for a, rr, c, ph in paper:
                d = rr * (1 - (1 - min(1.0, q * 2)) ** 3)
                x = W / 2 + d * math.cos(a) * 0.9
                y = bcy + d * math.sin(a) * 0.72 + 90 * q * q + 5 * math.sin(q * 16 + ph * 6)
                sp = q * 18 + ph * 6
                ink(cv, (x - 3 * math.cos(sp), y - 3 * math.sin(sp)), (x + 3 * math.cos(sp), y + 3 * math.sin(sp)), HATS[c], 1.4)
            for i, (hx, hy) in enumerate(polygon_pts(W / 2, bcy, 50, 6, math.pi / 6, 1.3)):
                f = 1 - (1 - min(1.0, q * 1.9)) ** 3
                party_hat(cv, W / 2 + (hx - W / 2) * f, bcy + (hy - bcy) * f + 10 * q * q, 7 + 11 * f, HATS[i], 0.5 * math.sin(q * 9 + i))
            cv += flash * np.array([255.0, 240, 200]) * 0.9
        sx, sy = rattle * wave(t, 41), rattle * 0.6 * wave(t, 37)
        return shake(cv, sx, sy)

    def d_party(u, t):
        # wide. the crowns settle like snow; the goblin dances; the purple one lands on the santa hat
        cv = night.copy()
        cv += np.exp(-((XX - W / 2) ** 2 + (YY - 120) ** 2) / (2 * 66.0 ** 2))[..., None] * np.array([58.0, 36, 14])
        tree(cv, wide, 1.0, t)
        pile(cv, 186)
        hop = abs(math.sin(u * math.pi * 5)) * 11
        gx, gy = 172, 150 - hop
        shadow(cv, gx, 206, 24, 4, 0.5)
        land = sm(0.44, 0.56, u)
        crownx, crowny = gx + 6 * (1 - land), 30 + (gy - 48 - 30) * land
        arm = 7 * wave(t, 12)
        goblin(cv, gx, gy, 23, t, "santa", grin=1.0, ear=wave(t, 16), flop=wave(t, 10), look=-1, hands=((gx - 32, gy + 12 + arm), (gx + 30, gy + 12 - arm)))
        if u >= 0.56:
            party_hat(cv, gx, gy - 46, 13, HATS[4], 0.12 * wave(t, 10))
        else:
            party_hat(cv, crownx, crowny, 13, HATS[4], 0.5 * (1 - land) * wave(t, 12))
        for i, (hx, ph) in enumerate(zip((30, 62, 104, 134, 196), (0.0, 0.33, 0.62, 0.15, 0.78))):
            q = (u * 0.8 + ph) % 1
            party_hat(cv, hx + 9 * math.sin(q * 11), 30 + 165 * q, 9, HATS[[0, 2, 1, 3, 5][i]], 0.45 * math.sin(q * 13))
        snowfall(cv, far, near, t)
        return cv

    def e_out(u, t):
        # the village goes to sleep: lights out, top down, as the snow thickens
        lit = 1 - sm(0.1, 0.8, u)
        cv = night * (0.72 + 0.28 * lit)
        tree(cv, wide, lit, t, 0.25 + 0.75 * lit)
        pile(cv, 186)
        snowfall(cv, far, near, t)
        return blackout(cv, sm(0.55, 1.0, u))

    SHOTS = [(0.0, 0.26, a_village), (0.26, 0.46, b_goblin), (0.46, 0.7, c_crack), (0.7, 0.92, d_party), (0.92, 1.01, e_out)]
    return "christmas", "the cracker", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- New Year: three tolls, then the sky answers and the water answers it ---------------
SEG = {"3": "abgcd", "2": "abged", "1": "bc"}
def numeral(cv, n, x, y, w, h, colour, size, ang=0.0):
    ca, sa = math.cos(ang), math.sin(ang)
    at = lambda px, py: (x + px * ca - py * sa, y + px * sa + py * ca)
    a, b, c, d = at(-w, -h), at(w, -h), at(-w, 0), at(w, 0)
    e, f = at(-w, h), at(w, h)
    lines = dict(a=(a, b), b=(b, d), c=(d, f), d=(e, f), e=(c, e), f=(a, c), g=(c, d))
    for s in SEG[n]: ink(cv, *lines[s], colour, size)

def skyline():
    """a Gielinor capital over a river: keep, spires, domes, the bell tower on the golden line. Returns the sky, the town, its mask"""
    sky = ramp([(6, 8, 26), (10, 14, 38), (24, 26, 58), (42, 34, 66)], np.clip((YY - 20) / 130, 0, 1)).copy()
    for x, y, b in stars(4201, 70, 22, 118):
        stamp(sky, x, y, np.array((150.0, 150, 170)) * b, 1.1)
    town, cover = np.zeros((H, W, 3)), np.zeros((H, W, 1))
    def block(pts, col=(26, 28, 46)):
        nonlocal town, cover
        m = poly(pts)
        town = town * (1 - m) + np.array(col, float) * m; cover = np.maximum(cover, m)
    HY = 150
    r = np.random.RandomState(4203)
    x = -6
    while x < W + 6:                                                       # terraces
        wd, hi = 10 + r.randint(16), 10 + r.randint(26)
        block([(x, HY), (x, HY - hi), (x + wd / 2, HY - hi - 8), (x + wd, HY - hi), (x + wd, HY)], (22, 24, 42))
        for _ in range(2):
            if r.rand() < 0.75:
                wx, wy = x + 3 + r.rand() * (wd - 6), HY - 4 - r.rand() * (hi - 4)
                block([(wx - 1, wy - 2), (wx + 2, wy - 2), (wx + 2, wy + 2), (wx - 1, wy + 2)], (150, 110, 50))
        x += wd
    for cx, wd, hi in ((26, 12, 62), (52, 9, 46), (170, 12, 56), (196, 9, 42)):   # spires
        block([(cx - wd, HY), (cx - wd, HY - hi), (cx, HY - hi - wd * 2), (cx + wd, HY - hi), (cx + wd, HY)], (30, 32, 56))
        block([(cx - 2, HY - hi + 8), (cx + 2, HY - hi + 8), (cx + 2, HY - hi + 16), (cx - 2, HY - hi + 16)], (170, 124, 56))
    # the dome
    im = Image.new("L", (W, H), 0); ImageDraw.Draw(im).ellipse((68, 92, 104, 128), fill=255)
    m = np.asarray(im, float)[..., None] / 255
    town = town * (1 - m) + np.array([36.0, 40, 66]) * m; cover = np.maximum(cover, m)
    block([(68, HY), (68, 110), (104, 110), (104, HY)], (28, 30, 50))
    # the bell tower, on the golden section
    bx = GOLD_X
    block([(bx - 13, HY), (bx - 13, 56), (bx + 13, 56), (bx + 13, HY)], (42, 44, 66))
    block([(bx - 17, 56), (bx, 26), (bx + 17, 56)], (54, 50, 76))
    block([(bx - 8, 86), (bx - 8, 70), (bx, 62), (bx + 8, 70), (bx + 8, 86)], (10, 10, 18))
    return sky, town, cover, HY, bx

def bell(cv, px, py, s, ang, lit, swing_clap=0.0, num=None):
    """a big bronze bell hung from (px, py): profile lit across its width, the numeral cut into it"""
    prof = [(-0.13, 0.0), (-0.32, 0.08), (-0.42, 0.3), (-0.46, 0.6), (-0.6, 0.86), (-0.78, 1.0), (0.78, 1.0), (0.6, 0.86), (0.46, 0.6), (0.42, 0.3), (0.32, 0.08), (0.13, 0.0)]
    ca, sa = math.cos(ang), math.sin(ang)
    # the clapper, behind the mouth
    cl = ang * 0.2 + swing_clap
    cx, cy = px + 1.08 * s * math.sin(-ang * 0.9 + 0) * 1.0 + 1.08 * s * math.sin(cl) * 0.2, py + 1.08 * s * math.cos(ang)
    ink(cv, (px - sa * s * 0.8, py + ca * s * 0.8), (cx, cy), (60, 44, 24), s * 0.035 + 0.5)
    ball(cv, cx, cy, s * 0.1, s * 0.1, np.array((230.0, 180, 80)) * (0.5 + 0.5 * lit))
    pts = rot_pts(prof, px, py, s, ang)
    m = poly(pts)
    across = ((XX - px) * ca + (YY - py) * sa) / (s * 0.78)
    v = np.clip(0.5 + 0.55 * np.cos((across + 0.28) * 3.0), 0, 1)
    v = np.round(v * 6) / 6
    col = ramp([(70, 40, 18), (168, 110, 40), (240, 180, 70), (255, 238, 170)], v * (0.55 + 0.45 * lit))[..., :3]
    down = ((YY - py) * ca - (XX - px) * sa) / s
    band = ((np.abs(down - 0.88) < 0.03) | (np.abs(down - 0.27) < 0.022))[..., None]
    col = col * (1 - band * 0.55)
    cv[:] = cv * (1 - m) + col * m
    ball(cv, px, py, s * 0.13, s * 0.1, (200, 150, 70))
    if num:
        c = py + ca * s * 0.58; xx = px - sa * s * 0.58
        numeral(cv, num, xx, c, s * 0.19, s * 0.205, (34, 14, 8), max(1.6, s * 0.05), ang)

def shape_dirs(kind):
    """the unit points a shell throws, each with a release delay. Every shape is an outline a child could name"""
    pts = []
    if kind == "peony":
        for i in range(22):
            a = i * 2 * math.pi / 22
            pts.append((math.cos(a), math.sin(a)))
    elif kind == "ring":
        pts = [(math.cos(i * 2 * math.pi / 34), math.sin(i * 2 * math.pi / 34)) for i in range(34)]
        pts += [(0.42 * math.cos(i * 2 * math.pi / 10), 0.42 * math.sin(i * 2 * math.pi / 10)) for i in range(10)]
    elif kind == "star":
        p = star_pts(0, 0, 1.0, 5, -math.pi / 2, 0.42)
        for i in range(10):
            a, b = p[i], p[(i + 1) % 10]
            pts += [(a[0] + (b[0] - a[0]) * f / 5, a[1] + (b[1] - a[1]) * f / 5) for f in range(5)]
    elif kind == "spiral":
        for arm in (0, 1, 2):
            for i in range(14):
                r_ = 0.12 + 0.88 * i / 13
                a = arm * 2 * math.pi / 3 + r_ * 4.2
                pts.append((r_ * math.cos(a), r_ * math.sin(a)))
    elif kind == "hex":
        p = polygon_pts(0, 0, 1.0, 6, 0.0)
        for i in range(6):
            a, b = p[i], p[(i + 1) % 6]
            pts += [(a[0] + (b[0] - a[0]) * f / 5, a[1] + (b[1] - a[1]) * f / 5) for f in range(5)]
            pts += [(a[0] * f / 4, a[1] * f / 4) for f in range(1, 4)]
    elif kind == "willow":
        for i in range(18):
            a = (i + 0.5) * 2 * math.pi / 18
            pts.append((math.cos(a), math.sin(a) * 0.6))
    return pts

class Shell:
    def __init__(self, x, y, kind, colour, start, life=0.3, r=44, launch=0.05, tip=(255, 255, 240)):
        self.x, self.y, self.kind, self.c, self.start, self.life, self.r, self.launch = x, y, kind, np.array(colour, float), start, life, r, launch
        self.tip, self.pts = np.array(tip, float), shape_dirs(kind)
    def light(self, u):
        q = (u - self.start - self.launch) / self.life
        return (self.x, self.y, math.exp(-q * 7) if q >= 0 else 0.0)
    def draw(self, cv, u):
        v = (u - self.start) / self.launch
        if 0 <= v < 1:                                   # the climb from the water
            yy = 150 + (self.y - 150) * (1 - (1 - v) ** 2)
            for k in range(5):
                stamp(cv, self.x, yy + k * 5, np.array((255.0, 214, 150)) * (1 - k / 5), 1.6)
        q = (u - self.start - self.launch) / self.life
        if not 0 <= q < 1: return
        grow = 1 - (1 - min(1.0, q * 2.2)) ** 3
        fade = (1 - sm(0.62, 1.0, q))
        fall = (90 if self.kind == "willow" else 20) * q * q
        size = 3.2 * (1 - 0.4 * q) + 0.4
        col = (self.c * 1.15 + 25) * fade + self.tip * (1 - min(1.0, q * 3)) * 0.8
        for dx, dy in self.pts:
            x, y = self.x + dx * self.r * grow, self.y + dy * self.r * grow * 0.96 + fall
            stamp(cv, x, y, col, size)
            if self.kind == "peony":                     # a chrysanthemum: every star leaves a spoke behind it
                for k in (0.84, 0.68, 0.52):
                    stamp(cv, self.x + dx * self.r * grow * k, self.y + dy * self.r * grow * k * 0.96 + fall, col * k * k * 0.7, size * 0.7)
            if self.kind == "willow":                    # every star droops, its trail hanging behind it in a curve
                for k in range(1, 9):
                    f = 1 - k * 0.115
                    stamp(cv, self.x + dx * self.r * grow * f, self.y + dy * self.r * grow * f * 0.96 + fall * f * f, col * (1 - k / 10), size * 0.75)
        if q < 0.16: stamp(cv, self.x, self.y, self.c * 0.8 + 60, 7 * (1 - q / 0.16) + 1); glow(cv, self.x, self.y, 10 + 30 * q, self.c * 0.8 + 60, (1 - q / 0.16) * 0.55)

def reflect(cv, hy, t, power=0.92):
    """the water: every row below the horizon reads the sky above it, squeezed and broken by ripples, with dark swell lines"""
    src = cv.copy()
    for y in range(hy, H):
        d = y - hy
        row = max(0, int(hy - 1 - d * 2.0))
        off = int(round((0.6 + d * 0.05) * math.sin(y * 0.8 + 2 * math.pi * t * 9) + (0.5 + d * 0.03) * math.sin(y * 0.31 - 2 * math.pi * t * 5)))
        band = 0.82 + 0.3 * math.sin(y * 1.5 - 2 * math.pi * t * 6)
        fall = power * (1 - 0.25 * d / (H - hy))
        cv[y] = np.roll(src[row], off, axis=0) * fall * band + np.array((5.0, 8, 15))
    return cv

def crowd(cv, seed=4211):
    """the quay: the whole town out to watch, a row of heads and raised arms as a dark foreground"""
    r = np.random.RandomState(seed)
    fill(cv, [(0, H), (0, 213), (W, 213), (W, H)], (9, 9, 18))
    x = 2
    while x < W:
        hy = 205 + r.randint(4)
        ball(cv, x, hy, 4, 5, (10, 10, 20), steps=1, lo=1.0)
        fill(cv, [(x - 6, 215), (x - 4, hy + 4), (x + 4, hy + 4), (x + 6, 215)], (10, 10, 20))
        if r.rand() < 0.4:
            s = 1 if r.rand() < 0.5 else -1
            ink(cv, (x + s * 4, hy + 6), (x + s * 8, hy - 7), (10, 10, 20), 1.1)
        x += 9 + r.randint(5)

def newyear_film():
    sky, town, cover, HY, bx = skyline()
    SHELLS = [Shell(66, 62, "star", (255, 205, 60), 0.02, 0.2, 40),
              Shell(158, 74, "ring", (70, 170, 255), 0.16, 0.2, 38),
              Shell(50, 74, "hex", (255, 70, 200), 0.3, 0.2, 38),
              Shell(150, 62, "spiral", (80, 240, 130), 0.44, 0.2, 42),
              Shell(107, 56, "peony", (255, 70, 60), 0.58, 0.2, 46),
              Shell(107, 48, "willow", (255, 195, 100), 0.7, 0.3, 74, 0.04),
              Shell(44, 62, "peony", (90, 240, 130), 0.78, 0.17, 30, 0.03),
              Shell(172, 66, "ring", (255, 90, 210), 0.8, 0.17, 30, 0.03),
              Shell(107, 74, "star", (255, 255, 200), 0.83, 0.17, 46, 0.03)]
    smoke_ = lambda t: noise(4221, 18, t)
    arch = np.array([30.0, 30, 46]) * (0.55 + 0.7 * fbm(4223, (20, 8, 4))[..., None])
    moon_d = np.hypot(XX - 44, YY - 54)

    def city(power=1.0, heat=(0, 0, 0, 0.0), moon=1.0):
        cv = sky * power
        cv = cv + np.clip(1 - np.abs(moon_d - 0) / 12, 0, 1)[..., None] * np.array([150.0, 150, 130]) * power * moon
        cv += np.exp(-(moon_d / 30) ** 2)[..., None] * np.array([34.0, 34, 40]) * power * moon
        hx, hy, c, f = heat
        lit = 1.0
        if f > 0:
            lit = 1 + 0.9 * f
            cv += np.exp(-((XX - hx) ** 2 + (YY - hy) ** 2) / (2 * 72.0 ** 2))[..., None] * np.array(c, float) * 0.3 * f
        return cv * (1 - cover) + town * lit * cover

    def a_city(u, t):
        # midnight. the town holds its breath; the tower's lamp comes up
        cv = city(0.5 + 0.5 * sm(0.0, 0.3, u))
        on = sm(0.3, 0.85, u)
        glow(cv, bx, 76, 13, (255, 180, 80), 0.75 * on)
        bell(cv, bx, 68, 16, 0.08 * wave(t, 14) * on, on)
        reflect(cv, HY, t)
        crowd(cv)
        return blackout(push(cv, 1 + 0.1 * u, 0.74, 0.34), 1 - sm(0.0, 0.2, u))

    def b_bell(u, t):
        # the tower arch from inside. three tolls, the count cut into the bell; each swing wider than the last
        cv = arch.copy()
        fill(cv, [(30, H), (30, 74), (107, 30), (184, 74), (184, H)], (10, 12, 30))
        for x, y, b in stars(4225, 26, 40, 200):
            if abs(x - 107) < 70: stamp(cv, x, y, np.array((130.0, 130, 150)) * b, 1.1)
        fill(cv, [(0, 42), (W, 42), (W, 54), (0, 54)], (58, 42, 26))
        k = min(2, int(u * 3)); q = u * 3 - k
        hit = math.exp(-q * 5)
        ang = [0.24, 0.33, 0.46][k] * math.cos(q * math.pi) * (1 if k % 2 == 0 else -1)
        for i in range(3):
            d = (q + i * 0.2) * 1.0
            if d < 1: ring(cv, 107, 118, 22 + 150 * d, 1.0, 5 - i, np.array((255.0, 190, 90)) * (1 - d) * 0.6)
        cv += np.exp(-np.hypot(XX - 107, YY - 118) / 62)[..., None] * np.array([120.0, 80, 30]) * hit
        bell(cv, 107, 52, 136, ang, 0.55 + 0.45 * hit, 0.3 * math.sin(q * 6), "321"[k])
        s = 3.5 * hit * wave(t, 37)
        out = shake(cv, s, s * 0.5)
        return blackout(out, 1 - sm(0.0, 0.05, u)) + np.array([255.0, 240, 200]) * sm(0.965, 1.0, u)

    def sky_scene(u, t, smoke=0.0, f=1.0):
        best = max(SHELLS, key=lambda s: s.light(u)[2])
        lx, ly, fl = best.light(u)
        cv = city(0.8, (lx, ly, best.c, fl), 0.25)
        if smoke > 0:
            n = smoke_(t)
            den = (np.clip(n - 0.42, 0, 1) * 2 * smoke * np.clip((150 - YY) / 100, 0, 1))[..., None]
            cv = cv * (1 - den * 0.4) + np.array([70.0, 58, 70]) * den * 0.5
        for s in SHELLS: s.draw(cv, u)
        reflect(cv, HY, t)
        crowd(cv)
        return cv

    def c_fire(u, t):
        # the sky answers in shapes: a star, a ring, a hexagon, a spiral, a peony; the river answers the sky
        cv = sky_scene(u, t, 0.5 * u)
        return cv + np.array([255.0, 240, 200]) * math.exp(-u * 60) * 0.9

    def e_out(u, t):
        cv = sky_scene(1.0, t, 0.7)
        return blackout(cv, sm(0.1, 1.0, u))

    SHOTS = [(0.0, 0.16, a_city), (0.16, 0.38, b_bell), (0.38, 0.95, c_fire), (0.95, 1.01, e_out)]
    return "newyear", "midnight over the water", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- Valentine: two birds draw a heart in light; it opens on a rose ---------------------
def heart_at(th, cx, cy, s):
    """th 0 is the cleft, pi the point"""
    x = 16 * math.sin(th) ** 3
    y = -(13 * math.cos(th) - 5 * math.cos(2 * th) - 2 * math.cos(3 * th) - math.cos(4 * th))
    return cx + x * s, cy + y * s

def rose(cv, cx, cy, r, bloom, t, stops=((120, 8, 30), (214, 26, 56), (255, 90, 110))):
    """A rose by phyllotaxis: petals on the golden angle, outside in, darker at the heart. bloom spreads them"""
    n = 19
    for i in range(n, 0, -1):
        f = math.sqrt(i / n)
        a = i * GOLDEN_ANGLE + 0.25 * wave(t, 2) * f
        rr = r * f * (0.28 + 0.72 * bloom)
        pr = r * (0.2 + 0.26 * f) * (0.5 + 0.5 * bloom)
        x, y = cx + rr * 0.66 * math.cos(a), cy + rr * 0.66 * math.sin(a) * 0.92
        tone = ramp(stops, np.array(0.25 + 0.75 * f * (0.8 + 0.2 * ((i * 3) % 2))))
        ball(cv, x + pr * 0.1, y + pr * 0.14, pr * 1.2, pr * 1.1, np.array(stops[0], float) * 0.35, steps=1, lo=1.0)
        ball(cv, x, y, pr * 1.1, pr, tone, light=(-0.55, -0.62), steps=4, lo=0.42)
    ball(cv, cx, cy, r * 0.24, r * 0.21, stops[0], steps=3, lo=0.5)
    if r > 24:                                           # the furl at the heart
        pts = phi_spiral(cx, cy, r * 0.035, 1.5, 2 * math.pi * t)
        for a, b in zip(pts, pts[1:]): ink(cv, a, b, (54, 2, 16), max(0.8, r * 0.022))

def garden_backdrop():
    """a rose walk at dusk: a low sun behind an iron arch, hedges down both sides, roses in them"""
    img = ramp([(34, 22, 62), (72, 34, 80), (134, 58, 84), (176, 90, 74)], np.clip((YY - 20) / 130, 0, 1)).copy() * 0.8
    glow(img, W / 2, 142, 40, (150, 90, 50), 0.6)
    d = np.hypot(XX - W / 2, YY - 142)
    img = img + (np.clip(16 - d, 0, 1))[..., None] * np.array([70.0, 60, 40])
    far = ridge(4301, 138, 8, (40, 16))
    m = (YY > far[None, :])[..., None]
    img = img * (1 - m) + np.array([34.0, 26, 44]) * m
    gn = fbm(4303, (10, 5, 2))[..., None]
    for sg in (-1, 1):
        edge = W / 2 + sg * (10 + (YY - 136) * 1.2 + 2.0 * np.sin(YY * 0.4))
        m = (((XX - edge) * sg > 0) & (YY > 136 - np.abs(XX - W / 2) * 0.62 + 3 * np.sin(XX * 0.33)))[..., None]
        img = img * (1 - m) + np.array([24.0, 52, 36]) * (0.5 + 0.8 * gn) * m
    path = ((np.abs(XX - W / 2) < 10 + (YY - 136) * 1.2) & (YY > 136))[..., None]
    img = img * (1 - path) + np.array([86.0, 62, 66]) * (0.6 + 0.5 * fbm(4305, (20, 6))[..., None]) * path
    r = np.random.RandomState(4307)
    for _ in range(28):
        sg = 1 if r.rand() < 0.5 else -1
        y = 120 + r.rand() * 90
        x = W / 2 + sg * (22 + (y - 120) * 0.95 + r.rand() * 50)
        rr = 2.2 + (y - 110) * 0.05
        ball(img, x, y, rr, rr, (130 + r.randint(40), 26, 48), steps=3, lo=0.5)
        if r.rand() < 0.5: ball(img, x, y, rr * 0.5, rr * 0.5, (70, 10, 26), steps=2, lo=0.7)
    # the arch
    for i in range(49):
        a = math.pi * i / 48; b = math.pi * (i + 1) / 48
        ink(img, (W / 2 + 40 * math.cos(a), 94 - 42 * math.sin(a)), (W / 2 + 40 * math.cos(b), 94 - 42 * math.sin(b)), (20, 18, 26), 1.6)
    for sg in (-1, 1):
        ink(img, (W / 2 + sg * 40, 94), (W / 2 + sg * 40, 170), (20, 18, 26), 1.6)
    for i in range(16):
        a = math.pi * (i + 0.5) / 16
        ball(img, W / 2 + 40 * math.cos(a), 94 - 42 * math.sin(a), 3, 3, (130, 28, 50), steps=3, lo=0.5)
    return img

def bird(cv, x, y, s, facing, flap, colour, eye=(16, 12, 16)):
    """a small plump bird. facing +1 looks right"""
    c = np.array(colour, float)
    fill(cv, [(x - facing * 0.8 * s, y - 0.1 * s), (x - facing * 1.5 * s, y - 0.25 * s), (x - facing * 1.45 * s, y + 0.12 * s), (x - facing * 0.7 * s, y + 0.35 * s)], c * 0.7)
    ball(cv, x, y, 1.0 * s, 0.78 * s, c, spec=0.2)
    ball(cv, x + facing * 0.72 * s, y - 0.56 * s, 0.56 * s, 0.56 * s, c)
    fill(cv, [(x + facing * 1.2 * s, y - 0.72 * s), (x + facing * 1.76 * s, y - 0.5 * s), (x + facing * 1.2 * s, y - 0.36 * s)], (255, 180, 70))
    ball(cv, x + facing * 0.88 * s, y - 0.66 * s, 0.13 * s, 0.14 * s, eye, steps=1, lo=1.0)
    fill(cv, [(x + facing * 0.32 * s, y - 0.2 * s), (x - facing * 0.5 * s, y - 0.2 * s - flap * 1.3 * s), (x - facing * 0.95 * s, y + 0.15 * s - flap * 0.6 * s)], c * 0.76)

def petal(cv, x, y, spin, c, size=1.0):
    dx, dy = 3.2 * size * math.cos(spin), 3.2 * size * math.sin(spin)
    ink(cv, (x - dx, y - dy), (x + dx, y + dy), c, 1.5 * size)
    ink(cv, (x - dx * 0.3, y - dy * 0.3), (x + dx * 0.5, y + dy * 0.5), np.minimum(np.array(c, float) * 1.35, 255), 0.8 * size)

PETALS = ((230, 50, 80), (250, 110, 130), (190, 24, 60))
def wind_petals(cv, field, t, cycles=5, size=1.0, power=1.0):
    for i, (x, y, ph, sp) in enumerate(field):
        q = (t * cycles + ph) % 1
        px = (x + q * W * 1.3) % (W + 30) - 15
        py = y + 18 * q + 9 * math.sin(q * 2 * math.pi * 2 + sp * 6)
        c = np.array(PETALS[i % 3], float) * power
        petal(cv, px, py, q * 2 * math.pi * 3 + sp * 6, c, size)

CX, CY, HS = W / 2, 106.0, 5.1
DOVE, ROSY = (255, 250, 240), (255, 170, 190)
def valentine_film():
    garden = garden_backdrop()
    r = np.random.RandomState(4311)
    wind = [(r.rand() * W, 22 + r.rand() * 180, r.rand(), r.rand()) for _ in range(30)]
    heart_pts = [heart_at(2 * math.pi * i / 120, CX, CY, HS) for i in range(120)]
    left_half = [heart_at(math.pi + math.pi * i / 60, CX, CY, HS) for i in range(61)]
    right_half = [heart_at(math.pi - math.pi * i / 60, CX, CY, HS) for i in range(61)]
    burst = [(i * GOLDEN_ANGLE, 40 + 2.8 * i ** 0.5 * 18 * r.rand() ** 0.5 + 40 * r.rand(), r.rand()) for i in range(60)]
    AMBER = np.array((255.0, 170, 170))

    def stroke(cv, pts, colour, upto=1.0, width=2.4):
        for a, b in zip(pts[:int((len(pts) - 1) * upto)], pts[1:int((len(pts) - 1) * upto) + 1]):
            line(cv, a, b, colour, width)

    def a_garden(u, t):
        # the walk at dusk; a rose in the foreground opens as the wind takes petals past it
        cv = garden * (0.75 + 0.25 * sm(0, 0.4, u))
        wind_petals(cv, wind[:18], t, 4, 1.0, 0.7)
        rx, ry = 54 + 6 * u, 164 + 3 * wave(t, 4)
        ink(cv, (rx, ry), (rx + 16, H), (24, 78, 40), 3)
        fill(cv, [(rx + 8, 200), (rx + 40, 186), (rx + 24, 204)], (40, 110, 60))
        rose(cv, rx, ry, 42, 0.45 + 0.5 * sm(0.15, 0.9, u), t)
        rose(cv, 176, 196, 30, 0.8, t)
        wind_petals(cv, wind[18:], t, 6, 1.5)
        return blackout(push(cv, 1 + 0.06 * u, 0.5, 0.6), 1 - sm(0.0, 0.22, u))

    def b_draw(u, t):
        # the garden falls back. two birds leave the point of a heart and each flies one side of it
        cv = garden * (0.5 - 0.1 * u)
        wind_petals(cv, wind[:10], t, 4, 1.0, 0.45)
        q = sm(0.04, 0.96, u)
        trail = (255, 90, 130)
        for pts in (left_half, right_half):
            stroke(cv, pts, np.array(trail, float) * 0.25, q, 4.5)
            stroke(cv, pts, trail, q, 2.0)
        lt = heart_at(math.pi + math.pi * q, CX, CY, HS); rt = heart_at(math.pi - math.pi * q, CX, CY, HS)
        for (x, y), col, face, ph in ((lt, DOVE, 1 if q > 0.55 else -1, 0.0), (rt, ROSY, -1 if q > 0.55 else 1, 0.3)):
            glow(cv, x, y, 12, (255, 120, 150), 0.3)
            bird(cv, x, y - 2, 10.5, face, 0.6 + 0.8 * wave(t, 24, ph), col)
        return cv

    def c_meet(u, t):
        # close on the cleft: they land and touch beaks, and a small heart of light jumps between them
        cv = garden * 0.2
        cv = push(cv, 1.5, 0.5, 0.42)
        cv += np.exp(-np.hypot(XX - W / 2, YY - 108) / 70)[..., None] * np.array([120.0, 40, 70])
        close = sm(0.0, 0.5, u)
        gap = 56 - 22 * close
        y0 = 112 + 4 * wave(t, 6)
        big = [heart_at(2 * math.pi * i / 240, W / 2, 128 + 65, 13) for i in range(240)]
        for a_, b_ in zip(big, big[1:]):
            if 0 < a_[0] < W and 0 < a_[1] < H: line(cv, a_, b_, (120, 30, 60), 5.0)
        for a_, b_ in zip(big, big[1:]):
            if 0 < a_[0] < W and 0 < a_[1] < H: line(cv, a_, b_, (255, 90, 130), 2.2)
        pop = sm(0.5, 0.72, u)
        if pop > 0:
            s = 2.1 * pop * (1 + 0.15 * wave(t, 18))
            hp = [heart_at(2 * math.pi * i / 40, W / 2, y0 - 50, s) for i in range(40)]
            glow(cv, W / 2, y0 - 52, 22, (255, 110, 150), 0.7 * pop)
            fill(cv, hp, (255, 96, 140)); fill(cv, [heart_at(2 * math.pi * i / 40, W / 2, y0 - 51, s * 0.5) for i in range(40)], (255, 200, 210))
        bird(cv, W / 2 - gap - 4, y0 + 2 * wave(t, 8) * (1 - close), 21, 1, (0.9 * wave(t, 16)) * (1 - close) - 0.1, DOVE)
        bird(cv, W / 2 + gap + 4, y0 + 2 * wave(t, 8, 0.3) * (1 - close), 21, -1, (0.9 * wave(t, 16, 0.3)) * (1 - close) - 0.1, ROSY)
        return cv + np.array((255.0, 215, 215)) * sm(0.93, 1.0, u)

    def d_open(u, t):
        # the heart fills, its two halves swing out like doors, and there is the rose
        cv = garden * 0.3
        swing = sm(0.14, 0.6, u)
        bloom = sm(0.26, 0.95, u)
        if swing > 0:
            cv += np.exp(-np.hypot(XX - CX, YY - CY - 4) / 54)[..., None] * np.array([255.0, 150, 130]) * swing * 0.72
            rays(cv, CX, CY + 4, 11, 170, (100, 50, 50), t, 0.2)
            rose(cv, CX, CY + 6, 16 + 54 * bloom, 0.25 + 0.75 * bloom, t, ((180, 12, 40), (250, 34, 66), (255, 130, 140)))
        ca = math.cos(swing * math.pi / 2)
        for pts, sg in ((left_half, -1), (right_half, 1)):
            if ca < 0.04: break
            hinge = CX + sg * 16 * HS
            door = [(hinge + (x - hinge) * ca + sg * 2.5 * (1 - ca), y) for x, y in pts]
            m = poly(door)
            v = np.clip((YY - 40) / 160, 0, 1)
            col = ramp([(255, 120, 150), (235, 60, 100), (190, 40, 80)], v)[..., :3] * (0.72 + 0.38 * ca)
            cv[:] = cv * (1 - m) + col * m
            for a, b in zip(door, door[1:]): line(cv, a, b, (255, 210, 215), 1.2)
        fl = 1 - sm(0.0, 0.14, u)
        for a, rr, ph in burst:
            q = (u - 0.26) / 0.74
            if q <= 0: continue
            f = 1 - (1 - min(1.0, q * 1.6)) ** 2
            x = CX + rr * f * math.cos(a) * 1.15 + 18 * q
            y = CY + rr * f * math.sin(a) + 60 * q * q
            petal(cv, x, y, q * 16 + ph * 6, PETALS[int(ph * 3)], 1.3)
        return cv + np.array((255.0, 215, 215)) * fl

    def e_out(u, t):
        # the rose keeps its light as the night comes in, and the wind has the last petals
        cv = garden * 0.3 * (1 - 0.6 * u)
        cv += np.exp(-np.hypot(XX - CX, YY - CY - 4) / 54)[..., None] * np.array([255.0, 150, 130]) * 0.72 * (1 - u)
        rose(cv, CX, CY + 6, 70, 1.0, t, ((180, 12, 40), (250, 34, 66), (255, 130, 140)))
        wind_petals(cv, wind, t, 5, 1.3)
        return blackout(cv, sm(0.42, 1.0, u))

    SHOTS = [(0.0, 0.24, a_garden), (0.24, 0.5, b_draw), (0.5, 0.66, c_meet), (0.66, 0.9, d_open), (0.9, 1.01, e_out)]
    return "valentine", "two birds", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

RECIPES = [celebrating(christmas_film, Wear.santa, [(150, 24, 30), (214, 44, 44), (250, 110, 90)]),
           celebrating(newyear_film, Wear.party, [(150, 100, 20), (230, 180, 50), (255, 236, 150)]),
           celebrating(valentine_film, Wear.heart, [(150, 30, 80), (226, 70, 120), (255, 150, 180)])]
