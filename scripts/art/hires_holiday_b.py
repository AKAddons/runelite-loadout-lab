"""Hi-res recipes, holiday films B: Easter, Lunar New Year, Holi. See HOLIDAYS.md. No sprite fits any of these, so the models
are built here from faceted lit balls (`ball`), the PS2 look done by hand: quantised normals give flat-shaded panels."""
from hires_holiday_card import celebrating
import hires_holiday_card as Wear
from hires_kit import *
from hires_batch2c import sm, shadow, shake, poly
from hires_last import ink
from hires_raids import FILM, BAR, letterbox, blackout, push, play, rays

def ball(cv, cx, cy, rx, ry, col, rot=0.0, L=(-0.55, -0.7), amb=0.42, egg=0.0, pattern=None, facets=12, keep=None, power=1.0):
    """A chunky lit ellipsoid that covers. egg narrows the top; pattern(u, v) paints in the ball's own coordinates (-1..1);
    keep(u, v) cuts it, for broken shell."""
    R = max(rx, ry) * (1 + abs(egg)) + 2
    x0, x1, y0, y1 = max(0, int(cx - R)), min(W, int(cx + R) + 2), max(0, int(cy - R)), min(H, int(cy + R) + 2)
    if x0 >= x1 or y0 >= y1 or rx <= 0.2 or ry <= 0.2: return
    X, Y = XX[y0:y1, x0:x1] - cx, YY[y0:y1, x0:x1] - cy
    ca, sa = math.cos(rot), math.sin(rot)
    a, b = X * ca + Y * sa, -X * sa + Y * ca
    v = b / ry; u = a / (rx * np.maximum(1 + egg * v, 0.2))
    rr = np.sqrt(u * u + v * v)
    al = np.clip((1 - rr) * min(rx, ry) + 0.5, 0, 1) * power
    if keep is not None: al = al * keep(u, v)
    if facets:
        st = 2 * math.pi / facets
        th = np.round(np.arctan2(v, u) / st) * st
        r2 = np.clip(np.round(rr * 3 + 0.2) / 3, 0, 0.95)
        nu, nv = r2 * np.cos(th), r2 * np.sin(th)
    else:
        nu, nv = np.clip(u, -1, 1), np.clip(v, -1, 1)
    nz = np.sqrt(np.clip(1 - nu * nu - nv * nv, 0, 1))
    lx, ly = nu * ca - nv * sa, nu * sa + nv * ca
    shade = amb + (1 - amb) * 1.15 * np.clip(lx * L[0] + ly * L[1] + nz * 0.62, 0, 1)
    base = pattern(u, v) if pattern is not None else np.array(col, float)
    c = base * shade[..., None]
    cv[y0:y1, x0:x1] = cv[y0:y1, x0:x1] * (1 - al[..., None]) + c * al[..., None]

def fill(cv, pts, col):
    m = poly([tuple(p) for p in pts])
    cv[:] = cv * (1 - m) + np.array(col, float) * m

def grass(cv, y, h, seed, t, lo, hi, n=60, sway=3.0, cycles=5):
    """tufts on one layer: everything below y is solid, blades lean on a looping wind"""
    r = np.random.RandomState(seed)
    im = Image.new("L", (W, H), 0); d = ImageDraw.Draw(im)
    d.rectangle((0, int(y), W, H), fill=255)
    for _ in range(n):
        x, hh, ph, wd, off = r.rand() * W, h * (0.55 + 0.7 * r.rand()), r.rand(), 3.5 + 3 * r.rand(), (r.rand() - 0.5) * 12
        tip = x + off + sway * wave(t, cycles, ph + x / W)
        d.polygon([(x - wd, y + 1), (tip, y - hh), (x + wd, y + 1)], fill=255)
    m = np.asarray(im, float)[..., None] / 255
    v = np.clip((y - YY) / max(h, 1), 0, 1)[..., None]
    c = np.array(lo, float) + (np.array(hi, float) - np.array(lo, float)) * v
    tone = 0.82 + 0.36 * _TUFT[..., None]
    cv[:] = cv * (1 - m) + c * tone * m

_TUFT = fbm(4000, (18, 8, 4))

# ---- Easter: a spring meadow at dawn -------------------------------------------------
def egg_paint(kind, a, b, c):
    A, B, C = (np.array(k, float) for k in (a, b, c))
    def pat(u, v):
        if kind == "zig":
            s = np.abs((u * 2.2) % 2 - 1) * 0.42
            m = np.abs(v - s + 0.2) < 0.22; m2 = np.abs(v - s + 0.85) < 0.1; m3 = np.abs(v - s - 0.42) < 0.1
        elif kind == "band":
            m = np.abs(v) < 0.24; m2 = np.abs(v + 0.58) < 0.13; m3 = np.abs(v - 0.58) < 0.13
        elif kind == "dot":
            m = ((np.round(u * 2.2) - u * 2.2) ** 2 + (np.round(v * 2.2 + 0.5) - v * 2.2 - 0.5) ** 2) < 0.13
            m2 = np.abs(v) < 0.08; m3 = v > 0.82
        else:
            m = (np.abs(((u + v) * 2.0) % 2 - 1) < 0.34) & (np.abs(((u - v) * 2.0) % 2 - 1) < 0.34)
            m2 = np.abs(v - 0.62) < 0.11; m3 = np.abs(v + 0.62) < 0.11
        out = np.zeros(u.shape + (3,)) + A
        out[m] = B; out[m2] = C; out[m3] = C
        return out
    return pat

EGGS = [(40, 183, 14, 18, 0.2, egg_paint("zig", (70, 110, 255), (255, 235, 90), (255, 90, 170))),
        (92, 192, 15, 19, -0.15, egg_paint("band", (255, 70, 120), (110, 240, 255), (255, 240, 120))),
        (140, 180, 13, 17, 0.1, egg_paint("dia", (255, 200, 40), (190, 70, 240), (70, 220, 120))),
        (184, 190, 15, 19, -0.2, egg_paint("dot", (60, 210, 130), (255, 110, 70), (255, 245, 170)))]
BIG_EGG = egg_paint("zig", (60, 90, 240), (255, 225, 70), (255, 80, 160))

def sparkle(cv, x, y, size, colour, k=1.0):
    c = np.array(colour, float) * k
    line(cv, (x - size, y), (x + size, y), c, 1.0); line(cv, (x, y - size), (x, y + size), c, 1.0)
    glow(cv, x, y, size * 0.7, colour, 0.5 * k)

