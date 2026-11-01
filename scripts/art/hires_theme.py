"""The plugin's own theme, for a search with no boss (Andrew 2026-10-31: "a really cool theme one for
loadout lab"): the logo's flask guy stands in the lab while every gear slot pours into him. Full, he is the logo."""
from hires_kit import *
from hires_batch2c import sm, poly

def logo():
    """the logo's body in canvas size: silhouette, outline, gold parts (band, heart), cap. y in canvas rows."""
    a = np.asarray(Image.open(ROOT / "icon.png").convert("RGBA"), float)
    lum, sat = a[..., :3].sum(2), a[..., :3].max(2) - a[..., :3].min(2)
    ink = (a[..., 3] > 128) & ((lum > 240) | (sat > 40))
    ink[:12] = ink[61:] = False
    ink[:, :9] = False
    ink[:20, 33:] = False
    gold = ink & (a[..., 0] > a[..., 1])
    h, w = ink.shape                                                            # outside: everything a walk from the corner can reach
    seen = np.zeros_like(ink); todo = [(0, 0)]
    while todo:
        y, x = todo.pop()
        if 0 <= y < h and 0 <= x < w and not seen[y, x] and not ink[y, x]:
            seen[y, x] = True
            todo += [(y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)]
    sil = ~seen
    box = (9, 12, 43, 61)
    def scaled(m, wide=126, tall=176):
        return np.asarray(Image.fromarray((m * 255).astype(np.uint8)).crop(box).resize((wide, tall), Image.NEAREST)) > 128
    cap = np.zeros_like(ink); cap[:18] = gold[:18]
    return scaled(sil & ~cap), scaled(gold & ~cap), scaled(cap)

def outline(m, n=3):
    e = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(n * 2 + 1))
    return m & ~(np.asarray(e) > 128)

def icons():
    """ten gear slots, drawn as flat shapes in a 30 px box"""
    def mk(draw):
        im = Image.new("L", (30, 30), 0); draw(ImageDraw.Draw(im)); return np.asarray(im, float) / 255
    f = 255
    return [
        mk(lambda d: (d.pieslice((6, 5, 24, 25), 180, 360, fill=f), d.rectangle((6, 15, 11, 25), fill=f), d.rectangle((19, 15, 24, 25), fill=f))),   # helm
        mk(lambda d: d.polygon([(10, 4), (20, 4), (26, 26), (4, 26)], fill=f)),                                                                      # cape
        mk(lambda d: (d.arc((7, 3, 23, 19), 20, 160, fill=f, width=2), d.ellipse((11, 16, 19, 25), fill=f))),                                        # amulet
        mk(lambda d: (d.polygon([(23, 3), (27, 3), (27, 7), (11, 22), (8, 19)], fill=f), d.line((5, 16, 14, 25), fill=f, width=3), d.line((8, 22, 3, 27), fill=f, width=3))),  # sword
        mk(lambda d: d.polygon([(4, 7), (11, 3), (19, 3), (26, 7), (23, 13), (21, 12), (21, 26), (9, 26), (9, 12), (7, 13)], fill=f)),               # body
        mk(lambda d: d.polygon([(5, 4), (25, 4), (25, 15), (15, 27), (5, 15)], fill=f)),                                                              # shield
        mk(lambda d: d.polygon([(7, 3), (23, 3), (25, 27), (17, 27), (15, 12), (13, 27), (5, 27)], fill=f)),                                         # legs
        mk(lambda d: (d.rounded_rectangle((8, 9, 22, 22), 3, fill=f), d.rectangle((10, 22, 20, 27), fill=f), d.rectangle((3, 11, 8, 17), fill=f))),  # gloves
        mk(lambda d: d.polygon([(8, 3), (17, 3), (17, 17), (27, 21), (27, 26), (8, 26)], fill=f)),                                                   # boots
        mk(lambda d: (d.ellipse((6, 8, 24, 26), outline=f, width=3), d.polygon([(15, 2), (20, 8), (15, 12), (10, 8)], fill=f))),                     # ring
    ]

