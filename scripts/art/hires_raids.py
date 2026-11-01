"""Hi-res recipes, the raids. Andrew 2026-10-31: "it would be great if raids were really cinematic". Each is a
short film, 112 frames (about 16 s) in shots, letterboxed, with close-ups painted from big sprites rather than zoomed."""
from hires_kit import *
from hires_batch2c import sm, cut_sprite, lum_of, rim_of, up_of, darken_round, over_above, shadow, shake, coursed, spike, poly
from hires_batch3a import figure, stand, spot, lean_bend, beat, ground, debris

FILM = 112
BAR = 20

def letterbox(cv):
    cv[:BAR] = BG; cv[H - BAR:] = BG
    return cv

def blackout(cv, f):
    """towards the panel colour, so the rim seems to hold the dark"""
    return BG + (cv - BG) * (1 - f)

def push(cv, zoom, fx=0.5, fy=0.5):
    """a slow camera move on the finished picture; keep it small, big moves get a bigger sprite"""
    ww, hh = W / zoom, H / zoom
    x0, y0 = (W - ww) * fx, (H - hh) * fy
    im = Image.fromarray(np.clip(cv, 0, 255).astype(np.uint8)).resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + ww, y0 + hh))
    return np.asarray(im, float)

def play(shots, t):
    """shots: (start, end, fn(u, t)) over the film; returns the picture of the shot t is in"""
    for a, b, fn in shots:
        if a <= t < b: return fn((t - a) / (b - a), t)
    return shots[-1][2](1.0, t)

def rays(cv, x, y, n, length, colour, t, spread=0.32, down=True):
    """god rays out of (x, y): soft wedges that drift"""
    a = np.arctan2(YY - y, XX - x); d = np.hypot(XX - x, YY - y)
    beam = 0.5 + 0.5 * np.cos(a * n + 2 * math.sin(2 * math.pi * t))
    cv += (beam ** 6 * np.clip(1 - d / length, 0, 1) ** 1.5 * (0.6 + spread))[..., None] * np.array(colour, float)

# ---- the Theatre of Blood: it is a theatre, so the curtain goes up -------------------
def stage_backdrop():
    """Verzik's stage: black-red back wall with arches, boards in perspective, footlights"""
    img = coursed(22, 40, 3201, (44, 18, 24), (20, 8, 3)).copy()
    for cx in (40, W - 40):
        m = ((np.abs(XX - cx) < 24) & (YY > 40 + 24 * (np.abs(XX - cx) / 24) ** 2))[..., None]
        img = img * (1 - m) + np.array([14.0, 6, 10]) * m
    z = np.clip((YY - 186) / 40, 0, 1)
    boards = 0.72 + 0.32 * (((XX - W / 2) / (0.4 + z)) % 22 > 2)
    m = (YY > 186)[..., None]
    return img * (1 - m) + (np.array([58.0, 30, 26]) * boards[..., None] * (0.55 + 0.6 * z[..., None])) * m