def rabbit(cv, x, y, s, f=1, lean=0.0, stretch=1.0, ears=0.0, nose=0.0, L=(-0.5, -0.7)):
    """x, y on the ground under the middle of it. f faces it, lean tips it, stretch lengthens it in a leap."""
    ca, sa = math.cos(lean), math.sin(lean)
    def P(px, py):
        px *= stretch; py = py / stretch ** 0.5
        ax, ay = px * ca - (py + 0.45) * sa, px * sa + (py + 0.45) * ca
        return x + f * ax * s, y + (ay - 0.45) * s
    FUR, DARK, PALE = (238, 214, 186), (176, 138, 110), (255, 250, 240)
    r0 = f * lean
    def part(px, py, rx, ry, col, rot=0.0, **kw):
        cx, cy = P(px, py)
        kw.setdefault('amb', 0.55); ball(cv, cx, cy, rx * s, ry * s, col, f * rot + r0, L=L, **kw)
    for dx, tip, c in ((-0.14, -0.5 + ears, DARK), (0.0, -0.22 + ears, FUR)):           # ears, far then near
        bx, by = 0.44 + dx, -1.08
        part(bx + 0.5 * math.sin(tip) * 0.8, by - 0.42 * math.cos(tip), 0.11, 0.42, c, tip)
        if c == FUR: part(bx + 0.5 * math.sin(tip) * 0.8, by - 0.40 * math.cos(tip), 0.05, 0.3, (255, 170, 170), tip, amb=0.7, facets=0)
    part(-0.72, -0.5, 0.18, 0.17, PALE, facets=8)                                       # tail
    part(0.42, -0.17, 0.1, 0.2, DARK, 0.05)                                             # far foreleg
    part(-0.05, -0.5, 0.58, 0.37, FUR, -0.12)                                           # body
    part(-0.34, -0.42, 0.38, 0.38, FUR)                                                 # haunch
    part(-0.12, -0.07, 0.34, 0.085, (250, 228, 200))                                    # hind foot
    part(0.3, -0.15, 0.1, 0.2, FUR, -0.05)
    part(0.5, -0.86, 0.3, 0.26, FUR, 0.1)                                               # head
    part(0.72, -0.8, 0.13, 0.11, PALE, facets=0)                                        # muzzle
    part(0.82, -0.84 + 0.02 * nose, 0.055, 0.05, (255, 120, 140), amb=0.9, facets=0)
    part(0.58, -0.94, 0.07, 0.085, (14, 10, 12), amb=1.0, facets=0)                     # eye
    gx, gy = P(0.565, -0.965); stamp(cv, gx, gy, (255, 255, 255), 1.6)

def chick(cv, x, y, s, flap=0.0, look=0.0, cheep=0.0):
    """x, y the bottom of the body. flap and cheep are 0..1"""
    Y, O = (255, 214, 50), (255, 150, 30)
    for sg in (-1, 1):                                                                  # wings out to the side
        ball(cv, x + sg * 0.95 * s, y - 0.78 * s - flap * 0.25 * s, 0.34 * s, 0.17 * s, (250, 180, 40), sg * (0.35 - 0.9 * flap))
    ball(cv, x, y - 0.68 * s, 0.82 * s, 0.72 * s, Y, amb=0.55)
    hx, hy = x + look * 0.12 * s, y - 1.42 * s
    ball(cv, hx, hy, 0.58 * s, 0.54 * s, (255, 226, 80), amb=0.58)
    ball(cv, hx, hy - 0.55 * s, 0.1 * s, 0.17 * s, O, 0.2)                              # the tuft
    for sg in (-1, 1):
        ball(cv, hx + sg * 0.26 * s + look * 0.1 * s, hy - 0.08 * s, 0.1 * s, 0.13 * s, (12, 8, 8), amb=1.0, facets=0)
        stamp(cv, hx + sg * 0.24 * s + look * 0.1 * s, hy - 0.13 * s, (255, 255, 255), max(1.2, 0.05 * s))
    bw, bh = 0.2 * s, (0.1 + 0.2 * cheep) * s
    bx, by = hx + look * 0.14 * s, hy + 0.17 * s
    fill(cv, [(bx - bw, by), (bx + bw, by), (bx, by + bh + 0.12 * s)], O)
    if cheep > 0.1: fill(cv, [(bx - bw * 0.8, by), (bx + bw * 0.8, by), (bx, by - bh)], (255, 180, 60))