def lab_backdrop():
    """the lab: the logo's slate plate, a benzene lattice on the wall, a checked floor in perspective"""
    img = np.array([45.0, 45, 52]) * (0.62 + 0.5 * fbm(3101, (26, 11, 5))[..., None])
    img = img + hex_mask(19, W / 2, 96, math.pi / 6, 1.5)[..., None] * np.array([14.0, 28, 24])
    z = np.clip((YY - 196) / 44, 0, 1)
    across = (XX - W / 2) / (0.45 + z)
    check = ((np.floor(across / 26) + np.floor(np.log(z + 0.2) * 3.4)) % 2)[..., None]
    floor_ = np.array([40.0, 44, 44]) * (0.6 + 0.5 * check) * (0.6 + 0.6 * z[..., None])
    m = (YY > 196)[..., None]
    return img * (1 - m) + floor_ * m

def flask_lab():
    sil, gold, cap = logo()
    edge = outline(sil)
    bh, bw = sil.shape
    gx, ground = (W - bw) // 2, H - 14
    gy = ground - bh
    # the neck, where everything goes in
    neck = (W / 2 - 3, gy + 20)
    slots = [(x, 22 + i * 39) for i in range(5) for x in (24, W - 24)]
    order = [0, 3, 4, 7, 8, 9, 6, 5, 2, 1]                                       # a loop round the two columns
    marks = icons()
    back = lab_backdrop()
    bursts = [(math.cos(i * GOLDEN_ANGLE), math.sin(i * GOLDEN_ANGLE), 22 + 11 * math.sqrt(i)) for i in range(1, 28)]
    GREEN, GLOW, GOLD = np.array((140.0, 200, 140)), np.array((190.0, 255, 170)), np.array((230.0, 196, 110))
    bub = Motes(3111, 26, lambda r: (gx + 18 + r.rand() * (bw - 36), ground - 4, 8 * (r.rand() - 0.5), -bh * (0.5 + 0.5 * r.rand()), 0))
    POP = 0.68
    def scene(t):
        u = t % 1
        level = sm(0.04, POP, u) * (1 - sm(0.86, 0.99, u))
        hit = math.exp(-(u - POP) * 10) if u >= POP else 0.0
        strain = sm(POP - 0.14, POP - 0.01, u) * (1 - sm(POP, POP + 0.04, u))
        cv = back * (0.8 + 0.25 * level + 0.5 * hit)
        cv += np.exp(-((XX - W / 2) / 90) ** 2 - ((YY - 130) / 110) ** 2)[..., None] * GREEN * (0.05 + 0.14 * level + 0.3 * hit)
        cv = feather(cv)
        # the gear slots: each lights in turn and pours in, then all go out with the drain
        for j, i in enumerate(order):
            x, y = slots[i]; t0 = 0.04 + j * 0.06
            on = sm(t0, t0 + 0.03, u) * (1 - sm(0.86, 0.95, u))
            frame_ = np.array((70.0, 70, 80)) * (1 - on) + GOLD * on
            for a, b in (((x - 16, y - 16), (x + 16, y - 16)), ((x + 16, y - 16), (x + 16, y + 16)), ((x + 16, y + 16), (x - 16, y + 16)), ((x - 16, y + 16), (x - 16, y - 16))):
                line(cv, a, b, frame_ * (0.7 + 0.3 * on), 0.9)
            m = marks[i][..., None]
            y0, x0 = y - 15, x - 15
            cv[y0:y0 + 30, x0:x0 + 30] = cv[y0:y0 + 30, x0:x0 + 30] * (1 - m) + (np.array((84.0, 84, 92)) * (1 - on) + np.array((250.0, 214, 124)) * on) * m
            flash = sm(t0, t0 + 0.02, u) * (1 - sm(t0 + 0.03, t0 + 0.09, u))
            if flash > 0: glow(cv, x, y, 15, GOLD, 0.6 * flash)
            q = (u - t0) / 0.1
            if 0 <= q < 1:                                                       # a spark on a high arc to the neck
                for k in range(5):
                    v = max(0.0, q - k * 0.04)
                    sx = x + (neck[0] - x) * v; sy = y + (neck[1] - y) * v - 42 * math.sin(math.pi * v)
                    stamp(cv, sx, sy, GOLD * (1 - k * 0.18), 2.6 - k * 0.35)
        # he sways, and strains upward as he fills, feet planted
        sway = 2.5 * wave(t, 2) * (1 - strain)
        tall = 7 * strain - 3 * hit * wave(t, 8)
        rows, cols = np.mgrid[0:bh, 0:bw]
        up = (bh - rows) / bh
        sy = np.clip(rows + np.round(tall * up).astype(int), 0, bh - 1)
        sx = np.clip(cols - np.round(sway * up ** 1.3).astype(int), 0, bw - 1)
        s_, e_, g_ = sil[sy, sx], edge[sy, sx], gold[sy, sx]
        full = lambda m: np.pad(m, ((gy, H - gy - bh), (gx, W - gx - bw)))[..., None]
        S, E, Gm = full(s_), full(e_), full(g_)
        n = noise(3113, 8, t)
        top = ground - bh * level + 2.5 * np.sin(XX * 0.13 + 2 * math.pi * t * 3) + 1.5 * np.sin(XX * 0.31 - 2 * math.pi * t * 2)
        liquid = (YY > top)[..., None]
        fluid = ramp([(70, 150, 86), (120, 200, 120), (168, 232, 150)], np.clip(n * 0.7 + 0.25 * (1 - (YY - top) / 60), 0, 1))[..., :3]
        glass = np.array([30.0, 48, 42]) + (0.5 + 0.5 * np.sin((XX - YY) * 0.16))[..., None] * 8
        body = glass * (1 - liquid) + fluid * liquid
        crest = (np.abs(YY - top) < 2.2)[..., None] * (level > 0.01)
        body = body * (1 - crest) + GLOW * crest
        cv[:] = cv * (1 - S) + body * S
        cv[:] = cv * (1 - E) + (GREEN * (1 - 0.3 * (1 - level)) + GLOW * hit * 0.4) * E
        glint = (0.5 + 0.5 * np.sin((XX + YY * 0.5) * 0.25 - 2 * math.pi * t * 2)) ** 18
        cv += (glint * S[..., 0] * ~E[..., 0])[..., None] * 34
        # bubbles, only inside the liquid
        tmp = np.zeros_like(cv)
        bub.draw(tmp, (t * 3) % 1, lambda v: (70, 90, 60), 2.3)
        cv += tmp * S * liquid * (level > 0.02)
        lit = (0.88 + 0.14 * wave(t, 4) + 0.4 * hit)
        cv[:] = cv * (1 - Gm) + GOLD * lit * Gm
        # the cap: strains, flies, and drops back on
        fly = sm(POP, POP + 0.07, u) * (1 - sm(POP + 0.1, 0.92, u))
        ch, cw = cap.shape
        cy = int(gy - tall - 44 * fly + 2 * strain * wave(t, 24))
        cx = int(gx + sway)
        cm = np.zeros((H, W), bool)
        ys, xs = np.nonzero(cap)
        ok = (ys + cy >= 0) & (ys + cy < H) & (xs + cx >= 0) & (xs + cx < W)
        cm[ys[ok] + cy, xs[ok] + cx] = True
        cv[cm] = GOLD * 1.1
        if u >= POP:
            f = min(1.0, (u - POP) / 0.26)
            ox, oy = neck[0] + sway, neck[1] - 4
            if f < 0.75:
                plume(cv, ox, oy, 0, -1, 90, 34, (t * 5) % 1, [(240, 255, 225), (180, 245, 160), (110, 200, 120), (60, 130, 80)], 3115, min(1.0, 1.3 - f * 1.6), wobble=0.12)
            for i, rr in enumerate(phi_radii(14, 3)):
                ring(cv, ox, oy + 50, rr + 150 * f, 1.0, 3 - 0.6 * i, GOLD * (1 - f) ** 1.2)
            for i, (dx, dy, rr) in enumerate(bursts):                             # the logo's gold plus-shaped sparkles
                d = rr * (0.3 + 2.4 * f)
                x, y = ox + dx * d, oy + 30 + dy * d + 30 * f * f
                tw = max(0.0, math.sin(2 * math.pi * (f * 3 + i * 0.13))) * (1 - f * 0.7)
                s = 2 + 3 * ((i % 3) == 0)
                line(cv, (x - s, y), (x + s, y), GOLD * tw * 1.1, 1.0)
                line(cv, (x, y - s * 1.4), (x, y + s * 1.4), GOLD * tw * 1.1, 1.0)
        return np.clip(cv, 0, 255)
    return "flask", "the lab", scene

RECIPES = [flask_lab]