def tob_film():
    back = stage_backdrop()
    seat, (sh, sw) = figure("Verzik_Vitur.png", 150, H - 2 * BAR - 4, 60, exposure=0.8, gamma=0.95, sat=1.15, healed=3)
    face, (fh, fw) = figure("Verzik_Vitur.png", 440, 560, 60, exposure=0.78, gamma=0.95, sat=1.15, healed=3)
    fly, (ah, aw) = figure("Verzik_Vitur_(flying).png", 180, H - 2 * BAR - 2, 60, exposure=0.8, gamma=0.95, sat=1.15, healed=3)
    last, (lh, lw) = figure("Verzik_Vitur_(final_form).png", W + 34, H - 2 * BAR + 6, 60, exposure=0.9, gamma=0.85, sat=1.25, healed=3)
    last_base = last.rgb.copy(); last_lum = lum_of(last, 4, 96)
    last_dark = ramp([(6, 2, 8), (20, 8, 22), (46, 18, 44), (90, 40, 70)], last_lum)[..., :3]
    folds = 0.5 + 0.5 * np.sin(XX * 0.36 + 1.3 * np.sin(YY * 0.02) + 0.4 * np.sin(XX * 0.09))
    velvet = np.array([150.0, 16, 26]) * (0.28 + 0.8 * folds[..., None] ** 1.5) * (0.75 + 0.35 * np.clip(YY / 200, 0, 1))[..., None]
    drops = Motes(3211, 40, lambda r: (r.rand() * W, -6, 6 * (r.rand() - 0.5), H + 10, 0))
    rubble = debris(3213, 30, W / 2, 196, 200, 150)
    gather = [(math.cos(i * GOLDEN_ANGLE), math.sin(i * GOLDEN_ANGLE), 18 + 15 * math.sqrt(i)) for i in range(1, 30)]
    pool_n = lambda t: noise(3215, 12, t)

    def curtains(cv, gap, t):
        """the two halves draw apart; gap 0 shut, 1 fully open"""
        half = W / 2 * (1 - gap)
        sway = 2.5 * np.sin(YY * 0.04 + 2 * math.pi * t * 4)
        m = ((XX < half + sway) | (XX > W - half + sway))[..., None]
        out = cv * (1 - m) + velvet * m
        hem = (np.abs(XX - (half + sway)) < 2.5) | (np.abs(XX - (W - half + sway)) < 2.5)
        return out * (1 - (hem * (gap > 0.01))[..., None] * 0.55)

    def footlights(cv, power, t):
        for i in range(9):
            x = 12 + i * 24
            glow(cv, x, H - BAR - 3, 9, (255, 170, 80), power * (0.45 + 0.08 * wave(t, 12, i * 0.3)))

    def a_curtain(u, t):
        gap = sm(0.22, 0.95, u)
        cv = back * (0.5 + 0.5 * gap)
        spot_ = np.exp(-((XX - W / 2 - (YY - 20) * 0) / (30 + YY * 0.22)) ** 2)[..., None] * gap
        cv = cv + spot_ * np.array([60.0, 36, 40])
        left, top = (W - sw) // 2, BAR + 2
        chains = dict(c=(0.5, 0.1), r=(0.3, 0.14), p=(0.5, 0.0), a=2.5 * wave(t, 4), soft=0.6)
        hand = dict(c=(0.73, 0.52), r=(0.16, 0.08), p=(0.62, 0.48), a=-3 * wave(t, 6), soft=0.5)
        stand(cv, seat, [chains, hand], left, top, (0.5 * wave(t, 4), -0.8), None, 0.5)
        footlights(cv, 0.4 + 0.6 * sm(0.0, 0.4, u), t)
        cv = curtains(cv, gap, t)
        # the valance over the top, always shut
        val = (YY < BAR + 14 + 8 * np.abs(np.sin(XX * 0.065)))[..., None]
        cv = cv * (1 - val) + velvet * 0.8 * val
        return push(cv, 1 + 0.07 * u, 0.5, 0.42)

    def b_face(u, t):
        # up close: she looks up, the room goes red, and something gathers in her hand
        cv = back * 0.45
        bloom = sm(0.25, 0.9, u)
        cv = cv + np.array([70.0, 4, 8]) * bloom * 0.8
        fx0, fy0 = 0.5, 0.415                                                    # the face stays on the upper golden line
        left = W / 2 - fx0 * fw + 6 * wave(t, 3)
        top = H / PHI - fy0 * fh - 40 * sm(0.0, 1.0, u) * 0.3 - 10
        head = dict(c=(0.5, 0.41), r=(0.1, 0.07), p=(0.5, 0.45), a=-4 * (1 - sm(0.1, 0.5, u)) + 2 * wave(t, 6), soft=0.5)
        stand(cv, face, [head], left, top, (0.8 * wave(t, 3), -0.7), None, 0.5)
        ex, ey = spot(face, head, 0.5, 0.405, left, top)
        for dx in (-13, 13):
            glow(cv, ex + dx, ey, 3 + 2 * bloom, (255, 20, 16), 0.3 + 0.6 * bloom)
            stamp(cv, ex + dx, ey, (255, 210, 170), 1.2 + 1.6 * bloom)
        for i, (dx, dy, rr) in enumerate(gather):                                # the blood comes to her
            q = (u * 2 + i * 0.07) % 1
            d = rr * 2.6 * (1 - q)
            stamp(cv, ex + dx * d, ey + 70 + dy * d, np.array((255.0, 30, 34)) * q * bloom, 1.8)
        for t0, (x, y, _, vy, _) in drops.p[:14]:                                # down the wall
            v = (t * 6 + t0) % 1
            line(cv, (x, y + vy * v), (x, y + vy * v - 16), np.array((150.0, 8, 14)) * bloom, 1.0)
        flash = sm(0.93, 1.0, u)
        cv = cv * (1 - flash) + np.array([255.0, 214, 196]) * flash
        return push(cv, 1 + 0.12 * u, 0.5, 0.4)

    def floor_blood(cv, level, t):
        n = pool_n(t)
        line_y = H - BAR - 6 - 42 * level + 2 * np.sin(XX * 0.07 + 2 * math.pi * t * 6)
        m = (YY > line_y)[..., None] * (level > 0.01)
        c = ramp([(50, 2, 8), (110, 8, 14), (200, 22, 26)], np.clip(n * 0.8 + 0.1, 0, 1))[..., :3]
        return cv * (1 - m * 0.92) + c * m * 0.92

    def c_fly(u, t):
        # the chair is gone; she hangs in the chains in a red storm, the camera low and rising
        flash = math.exp(-u * 9)
        strike = [0.2, 0.46, 0.72]
        lit = max(math.exp(-max(0.0, u - s) * 22) if u >= s else 0.0 for s in strike)
        cv = back * (0.55 + 0.9 * lit) * np.array([1.2, 0.5, 0.6])
        rays(cv, W / 2, BAR, 9, 230, (70, 8, 14), t)
        for i, x in enumerate((30, W - 30)):                                     # the pillars she will throw you behind
            spike(cv, x, H - BAR, 15, 118 + 14 * i, (64, 52, 60), (110, 96, 100))
        rise = 26 * (1 - sm(0.0, 1.0, u))
        left, top = (W - aw) // 2, BAR + rise - 8 + 4 * wave(t, 6)
        arm_l = dict(c=(0.2, 0.56), r=(0.2, 0.1), p=(0.4, 0.55), a=-9 + 5 * wave(t, 8), soft=0.45)
        arm_r = dict(c=(0.8, 0.56), r=(0.2, 0.1), p=(0.6, 0.55), a=9 - 5 * wave(t, 8, 0.2), soft=0.45)
        hem = lambda x, y: (-5 * np.clip(y - 0.65, 0, 1) * 3 * np.sin(2 * math.pi * (t * 8 - y * 2)), 0 * y)
        stand(cv, fly, [arm_l, arm_r], left, top, (0.8 * wave(t, 4), -0.7), hem, 0.55)
        ex, ey = left + 0.5 * aw, top + 0.47 * ah
        glow(cv, ex, ey, 9 + 12 * lit, (255, 30, 24), 0.7 + 0.8 * lit)
        for j, s in enumerate(strike):
            if s <= u < s + 0.08:
                for tx in (20 + j * 80, W - 30 - j * 60):
                    bolt(cv, ex, ey, tx, H - BAR - 18, 3221 + j + tx, (255, 90, 80), jag=46, size=1.6)
        drops.draw(cv, (t * 7) % 1, lambda v: (150, 10, 18), 1.0)
        cv = floor_blood(cv, 0.2 + 0.5 * u, t)
        if u < 0.5:
            rubble.draw(cv, u * 1.9, lambda v: np.array((110, 80, 84)) * (1 - v * 0.5), 2.2)
        cv += flash * np.array([255.0, 214, 196]) * 0.9
        return shake(push(cv, 1.0 + 0.05 * u, 0.5, 1.0), 2 * lit * wave(t, 40), 3 * lit * wave(t, 37))

    def d_last(u, t):
        # black. lightning shows her in pieces; then the lights come up and she is enormous
        times = [0.12, 0.3, 0.44]
        lit = max(math.exp(-max(0.0, u - s) * 16) if u >= s else 0.0 for s in times)
        up = sm(0.52, 0.72, u)
        show = max(lit, up)
        cv = back * (0.12 + 0.55 * show) * np.array([0.8, 0.5, 1.0])
        left, top = (W - lw) // 2, BAR - 4
        def step(i, cx, cy, px, py, sgn):
            return dict(c=(cx, cy), r=(0.18, 0.28), p=(px, py), a=sgn * 6 * wave(t, 6, i * 0.25), soft=0.45)
        parts = [step(0, 0.13, 0.56, 0.33, 0.5, 1), step(1, 0.87, 0.56, 0.67, 0.5, -1), step(2, 0.26, 0.8, 0.4, 0.6, -1), step(3, 0.76, 0.8, 0.6, 0.6, 1),
                 dict(c=(0.48, 0.47), r=(0.09, 0.07), p=(0.48, 0.52), a=3 * wave(t, 5), soft=0.5)]
        last.rgb = np.clip(last_dark * (1 - show) + last_base * show, 0, 255)
        stand(cv, last, parts, left, top, (0.8 * wave(t, 4), -0.7), lambda x, y: (0 * y, -3 * wave(t, 6) * up_of(y, 1.0)), 0.5)
        ex, ey = spot(last, parts[4], 0.48, 0.47, left, top)
        for dx in (-5, 5):                                                       # her eyes are the first thing you can see
            glow(cv, ex + dx, ey, 4 + 2 * up, (255, 30, 40), 0.9)
            stamp(cv, ex + dx, ey, (255, 220, 190), 1.3)
        if lit > 0.2:
            for j, s in enumerate(times):
                if s <= u < s + 0.05:
                    bolt(cv, 30 + j * 70, 0, 80 + j * 30, H - BAR, 3231 + j, (240, 210, 255), jag=56, size=1.5)
        cv = floor_blood(cv, 0.72, t)
        if up > 0.5:                                                             # blood answers: rings out from under her
            for i in range(3):
                q = ((u - 0.6) * 3 - i * 0.3) % 1
                ring(cv, W / 2, H - BAR - 18, 12 + 110 * q, 0.22, 3, np.array((255.0, 60, 70)) * (1 - q) * up)
        return push(cv, 1.06 - 0.06 * u, 0.5, 0.45)

    def e_close(u, t):
        cv = d_last(1.0, t) * (1 - 0.3 * u)
        cv = curtains(cv, 1 - sm(0.0, 0.92, u), t)
        val = (YY < BAR + 14 + 8 * np.abs(np.sin(XX * 0.065)))[..., None]
        return cv * (1 - val * sm(0, 0.5, u)) + velvet * 0.8 * val * sm(0, 0.5, u)

    SHOTS = [(0.0, 0.24, a_curtain), (0.24, 0.42, b_face), (0.42, 0.66, c_fly), (0.66, 0.91, d_last), (0.91, 1.01, e_close)]
    return "tob", "the last act", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- the Chambers of Xeric: a long way down to something waking ----------------------