def easter_film():
    far = ridge(4101, 128, 15, (60, 24)); near = ridge(4103, 150, 12, (46, 20))
    tex = fbm(4105, (22, 9, 4))
    petals = Motes(4107, 26, lambda r: (W * 1.1 + r.rand() * 40, 30 + r.rand() * 130, -W * 1.3, 30 + 40 * r.rand(), 0))
    flowers = [(r_x, r_y, c) for r_x, r_y, c in zip(*[np.random.RandomState(4109).rand(34) * W, 158 + np.random.RandomState(4110).rand(34) * 44,
               [((255, 150, 190), (255, 230, 110), (200, 160, 255), (255, 255, 240))[i % 4] for i in range(34)]])]
    close_n = fbm(4111, (30, 12))

    def sky(dawn, sun_y, t):
        v = np.clip((YY - 20 + (dawn - 0.5) * 30) / 150, 0, 1)
        night = ramp([(10, 14, 40), (24, 22, 58), (58, 36, 74), (96, 54, 80)], v)
        day = ramp([(44, 70, 110), (84, 84, 108), (150, 96, 84), (196, 118, 62)], v)
        cv = night * (1 - dawn) + day * dawn
        return cv + 0.0 * sun_y

    def meadow(dawn, sun_y, t):
        cv = sky(dawn, sun_y, t)
        d = np.hypot(XX - GOLD_X, YY - sun_y)
        cv += np.exp(-(d / 46) ** 2)[..., None] * np.array([90.0, 54, 22]) * (0.3 + 0.7 * dawn)
        m = np.clip(19 - d, 0, 1)[..., None]
        cv = cv * (1 - m) + (np.array([255.0, 190, 70]) + np.clip(10 - d, 0, 1)[..., None] * np.array([0.0, 50, 70])) * m
        rays(cv, GOLD_X, sun_y, 11, 150, np.array((26, 18, 8)) * dawn, t)
        lit = 0.45 + 0.55 * dawn
        for r_, c in ((far, (54, 84, 66)), (near, (44, 88, 52))):
            m = (YY > r_[None, :])[..., None]
            c_ = np.array(c, float) * lit * (0.82 + 0.34 * tex[..., None])
            rim_ = np.clip(1 - (YY - r_[None, :]) / 5, 0, 1)[..., None] * np.array([60.0, 44, 16]) * dawn
            cv = cv * (1 - m) + (c_ + rim_) * m
        grass(cv, 170, 16, 4113, t, np.array((30, 62, 34)) * lit, np.array((54, 104, 50)) * lit, n=70, sway=2)
        for x, y, c in flowers:
            if y < 176: stamp(cv, x, y, np.array(c, float) * 0.5 * lit, 2.6)
        return cv

    def eggs(cv, found, dawn, t, skip=()):
        """found: per egg 0 hidden, 1 lifted out of the grass"""
        for i, (x, y, rx, ry, rot, pat) in enumerate(EGGS):
            if i in skip: continue
            f = found[i]
            ey = y - 22 * f
            ball(cv, x, ey, rx * (1 + 0.15 * f), ry * (1 + 0.15 * f), None, rot + 0.15 * f * wave(t, 8, i * 0.3), egg=-0.2, pattern=lambda u, v, p=pat: p(u, v) * (0.62 + 0.38 * dawn), amb=0.62)

    def front(cv, dawn, t):
        lit = 0.45 + 0.55 * dawn
        grass(cv, 208, 20, 4115, t, np.array((26, 64, 30)) * lit, np.array((84, 150, 60)) * lit, n=40, sway=3.5)

    def a_dawn(u, t):
        # the dark lifts; the sun comes up on the golden line and the eggs catch it one by one
        dawn = sm(0.0, 1.0, u)
        sun_y = 140 - 62 * dawn
        cv = meadow(dawn, sun_y, t)
        eggs(cv, [0, 0, 0, 0], dawn, t)
        front(cv, dawn, t)
        for i, (x, y, rx, ry, _, _) in enumerate(EGGS):
            q = sm(0.3 + i * 0.16, 0.42 + i * 0.16, u) * (1 - sm(0.52 + i * 0.16, 0.66 + i * 0.16, u))
            if q > 0.01: sparkle(cv, x + 5, y - 16, 9 * q, (255, 245, 190), q)
        return blackout(push(cv, 1.08 - 0.08 * u, 0.6, 0.7), 1 - sm(0.0, 0.2, u))

    HOPS = [(-46, 18, 0), (18, 70, 1), (70, 122, 2), (122, 172, 3)]            # from, to, the egg found on landing

    def b_hunt(u, t):
        # the rabbit: leap, land, nose at the grass, the egg comes up; four times across the frame
        cv = meadow(1.0, 78, t)
        n = u * len(HOPS)
        k = min(int(n), len(HOPS) - 1); q = n - k
        x0, x1, idx = HOPS[k]
        air = sm(0.0, 0.5, q)
        xx = x0 + (x1 - x0) * air
        hop = 56 * math.sin(math.pi * min(q / 0.5, 1.0))
        land = sm(0.5, 0.6, q) * (1 - sm(0.64, 0.8, q))
        crouch = (1 - sm(0.0, 0.1, q)) * 0.5
        found = [sm(0.62 + i, 0.8 + i, n) for i in range(4)]
        ground_y = 200
        cv2 = cv
        shadow(cv2, xx, ground_y - 2, 42 * (1 - hop / 90), 5, 0.45)
        eggs(cv2, found, 1.0, t)
        lean = (-0.26 * math.cos(math.pi * min(q / 0.5, 1.0))) if q < 0.5 else 0.14 * sm(0.55, 0.62, q) * (1 - sm(0.84, 0.95, q))
        stretch = 1 + 0.28 * math.sin(math.pi * min(q / 0.5, 1.0)) - 0.14 * max(land, crouch)
        rabbit(cv2, xx, ground_y - hop, 48, 1, lean, stretch, ears=(-0.55 * math.sin(math.pi * min(q / 0.5, 1.0))) + 0.2 * land, nose=wave(t, 40))
        front(cv2, 1.0, t)
        for i, (x, y, *_ ) in enumerate(EGGS):
            a = n - (i + 0.62)
            if 0 < a < 0.7:
                for j, (dx, dy) in enumerate(polygon_pts(0, 0, 8 + 34 * a, 5, a * 2 + i)):
                    sparkle(cv2, x + dx, y - 24 + dy, 6 * (1 - a), (255, 245, 170), 1 - a)
        for j in range(2 if q < 0.15 else 0):
            stamp(cv2, xx - 30 + j * 26, ground_y - 8 - q * 90, (200, 220, 150), 3)
        petals.draw(cv2, (t * 4) % 1, lambda v: (255, 170, 200), 1.6)
        return cv2

    def c_chick(u, t):
        # up close: one egg rocks, cracks, and the top flies off
        back = ramp([(60, 74, 110), (120, 100, 90), (84, 120, 60)], np.clip(YY / 190, 0, 1)) * (0.55 + 0.5 * close_n[..., None])
        cv = back.copy()
        d = np.hypot(XX - 170, YY - 50)
        cv += np.exp(-(d / 60) ** 2)[..., None] * np.array([90.0, 56, 20])
        grass(cv, 196, 44, 4117, t, (30, 60, 30), (70, 112, 50), n=26, sway=2)
        rocks = [0.1, 0.27, 0.42]
        roll = sum(math.exp(-max(0.0, u - s) * 18) * math.sin(max(0.0, u - s) * 80) * 0.2 * (1 if u >= s else 0) for s in rocks)
        cx, cy = W / 2, 150
        rx, ry = 44, 56
        crack = sm(0.25, 0.52, u)
        pop = sm(0.52, 0.6, u)
        def zz(u_): return -0.22 + 0.2 * np.abs((u_ * 2.6) % 2 - 1)
        ground_ = cy + ry - 4
        shadow(cv, cx, ground_, 58, 8, 0.5)
        pop2 = sm(0.55, 0.72, u)
        if pop > 0:
            chick(cv, cx, cy + 34 - 28 * pop2, 42, flap=(0.5 + 0.5 * wave(t, 26)) * sm(0.72, 0.8, u), look=0.8 * wave(t, 6), cheep=max(0.0, wave(t, 13)))
        below = (lambda u_, v_: (v_ > zz(u_)).astype(float)) if pop > 0 else None
        ball(cv, cx, cy, rx, ry, None, roll, egg=-0.22, pattern=BIG_EGG, keep=below, amb=0.6)
        if pop == 0 and crack > 0:
            pts = [(cx - rx * (1 - crack * 2 if crack < 0.5 else 0) * 1.0 + i, 0) for i in range(0)]
            n = int(9 * crack) + 1
            for i in range(n):
                x0 = cx - rx + i * (2 * rx / 9)
                x1 = x0 + 2 * rx / 9
                y0 = cy + ry * (-0.22 + 0.2 * ((i % 2)))
                y1 = cy + ry * (-0.22 + 0.2 * ((i + 1) % 2))
                ink(cv, (x0 + roll * 30, y0), (x1 + roll * 30, y1), (14, 8, 6), 1.4)
            glow(cv, cx + (n - 4.5) * 2 * rx / 9, cy - 10, 14, (255, 240, 170), 0.6 * crack)
        if pop > 0:
            f = sm(0.52, 0.86, u)
            top = lambda u_, v_: (v_ < zz(u_)).astype(float)
            ball(cv, cx + 150 * f, cy - 92 * math.sin(math.pi * min(f * 0.72, 1)) + 160 * max(0.0, f - 0.5) ** 2 * 2, rx, ry, None, 1.8 * f, egg=-0.22, pattern=BIG_EGG, keep=top, amb=0.6)
            for i, (dx, dy) in enumerate(polygon_pts(0, 0, 1, 11, 0.3)):                # shell chips
                q = sm(0.52, 0.72, u)
                if q < 1: stamp(cv, cx + dx * 92 * q, cy - 16 + dy * 76 * q + 60 * q * q, np.array(((60, 90, 240), (255, 225, 70), (255, 80, 160))[i % 3], float) * (1 - q), 3.4)
            b = math.exp(-(u - 0.52) * 18)
            glow(cv, cx, cy - 20, 60, (255, 240, 180), 0.9 * b)
            for x, y in phyllotaxis(cx, cy - 56, 14, 16, u * 2):
                q = sm(0.66, 1.0, u)
                if q > 0: sparkle(cv, x, y, 5 * math.sin(math.pi * q) + 0.5, (255, 240, 170), math.sin(math.pi * q))
        grass(cv, 216, 16, 4119, t, (30, 72, 34), (96, 160, 66), n=26, sway=3)
        return push(cv, 1.0 + 0.09 * u, 0.5, 0.55)

    def d_home(u, t):
        # back out: the rabbit sits up with the chick in front of it, the eggs in a row, the day arrived, then dusk takes it
        cv = meadow(1.0 - 0.5 * sm(0.5, 1.0, u), 78, t)
        shadow(cv, 76, 182, 62, 6, 0.45)
        rabbit(cv, 76, 182, 60, 1, 0.0, 1.0, ears=0.1 * wave(t, 6), nose=wave(t, 40))
        chick(cv, 52, 146 + 2 * max(0.0, wave(t, 12)), 19, flap=max(0.0, wave(t, 24)), look=0.7, cheep=max(0.0, wave(t, 12)))
        front(cv, 1.0, t)
        for (x, y, rx, ry, rot, pat), ex in zip(EGGS, (36, 82, 130, 176)):
            ball(cv, ex, 196, rx * 1.1, ry * 1.1, None, rot, egg=-0.2, pattern=pat, amb=0.7)
        petals.draw(cv, (t * 4) % 1, lambda v: (255, 170, 200), 1.6)
        return blackout(cv, sm(0.62, 1.0, u))

    SHOTS = [(0.0, 0.2, a_dawn), (0.2, 0.55, b_hunt), (0.55, 0.84, c_chick), (0.84, 1.01, d_home)]
    return "easter", "the egg hunt", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- Lunar New Year: a lantern street at night ----------------------------------------
