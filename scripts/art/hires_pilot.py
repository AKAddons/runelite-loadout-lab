"""Hi-res recipes: the pilot three."""
from hires_kit import *
from hires_kit import _layer

# ---- recipes -----------------------------------------------------------------
def vorkath():
    rgb0, al0 = fit(sprite("Vorkath.png"), W * 1.2, H)
    h, w = al0.shape[:2]; left, top = 30, 66
    rig = Rig(rgb0, al0)
    drops = Motes(3, 40, lambda r: (6 * r.rand(), 4 * r.rand(), -8 + r.rand() * 18, 2 + r.rand() * 6, 50 + r.rand() * 34))
    back = ice_backdrop()
    far_snow = Motes(5, 40, lambda r: (r.rand() * W, r.rand() * H, -8 - 8 * r.rand(), 40, 0))
    near_snow = Motes(6, 14, lambda r: (r.rand() * W * 1.3, r.rand() * H, -40 - 20 * r.rand(), 150, 0))
    pool_at = (left + 8, H - 16)
    def scene(t):
        cv = back.copy()
        # an aurora rolls behind the peaks
        veil = np.clip(noise(140, 26, t) - 0.35, 0, 1) * np.exp(-((YY - 44 - 12 * np.sin(XX * 0.03 + 6.28 * t)) / 26) ** 2)
        cv += (veil * EDGE[..., 0])[..., None] * np.array([22.0, 84, 62])
        far_snow.draw(cv, t, lambda u: (60, 72, 88), 0.9)
        sh = np.clip(1 - ((XX - left - w * 0.47) / (w * 0.46)) ** 2 - ((YY - top - h * 0.98) / 11) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.45 * sh
        spit = 0.5 - 0.5 * math.cos(2 * math.pi * t)                     # pool and glow breathe with the spit
        beat = wave(t)
        head = dict(c=(0.075, 0.62), r=(0.105, 0.34), p=(0.17, 0.60), a=2.5 * wave(t, 2, 0.1) - 4 * spit, d=(-0.003 * spit, 0.008 * spit), lit=0.25)
        jaw = dict(c=(0.055, 0.78), r=(0.075, 0.075), p=(0.11, 0.70), a=head["a"] - 9 * spit, d=head["d"], soft=0.5)
        parts = [
            dict(c=(0.33, 0.56), r=(0.2, 0.2), p=(0.33, 0.56), d=(0, -0.012 * wave(t, 2)), soft=0.9),          # the ribs breathe
            dict(c=(0.26, 0.17), r=(0.28, 0.2), p=(0.19, 0.40), a=8 * beat, lit=0.15 * beat, soft=0.5),         # far wing
            dict(c=(0.58, 0.26), r=(0.11, 0.3), p=(0.51, 0.50), a=-8 * wave(t, 1, 0.08), lit=-0.2 * beat),     # wing fingers
            dict(c=(0.61, 0.62), r=(0.09, 0.26), p=(0.50, 0.52), a=6 * wave(t, 1, 0.12)),
            dict(c=(0.46, 0.74), r=(0.15, 0.3), p=(0.47, 0.44), a=-5 * wave(t, 1, 0.15), lit=-0.18 * beat),    # near wing, a beat behind
            head, jaw]
        def tail(x, y):
            u = np.clip((x - 0.62) / 0.38, 0, 1)
            return 0, 9 * u ** 1.4 * np.sin(2 * math.pi * (t * 2 - x * 1.6))
        ang = 2 * math.pi * t
        rgb, al = rig.pose(parts, light=(math.cos(ang), math.sin(ang) * 0.5 - 0.6), relief=0.3, bend=tail)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)                  # the feet stay planted: no whole-body bob
        mx, my = rig.at(head, 0.035, 0.76); mouth = (left + mx, top + my)
        rr = ((XX - pool_at[0]) / (20 + 44 * spit)) ** 2 + ((YY - pool_at[1]) / (6 + 10 * spit)) ** 2
        n = noise(21, 5, t * 2)
        pool = np.clip((1.0 - rr + (n - 0.5) * 1.1) * 3, 0, 1)[..., None]
        cv[:] = cv * (1 - pool) + ramp([(30, 110, 25), (70, 215, 50), (190, 255, 110)], n)[..., :3] * pool
        for t0, (x, y, vx, vy, g) in drops.p:                              # drops leave the moving mouth
            u = (t + t0) % 1.0
            stamp(cv, mouth[0] + x + vx * u, mouth[1] + y + vy * u + g * u * u, ramp([(210, 255, 130), (100, 235, 60), (50, 150, 35)], np.array(u)) * (0.3 + 0.7 * spit), 1.6)
        glow(cv, mouth[0], mouth[1], 11, (50, 150, 35), 0.2 + 0.35 * spit)        # it lights the jaw
        glow(cv, pool_at[0], pool_at[1] - 8, 30, (20, 80, 15), spit)
        near_snow.draw(cv, t, lambda u: (150, 170, 190), 1.2)
        return np.clip(cv, 0, 255)
    return "vorkath", "acid spit", scene