def cox_film():
    head, (hh, hw) = figure("Great_Olm.png", W + 20, 200, 60, exposure=0.82, gamma=0.92, sat=1.15, healed=3)
    big, (bh, bw) = figure("Great_Olm.png", 620, 400, 60, exposure=0.8, gamma=0.92, sat=1.15, healed=3)
    # the shaft: one long strip of rock, three screens tall, that the camera falls past
    SH = H * 3
    r = np.random.RandomState(3301)
    tex = np.asarray(Image.fromarray((r.rand(SH // 6, W // 6) * 255).astype(np.uint8)).resize((W, SH), Image.BICUBIC), float) / 255
    tex2 = np.asarray(Image.fromarray((r.rand(SH // 16, W // 16) * 255).astype(np.uint8)).resize((W, SH), Image.BICUBIC), float) / 255
    yy3 = np.arange(SH)[:, None]; xx3 = np.arange(W)[None, :]
    strata = 0.5 + 0.5 * np.sin(yy3 * 0.2 + tex2 * 9)
    well = np.clip(1 - np.abs(xx3 - W / 2) / 74 - tex2 * 0.25, 0, 1)                  # the open well down the middle is darkest
    rock = np.stack([tex * 0 + 1] * 3, -1) * np.array([50.0, 54, 62]) * (0.4 + 0.5 * strata[..., None] + 0.4 * tex[..., None]) * (1 - 0.86 * (well[..., None] > 0.02))
    crystals = [(r.randint(4, W - 4), r.randint(0, SH), r.randint(3, 8)) for _ in range(46)]
    cave = fbm(3303, (28, 12, 5))
    wall = ramp([(18, 28, 30), (28, 42, 40), (40, 56, 48)], cave).copy()
    pit = np.clip(1 - np.hypot((XX - W / 2) / 112, (YY - 140) / 88), 0, 1)
    wall = wall * (1 - np.clip(pit * 5, 0, 1)[..., None]) + np.array([6.0, 8, 8]) * np.clip(pit * 5, 0, 1)[..., None]
    wall = ground(wall, 188, (36, 44, 40), 3305)
    shards = polygon_pts(W / 2, 212, 92, 7, 0.3, 0.2)
    dust = Motes(3311, 26, lambda q: (q.rand() * W, BAR, 8 * (q.rand() - 0.5), 200, 0))
    tremor = lambda u, n: math.sin(2 * math.pi * u * n)
    GREEN = np.array((110.0, 255, 130))

    def a_shaft(u, t):
        fall = sm(0.0, 1.0, u) ** 1.2 * (SH - H)
        y0 = int(fall)
        cv = rock[y0:y0 + H].copy()
        for x, y, s in crystals:                                                 # bioluminescent seams pass the camera
            yy = y - fall
            if -14 < yy < H + 14:
                spike(cv, x, yy + s * 2, s, s * 3, (60, 150, 90), (180, 255, 190))
                glow(cv, x, yy, s * 2.4, (50, 150, 90), 0.5)
        cv += np.exp(-(((XX - W / 2) / 60) ** 2))[..., None] * np.array([18.0, 26, 26]) * (1 - u)
        for i in range(10):                                                      # the fall itself: grit streaks up past us
            x = 40 + i * 14 + 7 * math.sin(i * 2.1); q = (t * 16 + i * 0.37) % 1
            line(cv, (x, H * (1 - q)), (x, H * (1 - q) - 26), np.array((110, 120, 120)) * 0.7, 0.8)
        return blackout(cv, max(1 - sm(0.0, 0.16, u), sm(0.9, 1.0, u)))

    def rig_parts(t, turn, roar):
        return [dict(c=(0.28, 0.36), r=(0.3, 0.38), p=(0.5, 0.45), a=2.5 * wave(t, 4) + turn, d=(-0.012 * roar, 0.012 * roar), soft=0.5),
                dict(c=(0.26, 0.74), r=(0.14, 0.16), p=(0.34, 0.6), a=-3 * wave(t, 6), soft=0.5),
                dict(c=(0.68, 0.72), r=(0.14, 0.16), p=(0.6, 0.6), a=3 * wave(t, 6, 0.2), soft=0.5)]

    def b_rise(u, t):
        cv = wall * (0.72 + 0.1 * wave(t, 8))
        rays(cv, W * 0.8, BAR - 4, 7, 230, (24, 56, 44), t)
        rise = sm(0.05, 0.92, u)
        rumble = 2.4 * (1 - sm(0.82, 1.0, u)) * tremor(u, 24)
        left, top = (W - hw) // 2, 188 - hh * rise + 20
        parts = rig_parts(t, 0, 0)
        rgb, al = head.pose(parts, light=(0.6 * wave(t, 4), -0.8), relief=0.3)
        over_above(cv, rgb, al, int(left) - head.pad, int(top) - head.pad, 192)
        dust.draw(cv, (t * 5) % 1, lambda v: np.array((140, 136, 110)) * (1 - v * 0.6), 1.2)
        if u < 0.82:
            for i in range(6):
                q = (u * 4 + i * 0.17) % 1
                stamp(cv, 24 + i * 34, 186 - 34 * q + 20 * q * q, np.array((130, 120, 100)) * (1 - q), 2.4)
        return shake(cv, rumble, rumble * 0.8)

    def c_eye(u, t):
        # hard in on the eye: shut, cracks, opens
        cv = np.array([6.0, 8, 8]) * np.ones_like(wall)
        fx, fy = 0.36, 0.28
        left, top = W * 0.5 - fx * bw, H * 0.5 - fy * bh
        p_ = [dict(c=(0.26, 0.36), r=(0.3, 0.38), p=(0.5, 0.45), a=0.8 * wave(t, 8), soft=0.5)]
        stand(cv, big, p_, left, top, (0.7 * wave(t, 6), -0.7), None, 0.2)
        ex, ey = W * 0.5, H * 0.5
        opens = sm(0.28, 0.72, u)
        im = Image.new("L", (W, H), 0)
        ImageDraw.Draw(im).ellipse((ex - 34, ey - 22 * opens - 1, ex + 34, ey + 22 * opens + 1), fill=255)
        eye = np.asarray(im, float)[..., None] / 255
        d = np.hypot(XX - ex - 6 * wave(t, 4) * opens, YY - ey)
        iris = ramp([(255, 255, 210), (150, 255, 120), (30, 130, 70), (8, 30, 20)], np.clip(d / 26, 0, 1))[..., :3]
        iris = iris * (1 - (d < 6)[..., None]) + np.array([4.0, 6, 4]) * (d < 6)[..., None]
        cv = cv * (1 - eye) + iris * eye
        glow(cv, ex, ey, 22 + 40 * sm(0.7, 1.0, u), GREEN, 0.25 * opens + 0.3 * sm(0.72, 1.0, u))
        rays(cv, ex, ey, 12, 200, (40, 110, 60), t, 0.2)
        cv *= 1 - 0.0
        return push(cv, 1 + 0.16 * sm(0.0, 1.0, u), 0.5, 0.5)

    def d_attack(u, t):
        cv = wall * (0.9 + 0.2 * max(wave(t, 12), 0))
        turn = 8 * math.sin(2 * math.pi * u * 2)
        roar = sm(0.1, 0.25, u) * (1 - sm(0.76, 0.92, u))
        left, top = (W - hw) // 2, 188 - hh + 20
        parts = rig_parts(t, turn, roar)
        rgb, al = head.pose(parts, light=(0.7 * wave(t, 4), -0.7), relief=0.3)
        over_above(cv, rgb, al, int(left) - head.pad, int(top) - head.pad, 192)
        mx, my = left + 0.2 * hw + turn, top + 0.46 * hh
        ex, ey = left + 0.36 * hw + turn, top + 0.28 * hh
        glow(cv, ex, ey, 9, GREEN, 1.0); stamp(cv, ex, ey, (240, 255, 220), 2.2)
        # crystal rain on the heptagon, one after another, each with its ring
        for i, (x, y) in enumerate(shards):
            q = (u * 3 + i * 0.143) % 1
            if q < 0.34:
                v = q / 0.34
                spike(cv, x, BAR + (y - BAR) * v, 5, 22, (80, 230, 120), (225, 255, 225))
                ring(cv, x, y, 10, 0.3, 1.6, GREEN * 0.5)
            elif q < 0.72:
                f = (q - 0.34) / 0.38
                spike(cv, x, y, 8, 22 * (1 - f * 0.5), (60, 180, 100), (200, 255, 200))
                ring(cv, x, y, 5 + 34 * f, 0.3, 3, GREEN * (1 - f))
        # the flame wall comes up the floor behind the crystals, in the second half
        wall_up = sm(0.45, 0.6, u) * (1 - sm(0.82, 0.95, u))
        if wall_up > 0.01:
            plume(cv, mx - 6, my + 3, 0.5, 1.0, 92, 26, (t * 8) % 1, [(255, 255, 220), (255, 190, 60), (230, 80, 20), (90, 24, 10)], 3321, wall_up, wobble=0.18)
        dust.draw(cv, (t * 6) % 1, lambda v: np.array((130, 136, 110)) * (1 - v * 0.6), 1.2)
        s = 2.2 * roar * tremor(u, 22)
        return shake(cv, s, s * 0.8)

    def e_out(u, t):
        # dark takes everything but the eye; then that shuts too
        cv = d_attack(1.0, t) * (1 - sm(0.0, 0.5, u))
        left, top = (W - hw) // 2, 188 - hh + 20
        ex, ey = left + 0.36 * hw, top + 0.28 * hh
        o = 1 - sm(0.68, 0.92, u)
        glow(cv, ex, ey, 7 * o + 0.1, GREEN, o); stamp(cv, ex, ey, np.array((240.0, 255, 220)) * o, 2.2)
        return BG + (cv - BG) * 1.0 if u < 0.5 else cv * (1 - sm(0.9, 1.0, u)) + BG * sm(0.9, 1.0, u)

    SHOTS = [(0.0, 0.2, a_shaft), (0.2, 0.42, b_rise), (0.42, 0.58, c_eye), (0.58, 0.91, d_attack), (0.91, 1.01, e_out)]
    return "cox", "the descent", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

# ---- the Tombs of Amascut: from the dunes to where the sun and moon meet -------------
def toa_film():
    tu, (th, tw) = figure("Tumeken's_Warden_(level-489).png", 150, 176, 60, exposure=0.68, gamma=1.05, sat=1.05, healed=3)
    el, (eh, ew) = figure("Elidinis'_Warden_(level-489).png", 150, 176, 60, exposure=0.68, gamma=1.05, sat=1.05, healed=3)
    tu_big, (tbh, tbw) = figure("Tumeken's_Warden_(level-489).png", 480, 560, 60, exposure=0.68, gamma=1.05, sat=1.05, healed=3)
    el_big, (ebh, ebw) = figure("Elidinis'_Warden_(level-489).png", 480, 560, 60, exposure=0.68, gamma=1.05, sat=1.05, healed=3)
    core_rgb, core_al = fit(cut_sprite("Dark_energy_core.png", 60), 40, 52, exposure=0.95, gamma=0.8, grain=0.25)
    GOLD, BLUE = np.array((255.0, 190, 70)), np.array((90.0, 180, 255))
    # dunes: three ridges, each slid by its own parallax
    R1, R2, R3 = ridge(3401, 150, 26, (60, 28)), ridge(3403, 170, 18, (50, 22)), ridge(3405, 190, 14, (34, 14))
    wn = fbm(3407, (40, 12, 4))
    drift = Motes(3411, 50, lambda r: (r.rand() * W * 1.4, 130 + r.rand() * 90, -W * 1.5, 12 * (r.rand() - 0.5), 0))
    # the hall
    hall = coursed(18, 26, 3413, (58, 46, 30), (20, 8, 3)).copy()
    glyph = (np.random.RandomState(3415).rand(30, 40)[(YY // 9).astype(int) % 30, (XX // 7).astype(int) % 40] > 0.62) & (YY % 18 > 4) & (XX % 7 > 1) & (YY % 9 > 2)
    hall = hall * (1 - 0.4 * glyph[..., None])
    hall = ground(hall, 180, (70, 54, 34), 3417)
    sigil = hex_mask(15, W / 2, 200, 0.0, 1.6, 0.3)
    sand_n = noise(3419, 14, 0.2)
    def dunes(u, t):
        sun_y = 140 - 94 * sm(0.0, 1.0, u)
        sky = ramp([(40, 22, 60), (140, 60, 50), (230, 130, 60), (250, 200, 110)], np.clip((YY - sun_y + 120) / 170, 0, 1)) * (0.5 + 0.5 * sm(0.0, 0.5, u))
        cv = sky.copy()
        d = np.hypot(XX - GOLD_X, YY - sun_y)
        cv = cv * (1 - np.clip(26 - d, 0, 1)[..., None]) + np.array([255.0, 240, 180]) * np.clip(26 - d, 0, 1)[..., None]
        cv += np.exp(-(d / 70) ** 2)[..., None] * np.array([100.0, 60, 24]) * (0.5 + 0.5 * u)
        rays(cv, GOLD_X, sun_y, 11, 210, (70, 44, 18), t)
        pan = 46 * u
        def ridgeline(r_, speed): return np.roll(r_, int(pan * speed))[None, :]
        # the pyramids stand on the far ridge
        for cx, wd, tall, sh in ((60 - pan * 0.4, 56, 92, (60, 34, 34)), (150 - pan * 0.4, 40, 66, (70, 40, 36))):
            v = (YY > 160 - tall + np.abs(XX - cx) * tall / wd) & (YY < 170)
            lit = np.where(XX > cx, 1.0, 0.55)[..., None]
            cv = cv * (1 - v[..., None]) + np.array(sh, float) * lit * v[..., None]
        for r_, sp, c_ in ((R1, 0.5, (120, 66, 44)), (R2, 1.0, (92, 52, 38)), (R3, 1.8, (60, 36, 30))):
            line_ = ridgeline(r_, sp)
            m = (YY > line_)[..., None]
            stripe = 0.82 + 0.28 * np.sin((YY - line_) * 0.6 + wn * 7)
            cv = cv * (1 - m) + np.array(c_, float) * stripe[..., None] * m
        drift.draw(cv, (t * 7) % 1, lambda v: (200, 150, 90), 0.9)
        return blackout(cv, 1 - sm(0.0, 0.2, u))

    def corridor(u, t):
        # flying down the passage: walls and glyphs rush past one vanishing point
        dx, dy = (XX - W / 2) / 74, (YY - H / 2 - 8) / 74
        depth = 1 / np.maximum(np.maximum(np.abs(dx), np.abs(dy)), 0.06)
        travel = (u ** 1.6) * 14
        angle = np.where(np.abs(dx) > np.abs(dy), dy / np.maximum(np.abs(dx), 0.02), dx / np.maximum(np.abs(dy), 0.02))
        z_cell = np.floor((depth + travel) * 2.5)
        a_cell = np.floor(angle * 7)
        tile = np.random.RandomState(3421).rand(64, 64)[z_cell.astype(int) % 64, a_cell.astype(int) % 64]
        lines = ((depth + travel) * 2.5 % 1 < 0.1) | (angle * 7 % 1 < 0.07)
        side = np.abs(dx) > np.abs(dy)
        base = np.where(side, 0.95, 0.65)[..., None] * np.array([96.0, 72, 40])
        fall = np.clip(1.6 / depth, 0.08, 1)[..., None]
        cv = base * (0.55 + 0.7 * tile[..., None]) * (1 - 0.6 * lines[..., None]) * fall
        for k in range(4):                                                       # a torch pair on each pillar passing
            zz = (k * 0.25 - travel / 4) % 1
            s = 74 / (0.25 + zz * 5)
            for sx in (-1, 1):
                x, y = W / 2 + sx * s, H / 2 + 8 - s * 0.3
                if 0 < x < W: glow(cv, x, y, 3 + s * 0.09, (255, 160, 60), min(1.0, s / 40))
        end = 8 + 90 * sm(0.5, 1.0, u)
        cv += np.exp(-(np.hypot(XX - W / 2, YY - H / 2 - 8) / end) ** 2)[..., None] * np.array([255.0, 225, 150]) * (0.6 + 0.8 * sm(0.5, 1.0, u))
        return blackout(cv, 1 - sm(0.0, 0.08, u)) + sm(0.9, 1.0, u) * 200

    def wardens(u, t):
        # gold on the left, blue on the right: a core floats between, each pulls it in turn
        pull = math.sin(2 * math.pi * u * 2)
        cv = hall * (0.62 + 0.1 * wave(t, 8))
        cv += np.exp(-((XX - 0) / 110) ** 2)[..., None] * GOLD * 0.1 * (0.6 + 0.6 * max(pull, 0))
        cv += np.exp(-((XX - W) / 110) ** 2)[..., None] * BLUE * 0.1 * (0.6 + 0.6 * max(-pull, 0))
        cv += (sigil * (YY > 180))[..., None] * (GOLD * max(pull, 0) + BLUE * max(-pull, 0) + 18) * 0.55
        floor_y = H - BAR - 4
        def one(rig, h_, w_, x, phase, mirror_light):
            top_ = floor_y - h_
            arm = dict(c=(0.2, 0.42), r=(0.24, 0.2), p=(0.34, 0.33), a=5 * wave(t, 8, phase), soft=0.45)
            head_ = dict(c=(0.42, 0.13), r=(0.2, 0.14), p=(0.42, 0.25), a=2.5 * wave(t, 6, phase), soft=0.5)
            shadow(cv, x + w_ * 0.5, floor_y, w_ * 0.5, 5, 0.5)
            stand(cv, rig, [arm, head_], x, top_, (mirror_light * 0.7, -0.7), None, 0.5)
            return x, top_, head_
        lx = -18; rx = W - ew + 18
        one(tu, th, tw, lx, 0.0, 1)
        one(el, eh, ew, rx, 0.3, -1)
        cx = W / 2 + 30 * pull; cy = 96 + 8 * wave(t, 8)
        for col, sx in ((GOLD, lx + tw * 0.45), (BLUE, rx + ew * 0.55)):
            on = max(pull, 0) if col is GOLD else max(-pull, 0)
            if on > 0.05:
                bolt(cv, sx, 60, cx, cy, 3431 + int(t * FILM) + int(sx), col * (0.5 + 0.6 * on), jag=18, size=1.3)
        glow(cv, cx, cy, 20, GOLD if pull > 0 else BLUE, 0.6)
        ch, cw = core_al.shape[:2]
        over(cv, core_rgb, core_al, int(cx - cw / 2), int(cy - ch / 2))
        return push(cv, 1 + 0.05 * u, 0.5, 0.5) + np.exp(-u * 14) * 200

    def eclipse(u, t):
        # the split close-up: half her face, half his, a seam of light, and sun and moon meet overhead
        cv = np.ones_like(hall) * np.array([10.0, 8, 12])
        d_ = sm(0.0, 0.7, u)
        sx, moonx = W / 2 - 100 * (1 - d_), W / 2 + 100 * (1 - d_)
        sy = 62
        ds, dm = np.hypot(XX - sx, YY - sy), np.hypot(XX - moonx, YY - sy)
        cv = cv + np.exp(-(ds / 60) ** 2)[..., None] * GOLD * 0.35
        cv = cv * (1 - np.clip(26 - ds, 0, 1)[..., None]) + np.array([255.0, 225, 150]) * np.clip(26 - ds, 0, 1)[..., None]
        m = np.clip(25 - dm, 0, 1)[..., None]
        cv = cv * (1 - m) + np.array([12.0, 24, 44]) * m
        ang = np.arctan2(YY - sy, XX - W / 2)
        total = sm(0.62, 0.74, u) * (1 - sm(0.9, 1.0, u))
        cor = np.exp(-np.maximum(ds - 26, 0) / 13) * (0.65 + 0.2 * np.cos(ang * 9 + 2 * math.pi * t * 4) + 0.15 * np.cos(ang * 17 - 2 * math.pi * t * 6)) * (ds >= 25)
        cv += (cor * total)[..., None] * np.array([255.0, 225, 170]) * 1.3
        gap = 8 + 4 * wave(t, 8)
        left_t = W / 2 - gap - 0.42 * tbw; top_ = 124 - 0.15 * tbh + 26 * (1 - sm(0.0, 1.0, u))
        left_e = W / 2 + gap - 0.40 * ebw
        rgb, al = tu_big.pose([], light=(0.8, -0.6), relief=0.3)
        al2 = al * (np.arange(al.shape[1]) - tu_big.pad + left_t < W / 2 - gap)[None, :, None]
        over(cv, rgb, al2, int(left_t) - tu_big.pad, int(top_) - tu_big.pad)
        rgb, al = el_big.pose([], light=(-0.8, -0.6), relief=0.3)
        al2 = al * (np.arange(al.shape[1]) - el_big.pad + left_e > W / 2 + gap)[None, :, None]
        over(cv, rgb, al2, int(left_e) - el_big.pad, int(top_) - el_big.pad)
        seam = np.exp(-((XX - W / 2) / (2 + 5 * total)) ** 2)[..., None] * (YY > sy + 26)[..., None]
        cv += seam * np.array([255.0, 240, 200]) * (0.5 + 0.6 * total)
        if u > 0.74:
            f = (u - 0.74) / 0.26
            for i, rr in enumerate(phi_radii(20, 3)):
                ring(cv, W / 2, sy, rr + 240 * f, 1.0, 5 - i, (np.array((255.0, 225, 170)) if i != 1 else BLUE * 1.1) * (1 - f))
        white = sm(0.93, 1.0, u)
        return cv * (1 - white) + np.array([250.0, 230, 190]) * white

    def wipe(u, t):
        # the flash settles into blowing sand, and the sand into the dark before dawn
        cv = np.array([250.0, 230, 190]) * (1 - sm(0.0, 0.3, u)) + np.array([40.0, 28, 26]) * sm(0.0, 0.3, u)
        cv = cv * np.ones_like(hall)
        n = noise(3419, 14, t * 5 % 1)
        den = np.clip((n - 0.25) * 1.6, 0, 1)[..., None] * (1 - sm(0.45, 1.0, u))
        cv = cv * (1 - den) + np.array([176.0, 128, 80]) * den
        return blackout(cv, sm(0.55, 1.0, u))

    SHOTS = [(0.0, 0.24, dunes), (0.24, 0.38, corridor), (0.38, 0.66, wardens), (0.66, 0.93, eclipse), (0.93, 1.01, wipe)]
    return "toa", "sun and moon", lambda t: np.clip(letterbox(feather(play(SHOTS, t))), 0, 255), FILM

RECIPES = [tob_film, cox_film, toa_film]