VPX, VPY = W / 2, 116
ROWS_D = [14, 22, 36, 58, 92, 140]

def lantern(cv, x, y, r, lit, sway=0.0):
    """ribs, gold caps, a tassel; lit brings the red up and throws light"""
    RED = np.array((70.0, 16, 18)) + np.array((185.0, 34, 14)) * lit
    def ribs(u, v):
        return RED * (0.72 + 0.34 * (np.abs(np.sin(u * 5.2)) > 0.3))[..., None] + np.array((60.0, 40, 0)) * lit * np.exp(-(u * u + v * v) * 2.4)[..., None]
    glow(cv, x, y, r * 1.7, (255, 70, 30), 0.13 * lit)
    GOLD = np.array((120.0, 90, 40)) + np.array((135.0, 110, 30)) * lit
    if r > 3:
        ink(cv, (x, y + r * 0.9), (x + sway, y + r * 1.75), GOLD * 0.85, max(0.8, r * 0.09))
    ball(cv, x, y, r * 1.1, r * 0.92, None, sway * 0.03, pattern=ribs, amb=0.7, facets=0)
    ball(cv, x, y - r * 0.88, r * 0.5, r * 0.15, GOLD, amb=0.8)
    ball(cv, x, y + r * 0.88, r * 0.5, r * 0.15, GOLD, amb=0.8)

def street():
    """the night street in one-point perspective, built once"""
    img = ramp([(10, 10, 34), (20, 14, 44), (36, 20, 52)], np.clip(YY / VPY, 0, 1)).copy()
    d = np.hypot(XX - 150, YY - 46); img += np.exp(-(d / 14) ** 4)[..., None] * np.array([44.0, 40, 30])          # the moon, dim
    dx = np.abs(XX - VPX)
    top, bot = VPY - dx * 0.62 - 2, VPY + dx * 0.56
    walls = (dx > 7) & (YY > top) & (YY < bot)
    k = np.floor(np.log(np.maximum(dx, 7.1) / 7) * 3.1)
    side = np.where(XX < VPX, 0, 3)
    r = np.random.RandomState(4201).rand(40, 2)
    tone = r[(k.astype(int) + side) % 40, 0]
    wall = np.array([58.0, 34, 40]) * (0.65 + 0.7 * tone)[..., None]
    v = (YY - top) / np.maximum(bot - top, 1)
    z = np.log(np.maximum(dx, 7.1)) * 3.1
    win = ((z % 1) > 0.3) & ((z % 1) < 0.75) & (np.abs(v - 0.34) < 0.13) & (dx > 16)
    wall = wall * (1 - 0.9 * win[..., None]) + np.array([150.0, 94, 40]) * win[..., None] * (0.4 + 0.6 * tone)[..., None] * 0.7
    door = ((z % 1) > 0.3) & ((z % 1) < 0.75) & (v > 0.62)
    wall = wall * (1 - 0.65 * door[..., None])
    img = np.where(walls[..., None], wall, img)
    road = YY >= bot
    stone = 0.7 + 0.5 * fbm(4203, (12, 5))[..., None]
    img = np.where(road[..., None], np.array([36.0, 26, 36]) * stone, img)
    img += road[..., None] * np.exp(-(((XX - VPX) / (10 + (YY - VPY) * 0.9)) ** 2))[..., None] * np.array([54.0, 12, 8])
    img = img.copy()
    for sg in (-1, 1):                                                                  # upturned eaves on each house
        for a in range(0, 9):
            d0, d1 = 7 * math.exp(a / 3.1), 7 * math.exp((a + 1) / 3.1)
            y0, y1 = VPY - d0 * 0.62 - 2, VPY - d1 * 0.62 - 2
            lift = d1 * 0.12
            pts = [(VPX + sg * d0, y0 + 1), (VPX + sg * d1, y1 + 1), (VPX + sg * d1 * 1.04, y1 - lift), (VPX + sg * (d0 + d1) / 2, (y0 + y1) / 2 - lift * 0.3), (VPX + sg * d0 * 0.98, y0 - lift * 0.5)]
            fill(img, pts, (22, 38, 44) if a % 2 else (64, 22, 26))
    for i, w_ in enumerate((13, 9, 6)):                                                 # a pagoda closes the street
        y_ = VPY - 12 - i * 7
        fill(img, [(VPX - w_, y_), (VPX + w_, y_), (VPX + w_ - 4, y_ - 5), (VPX - w_ + 4, y_ - 5)], (48, 22, 30))
    return img

def coin(cv, x, y, r, spin):
    c = math.cos(spin); rx = r * abs(c) + 0.8
    GOLD = np.array((255.0, 205, 60)); HI = np.array((255.0, 240, 150))
    def pat(u, v):
        out = np.zeros(u.shape + (3,)) + GOLD
        out[u * u + v * v > 0.68] = HI
        if abs(c) > 0.35: out[(np.abs(u) < 0.27) & (np.abs(v) < 0.27)] = (60, 24, 8)
        return out
    ball(cv, x, y, rx, r, None, 0.3, pattern=pat, amb=0.8, facets=0)

def segment(cv, x, y, r, i, t):
    RED, GOLD = np.array((236.0, 44, 30)), np.array((255.0, 196, 50))
    stripe = i % 2 == 0
    def pat(u, v):
        out = np.zeros(u.shape + (3,)) + RED
        out[v < -0.42] = GOLD
        if r > 7:
            out[(np.abs(((u * 2.4) % 1) - 0.5) < 0.1) & (v > -0.42)] = RED * 0.62
        out[v > 0.58] = (255, 222, 130)
        return out
    ball(cv, x, y, r * 1.28, r, None, 0, pattern=pat, amb=0.62, facets=10)
    if r > 6:
        fill(cv, [(x - r * 0.4, y - r * 0.82), (x, y - r * 1.55), (x + r * 0.4, y - r * 0.82)], GOLD)