def graardor():
    rgb0, al0 = fit(sprite("General_Graardor.png"), W - 20, H - 30)
    h, w = al0.shape[:2]; left = (W - w) // 2; ground = H - 16
    dust = Motes(9, 70, lambda r: (W * 0.5 + (r.rand() - 0.5) * w * 0.5 - 18, ground, (r.rand() - 0.5) * W * 1.1, -40 * r.rand(), 34))
    rocks = Motes(4, 16, lambda r: (W * 0.5 + (r.rand() - 0.5) * w * 0.6, ground, (r.rand() - 0.5) * W * 0.8, -150 * (0.4 + r.rand()), 190))
    slam = 0.55
    rig = Rig(rgb0, al0)
    wall = keep_backdrop()
    torches = [(13, 84), (W - 13, 84)]
    falling = Motes(14, 30, lambda r: (r.rand() * W, 4, 0, 40, 170))
    def scene(t):
        lift = (t / slam) ** 2 if t < slam else 0.0
        since = t - slam if t >= slam else t + 1 - slam
        impact = math.exp(-since * 8)
        sx = int(round(impact * 6 * math.sin(since * 80))); sy = int(round(impact * 4 * math.cos(since * 110)))
        # torchlight on the wall; the wall shakes less than he does, which reads as depth
        light = np.full((H, W), 0.42 + 0.14 * impact)
        for i, (tx, ty) in enumerate(torches):
            fl = 0.85 + 0.15 * math.sin(2 * math.pi * (t * (3 + i) + i * 0.3))
            light = light + fl * 1.15 * np.exp(-((XX - tx) ** 2 + (YY - ty) ** 2) / (2 * 42.0 ** 2))
        lit = np.roll(wall, (sy // 2, sx // 2), (0, 1)) * light[..., None] + np.clip(light - 0.42, 0, 1)[..., None] * np.array([38.0, 18, 0])
        cv = feather(lit)
        for i, (tx, ty) in enumerate(torches):
            fl = math.sin(2 * math.pi * (t * (3 + i) + i * 0.3))
            stamp(cv, tx, ty + 8, (60, 50, 40), 2.0)
            for k in range(5):
                stamp(cv, tx + math.sin(2 * math.pi * t * 5 + k) * 1.2, ty + 3 - k * 2.2 - fl, np.array([255.0, 190 - k * 28, 70 - k * 12]) * (0.9 - k * 0.1), 2.4 - k * 0.3)
        n = _layer(31, 5)
        floor = np.clip((YY - ground + 4) / 3.0, 0, 1)[..., None]
        cv[:] = cv * (1 - floor) + (np.array([74.0, 62, 50]) + (n[..., None] - 0.5) * 70) * floor
        sh = np.clip(1 - ((XX - W / 2) / (w * 0.55 * (1 - 0.3 * lift))) ** 2 - ((YY - ground - 2) / 6) ** 2, 0, 1)[..., None]
        cv *= 1 - 0.5 * sh
        settle = impact * math.cos(since * 26)                            # arms and cloth overshoot, then settle
        parts = [
            dict(c=(0.38, 0.36), r=(0.22, 0.13), p=(0.38, 0.36), d=(0, -0.008 * wave(t, 2)), soft=0.9),         # chest
            dict(c=(0.70, 0.24), r=(0.24, 0.19), p=(0.58, 0.30), a=-7 * lift + 5 * settle, lit=0.12 * lift),   # pauldron rides the shoulder
            dict(c=(0.13, 0.56), r=(0.17, 0.27), p=(0.19, 0.36), a=15 * lift - 11 * settle),                   # arms out for balance
            dict(c=(0.80, 0.62), r=(0.18, 0.28), p=(0.74, 0.41), a=-13 * lift + 10 * settle),
            dict(c=(0.35, 0.72), r=(0.15, 0.12), p=(0.35, 0.62), a=8 * settle, d=(0, -0.012 * lift)),           # cloth lags the hips
            dict(c=(0.37, 0.10), r=(0.2, 0.16), p=(0.37, 0.20), a=5 * wave(t, 1, 0.2) + 7 * lift - 11 * impact, d=(0.02 * lift, 0.03 * impact), lit=0.2 * impact),
        ]
        lean, drop = 12 * lift - 5 * impact, 11 * impact - 6 * lift
        def body(x, y):
            up = np.clip(1 - y, 0, 1)                                      # the planted foot stays put
            leg = np.exp(-((x - 0.33) / 0.17) ** 2) * np.clip((y - 0.64) / 0.22, 0, 1)
            return -lean * up, -drop * up + 40 * lift * leg
        # the shock lights him from below for a beat, and the torches rake across him
        rgb, al = rig.pose(parts, light=(0.9 * wave(t, 3), -0.55), relief=0.3, bend=body)
        under = np.clip(np.arange(rig.al.shape[0]) / rig.al.shape[0] - 0.5, 0, 1)[:, None, None] * 2
        rgb = rgb * (1 + 0.25 * impact) + under * np.array([120.0, 70, 25]) * impact
        over(cv, rgb, al, left + sx - rig.pad, ground - h + 2 + sy - rig.pad)
        # the eyes flare with the blow
        ex, ey = rig.at(parts[-1], 0.405, 0.102)
        glow(cv, left + sx + ex, ground - h + 2 + sy + ey, 5, (150, 120, 20), 0.15 + 0.85 * impact)
        if since < 0.4:
            cracks = (_layer(44, 7) > 0.55) * floor[..., 0]
            ring(cv, left + 0.33 * w, ground, since * 330, 0.28, 10, np.array([255.0, 150, 50]) * (1 - since / 0.4) * 1.2, cracks)
        if since < 0.55:
            u = since / 0.55
            falling.draw(cv, u * 0.9, lambda v: np.array((150, 130, 110)) * (1 - v), 1.0)
            dust.draw(cv, u * 0.8, lambda v: np.array((190, 165, 130)) * (1 - v) * 0.75, 2.2)
            rocks.draw(cv, u * 0.7, lambda v: (170, 140, 110), 1.3)
        return np.clip(cv, 0, 255)
    return "graardor", "the stomp", scene

def kbd():
    rgb0, al0 = fit(sprite("King_Black_Dragon.png"), W - 34, H - 26, exposure=1.25, gamma=0.62, sat=0.55)
    h, w = al0.shape[:2]; left, top = W - w, (H - h) // 2
    rig = Rig(rgb0, al0)
    mouths = [(0.03, 0.77), (0.09, 0.86), (0.31, 0.93)]
    necks = [dict(c=(0.11, 0.70), r=(0.14, 0.27), p=(0.23, 0.60)), dict(c=(0.17, 0.78), r=(0.15, 0.24), p=(0.28, 0.63)), dict(c=(0.35, 0.76), r=(0.11, 0.3), p=(0.38, 0.58))]
    kinds = [([(255, 245, 170), (255, 150, 30), (200, 50, 15), (70, 25, 15)], "plume"),
             ([(215, 255, 130), (90, 215, 60), (35, 130, 35), (25, 60, 25)], "cloud"),
             ([(235, 245, 255), (110, 190, 255), (50, 80, 200), (30, 30, 80)], "bolts")]
    rock = lair_backdrop()
    # a foreground lip of rock passes in front of the claws
    lip = ridge(330, H - 6, 9, (22, 9, 4))
    lip_tex = fbm(340, (10, 4))[..., None]
    seam = np.abs(YY - (H - 30 + 8 * fbm(350, (30, 12))[0, :][None, :])) < 2.5
    embers = Motes(52, 18, lambda r: (r.rand() * W, H - 10 - r.rand() * 40, 12 * (r.rand() - 0.5), -90 - 80 * r.rand(), 0))
    def scene(t):
        phase = t * 3; k = int(phase) % 3; u = phase % 1
        stops, mode = kinds[k]
        on = max(0.0, math.sin(math.pi * u)) ** 0.6
        # each neck sways on its own; the one attacking rears back, then lunges
        heads = []
        for i, base in enumerate(necks):
            strike = on if i == k else 0.0
            rear = max(0.0, math.sin(math.pi * u * 2)) if i == k and u < 0.5 else 0.0
            heads.append(dict(base, a=3.5 * wave(t, 2, i * 0.31) - 9 * strike, d=(-0.035 * strike, 0.02 * strike - 0.01 * rear), lit=0.2 * strike))
        mx, my = rig.at(heads[k], *mouths[k]); m = (left + mx, top + my)
        # lava light from below, and the attack's colour thrown on the wall behind
        pulse = 0.8 + 0.2 * math.sin(2 * math.pi * t * 2)
        under = np.clip((YY - H * 0.35) / (H * 0.65), 0, 1)[..., None] ** 2 * pulse
        thrown = np.exp(-((XX - m[0]) ** 2 + (YY - m[1]) ** 2) / (2 * 80.0 ** 2))[..., None] * on
        cv = rock * (0.8 + 0.35 * under) + under * np.array([22.0, 8, 2]) + thrown * rock * np.array(stops[1], float) / 400
        n_lava = noise(360, 6, t)
        hot = (seam * np.clip(n_lava * 1.6 - 0.2, 0, 1))[..., None]
        cv = cv * (1 - hot) + ramp([(255, 220, 110), (255, 120, 20), (120, 30, 10)], 1 - n_lava)[..., :3] * hot
        cv = feather(cv)
        flap = wave(t, 3)
        parts = [
            dict(c=(0.60, 0.50), r=(0.24, 0.2), p=(0.60, 0.50), d=(0, -0.012 * wave(t, 3, 0.25)), soft=0.9),   # flank breathes
            dict(c=(0.76, 0.30), r=(0.22, 0.18), p=(0.56, 0.47), a=-7 * wave(t, 3, 0.1), lit=-0.2 * flap, soft=0.5),
            dict(c=(0.89, 0.25), r=(0.13, 0.27), p=(0.90, 0.48), a=6 * wave(t, 2, 0.4)),                       # tail
            dict(c=(0.36, 0.22), r=(0.26, 0.25), p=(0.22, 0.44), a=8 * flap, lit=0.25 * flap, soft=0.5),      # near wing
            heads[2], heads[0], heads[1]]
        ang = 2 * math.pi * t
        rgb, al = rig.pose(parts, light=(math.cos(ang) * 0.7, -0.7), relief=0.35)
        # the attack lights the near side of the dragon in its own colour
        yy_, xx_ = rig.yy - rig.pad, rig.xx - rig.pad
        d = np.exp(-((xx_ - (m[0] - left)) / (w * 0.32)) ** 2 - ((yy_ - (m[1] - top)) / (h * 0.45)) ** 2)[..., None]
        rgb = rgb + d * np.array(stops[1], float) * 0.45 * on
        embers.draw(cv, t, lambda v: np.array((230, 120, 40)) * math.sin(math.pi * v) * 0.7, 1.0)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        if on > 0.05:
            if mode == "plume":
                plume(cv, m[0] + 2, m[1], -1, 0.35, 44 * on + 16, 26, t * 6, stops, 61, min(1.0, on * 1.4))
            elif mode == "cloud":
                # poison rolls low and hangs
                n = noise(71, 12, t * 3)
                fall = np.exp(-(((XX - (m[0] - 10)) / (42 * on + 8)) ** 2 + ((YY - (m[1] + 12)) / 30) ** 2))
                den = np.clip(fall * 1.6 * on + (n - 0.5) * 1.2 * fall - 0.3, 0, 1)
                cv[:] = cv * (1 - 0.8 * den[..., None]) + ramp(stops, 1 - den)[..., :3] * 0.8 * den[..., None]
            else:
                f = int(t * FRAMES)
                for i in range(3):
                    bolt(cv, m[0], m[1], 4 + 16 * i, H - 4 - (2 - i) * 18, 80 + (f // 2) * 7 + i, np.array(stops[0]) * min(1.0, on * 1.3), jag=26, size=1.5)
                glow(cv, m[0], m[1], 18, stops[1], 0.6 * on)
            glow(cv, m[0] - 6, m[1], 16, stops[1], 0.35 * on)
        fore = (YY > lip[None, :])[..., None]
        cv[:] = cv * (1 - fore) + (np.array([24.0, 20, 22]) * (0.7 + 0.7 * lip_tex) + np.clip(1 - (YY - lip[None, :]) / 3, 0, 1)[..., None] * np.array([52.0, 24, 10]) * pulse) * fore
        return np.clip(cv, 0, 255)
    return "kbd", "three breaths", scene

RECIPES = [vorkath, graardor, kbd]