def dragon_head(cv, x, y, s, jaw, t, tilt=0.0):
    """frontal, the mask a dance carries: x, y between the eyes"""
    RED, GOLD, WHITE = (240, 46, 30), (255, 200, 50), (252, 244, 226)
    for k in range(14):                                                                 # the mane
        a = k / 14 * 2 * math.pi + tilt
        c = RED if k % 2 else GOLD
        rr = 1.12 + 0.07 * wave(t, 12, k * 0.3)
        ball(cv, x + rr * s * math.cos(a), y + 0.05 * s + rr * s * 0.9 * math.sin(a), 0.2 * s, 0.42 * s, c, a + math.pi / 2, amb=0.6)
    for sg in (-1, 1):
        ball(cv, x + sg * 0.62 * s, y - 1.05 * s, 0.15 * s, 0.56 * s, GOLD, sg * 0.42 + tilt, amb=0.65)       # horns
        ball(cv, x + sg * 0.86 * s, y - 1.2 * s, 0.09 * s, 0.26 * s, GOLD, sg * 1.1 + tilt, amb=0.65)
    my = y + (0.92 + 0.28 * jaw) * s
    ball(cv, x, my + 0.22 * s + 0.2 * jaw * s, 0.72 * s, 0.26 * s, GOLD, tilt, amb=0.6)                       # lower jaw
    for k in range(5):
        ball(cv, x + (k - 2) * 0.28 * s, my + (0.46 + 0.2 * jaw - 0.04 * abs(k - 2)) * s, 0.12 * s, 0.17 * s, WHITE, amb=0.7)   # beard
    ball(cv, x, my - 0.02 * s, 0.66 * s, (0.08 + 0.36 * jaw) * s, (50, 6, 14), amb=1.0, facets=0)             # the mouth
    ball(cv, x, y, 1.08 * s, 0.86 * s, RED, tilt, amb=0.62)
    for sg in (-1, 1):
        ball(cv, x + sg * 0.5 * s, y - 0.5 * s, 0.46 * s, 0.2 * s, GOLD, sg * 0.28 + tilt, amb=0.7)            # brow
        ball(cv, x + sg * 0.45 * s, y - 0.14 * s, 0.27 * s, 0.27 * s, WHITE, amb=0.9, facets=0)
        ball(cv, x + sg * 0.42 * s, y - 0.12 * s, 0.14 * s, 0.16 * s, (12, 8, 10), amb=1.0, facets=0)
        stamp(cv, x + sg * 0.38 * s, y - 0.18 * s, (255, 255, 255), max(1.2, 0.05 * s))
    ball(cv, x, y + 0.5 * s, 0.82 * s, 0.42 * s, (255, 110, 44), tilt, amb=0.65)                               # the snout
    ball(cv, x, y + 0.32 * s, 0.36 * s, 0.12 * s, GOLD, amb=0.7)
    for sg in (-1, 1):
        ball(cv, x + sg * 0.3 * s, y + 0.55 * s, 0.1 * s, 0.09 * s, (30, 6, 8), amb=1.0, facets=0)
        for j in range(8):                                                              # whiskers: they lag the head
            a0, a1 = j / 8, (j + 1) / 8
            def wh(q): return x + sg * (0.75 + 1.05 * q) * s, y + (0.6 + 0.38 * q * q + 0.12 * q * math.sin(2 * math.pi * (t * 12 + q))) * s
            line(cv, wh(a0), wh(a1), np.array((230.0, 180, 60)) * 0.8, max(0.9, 0.035 * s))
    for k in range(5):                                                                  # teeth
        tx = x + (k - 2) * 0.24 * s
        fill(cv, [(tx - 0.1 * s, my - (0.1 + 0.3 * jaw) * s), (tx + 0.1 * s, my - (0.1 + 0.3 * jaw) * s), (tx, my + (0.06 - 0.12 * jaw) * s)], WHITE)

def lunar_film():
    back = street()
    paper = Motes(4211, 60, lambda r: (r.rand() * W, BAR - 10 - r.rand() * 20, (r.rand() - 0.5) * 30, H - BAR, 0))
    r0 = np.random.RandomState(4213)
    fly = [(r0.rand() * W * 0.86 + W * 0.07, r0.rand(), 5 + r0.rand() * 12, r0.rand() * 6) for _ in range(20)]
    smoke = fbm(4215, (26, 12, 5))
    fall = [(r0.rand() * W, r0.rand(), r0.rand() * 6, 3 + 5 * r0.rand()) for _ in range(30)]

    def lanterns(cv, lit, t):
        """rows across the street, farthest first; lit(row) 0..1"""
        for k, d in enumerate(ROWS_D):
            y0 = VPY - d * 0.62 + 2
            n = 5 if d > 20 else 3
            rr = d * 0.095
            sag = d * 0.13
            pts = []
            for i in range(n):
                f = (i + 0.5) / n
                pts.append((VPX - d + 2 * d * f, y0 + sag * (1 - (2 * f - 1) ** 2)))
            if d > 20:
                for i in range(10):
                    f0, f1 = i / 10, (i + 1) / 10
                    y = lambda f: y0 + sag * (1 - (2 * f - 1) ** 2) - rr
                    ink(cv, (VPX - d + 2 * d * f0, y(f0)), (VPX - d + 2 * d * f1, y(f1)), (14, 10, 14), 0.8)
            for i, (x, y) in enumerate(pts):
                if 2 < x < W - 2:
                    lantern(cv, x, y, max(1.4, rr), lit(k, i) * (0.92 + 0.08 * wave(t, 20, i * 0.31 + k * 0.17)), sway=rr * 0.2 * wave(t, 6, i * 0.2))

    def a_street(u, t):
        # the street is dark; lanterns come on row by row, from the pagoda out to us
        def lit(k, i): return sm(0.12 + k * 0.11 + i * 0.012, 0.22 + k * 0.11 + i * 0.012, u)
        all_lit = sm(0.2, 0.85, u)
        cv = back * (0.45 + 0.55 * all_lit)
        cv = cv + (YY > VPY)[..., None] * np.array([18.0, 4, 2]) * all_lit
        lanterns(cv, lit, t)
        return blackout(push(cv, 1.0 + 0.1 * u, 0.5, 0.48), 1 - sm(0.0, 0.14, u))

    def lit_all(k, i): return 1.0

    def b_dance(u, t):
        # the dragon comes down the street, winding, poles under it, from the pagoda to right in our faces
        cv = back + (YY > VPY)[..., None] * np.array([18.0, 4, 2])
        lanterns(cv, lit_all, t)
        n = 70; gap = 0.0155
        head_s = 0.04 + 0.6 * sm(0.0, 1.0, u) ** 0.85
        items = []
        for i in range(n):
            s = head_s - i * gap
            if s <= 0: continue
            items.append((s, i))
        for s, i in sorted(items):
            sc = 0.1 + 1.25 * s * s
            amp = 10 + 62 * s
            x = VPX + amp * math.sin(s * 8.5 - 2 * math.pi * t * 7)
            floor_ = VPY + 92 * sc ** 0.72
            bob = 6 * sc * wave(t, 14, i * 0.09)
            y = floor_ - 44 * sc + bob
            r = 22 * sc
            if i == 0:
                dragon_head(cv, x, y - 8 * sc, 40 * sc, 0.25 + 0.35 * max(0.0, wave(t, 10)), t, tilt=0.1 * wave(t, 7))
                continue
            if i % 5 == 2 and sc > 0.2:                                                 # a pole, a dancer under it
                ink(cv, (x, y + r * 0.6), (x, floor_ - 13 * sc), (200, 160, 90), max(0.7, 1.6 * sc))
                if sc > 0.34:
                    ball(cv, x, floor_ - 9 * sc, 5 * sc, 11 * sc, (40, 26, 56), amb=0.8)
                    ball(cv, x, floor_ - 21 * sc, 4 * sc, 4 * sc, (214, 166, 120), amb=0.8)
            segment(cv, x, y, r, i, t)
        sparks = sm(0.0, 0.4, u)
        for i in range(10):
            q = (t * 18 + i * 0.37) % 1
            stamp(cv, 20 + i * 19 + 6 * math.sin(i * 2.3), H - BAR - 4 - 50 * q, np.array((255.0, 170, 70)) * (1 - q) * sparks, 1.6)
        return cv

    def b_cross(u, t):
        # side on and close: the whole length of it rolling through, a pole and a dancer under every hump
        cv = back * 0.72 + (YY > VPY)[..., None] * np.array([18.0, 4, 2])
        lanterns(cv, lit_all, t)
        floor_ = 208
        hx = W + 44 - (W + 96) * u
        path = lambda x: 112 + 30 * math.sin(x * 0.046 + 2 * math.pi * t * 7)
        n = 44
        pts = [(hx + i * 9.0, path(hx + i * 9.0)) for i in range(n)]
        shadow(cv, W / 2, floor_, 120, 5, 0.35)
        for i, (x, y) in enumerate(pts):
            if i % 6 == 3 and -10 < x < W + 10:                                         # dancers, stepping out of time with each other
                step = wave(t, 28, i * 0.11)
                ink(cv, (x, y + 8), (x, floor_ - 48), (214, 170, 96), 1.5)
                for sg in (-1, 1):
                    ink(cv, (x, floor_ - 26), (x + sg * (6 + 5 * step * sg), floor_ - 2), (64, 40, 96), 3.2)
                ball(cv, x, floor_ - 36, 8.5, 13, (250, 196, 60), amb=0.7)
                ball(cv, x, floor_ - 53, 6.5, 6.5, (222, 170, 124), amb=0.75)
                ball(cv, x, floor_ - 57, 6.8, 3.6, (214, 40, 34), amb=0.8)
        for i in range(n - 1, 0, -1):
            x, y = pts[i]
            taper = min(1.0, (n - i) / 7) * (0.8 + 0.2 * min(1.0, i / 5))
            if -24 < x < W + 24: segment(cv, x, y, 17 * taper, i, t)
        dragon_head(cv, pts[0][0], pts[0][1] - 4, 30, 0.3 + 0.4 * max(0.0, wave(t, 10)), t, tilt=-0.12 * math.cos(pts[0][0] * 0.046 + 2 * math.pi * t * 7))
        return cv

    def c_crackers(u, t):
        # a string of crackers, the spark climbing it, each one going off
        cv = ramp([(40, 8, 12), (56, 12, 14), (34, 10, 14)], np.clip(YY / H, 0, 1)) * (0.7 + 0.5 * smoke[..., None])
        rows = 9
        step = (H - 2 * BAR - 14) / rows
        spark = sm(0.05, 0.86, u)
        pops = 0.0
        cracks = []
        for i in range(rows):                                                           # lowest goes first
            ti = i / rows
            y = H - BAR - 16 - i * step
            for sg in (-1, 1):
                cracks.append((ti + (0.03 if sg > 0 else 0), W / 2 + sg * 15, y + (sg + 1) * 3, sg))
        top_y = H - BAR - 16 - spark * rows * step
        ink(cv, (W / 2, BAR - 2), (W / 2, max(top_y, BAR)), (200, 160, 90), 1.2)
        ink(cv, (W / 2, BAR), (W / 2, BAR + 8), (255, 200, 60), 3)
        for ti, x, y, sg in cracks:
            age = spark - ti
            if age < 0 or spark <= 0:
                def pat(u_, v_, sg=sg):
                    out = np.zeros(u_.shape + (3,)) + (240, 34, 30)
                    out[np.abs(v_) < 0.18] = (255, 205, 70)
                    out[np.abs(v_ + 0.8) < 0.14] = (255, 205, 70)
                    return out
                ball(cv, x + sg * 16 + sg * 2 * wave(t, 18, ti), y, 9.5, 22, None, sg * -1.2 + 0.05 * wave(t, 18, ti), pattern=pat, amb=0.7, facets=10)
            elif age < 0.2:
                q = age / 0.2
                pops = max(pops, 1 - q)
                glow(cv, x, y, 12 + 36 * q, (255, 180, 70), 1.2 * (1 - q) ** 2)
                if q < 0.34: stamp(cv, x, y, (255, 250, 220), 13 * (1 - q * 2))
                for j, (dx, dy) in enumerate(polygon_pts(0, 0, 1, 7, ti * 9)):
                    stamp(cv, x + dx * 54 * q, y + dy * 54 * q + 34 * q * q, np.array(((255, 60, 40), (255, 220, 110))[j % 2], float) * (1 - q), 3)
        glow(cv, W / 2, top_y, 7, (255, 220, 120), 0.9 * (1 - sm(0.84, 0.9, u)))
        stamp(cv, W / 2, top_y, (255, 255, 230), 3.4 * (1 - sm(0.84, 0.9, u)))
        cv += np.array([70.0, 34, 12]) * pops * (0.5 + 0.5 * max(0.0, wave(t, 30)))
        paper.draw(cv, (t * 10) % 1, lambda v: (230, 40, 34), 2.2)
        return shake(cv, 2.5 * pops * wave(t, 37), 2.5 * pops * wave(t, 41))

    def d_coins(u, t):
        # close: the great head fills the street, the jaw drops, gold pours out of it
        cv = back * 0.6
        lanterns(cv, lit_all, t)
        cv *= 0.5
        s = 46 + 6 * sm(0.0, 1.0, u)
        jaw = sm(0.14, 0.32, u)
        hy = 76 + 4 * wave(t, 10)
        hx = W / 2 + 7 * wave(t, 6)
        dragon_head(cv, hx, hy, s, 0.15 + 0.85 * jaw, t, tilt=0.05 * wave(t, 6))
        mx, my = hx, hy + (0.92 + 0.28 * jaw) * s
        glow(cv, mx, my, 34, (255, 190, 60), 0.5 * jaw)
        if jaw > 0.1:
            for i in range(16):
                a = i * GOLDEN_ANGLE
                q = (u * 2.2 + i / 16) % 1 if u > 0.2 else 0
                if q == 0: continue
                vx, vy = 112 * math.cos(a), -50 + 30 * math.sin(a)
                x, y = mx + vx * q, my + vy * q + 110 * q * q
                coin(cv, x, y, 3 + 14 * q, i * 1.7 + q * 7)
        return push(cv, 1 + 0.05 * u, 0.5, 0.5) + np.exp(-u * 18) * 40

    def e_rise(u, t):
        # the street falls away; red lanterns go up into the dark with the last gold under them
        fade = sm(0.68, 1.0, u)
        cv = ramp([(8, 8, 28), (20, 12, 40), (52, 22, 44)], np.clip(YY / H, 0, 1)).copy()
        drop = 130 * sm(0.0, 1.0, u)
        roof = (YY > 176 + drop - 50 * np.abs(np.sin(XX * 0.05)))[..., None]
        cv = cv * (1 - roof) + np.array([14.0, 12, 22]) * roof
        for x, ph, r, sw in fly:
            q = (u * 0.8 + ph) % 1
            size = (0.5 + 0.5 * r / 17) * (1 - q * 0.45)
            lantern(cv, x + 7 * math.sin(q * 6 + sw), H - BAR + 10 - q * (H - 2 * BAR + 30), 4 + r * 0.7 * size, 1.0 - 0.3 * q)
        for x, ph, sw, r in fall:
            q = (u * 1.5 + ph) % 1
            if q < 0.7: coin(cv, x, BAR + q * (H - 2 * BAR), r * 0.55, sw + q * 12)
        return blackout(cv, fade) + np.exp(-u * 18) * 40

    SHOTS = [(0.0, 0.16, a_street), (0.16, 0.28, b_dance), (0.28, 0.52, b_cross), (0.52, 0.68, c_crackers), (0.68, 0.87, d_coins), (0.87, 1.01, e_rise)]
    return "lunar", "the dragon dance", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- Holi: daylight courtyard ---------------------------------------------------------
MAG, YEL, GRN, BLU = np.array((255.0, 30, 140)), np.array((255.0, 215, 20)), np.array((40.0, 225, 80)), np.array((40.0, 110, 255))
HUES = [MAG, YEL, GRN, BLU]

def courtyard():
    """warm sandstone, three arches onto a pale sky, tile floor, marigolds along the top"""
    row = (YY // 16).astype(int); off = (row % 2) * 13
    col = ((XX + off) // 26).astype(int)
    sh = np.random.RandomState(4301).rand(40, 40)[row % 40, col % 40]
    mort = (YY % 16 < 1.4) | ((XX + off) % 26 < 1.4)
    wall = np.array([112.0, 92, 70]) * (0.78 + 0.34 * sh + 0.2 * (fbm(4302, (22, 8)) - 0.5))[..., None] * (1 - 0.3 * mort[..., None])
    img = wall.copy()
    sky = ramp([(92, 136, 176), (130, 160, 180)], np.clip((YY - 30) / 110, 0, 1))
    for cx in (27, 81, 135, 189):
        wd = 17
        ar = (np.abs(XX - cx) < wd) & (YY < 172) & (YY > 62 + 28 * (1 - np.sqrt(np.clip(1 - ((XX - cx) / wd) ** 2, 0, 1))) - 2)
        band = (np.abs(XX - cx) < wd + 5) & (YY < 172) & (YY > 56 + 28 * (1 - np.sqrt(np.clip(1 - ((XX - cx) / (wd + 5)) ** 2, 0, 1))))
        img = np.where(band[..., None], np.array([140.0, 118, 88]), img)
        img = np.where(ar[..., None], sky, img)
    z = np.clip((YY - 172) / 48, 0, 1)
    fx, fy = (XX - W / 2) / (0.45 + z), z ** 0.6 * 6
    tile = ((np.floor(fx / 22) + np.floor(fy)) % 2)[..., None]
    fl = np.array([118.0, 88, 68]) * (0.72 + 0.26 * tile) * (0.75 + 0.4 * z[..., None])
    img = np.where((YY > 172)[..., None], fl, img)
    for j in range(3):                                                                  # marigolds swagged along the wall
        x0 = 12 + j * 64
        for i in range(17):
            f = i / 16
            x, y = x0 + 64 * f, 30 + 13 * (1 - (2 * f - 1) ** 2)
            ball(img, x, y, 4.2, 4.2, (232, 132, 26) if i % 3 else (240, 190, 40), amb=0.65, facets=6)
    return img

def splat_field(seed, n):
    """arrival: per pixel, the level (0..1) at which a splat reaches it, and whose colour it is. Blobs grow and merge until all is covered."""
    r = np.random.RandomState(seed)
    warp_ = fbm(seed + 1, (20, 9, 4)) - 0.5
    arrival = np.full((H, W), 9.0); colour = np.zeros((H, W), int)
    for i in range(n):
        x, y, t0, sp = r.rand() * W, 22 + r.rand() * (H - 44), r.rand(), 26 + r.rand() * 18
        d = np.hypot(XX - x, YY - y) + warp_ * 46
        a = t0 + np.maximum(d, 0) / sp * 0.5
        m = a < arrival
        arrival[m] = a[m]; colour[m] = i % 4
    return arrival, np.stack(HUES)[colour]

def person(cv, x, y, s, up_l, up_r, lean=0.0, cloth=(246, 240, 228), skin=(226, 170, 124), hair=(30, 20, 20), jump=0.0):
    """x, y the feet; s the height. up_* raise the arms, 0 by the side to 1 overhead."""
    y = y - jump * s * 0.22
    lx = lean * s
    SH = lambda c, k: tuple(np.array(c, float) * k)
    for sg in (-1, 1):                                                                  # legs
        kx = x + sg * 0.09 * s
        ball(cv, kx + lx * 0.2, y - 0.2 * s, 0.075 * s, 0.22 * s, SH(cloth, 0.9), amb=0.7)
    ball(cv, x + lx * 0.6, y - 0.54 * s, 0.2 * s, 0.25 * s, cloth, lean * 0.5, amb=0.6)                       # the kurta
    sx, sy = x + lx, y - 0.68 * s
    for sg, up in ((-1, up_l), (1, up_r)):
        ang = math.radians(170 - 145 * up)                                              # from the hip round to overhead
        ex, ey = sx + sg * 0.17 * s + sg * 0.2 * s * math.sin(ang), sy - 0.2 * s * math.cos(ang)
        hx, hy = ex + sg * 0.18 * s * math.sin(ang), ey - 0.18 * s * math.cos(ang)
        ink(cv, (sx + sg * 0.15 * s, sy), (ex, ey), cloth, 0.065 * s)
        ink(cv, (ex, ey), (hx, hy), skin, 0.05 * s)
        ball(cv, hx, hy, 0.065 * s, 0.065 * s, skin, amb=0.7)
    ball(cv, sx, y - 0.9 * s, 0.13 * s, 0.135 * s, skin, amb=0.75)                      # head
    ball(cv, sx, y - 0.9 * s, 0.135 * s, 0.14 * s, hair, amb=0.9, keep=lambda u_, v_: (v_ < -0.28).astype(float))
    return sx, sy

def hand(x, y, s, up, sg):
    ang = math.radians(170 - 150 * up)
    ex = x + sg * 0.17 * s + sg * 0.38 * s * math.sin(ang)
    return ex, y - 0.38 * s * math.cos(ang)

def holi_film():
    back = courtyard()
    cl = [fbm(4310 + i * 5, (34, 14, 6)) for i in range(4)]
    arrival, tint = splat_field(4330, 150)
    # four throws: who, when in the film, where it bursts, colour. On the golden section lines and their mirrors.
    FOLK = [(54, 192, 100), (108, 198, 108), (162, 192, 100)]
    THROWS = [(0, 0.15, (GOLD_X - 18, GOLD_Y - 70), 0), (2, 0.25, (W - GOLD_X + 10, H - GOLD_Y + 24), 1),
              (1, 0.35, (GOLD_X + 18, H - GOLD_Y + 4), 2), (0, 0.45, (W - GOLD_X - 14, GOLD_Y - 4), 3)]
    dust = [(np.random.RandomState(4340 + i).rand(3)) for i in range(70)]
    fog_n = lambda t: noise(4350, 60, t)

    def cloud(cv, x, y, r, hue, idx, opa, t):
        if opa <= 0.01 or r < 2: return
        n = np.roll(cl[idx], (int(t * 90 * (1 + idx % 2)), int(-t * 60)), (0, 1))
        d = np.hypot((XX - x), (YY - y) * 1.1) / r
        den = np.clip((1 - d) * 1.9 + (n - 0.5) * 2.2, 0, 1) * opa
        tone = np.clip(0.8 + 2.2 * (np.roll(n, 7, 0) - n) + 0.25 * np.clip((y - YY) / r, -1, 1), 0.55, 1.2)
        c = hue * tone[..., None] + 30 * n[..., None]
        cv[:] = cv * (1 - den[..., None] * 0.94) + c * den[..., None] * 0.94

    def drench(cv, level):
        if level <= 0: return cv
        m = np.clip((level - arrival) * 30, 0, 1)[..., None] * 0.92
        lum = cv.mean(2, keepdims=True) / 150
        return cv * (1 - m) + tint * np.clip(cv.mean(2, keepdims=True) / 200, 0.3, 1.2) ** 1.1 * m

    def fog(cv, a, t):
        if a <= 0.001: return cv
        n = np.clip(fog_n(t), 0, 1)
        c = ramp([MAG, YEL, GRN, BLU, MAG], (n * 0.9 + 0.1 + (XX / W) * 0.3) % 1)[..., :3]
        edge = np.clip((a * 1.6 - (1 - n) * 0.6), 0, 1)[..., None]
        return cv * (1 - edge) + c * (0.72 + 0.4 * n[..., None]) * edge

    def throw_arm(i, u):
        """how far each person's throwing arm is raised at film time u"""
        up = 0.05
        for who, at, _, _ in THROWS:
            if who == i: up = max(up, sm(at - 0.05, at - 0.005, u) * (1 - sm(at + 0.03, at + 0.08, u)))
        return up

    def bursts(cv, u, t):
        for who, at, (bx, by), idx in THROWS:
            age = u - at
            fx, fy, fs = FOLK[who]
            hx, hy = hand(fx, fy - 0.68 * fs, fs, 1.0, 1 if who != 2 else -1)
            hue = HUES[idx]
            if -0.03 < age <= 0.0:                                                      # the handful in flight
                q = (age + 0.03) / 0.03
                for k in range(8):
                    p = max(0.0, q - k * 0.05)
                    stamp(cv, hx + (bx - hx) * p, hy + (by - hy) * p - 26 * math.sin(math.pi * p), hue * (1 - k * 0.1), 5 - k * 0.35)
            if age > 0:
                r = 74 * (1 - math.exp(-age * 18)) + 12
                drift = age * 30
                cloud(cv, bx + drift * (1 if idx % 2 else -1), by + age * 26, r, hue, idx, 1 - sm(0.32, 0.56, age) * 0.55, t)
                if age < 0.1:
                    q = age / 0.1
                    for k, (x, y) in enumerate(polygon_pts(bx, by, 10 + 70 * q, 11, idx)):
                        stamp(cv, x, y + 20 * q * q, hue * (1 - q) + 40 * (1 - q), 3)

    def folk(cv, u, t, jump=0.0, both=False):
        for i, (x, y, s) in enumerate(FOLK):
            up = throw_arm(i, u)
            shadow(cv, x, y, 24, 4, 0.5)
            hop = jump * max(0.0, wave(t, 16, phi_phase(i)))
            if both:
                u_l, u_r = 0.93 + 0.07 * wave(t, 16, i * 0.2), 0.93 + 0.07 * wave(t, 16, i * 0.2 + 0.4)
            else:
                u_l, u_r = (0.1, up) if i != 2 else (up, 0.1)
            person(cv, x, y, s, u_l, u_r, lean=(0.05 * up * (1 if i != 2 else -1)), jump=hop)

    def bowls(cv, level):
        for (x, y), hue in zip(((30, 206), (80, 209), (136, 209), (186, 206)), HUES):
            ball(cv, x, y - 2, 16, 8, hue * 1.0, amb=0.7)
            ball(cv, x, y + 2, 19, 6, (190, 150, 70), amb=0.65)

    def a_morning(u, t):
        # the last fog of colour clears off a quiet courtyard: three friends in white
        cv = back.copy()
        folk(cv, 0.0, t)
        bowls(cv, 0)
        return fog(cv, 1 - sm(0.0, 1.0, u), t)

    def b_throw(u, t):
        # film time from 0.13; each of four colours goes up and bursts
        ft = 0.13 + u * 0.37
        cv = back.copy()
        level = sm(0.2, 0.52, ft) * 0.55
        cv = drench(cv, level)
        folk(cv, ft, t)
        bowls(cv, 0)
        cv = drench(cv, level * 0.8)
        bursts(cv, ft, t)
        return cv

    def c_meet(u, t):
        # no courtyard left: two walls of colour meet head on, and the others roll through them
        cv = np.array([150.0, 120, 96]) * np.ones_like(back) * (0.55 + 0.3 * cl[1][..., None])
        cloud(cv, 18 + 80 * u, 110, 116, MAG, 0, 1, t)
        cloud(cv, W - 18 - 76 * u, 140, 116, GRN, 2, 1, t)
        cloud(cv, W / 2 + 40 - 30 * u, 40 + 60 * u, 62, YEL, 1, sm(0.1, 0.45, u), t)
        cloud(cv, W / 2 - 34 + 30 * u, 216 - 60 * u, 62, BLU, 3, sm(0.3, 0.7, u), t)
        # sun coming through it, and the grains flung off the front where they meet
        rays(cv, W / 2, 24, 9, 200, (70, 60, 40), t)
        for x, y in phyllotaxis(W / 2, 118, 60, 11.5, u * 2.4, 1.0):
            q = sm(0.1, 0.9, u)
            k = np.array(((255, 245, 220), (255, 200, 240))[int(x) % 2], float) * math.sin(math.pi * q) * 0.75
            stamp(cv, W / 2 + (x - W / 2) * q * 1.1, 118 + (y - 118) * q * 1.1, k, 2.4)
        return push(cv, 1.0 + 0.12 * u, 0.5, 0.5)

    def d_drenched(u, t):
        # everyone and everything has it now: arms up, colour raining
        cv = back * 0.72
        folk(cv, 1.0, t, jump=1.0, both=True)
        level = 0.75 + 0.9 * sm(0.0, 0.6, u)
        cv = drench(cv, level)
        for i, (x, y, s_) in enumerate(FOLK):                                           # faces stay faces, with a smear of someone's colour
            hop = max(0.0, wave(t, 16, phi_phase(i)))
            hy = y - hop * s_ * 0.22 - 0.9 * s_
            ball(cv, x, hy, 0.13 * s_, 0.135 * s_, np.array((226.0, 170, 124)) * 0.8 + HUES[(i + 3) % 4] * 0.2, amb=0.8)
            ball(cv, x, hy, 0.135 * s_, 0.14 * s_, (30, 20, 20), amb=0.9, keep=lambda u_, v_: (v_ < -0.28).astype(float))
        for i, (a, b, c) in enumerate(dust):
            q = (t * 10 + a) % 1
            stamp(cv, b * W, BAR + q * (H - 2 * BAR), HUES[i % 4] * 1.0 + 30, 2.4 + 2 * c)
        cloud(cv, 30 + 30 * u, 46, 34 + 14 * u, MAG, 0, 0.8, t)
        cloud(cv, W - 30 - 30 * u, 50, 34 + 14 * u, BLU, 3, 0.8, t)
        cloud(cv, W / 2 + 40 * math.sin(u * 6), 30, 28 + 12 * u, YEL, 1, 0.7, t)
        return cv

    def e_fog(u, t):
        cv = d_drenched(1.0, t)
        return fog(cv, sm(0.0, 1.0, u), t)

    SHOTS = [(0.0, 0.13, a_morning), (0.13, 0.5, b_throw), (0.5, 0.66, c_meet), (0.66, 0.92, d_drenched), (0.92, 1.01, e_fog)]
    return "holi", "the colours", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

RECIPES = [celebrating(easter_film, Wear.ears, [(110, 160, 220), (190, 150, 230), (250, 220, 150)]),
           celebrating(lunar_film, Wear.lantern, [(150, 24, 24), (226, 54, 36), (250, 180, 70)]),
           celebrating(holi_film, Wear.spatter, [(226, 50, 160), (250, 200, 50), (60, 200, 120)])]
