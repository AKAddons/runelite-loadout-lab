"""Hi-res recipes, batch 1a: jad, zuk, hydra, cerberus, scurrius, nex."""
from hires_kit import *
from hires_kit import _layer

# ---- shared helpers ----------------------------------------------------------
def upper(y, y0=0.85):
    """1 at the top of the sprite, 0 from y0 down: the planted part does not move"""
    return np.clip((y0 - y) / y0, 0, 1)

def shifted(x, y, fy, lean, drop, y0=0.85):
    """a canvas point after the body bend moved it"""
    u = float(np.clip((y0 - fy) / y0, 0, 1))
    return x + lean * u, y + drop * u

def lit_on(rig, rgb, x, y, rx, ry, colour, power):
    """a coloured light on the model; x, y in sprite pixels"""
    d = np.exp(-((rig.xx - rig.pad - x) / rx) ** 2 - ((rig.yy - rig.pad - y) / ry) ** 2)[..., None]
    return rgb + d * np.array(colour, float) * power

def rising(seed, cell, t, n=1, tall=1.0):
    """noise climbing the canvas, looping: two scrolled layers"""
    a, b = _layer(seed, cell), _layer(seed + 1, max(2, cell // 2))
    return 0.6 * np.roll(a, -int(t * n * H) % H, 0) + 0.4 * np.roll(b, -int(t * n * 2 * H) % H, 0)

def shadow(cv, x, y, rx, ry, power=0.5):
    sh = np.clip(1 - ((XX - x) / rx) ** 2 - ((YY - y) / ry) ** 2, 0, 1)[..., None]
    cv *= 1 - power * sh

def spike(cv, x, y, wid, tall, colour, tip=None, lean=0.0):
    """a shard standing on (x, y): faceted, lighter toward the tip"""
    if tall < 1: return
    v = np.clip((y - YY) / tall, 0, 1)
    d = (XX - x - lean * v) / np.maximum(wid * (1 - v), 0.01)
    m = (YY <= y) & (YY >= y - tall) & (np.abs(d) < 1)
    col = np.array(colour, float) * (0.75 + 0.45 * (d > 0))[..., None]
    if tip is not None: col = col + (np.array(tip, float) - np.array(colour, float)) * v[..., None]
    cv[:] = np.where(m[..., None], col, cv)

def smooth(a, b, x):
    u = np.clip((x - a) / (b - a), 0, 1)
    return u * u * (3 - 2 * u)

def coursed(course, brick, seed, colour, cells=(24, 8, 3)):
    """brick courses: shade per brick, dark mortar"""
    row = (YY // course).astype(int); off = (row % 2) * brick // 2
    col = ((XX + off) // brick).astype(int)
    shade = np.random.RandomState(seed).rand(60, 60)[row % 60, col % 60]
    mortar = ((YY % course < 1.6) | ((XX + off) % brick < 1.6))
    stone = (0.65 + 0.6 * shade) * (0.75 + 0.5 * fbm(seed + 1, cells))
    return (np.array(colour, float) * stone[..., None]) * (1 - 0.65 * mortar[..., None])

# ---- backdrops ---------------------------------------------------------------
def tzhaar_backdrop():
    """the Fight Cave: basalt columns, stalactites, seams of lava low down"""
    cols = _layer(401, 11)[0, :][None, :]
    facet = np.floor(XX / 13 + 2 * fbm(402, (30, 12)))
    shade = np.random.RandomState(403).rand(64)[facet.astype(int) % 64]
    rock = np.array([38.0, 28, 26]) * (0.55 + 0.55 * shade[..., None]) * (0.7 + 0.6 * fbm(404, (16, 6, 3))[..., None])
    drip = 16 + 40 * np.abs(np.sin(XX * 0.16 + 2 * np.sin(XX * 0.06))) ** 3 + 12 * cols
    top = (YY < drip)[..., None]
    rock = rock * (1 - top) + np.array([14.0, 10, 10]) * top
    return rock * (0.75 + 0.5 * np.clip(YY / H, 0, 1)[..., None])

def inferno_backdrop():
    """the Inferno: a black basalt bowl, a distant rim, smouldering seams"""
    sky = ramp([(20, 12, 12), (34, 18, 14), (58, 26, 14)], np.clip(YY / 170, 0, 1))
    far = ridge(411, 120, 26)
    tex = fbm(412, (20, 8, 3))[..., None]
    m = (YY > far[None, :])[..., None]
    img = sky * (1 - m) + (np.array([30.0, 22, 22]) * (0.6 + 0.8 * tex) + np.clip(1 - (YY - far[None, :]) / 6, 0, 1)[..., None] * np.array([40.0, 16, 4])) * m
    near = ridge(413, 168, 12)
    m = (YY > near[None, :])[..., None]
    return img * (1 - m) + (np.array([26.0, 20, 20]) * (0.5 + 0.9 * tex)) * m

def karuulm_backdrop():
    """the hydra's chamber: carved stone, brass pipes, a dark ring of floor"""
    img = coursed(24, 44, 421, (40, 42, 38), (20, 8, 3))
    bend_ = fbm(422, (40, 16))
    img *= (0.7 + 0.5 * bend_[..., None])
    for x0, wid in ((W - 24, 8), (W - 44, 5)):
        d = (XX - x0) / wid
        m = (np.abs(d) < 1)[..., None]
        img = img * (1 - m) + (np.array([58.0, 46, 26]) * (1 - 0.7 * np.abs(d)) [..., None]) * m
    y0 = H - 42
    floor = (YY > y0)[..., None]
    rr = np.sqrt(((XX - W / 2) / 110) ** 2 + ((YY - (H - 16)) / 28) ** 2)
    tiles = 0.75 + 0.5 * np.sin(rr * 18)[..., None] * 0.5
    return img * (1 - floor) + np.array([44.0, 46, 42]) * tiles * (0.7 + 0.6 * fbm(423, (12, 5))[..., None]) * floor

def taverley_backdrop():
    """Cerberus's lair: rough blocks, a low arch, sooted from below"""
    img = coursed(22, 38, 431, (42, 36, 34), (20, 8, 3))
    arch = np.sqrt(((XX - W * 0.56) / 86) ** 2 + ((YY - 150) / 112) ** 2)
    inside = (arch < 1)[..., None]
    rim = (np.abs(arch - 1) < 0.07)[..., None]
    img = img * (1 - inside) + np.array([20.0, 15, 15]) * (0.7 + 0.5 * fbm(432, (14, 5))[..., None]) * inside
    img = img * (1 - rim) + np.array([60.0, 50, 44]) * (0.7 + 0.5 * fbm(433, (8, 3))[..., None]) * rim
    y0 = H - 40
    floor = (YY > y0)[..., None]
    return img * (1 - floor) + np.array([40.0, 32, 28]) * (0.6 + 0.7 * fbm(434, (14, 5))[..., None]) * floor

def sewer_backdrop():
    """brick vault, a black culvert behind him, slime running down the courses"""
    img = coursed(13, 26, 441, (58, 44, 36), (20, 8, 3))
    arch = np.sqrt(((XX - W * 0.5) / 74) ** 2 + ((YY - (H - 24)) / 118) ** 2)
    inside = (arch < 1)[..., None]
    rim = ((arch >= 1) & (arch < 1.14))[..., None]
    ang = np.arctan2(YY - (H - 24), XX - W * 0.5)
    vous = 0.65 + 0.55 * (np.sin(ang * 34) > 0)[..., None]
    img = img * (1 - rim) + np.array([58.0, 50, 42]) * vous * rim
    img = img * (1 - inside) + np.array([24.0, 28, 24]) * (0.8 + 0.4 * fbm(442, (12, 5))[..., None]) * inside
    slime = np.clip(fbm(443, (9, 4))[0, :][None, :] * 3 - 1.5, 0, 1) * np.clip(1 - YY / 90, 0, 1)
    return img + slime[..., None] * np.array([-4.0, 22, 2]) * (~inside)

def prison_backdrop():
    """Nex's chamber: pale Zarosian stone, two pillars, a floor of dark flags"""
    img = coursed(30, 58, 451, (40, 36, 48), (24, 10, 4))
    for x0 in (22, W - 22):
        d = (XX - x0) / 13
        m = (np.abs(d) < 1)[..., None]
        img = img * (1 - m) + np.array([52.0, 46, 66]) * (1 - 0.65 * d[..., None] ** 2) * (0.75 + 0.5 * fbm(452, (20, 8))[..., None]) * m
    y0 = H - 36
    floor = (YY > y0)[..., None]
    flag = 0.7 + 0.4 * (((XX + (YY // 12) * 15) % 30 < 28) & (YY % 12 < 11))[..., None]
    return img * (1 - floor) + np.array([34.0, 30, 42]) * flag * (0.7 + 0.6 * fbm(453, (12, 5))[..., None]) * floor

# ---- jad ---------------------------------------------------------------------
def solid_sprite(name):
    """The sprite without its soft ground shadow. kit.sprite() makes every non-empty pixel opaque,
    so the faint shadow round the feet would become part of the model and move with it."""
    a = np.asarray(Image.open(SPR / name).convert("RGBA"), float).copy()
    mask = a[..., 3] > 128
    ys, xs = np.where(mask)
    a[..., 3] = mask * 255.0
    return Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.uint8), "RGBA")

def jad_grade(spr, w, h):
    """Jad is one saturated red, which clips in fit: grade his brightness, then tint it,
    leaving the eye and teeth in their own colours and the spines purple"""
    a = np.asarray(spr, float)
    v = a[..., :3].max(2)
    grey = Image.fromarray(np.dstack([v, v, v, a[..., 3]]).astype(np.uint8), "RGBA")
    rgb, al = fit(grey, w, h, exposure=0.98, gamma=1.4, sat=1.0)
    o = np.asarray(spr.resize((al.shape[1], al.shape[0]), Image.LANCZOS), float)
    keep = (o[..., 1] > o[..., 0] * 0.35) & (o[..., 1] > 50)
    spine = (o[..., 2] > o[..., 0] * 0.62) & ~keep
    out = rgb * np.array([1.0, 0.30, 0.30]) + np.array([34.0, 8, 10])
    out[spine] = (rgb * np.array([0.72, 0.50, 0.66]) + np.array([24.0, 12, 22]))[spine]
    out[keep] = np.clip(o[..., :3][keep] * 1.25, 0, 255)
    return np.clip(out, 0, 255), al

def jad_rock():
    rgb0, al0 = jad_grade(solid_sprite("TzTok-Jad.png"), W - 26, H - 32)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 4; ground = H - 10; top = ground - h
    rig = Rig(rgb0, al0)
    back = tzhaar_backdrop()
    seam = np.abs(YY - (H - 44 + 14 * fbm(461, (34, 14))[0, :][None, :])) < 2.2
    tex = fbm(462, (10, 4))[..., None]
    embers = Motes(463, 14, lambda r: (r.rand() * W, H - 26, 8 * (r.rand() - 0.5), -110 - 60 * r.rand(), 0))
    rocks = [(30, 0.0, 15), (62, 0.05, 11), (10 + 40 * 0.2, 0.11, 8), (84, 0.15, 9)]
    dust = Motes(464, 36, lambda r: (W * 0.42 + (r.rand() - 0.5) * 90, ground, (r.rand() - 0.5) * 120, -44 * r.rand(), 34))
    slam = 0.74
    def scene(t):
        # first half: the mage rear, spit at 0.24. second half: the ranged stomp, down at 0.74
        rear = math.sin(math.pi * min(t / 0.5, 1)) ** 2 if t < 0.5 else 0.0
        spit = 0.24
        since_spit = t - spit if t >= spit else t + 1 - spit
        fire = math.exp(-since_spit * 14)
        lift = ((t - 0.5) / (slam - 0.5)) ** 2 if 0.5 <= t < slam else 0.0
        since = t - slam if t >= slam else t + 1 - slam
        impact = math.exp(-since * 9)
        sx = int(round(impact * 5 * math.sin(since * 80))); sy = int(round(impact * 3 * math.cos(since * 110)))
        charge = smooth(0.04, 0.22, t) * (t < spit + 0.02)
        lean = 8 * rear + 7 * lift - 5 * fire - 6 * impact
        drop = -14 * rear - 7 * lift + 13 * impact + 6 * fire
        pulse = 0.75 + 0.25 * wave(t, 3)
        cv = back * (0.92 + 0.3 * impact)
        n = noise(465, 7, t)
        hot = (seam * np.clip(n * 1.7 - 0.25, 0, 1))[..., None]
        cv = cv * (1 - hot) + ramp([(240, 190, 90), (225, 100, 20), (110, 30, 10)], 1 - n)[..., :3] * hot
        cv += np.exp(-((YY - (H - 44)) / 26) ** 2)[..., None] * np.array([26.0, 9, 2]) * pulse
        cv = feather(cv)
        floor = np.clip((YY - ground + 22) / 3.0, 0, 1)[..., None]
        cv[:] = cv * (1 - floor) + (np.array([40.0, 30, 28]) * (0.7 + 0.7 * tex)) * floor
        shadow(cv, W * 0.5 + 5, ground - 3, w * 0.58, 9, 0.6)
        embers.draw(cv, t, lambda v: np.array((220, 110, 40)) * math.sin(math.pi * v) * 0.6, 0.9)
        head = dict(c=(0.25, 0.42), r=(0.28, 0.2), p=(0.42, 0.46), a=8 * rear + 3 * wave(t, 2, phi_phase(1)) + 5 * lift - 7 * impact, d=(0, -0.006 * rear))
        jaw = dict(c=(0.29, 0.52), r=(0.24, 0.085), p=(0.42, 0.48), a=head["a"] - 9 * (charge + fire) - 6 * impact, soft=0.5, d=(0, 0.012 * (charge + fire)))
        parts = [
            dict(c=(0.62, 0.28), r=(0.34, 0.2), p=(0.62, 0.42), a=3 * wave(t, 2, phi_phase(2)) - 3 * rear, d=(0, -0.01 * wave(t, 2)), soft=0.8),  # the hunched back heaves
            dict(c=(0.86, 0.60), r=(0.14, 0.2), p=(0.82, 0.40), a=-4 * rear + 3 * impact, soft=0.6),                                    # far arm
            dict(c=(0.60, 0.58), r=(0.2, 0.2), p=(0.6, 0.42), d=(0.006 * rear, -0.012 * lift), soft=0.8),                              # near shoulder rides up
            head, jaw]
        def body(x, y):
            up = upper(y, 0.85)
            leg = np.clip((x - 0.39) / 0.03, 0, 1) * np.clip((0.77 - x) / 0.03, 0, 1) * np.clip((y - 0.62) / 0.24, 0, 1)    # the near foot rises to stomp
            return -lean * up, -drop * up + 26 * lift * leg
        ang = 2 * math.pi * t
        rgb, al = rig.pose(parts, light=(math.cos(ang) * 0.8, -0.6), relief=0.3, bend=body)
        mx, my = rig.at(jaw, 0.22, 0.53); mx, my = shifted(mx, my, 0.53, lean, drop)
        rgb = lit_on(rig, rgb, mx, my, w * 0.24, h * 0.2, (255, 110, 30), 0.42 * charge + 0.35 * fire)
        under = np.clip(np.arange(rig.al.shape[0]) / rig.al.shape[0] - 0.55, 0, 1)[:, None, None] * 2
        rgb = rgb + under * np.array([70.0, 26, 8]) * (pulse * 0.5 + 1.1 * impact)
        ox, oy = left - rig.pad, top - rig.pad
        over(cv, rgb, al, ox, oy)
        m = (left + mx, top + my)
        ex, ey = rig.at(head, 0.27, 0.31); ex, ey = shifted(ex, ey, 0.31, lean, drop)
        glow(cv, left + ex, top + ey, 4 + 3 * charge, (230, 140, 30), 0.3 + 0.7 * max(charge, impact))
        # the magic: fire gathers in the throat, then a ball leaves the moving mouth
        if charge > 0.02:
            glow(cv, m[0], m[1], 5 + 9 * charge, (255, 130, 40), 0.9 * charge)
            stamp(cv, m[0], m[1], (255, 220, 130), 1.5 + 2.5 * charge)
        u = since_spit / 0.26
        if 0 <= u < 1:
            for k in range(7):
                v = u - k * 0.05
                if v < 0: break
                tx, ty = (left + 0.22 * w) - 8 - 34 * v, top + 0.53 * h + 66 * v * v + 6 * v
                stamp(cv, tx, ty, ramp([(255, 240, 170), (255, 130, 30), (140, 40, 10)], np.array(k / 6)) * 0.55, (5 + 5 * u) * (1 - k * 0.11))
            glow(cv, (left + 0.22 * w) - 8 - 34 * u, top + 0.53 * h + 66 * u * u + 6 * u, 16 + 10 * u, (255, 110, 30), 0.6)
        # the ranged: rocks drop from the roof the moment he lands
        if since < 0.26:
            for rx, delay, sz in rocks:
                v = (since - delay) / 0.14
                if 0 <= v < 1:
                    ry = 8 + (ground - 14) * v * v
                    stamp(cv, rx, ry, (120, 92, 80), sz * 0.55)
                    stamp(cv, rx - 1, ry - 2, (90, 66, 50), sz * 0.3)
                    glow(cv, rx, ry + 3, sz, (110, 45, 10), 0.3)
                elif 1 <= v < 2.2:
                    ring(cv, rx, ground - 8, (v - 1) * 26, 0.28, 4, np.array([170.0, 90, 40]) * (1 - (v - 1) / 1.2))
        if since < 0.42:
            dust.draw(cv, since / 0.42 * 0.8, lambda v: np.array((150, 115, 90)) * (1 - v) * 0.6, 2.0)
        # the shake moves the whole picture together, so his feet never slide on the floor
        cv = cv[np.clip(np.arange(H) - sy, 0, H - 1)][:, np.clip(np.arange(W) - sx, 0, W - 1)]
        return np.clip(cv, 0, 255)
    return "jad", "the mage rock", scene

# ---- zuk ---------------------------------------------------------------------
def glyph_plate(w=72, h=30):
    """the ancestral glyph: a floating slab with a rune cut through it"""
    s = 3
    mask = Image.new("L", (w * s, h * s), 0); rune = Image.new("L", (w * s, h * s), 0)
    d = ImageDraw.Draw(mask)
    d.polygon([(10 * s, 0), ((w - 10) * s, 0), ((w - 1) * s, h // 2 * s), ((w - 10) * s, (h - 1) * s), (10 * s, (h - 1) * s), (0, h // 2 * s)], fill=255)
    r = ImageDraw.Draw(rune); cx, cy = w // 2, h // 2
    r.ellipse([(cx - 11) * s, (cy - 11) * s, (cx + 11) * s, (cy + 11) * s], outline=255, width=2 * s)
    r.line([(cx * s, (cy - 8) * s), ((cx + 7) * s, cy * s), (cx * s, (cy + 8) * s), ((cx - 7) * s, cy * s), (cx * s, (cy - 8) * s)], fill=255, width=2 * s)
    for sign in (-1, 1):
        r.line([((cx + sign * 15) * s, (cy - 8) * s), ((cx + sign * 22) * s, cy * s), ((cx + sign * 15) * s, (cy + 8) * s)], fill=255, width=2 * s)
        r.line([((cx + sign * 25) * s, (cy - 4) * s), ((cx + sign * 25) * s, (cy + 4) * s)], fill=255, width=2 * s)
    al = (np.asarray(mask.resize((w, h), Image.BILINEAR), float) / 255 > 0.5).astype(float)[..., None]
    gl = np.asarray(rune.resize((w, h), Image.BILINEAR), float) / 255
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    tex = np.random.RandomState(471).rand(h // 3 + 1, w // 3 + 1)
    tex = np.asarray(Image.fromarray((tex * 255).astype(np.uint8)).resize((w, h), Image.NEAREST), float) / 255
    stone = np.array([66.0, 72, 96]) * (0.75 + 0.5 * tex)[..., None] * (1.25 - 0.6 * yy / h)[..., None]
    return stone, al, gl

def zuk_shield():
    rgb0, al0 = fit(solid_sprite("TzKal-Zuk.png"), W - 62, H - 62, exposure=0.6, gamma=1.08, sat=0.55)
    h, w = al0.shape[:2]; left = (W - w) // 2; ground = H - 16; top = ground - h
    rig = Rig(rgb0, al0)
    base = rig.rgb * np.array([0.60, 0.52, 0.50])
    dark = np.clip(1 - base.mean(2, keepdims=True) / 70, 0, 1)            # the seams of his hide, which heat up
    horizon = H - 88
    back = inferno_backdrop()
    # the floor: a basalt causeway in perspective, its joints converging on him; it never moves
    z = np.clip((YY - horizon) / (H - horizon), 0, 1)
    across = (XX - W / 2) / (z + 0.16)
    joints = (np.abs((across + 24) % 48 - 24) < 2.2 + 6 * z) | (np.abs(((np.log(z + 0.05) * 3.2) % 1) - 0.5) < 0.05 + 0.03 * z)
    is_floor = (YY > horizon)
    slab = np.array([44.0, 34, 34]) * (0.45 + 0.9 * z)[..., None] * (0.7 + 0.6 * fbm(476, (14, 6, 3))[..., None])
    back = np.where(is_floor[..., None], slab, back)
    crack = (joints & is_floor).astype(float) * (0.35 + 0.65 * z)
    stone, sal, gl = glyph_plate()
    sw, shh = sal.shape[1], sal.shape[0]
    ash = Motes(472, 26, lambda r: (r.rand() * W, horizon, 14 * (r.rand() - 0.5), -170 - 50 * r.rand(), 0))
    sparks = Motes(473, 20, lambda r: (0, 0, (r.rand() - 0.5) * 110, -70 * r.rand() - 8, 120))
    glyph_y = H - 56
    miss_at = (W * 0.90, H - 58)
    hot = [(255, 240, 180), (255, 150, 40), (205, 50, 15), (70, 20, 10)]
    def scene(t):
        s = math.sin(2 * math.pi * t)                                     # the glyph crosses the arena, in front of him
        sx = W / 2 + 68 * s
        t_block, t_miss, fly = 0.25, 0.75, 0.10
        since_b, since_m = (t - t_block) % 1.0, (t - t_miss) % 1.0
        block, miss = math.exp(-since_b * 15), math.exp(-since_m * 7)
        to_next = min((t_block - t) % 1.0, (t_miss - t) % 1.0)
        firing_miss = (t_miss - t) % 1.0 < (t_block - t) % 1.0
        charge = smooth(0.34, fly, to_next) if to_next > fly else 0.0     # he gathers, and the arena answers
        flying = 1 - to_next / fly if to_next <= fly else 0.0
        heat = max(charge, 1.0 if flying > 0 else 0.0, 0.8 * block, miss)
        lean = 9 * abs(s) - 4 - 5 * charge + 7 * flying - 5 * max(block, miss)
        drop = 9 * charge - 11 * flying + 5 * max(block, miss) + wave(t, 4, 0.1)
        pulse = 0.8 + 0.2 * wave(t, 3)
        # the room holds its breath before the shot, then every joint in the floor lights up
        cv = back * (0.86 - 0.22 * charge + 0.5 * max(block, miss))
        rim = np.exp(-((YY - horizon) / 16) ** 2)[..., None]
        cv = cv + rim * np.array([70.0, 24, 4]) * (0.5 + 0.9 * heat) * pulse
        n = noise(475, 9, t)
        glowing = crack * (0.28 + 0.75 * heat + (n - 0.5) * 0.4)
        cv = cv + np.clip(glowing, 0, 1.3)[..., None] * np.array([245.0, 96, 18])
        cv = feather(cv)
        # the rim of the arena burns higher with the charge
        fire_wall(cv, horizon - 12 - 52 * heat, horizon + 3, t, 477, 0.8, 0.55 + 0.5 * heat * (0.65 + 0.35 * np.sin(XX * 0.07 + 2 * math.pi * t)), [(250, 170, 60), (220, 90, 20), (135, 38, 12), (60, 18, 8)])
        ash.draw(cv, t, lambda v: np.array((225, 105, 35)) * math.sin(math.pi * v) * (0.5 + 0.5 * heat), 0.9)
        # the shield's reflection slides over the still floor
        cv += (np.exp(-((XX - sx) / 38) ** 2 - ((YY - (H - 28)) / 10) ** 2) * is_floor)[..., None] * np.array([26.0, 72, 120]) * (0.8 + block)
        shadow(cv, sx, H - 26, 40, 5, 0.45)
        # miss: the floor erupts where it lands
        if since_m < 0.26:
            v = since_m / 0.26
            tall = 150 * math.sin(math.pi * min(1.0, v * 1.15)) ** 0.7
            if tall > 4:
                plume(cv, miss_at[0], miss_at[1] + 4, 0.04, -1, tall, 20, (t * 5) % 1, hot, 479, min(1.0, 1.25 - v), wobble=0.12)
            ring(cv, miss_at[0], miss_at[1], 8 + v * 64, 0.3, 5, np.array([255.0, 130, 35]) * (1 - v))
            glow(cv, miss_at[0], miss_at[1] - 8, 34, (255, 90, 20), 0.8 * (1 - v))
        head = dict(c=(0.52, 0.22), r=(0.30, 0.25), p=(0.5, 0.40), a=-3 * s + 3 * wave(t, 3, phi_phase(3)) + 6 * charge - 6 * flying, d=(0, 0.012 * charge), soft=0.45)
        arm_l = dict(c=(0.13, 0.57), r=(0.22, 0.17), p=(0.29, 0.50), a=9 * wave(t, 1, phi_phase(1)) + 11 * charge - 9 * flying, soft=0.4)
        arm_r = dict(c=(0.86, 0.62), r=(0.2, 0.28), p=(0.72, 0.38), a=-7 * wave(t, 1, phi_phase(2)) - 9 * charge + 8 * flying, soft=0.4)
        parts = [
            dict(c=(0.50, 0.55), r=(0.26, 0.17), p=(0.50, 0.55), d=(0, -0.012 * wave(t, 2, 0.05) - 0.012 * charge), soft=0.9),
            dict(c=(0.52, 0.76), r=(0.24, 0.11), p=(0.5, 0.65), a=2.5 * math.cos(2 * math.pi * t), soft=0.8),
            arm_l, arm_r, head]
        def body(x, y):
            up = upper(y, 0.84)
            return -lean * up, -drop * up
        rig.rgb = np.clip(base + dark * np.array([235.0, 72, 10]) * (0.14 + 0.86 * heat), 0, 255)
        rgb, al = rig.pose(parts, light=((sx - W / 2) / 68 * 0.9, -0.5 + 0.8 * block), relief=0.3, bend=body)
        ex, ey = rig.at(head, 0.47, 0.165); ex, ey = shifted(ex, ey, 0.165, lean, drop, 0.84)
        rgb = lit_on(rig, rgb, ex, ey, w * 0.28, h * 0.16, (255, 70, 30), 0.2 + 0.7 * charge + 0.4 * flying)
        rgb = lit_on(rig, rgb, sx - left, glyph_y - top, w * 0.2, h * 0.2, (70, 170, 255), 0.22 + 0.4 * block)
        under = np.clip(np.arange(rig.al.shape[0]) / rig.al.shape[0] - 0.5, 0, 1)[:, None, None] * 2
        rgb = rgb + under * np.array([90.0, 30, 6]) * (0.2 + 0.5 * heat)
        shadow(cv, W / 2, ground + 1, w * 0.46, 7, 0.55)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        eye = (left + ex, top + ey)
        glow(cv, eye[0], eye[1], 5 + 7 * charge, (255, 70, 25), 0.4 + 0.7 * charge)
        if charge > 0.1: stamp(cv, eye[0], eye[1], (255, 225, 160), 1 + 3 * charge)
        gy = glyph_y + 1.8 * wave(t, 2, 0.2)
        # the moat in front: lava with a slow current; its bank is fixed
        flow = np.sin(XX * 0.11 + 5 * fbm(478, (24, 10)) + 2 * math.pi * t * 2) * 0.5 + 0.5
        lava = (YY > H - 12)[..., None]
        moat = ramp([(70, 18, 8), (165, 48, 12), (225, 105, 26), (250, 170, 70)], np.clip(0.1 + 0.55 * flow + 0.3 * heat, 0, 1))[..., :3]
        cv[:] = cv * (1 - lava) + moat * lava
        # the shot leaves the moving eye; one is caught on the glyph, the next gets past it
        if flying > 0:
            hx, hy = (miss_at if firing_miss else (W / 2 + 68 * math.sin(2 * math.pi * t_block), gy - 6))
            for k in range(9):
                v = flying - k * 0.055
                if v < 0: break
                stamp(cv, eye[0] + (hx - eye[0]) * v, eye[1] + (hy - eye[1]) * v, ramp(hot[:3], np.array(k / 8)), 9 * (1 - k * 0.09))
            glow(cv, eye[0] + (hx - eye[0]) * flying, eye[1] + (hy - eye[1]) * flying, 20, (255, 90, 25), 0.7)
        # the glyph: lit stone with a rune burning through it
        rune_pow = 0.8 + 0.2 * wave(t, 4) + 0.9 * block
        glow(cv, sx, gy, 42, (40, 110, 200), 0.36 + 0.6 * block)
        ix, iy = int(sx - sw / 2), int(gy - shh / 2)
        col = stone * (1 + 0.5 * block) + gl[..., None] * np.array([120.0, 215, 255]) * rune_pow
        over(cv, col, sal, ix, iy)
        # block: the fire splits round the glyph in two sheets
        if since_b < 0.2:
            v = since_b / 0.2
            for sgn in (-1, 1):
                plume(cv, sx + sgn * 26, gy - 6, sgn, 0.28, 18 + 62 * v, 16, (t * 5) % 1, hot, 481 + sgn, 1 - v, wobble=0.1)
            ring(cv, sx, gy, since_b * 300, 0.55, 5, np.array([120.0, 210, 255]) * (1 - v))
            for t0, (_, _, vx, vy, g) in sparks.p:
                stamp(cv, sx + vx * v, gy - 8 + vy * v + g * v * v, np.array((255, 180, 80)) * (1 - v), 1.5)
        # the shake moves the whole picture together, so his feet never slide
        e = max(block * 0.7, miss)
        since_ = min(since_b, since_m)
        ox_, oy_ = int(round(e * 4 * math.sin(since_ * 80))), int(round(e * 3 * math.cos(since_ * 110)))
        cv = cv[np.clip(np.arange(H) - oy_, 0, H - 1)][:, np.clip(np.arange(W) - ox_, 0, W - 1)]
        return np.clip(cv, 0, 255)
    return "zuk", "the glyph shield", scene

# ---- hydra -------------------------------------------------------------------
def hydra_phases():
    rgb0, al0 = fit(sprite("Alchemical_Hydra_(electric).png"), W - 30, H - 34, exposure=0.9, gamma=0.64, sat=0.85)
    h, w = al0.shape[:2]; left = 30; ground = H - 9; top = ground - h
    rig = Rig(rgb0, al0)
    # the sprite has four heads and a stump: a fifth head, the far left one again, grown on the stump
    sx0, sy0, sw, sh = rig.pad + int(0.01 * w), rig.pad, int(0.24 * w), int(0.22 * h)
    dx0, dy0 = sx0 - int(0.10 * w), sy0 + int(0.24 * h)
    cap_rgb, cap_al = rig.rgb[sy0:sy0 + sh, sx0:sx0 + sw].copy(), rig.al[sy0:sy0 + sh, sx0:sx0 + sw].copy()
    rig.rgb[dy0:dy0 + sh, dx0:dx0 + sw] = rig.rgb[dy0:dy0 + sh, dx0:dx0 + sw] * (1 - cap_al) + cap_rgb * cap_al
    rig.al[dy0:dy0 + sh, dx0:dx0 + sw] = np.maximum(rig.al[dy0:dy0 + sh, dx0:dx0 + sw], cap_al)
    base_rgb, base_al = rig.rgb.copy(), rig.al.copy()
    cyan = np.clip((base_rgb[..., 1] - base_rgb[..., 0] - 45) / 45, 0, 1)[..., None] * base_al
    back = karuulm_backdrop()
    # phase colours: poison, lightning, flame, the enraged last
    tones = [np.array([120.0, 230, 60]), np.array([80.0, 190, 255]), np.array([255.0, 90, 30]), np.array([225.0, 110, 255])]
    # the whole body takes the phase's hue: a ramp over the sprite's own brightness keeps its form
    skins = [[(14, 50, 14), (42, 135, 34), (118, 225, 68), (225, 255, 170)],
             [(10, 28, 85), (30, 85, 205), (70, 170, 255), (215, 245, 255)],
             [(62, 8, 6), (170, 28, 14), (248, 82, 25), (255, 205, 120)],
             [(38, 8, 60), (108, 30, 165), (202, 85, 238), (250, 205, 255)]]
    bright_ = base_rgb.mean(2)
    lo_, hi_ = np.percentile(bright_[base_al[..., 0] > 0], [3, 97])
    lum = np.clip((bright_ - lo_) / max(hi_ - lo_, 1), 0, 1)
    dyed = []
    for skin, tn in zip(skins, tones):
        d_ = ramp(skin, lum)[..., :3] * 0.92 + base_rgb * 0.08
        dyed.append(d_ * (1 - cyan) + cyan * np.clip(tn * 0.6 + 110, 0, 255))
    # which head goes at the end of which phase: (ellipse centre, radii, stump, pose index)
    doomed = [dict(c=(0.03, 0.34), r=(0.16, 0.13), stump=(0.05, 0.42), i=3),
              dict(c=(0.11, 0.12), r=(0.17, 0.25), stump=(0.21, 0.36), i=0),
              dict(c=(0.34, 0.12), r=(0.14, 0.19), stump=(0.46, 0.37), i=1)]
    hh, ww = base_al.shape[:2]
    grain = np.asarray(Image.fromarray((np.random.RandomState(481).rand(hh // 3 + 1, ww // 3 + 1) * 255).astype(np.uint8)).resize((ww, hh), Image.BICUBIC), float) / 255
    masks = [rig.weight(dict(c=d["c"], r=d["r"], soft=0.12), rig.xx, rig.yy) for d in doomed]
    masks[2] = np.maximum(masks[2], rig.weight(dict(c=(0.48, 0.045), r=(0.09, 0.06), soft=0.2), rig.xx, rig.yy))   # the halo goes with it
    vents = [(16, H - 22), (W - 26, H - 20), (W * 0.40, H - 12)]
    gore = Motes(482, 26, lambda r: (0, 0, (r.rand() - 0.5) * 60, -40 * r.rand() - 6, 120))
    def scene(t):
        p = t * 4; k = int(p) % 4; u = p % 1
        ton = tones[k]                                                     # snaps on the head loss
        flash = math.exp(-u * 16)                                          # a beat of pale light as the new colour lands
        # how gone each head is: dissolved at the end of its phase, back together before the loop wraps
        cuts = []
        for j in range(3):
            if k < j: c = 0.0
            elif k == j: c = smooth(0.8, 1.0, u)
            else: c = 1.0
            if k == 3: c = 1 - smooth(0.72, 0.98, u)
            cuts.append(c)
        keep = np.ones((hh, ww))
        for m, c in zip(masks, cuts):
            if c > 0:
                keep = keep * (1 - m * np.clip((c * 2.2 - grain * 1.2), 0, 1) ** 0.5 * (c > 0))
        rig.al = base_al * (keep > 0.5)[..., None]
        rig.rgb = np.clip(dyed[k] * (1 + 0.5 * flash) + (ton * 0.4 + 40) * flash, 0, 255)
        # the head about to go thrashes
        thrash = [0.0] * 4                                                 # by neck: left, middle, right, fifth
        if k < 3: thrash[doomed[k]["i"]] = smooth(0.5, 0.8, u) * wave(u, 3)
        breathe = wave(t, 2)
        necks = [
            dict(c=(0.16, 0.24), r=(0.22, 0.32), p=(0.30, 0.52), a=5 * wave(t, 2, phi_phase(1)) + 8 * thrash[0], d=(0, -0.006 * breathe), soft=0.45),
            dict(c=(0.40, 0.20), r=(0.19, 0.25), p=(0.46, 0.48), a=-4 * wave(t, 3, phi_phase(2)) + 7 * thrash[1], soft=0.4),
            dict(c=(0.58, 0.25), r=(0.17, 0.26), p=(0.60, 0.52), a=-5 * wave(t, 2, phi_phase(3)) - 8 * thrash[2], soft=0.4),
            dict(c=(0.05, 0.38), r=(0.18, 0.2), p=(0.20, 0.53), a=8 * wave(t, 2, phi_phase(5)) + 8 * thrash[3], soft=0.5),
        ]
        fourth = dict(c=(0.38, 0.42), r=(0.19, 0.17), p=(0.47, 0.55), a=6 * wave(t, 2, phi_phase(4)), d=(-0.008 * breathe, 0), soft=0.45)
        rear = smooth(0.0, 0.18, u) * (1 - smooth(0.7, 1.0, u))
        parts = [
            dict(c=(0.50, 0.62), r=(0.22, 0.13), p=(0.50, 0.62), d=(0, -0.01 * breathe), soft=0.9),
            necks[3],   # the fifth head, on what the sprite has as a stump
            necks[1], necks[0], necks[2], fourth]
        lean, drop = 6 * wave(t, 4, 0.1) * rear, 4 * wave(t, 4, 0.35)
        def body(x, y):
            up = upper(y, 0.72)
            tail = np.clip((x - 0.62) / 0.38, 0, 1)
            return -lean * up, -drop * up + 11 * tail ** 1.4 * np.sin(2 * math.pi * (t * 3 - x * 1.5))
        cv = back.copy()
        # vents: the phase colour in the floor throws light on the walls
        bright = 0.85 + 0.15 * wave(t, 8)
        cv = cv * (0.9) + np.clip((YY - 70) / 170, 0, 1)[..., None] * (ton * 0.16) * bright
        cv = feather(cv)
        shadow(cv, left + w * 0.45, ground - 4, w * 0.5, 11, 0.55)
        for ri, rr_ in enumerate(phi_radii(8, 6)):
            amp = 0.22 + 0.75 * math.exp(-(ri - u * 9) ** 2)
            ring(cv, left + w * 0.45, H - 15, rr_, 0.3, 2.4, ton * 0.42 * amp)
        n = rising(483, 8, t, 2)
        for i, (vx, vy) in enumerate(vents):
            th = wave(t, 2, phi_phase(i)) * 0.15 + 0.85
            hgt = 62 * th * (0.8 + 0.3 * i % 2)
            along = np.clip((vy - YY) / hgt, 0, 1)
            wid = 3 + 11 * along
            den = np.exp(-((XX - vx - (n - 0.5) * 8 * along) / wid) ** 2) * (YY < vy) * (1 - along) ** 0.8
            den = np.clip(den * 1.5 + (n - 0.5) * 1.4 * den - 0.1, 0, 1)
            if i < 2:
                cv[:] = cv * (1 - 0.55 * den[..., None]) + (ton * 0.6 + 16) * den[..., None] * 0.55
            r2 = ((XX - vx) / 14) ** 2 + ((YY - vy) / 4) ** 2
            cv[:] = np.where((r2 < 1)[..., None], cv * 0.6 + ton * 0.22, cv)
        rgb, al = rig.pose(parts, light=(math.cos(2 * math.pi * t), -0.6), relief=0.3, bend=body)
        rgb = rgb * (1 + 0.15 * np.clip(rig.yy / rig.al.shape[0] - 0.5, 0, 1))[..., None] + ton * np.clip(rig.yy / rig.al.shape[0] - 0.62, 0, 1)[..., None] * 0.25
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        vx, vy = vents[2]
        n2 = n
        along = np.clip((vy - YY) / 40, 0, 1); den = np.exp(-((XX - vx) / (3 + 9 * along)) ** 2) * (YY < vy) * (1 - along)
        den = np.clip(den * 1.4 + (n2 - 0.5) * 1.2 * den - 0.1, 0, 1)
        cv[:] = cv * (1 - 0.6 * den[..., None]) + (ton * 0.7 + 30) * den[..., None] * 0.6
        # burnt stumps; and the moment a head goes, a burst in the phase colour
        for j, d in enumerate(doomed):
            part = necks[d["i"]]
            sx, sy = rig.at(part, *d["stump"]); sx, sy = shifted(sx, sy, d["stump"][1], lean, drop, 0.72)
            if cuts[j] > 0.3 and j != 2:    # the middle stump hides behind the right head, which stays
                glow(cv, left + sx, top + sy, 6, ton, 0.55 * cuts[j])
                stamp(cv, left + sx, top + sy, ton, 2.2 * cuts[j])
            if k == j and u > 0.78:
                hx, hy = rig.at(part, *d["c"]); hx, hy = shifted(hx, hy, d["c"][1], lean, drop, 0.72)
                v = (u - 0.78) / 0.22
                glow(cv, left + hx, top + hy, 10 + 18 * v, ton, 1.0 * (1 - v))
                for t0, (_, _, gx, gy, g) in gore.p:
                    stamp(cv, left + hx + gx * v, top + hy + gy * v + g * v * v, ton * (1.1 - v * 0.8), 1.6)
        # the stump's own light follows the phase
        ox, oy = rig.at(parts[1], 0.03, 0.41); ox, oy = shifted(ox, oy, 0.41, lean, drop, 0.72)
        glow(cv, left + ox, top + oy, 9, ton, 0.45 + 0.2 * wave(t, 4))
        rig.al, rig.rgb = base_al, base_rgb
        return np.clip(cv, 0, 255)
    return "hydra", "four phases", scene

# ---- cerberus ----------------------------------------------------------------
def fire_wall(cv, top_y, bottom_y, t, seed, power=1.0, gust=None, stops=None):
    """fire as a fluid: noise climbing from a floor line, eaten away with height"""
    stops = stops or [(225, 135, 40), (185, 75, 20), (120, 38, 12), (60, 18, 8)]
    n = rising(seed, 9, t, 2)
    v = np.clip((bottom_y - YY) / max(1, bottom_y - top_y), 0, 1)
    env = gust if gust is not None else 1.0
    den = np.clip((1.05 - v / np.maximum(env, 0.05)) * 1.4 + (n - 0.5) * 2.4 - 0.5, 0, 1) * (YY < bottom_y) * power
    cv[:] = cv * (1 - 0.92 * den[..., None]) + ramp(stops, 1 - den)[..., :3] * 0.92 * den[..., None]

def cerberus_heads():
    rgb0, al0 = fit(sprite("Cerberus.png"), W - 36, H - 40, exposure=0.85, gamma=0.74, sat=0.8)
    rgb0 = rgb0 * 0.9 + np.array([52.0, 30, 26])        # lift the black facets, or they read as holes against the dark
    h, w = al0.shape[:2]; left = 31; ground = H - 16; top = ground - h
    rig = Rig(rgb0, al0)
    base_c = rig.rgb.copy()
    back = taverley_backdrop()
    bases = [
        dict(c=(0.12, 0.25), r=(0.17, 0.12), p=(0.29, 0.29)),   # far head, on the left
        dict(c=(0.44, 0.33), r=(0.17, 0.22), p=(0.46, 0.46)),   # the front-on head
        dict(c=(0.13, 0.42), r=(0.20, 0.17), p=(0.30, 0.44)),   # near head
    ]
    mouths = [(0.03, 0.27), (0.42, 0.44), (0.05, 0.47)]
    # far head: magic. front head: ranged. near head: melee.
    style_light = [(130, 80, 255), (255, 130, 30), (255, 70, 50)]
    # each head wears its style's colour all the time; a dark seam parts the two on the left
    head_tint = [np.array([0.62, 0.55, 1.25]), np.array([1.12, 0.98, 0.5]), np.array([1.2, 0.52, 0.48])]
    w_near = rig.weight(dict(bases[2], soft=0.3), rig.xx, rig.yy)
    w_far = rig.weight(dict(bases[0], soft=0.3), rig.xx, rig.yy) * (1 - w_near)
    w_front = rig.weight(dict(bases[1], soft=0.3), rig.xx, rig.yy) * (1 - w_near) * (1 - w_far)
    head_w = [w_far[..., None], w_front[..., None], w_near[..., None]]
    nx, ny = rig.px(*bases[2]["c"])
    dn = np.sqrt(((rig.xx - nx) / (bases[2]["r"][0] * rig.w)) ** 2 + ((rig.yy - ny) / (bases[2]["r"][1] * rig.h)) ** 2)
    seam = (np.exp(-((dn - 0.88) / 0.13) ** 2) * (rig.yy < ny))[..., None]
    base_c = base_c * (1 - 0.7 * seam)
    eye_tones = [(170, 130, 255), (255, 170, 50), (255, 90, 70)]
    orb_to = (11, H - 62)
    xs = np.arange(W, dtype=float)
    river_y = H - 62 + 5 * np.sin(xs * 0.028 + 0.6) + 2.5 * np.sin(xs * 0.071)
    river_w = 11 + 2 * np.sin(xs * 0.043 + 2)
    river_warp = fbm(499, (28, 12))
    spit_to = [W * 0.13, W * 0.30, W * 0.50, W * 0.72, W * 0.90]
    sparks = Motes(491, 26, lambda r: (r.rand() * W, H - 26 - r.rand() * 16, 16 * (r.rand() - 0.5), -130 - 90 * r.rand(), 0))
    def scene(t):
        phase = t * 3; k = int(phase) % 3; u = phase % 1
        on = max(0.0, math.sin(math.pi * u)) ** 0.7
        # each style has its own body language: wind-up, then the blow
        wind = smooth(0.05, 0.36, u) * (1 - smooth(0.36, 0.46, u))
        strike = smooth(0.36, 0.46, u) * (1 - smooth(0.72, 0.98, u))
        heads = []
        for i, b in enumerate(bases):
            sway = wave(t, 2 + (i == 1), phi_phase(i))
            if i != k: w_, s = 0.0, 0.0
            else: w_, s = wind, strike
            if i == 0:                                                     # magic: rears to gather, recoils on release
                heads.append(dict(b, a=5 * sway + 10 * w_ + 3 * s, d=(0.012 * w_ + 0.012 * s, -0.014 * w_), lit=0.25 * w_, soft=0.4))
            elif i == 1:                                                   # ranged: pumps down with each spit
                pump = s * max(0.0, wave(u, 5))
                heads.append(dict(b, a=4 * sway + 3 * w_, d=(0.006 * wave(t, 1, 0.2), 0.018 * pump - 0.024 * w_), lit=0.2 * s, soft=0.4))
            else:                                                          # melee: draws right back, then drives in
                heads.append(dict(b, a=5 * sway + 11 * w_ - 13 * s, d=(0.035 * w_ - 0.095 * s, -0.012 * w_ + 0.03 * s), lit=0.25 * s, soft=0.45))
        force = (0.3, 0.5, 1.0)[k]
        lean = -11 * strike * force + 5 * wind * force
        drop = 7 * strike * force - 4 * wind * force + 1.5 * wave(t, 3)
        bite_at = 0.44
        since_bite = (u - bite_at) if k == 2 and u >= bite_at else 9.0
        bite = math.exp(-since_bite * 6) if since_bite < 9 else 0.0
        pulse = 0.82 + 0.18 * wave(t, 5)
        cv = back * (0.8 + 0.3 * np.exp(-((YY - (H - 30)) / 60) ** 2)[..., None] * pulse) + np.exp(-((YY - (H - 30)) / 42) ** 2)[..., None] * np.array([34.0, 13, 3]) * pulse
        # the room is relit in the attacker's colour
        room = np.array(style_light[k], float) * (0.25 * wind + 0.32 * strike + (0.28 * bite if k == 2 else 0))
        cv = cv * (1 + np.clip(room / 255, 0, 1) * 1.6) + np.exp(-((XX - W * 0.5) / 120) ** 2 - ((YY - 90) / 80) ** 2)[..., None] * room * 0.5
        cv = feather(cv)
        gust = 0.55 + 0.25 * np.sin(XX * 0.05 + 2 * math.pi * t) * 0.6 + 0.3 * strike * np.exp(-((XX - left - 40) / 80) ** 2)
        # the lava river behind him: a band across the lair, its crusts and veins visibly flowing left
        fl = 2 * math.pi * t
        vein = 0.5 + 0.3 * np.sin(XX * 0.13 + river_warp * 7 + YY * 0.25 + fl * 3) + 0.2 * np.sin(XX * 0.31 - river_warp * 5 - YY * 0.4 + fl * 5)
        d_ = np.abs(YY - river_y[None, :]) / river_w[None, :]
        band = np.clip((1 - d_) * 3.5, 0, 1)
        core = np.clip(1 - d_ * 1.1, 0, 1)
        crust = np.clip((vein - 0.34) * 3.2, 0, 1)                         # black plates ride the current
        molten = ramp([(62, 18, 8), (160, 44, 8), (235, 104, 18), (252, 182, 68)], np.clip(crust * (0.5 + 0.7 * core), 0, 1))[..., :3]
        halo = np.exp(-(np.maximum(d_ - 1, 0) * river_w[None, :] / 26) ** 2)[..., None] * (1 - band[..., None])
        cv = cv + cv * halo * 0.9 + halo * np.array([44.0, 15, 2]) * pulse
        cv = cv * (1 - band[..., None]) + molten * band[..., None] * EDGE ** 0.5
        fire_wall(cv, H - 40, H - 16, t, 492, 0.4, gust)
        sparks.draw(cv, t, lambda v: np.array((240, 150, 50)) * math.sin(math.pi * v) * 0.7, 0.9)
        shadow(cv, left + w * 0.5, ground - 2, w * 0.55, 7, 0.65)
        parts = [
            dict(c=(0.76, 0.38), r=(0.26, 0.15), p=(0.66, 0.5), a=-2.5 * wave(t, 2, 0.15), d=(0, -0.01 * wave(t, 2)), soft=0.8),   # flank heaves
            dict(c=(0.50, 0.53), r=(0.16, 0.08), p=(0.50, 0.53), d=(0, -0.008 * wave(t, 2, 0.1)), soft=0.9),
            heads[0], heads[1], heads[2]]
        if k == 2:                                                         # the near jaw gapes on the wind-up and snaps shut
            near = heads[2]
            parts.append(dict(c=(0.07, 0.49), r=(0.09, 0.05), p=(0.17, 0.44), a=near["a"] - 13 * wind, d=near["d"], soft=0.5))
        def body(x, y):
            up = upper(y, 0.72)
            # the off hind paw shifts its weight
            hind = np.exp(-((x - 0.92) / 0.1) ** 2) * np.clip((y - 0.62) / 0.2, 0, 1)
            return -lean * up, -drop * up + 7 * max(0.0, wave(t, 1, 0.1)) * hind
        ang = 2 * math.pi * t
        dyed = base_c.copy()
        for i in range(3):
            power = wind + strike if i == k else 0.0
            level = (0.8 + 0.5 * min(power, 1)) * head_tint[i]            # the two waiting heads step back
            dyed = dyed * (1 + head_w[i] * (level - 1)) + head_w[i] * np.array(style_light[i], float) * 0.34 * min(power, 1)
        rig.rgb = np.clip(dyed, 0, 255)
        rgb, al = rig.pose(parts, light=(math.cos(ang) * 0.7, 0.35), relief=0.3, bend=body)
        mx, my = rig.at(heads[k], *mouths[k]); mx, my = shifted(mx, my, mouths[k][1], lean, drop, 0.72)
        rgb = lit_on(rig, rgb, mx, my, w * 0.2, h * 0.2, style_light[k], (0.65 * wind if k == 0 else 0.1 * on if k == 1 else 0.8 * bite))
        under =np.clip(np.arange(rig.al.shape[0]) / rig.al.shape[0] - 0.45, 0, 1)[:, None, None] * 2
        rgb = rgb + under * np.array([150.0, 62, 12]) * pulse * 0.7          # the river lights his belly and legs
        # a dark contact edge, so red legs stay separate from the orange river
        edge_ = np.asarray(Image.fromarray((al[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2)), float) / 255
        over(cv, np.zeros_like(rgb), np.clip(edge_ * 1.6, 0, 0.8)[..., None], left - rig.pad, top - rig.pad)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        m = (left + mx, top + my)
        if k == 0:
            # magic: an orb gathers in the mouth, then flies out and bursts with crackling arcs
            if u < 0.42:
                size = smooth(0.05, 0.40, u)
                glow(cv, m[0], m[1], 5 + 11 * size, (90, 60, 255), 0.9 * size)
                nested(cv, m[0], m[1], 8 + 20 * size, 5, 2, np.array((150.0, 120, 255)) * size, rot=2 * math.pi * t * 3, twist=math.pi / 5, size=1.1)
                stamp(cv, m[0], m[1], (200, 190, 255), 1 + 4.5 * size)
            elif u < 0.72:
                v = (u - 0.42) / 0.30
                for j in range(6):
                    q = v - j * 0.06
                    if q < 0: break
                    stamp(cv, m[0] + (orb_to[0] - m[0]) * q, m[1] + (orb_to[1] - m[1]) * q, np.array((170, 120, 255)) * (1 - j * 0.15), 6 * (1 - j * 0.14))
                ox, oy = m[0] + (orb_to[0] - m[0]) * v, m[1] + (orb_to[1] - m[1]) * v
                stamp(cv, ox, oy, (235, 230, 255), 3.5)
                glow(cv, ox, oy, 16, (80, 60, 255), 0.8)
            else:
                v = (u - 0.72) / 0.28
                f = int(t * FRAMES)
                ring(cv, orb_to[0], orb_to[1], 6 + v * 46, 0.7, 4, np.array([140.0, 110, 255]) * (1 - v))
                for j in range(3):
                    bolt(cv, orb_to[0], orb_to[1], orb_to[0] + 12 + j * 16, orb_to[1] + 46 - j * 12, 610 + j + f, np.array((190, 170, 255)) * (1 - v), jag=18, size=1.1)
                glow(cv, orb_to[0], orb_to[1], 20, (80, 60, 255), 0.9 * (1 - v))
        elif k == 1:
            # ranged: a volley of spit, one after another, arcing out to the floor
            glow(cv, m[0], m[1], 4 + 3 * on, (255, 130, 30), 0.25 + 0.4 * strike)
            for j, tx in enumerate(spit_to):
                v = (u - 0.36 - j * 0.07) / 0.28
                ty = H - 26 + (j % 2) * 5
                if 0 <= v < 1:
                    for q_, c_ in ((0.0, 1.0), (0.06, 0.6), (0.12, 0.35)):
                        q = max(0.0, v - q_)
                        stamp(cv, m[0] + (tx - m[0]) * q, m[1] + (ty - m[1]) * q - 52 * 4 * q * (1 - q), np.array((255, 205, 80)) * c_, 6.0 - 9 * q_)
                    stamp(cv, m[0] + (tx - m[0]) * v, m[1] + (ty - m[1]) * v - 52 * 4 * v * (1 - v), (170, 150, 100), 2.6)
                elif 1 <= v < 1.7:
                    q = (v - 1) / 0.7
                    ring(cv, tx, ty, 2 + q * 18, 0.35, 3.5, np.array([255.0, 140, 40]) * (1 - q))
                    for sp in range(5):
                        stamp(cv, tx + (sp - 2) * q * 9, ty - 34 * q * (1 - q) * (3 - abs(sp - 2)), np.array((255, 175, 60)) * (1 - q), 1.4)
                    glow(cv, tx, ty - 3, 9, (255, 110, 25), 0.9 * (1 - q))
        else:
            # melee: no projectile. the jaws meet at the end of the lunge: a flash and three rakes
            if bite > 0.03:
                bx, by = m[0] - 5, m[1] + 1
                glow(cv, bx, by, 8 + 14 * (1 - bite), (255, 60, 40), 1.0 * bite)
                for j in range(3):
                    x0 = bx - 8 + j * 12; y0 = by - 26
                    for q in np.linspace(0, 1, 15):
                        stamp(cv, x0 - 14 * q, y0 + 54 * q, np.array((255, 235, 215)) * min(1.0, bite * 1.6), 2.6)
        # the attacker's eyes burn in its style; the others smoulder
        for i, ex, ey in [(0, 0.02, 0.22), (1, 0.377, 0.285), (1, 0.475, 0.285), (2, 0.14, 0.345)]:
            px, py = rig.at(heads[i], ex, ey); px, py = shifted(px, py, ey, lean, drop, 0.72)
            glow(cv, left + px, top + py, 3.0 + 1.5 * (i == k) * on, eye_tones[i] if i == k else (255, 215, 60), (1.1 if i == k else 0.6) + 0.2 * wave(t, 3, i * 0.3))
        # low flames lick in front of the paws
        fire_wall(cv, H - 30, H - 4, t, 498, 0.85, 0.45 + 0.3 * np.sin(XX * 0.11 - 2 * math.pi * t * 2), [(255, 215, 110), (245, 130, 35), (180, 55, 15), (70, 20, 8)])
        if bite > 0.05:                                                    # the bite shakes the whole picture together
            ox_, oy_ = int(round(bite * 4 * math.sin(since_bite * 60))), int(round(bite * 3 * math.cos(since_bite * 90)))
            cv = cv[np.clip(np.arange(H) - oy_, 0, H - 1)][:, np.clip(np.arange(W) - ox_, 0, W - 1)]
        return np.clip(cv, 0, 255)
    return "cerberus", "three heads in the fire", scene

# ---- scurrius ----------------------------------------------------------------
def scurrius_sewer():
    rgb0, al0 = fit(sprite("Scurrius.png"), W - 26, H - 30, exposure=0.74, gamma=0.95, sat=0.72)
    h, w = al0.shape[:2]; left = 16; ground = H - 6; top = ground - h
    rig = Rig(rgb0, al0)
    back = sewer_backdrop()
    water_y = H - 28
    falls = [(0.02, 22, 8), (0.12, 176, 9), (0.22, 62, 7), (0.33, 198, 6), (0.42, 34, 9), (0.52, 124, 7), (0.63, 14, 6), (0.72, 160, 8), (0.82, 82, 7), (0.92, 190, 6)]
    drips = Motes(501, 12, lambda r: (r.rand() * W, 6, 0, 30, water_y - 10))
    def scene(t):
        # he rears and screeches, then slams his weight forward: the roof comes down
        rear = smooth(0.0, 0.30, t) * (1 - smooth(0.30, 0.38, t)) + smooth(0.62, 0.78, t) * (1 - smooth(0.78, 0.84, t)) * 0.7
        since = min(((t - 0.38) % 1.0, (t - 0.84) % 1.0))
        impact = math.exp(-since * 9)
        roar = smooth(0.14, 0.30, t) * (1 - smooth(0.30, 0.40, t))
        lean = 13 * rear - 12 * impact
        drop = -13 * rear + 15 * impact
        sx = int(round(impact * 4 * math.sin(since * 80)))
        ang = 2 * math.pi * t
        cv = back * (0.86 + 0.25 * impact)
        cv += np.exp(-((XX - W * 0.5 - 40 * math.sin(ang)) / 80) ** 2 - ((YY - 40) / 60) ** 2)[..., None] * np.array([10.0, 14, 8])
        cv = feather(cv)
        # the small rat chatters on his own beat
        small = dict(c=(0.70, 0.13), r=(0.24, 0.16), p=(0.62, 0.24), a=9 * wave(t, 3, phi_phase(1)) - 6 * impact, d=(0.01 * wave(t, 2, 0.4), 0), soft=0.45)
        head = dict(c=(0.17, 0.37), r=(0.22, 0.2), p=(0.34, 0.41), a=9 * rear + 3 * wave(t, 3, phi_phase(2)) - 8 * impact, soft=0.4)
        jaw = dict(c=(0.10, 0.425), r=(0.09, 0.055), p=(0.17, 0.39), a=head["a"] - 15 * (roar + 0.3 * impact) - 3 * max(0, wave(t, 5)), soft=0.5)
        parts = [
            dict(c=(0.52, 0.62), r=(0.2, 0.22), p=(0.5, 0.62), d=(0, -0.012 * wave(t, 2)), soft=0.9),      # the belly breathes
            dict(c=(0.15, 0.55), r=(0.14, 0.16), p=(0.30, 0.45), a=-12 * rear + 13 * impact + 4 * wave(t, 2, phi_phase(3)), soft=0.4),  # free arm lifts and claws
            dict(c=(0.44, 0.65), r=(0.11, 0.14), p=(0.50, 0.50), a=-5 * rear + 6 * impact + 3 * wave(t, 2, phi_phase(4)), soft=0.45),
            small, head, jaw]
        def body(x, y):
            up = upper(y, 0.82)
            # the tail is the long curl on the right, a whip travelling down it
            tail = smooth(0.74, 0.92, x) * smooth(0.28, 0.45, y) * (1 - smooth(0.74, 0.82, y) * 0.0)
            u_ = np.clip((y - 0.3) / 0.45, 0, 1)
            wx = 5 * tail * np.sin(2 * math.pi * (t * 2 - y * 1.2)) * (0.5 + u_ * 0.5)
            wy = 2 * tail * np.cos(2 * math.pi * (t * 2 - y * 1.2))
            return -lean * up + wx, -drop * up + wy
        rgb, al = rig.pose(parts, light=(math.cos(ang) * 0.8, -0.6), relief=0.3, bend=body)
        rgb = lit_on(rig, rgb, 0.92 * w, 0.52 * h, w * 0.14, h * 0.26, (54, 56, 50), 1.0)        # the grey tail would sink into the brick
        ox, oy = left + sx - rig.pad, top - rig.pad
        over(cv, rgb, al, ox, oy)
        # eyes: his red, the small rat's green
        hx, hy = rig.at(head, 0.155, 0.31); hx, hy = shifted(hx, hy, 0.31, lean, drop, 0.82)
        glow(cv, left + sx + hx, top + hy, 3.5, (220, 40, 30), 0.5 + 0.6 * roar)
        gx, gy = rig.at(small, 0.685, 0.06); gx, gy = shifted(gx, gy, 0.06, lean, drop, 0.82)
        glow(cv, left + sx + gx, top + gy, 3.5, (90, 240, 60), 0.6 + 0.3 * wave(t, 3))
        jx, jy = rig.at(jaw, 0.05, 0.42); jx, jy = shifted(jx, jy, 0.42, lean, drop, 0.82)
        if roar > 0.1: ring(cv, left + sx + jx - 3, top + jy, 8 + 40 * ((t * 7) % 1), 0.9, 2.5, np.array([70.0, 60, 40]) * roar, (XX < left + sx + jx))
        # debris and its telegraph: a shadow on the water first, then the rock, then a splash
        water = np.clip((YY - water_y - 3 * np.sin(XX * 0.1 + ang)) / 2.0, 0, 1)[..., None]
        n = noise(502, 9, t)
        sheen = np.clip(n - 0.55, 0, 1) * 2 * (0.5 + 0.5 * np.sin(YY * 1.4 + XX * 0.05 + ang))
        wet = np.array([30.0, 50, 40]) * (0.75 + 0.5 * n[..., None]) + sheen[..., None] * np.array([70.0, 100, 74])
        cv[:] = cv * (1 - 0.82 * water) + wet * 0.82 * water
        for i, (t0, x, sz) in enumerate(falls):
            u = (t - t0) % 1.0
            land_y = water_y + 6 + (i * 5) % 14
            if u < 0.2:
                v = u / 0.2
                shadow(cv, x, land_y, sz * (1.8 - 0.8 * v), sz * 0.45, 0.3 + 0.4 * v)
                y = 2 + (land_y - 2) * v ** 1.5
                stamp(cv, x, y, (205, 175, 140), sz * 0.85)
                stamp(cv, x + 1, y + 1, (-70, -60, -50), sz * 0.45)
            elif u < 0.38:
                v = (u - 0.2) / 0.18
                ring(cv, x, land_y, 3 + v * sz * 3.2, 0.32, 3, np.array([170.0, 210, 170]) * (1 - v))
                for j in range(5):
                    a = j * 1.26 + i
                    stamp(cv, x + math.cos(a) * v * sz * 2.4, land_y - math.sin(j * 0.6 + 0.6) * v * (1 - v) * 44, np.array((190, 225, 180)) * (1 - v), 1.5)
        drips.draw(cv, t * 2 % 1, lambda v: (70, 90, 70), 0.8)
        return np.clip(cv, 0, 255)
    return "scurrius", "the sewer", scene

# ---- nex ---------------------------------------------------------------------
def nex_phases():
    rgb0, al0 = fit(sprite("Nex.png"), W - 20, H - 26, exposure=0.92, gamma=0.82, sat=1.15)
    h, w = al0.shape[:2]; left = (W - w) // 2 + 1; ground = H - 9; top = ground - h
    rig = Rig(rgb0, al0)
    back = prison_backdrop()
    # smoke, shadow, blood, ice, Zaros
    tones = [np.array([125.0, 130, 110]), np.array([95.0, 60, 130]), np.array([220.0, 40, 40]), np.array([120.0, 200, 255]), np.array([190.0, 100, 255])]
    bleed = Motes(511, 26, lambda r: (r.rand() * 2 * math.pi, 40 + r.rand() * 60, 0, 0, 0))
    snow = Motes(512, 22, lambda r: (r.rand() * W, 0, -10 * r.rand(), 150, 60))
    shards = [(-70, 11, 58), (-44, 8, 40), (-22, 7, 30), (26, 7, 34), (50, 9, 46), (74, 11, 60)]
    def scene(t):
        p = t * 5; k = int(p) % 5; u = p % 1
        on = math.sin(math.pi * u) ** 0.5
        tone = tones[k] if u > 0.12 else tones[k - 1] + (tones[k] - tones[k - 1]) * u / 0.12
        ang = 2 * math.pi * t
        flap = wave(t, 5, 0.75)                                             # one wingbeat per spell; up mid-phase
        lag = wave(t, 5, 0.65)
        # backdrop takes the phase colour
        wash = np.exp(-((XX - W / 2) / 110) ** 2 - ((YY - 110) / 120) ** 2)[..., None]
        cv = back * (0.85 + 0.18 * wash) + wash * tone * 0.16 * on
        if k == 1: cv = cv * (1 - 0.4 * on)
        cv = feather(cv)
        cx, floor_y = left + 0.48 * w, ground - 6
        shadow(cv, cx, ground - 1, w * 0.33, 6, 0.6)
        vortex = phi_field(cx, top + 0.27 * h, t, arms=5, spin=2, sharp=2.2)
        cv += (vortex * np.clip(1 - np.hypot(XX - cx, YY - top - 0.27 * h) / 135, 0, 1))[..., None] * tone * (0.26 + 0.2 * on) * EDGE
        if k == 4:                                                          # Zaros: slow beams turning behind her
            b = np.arctan2(YY - 70, XX - cx)
            beams = np.clip(np.cos(b * 5 + ang * 2) - 0.72, 0, 1) * 3.2 * np.clip(1 - np.hypot(XX - cx, YY - 70) / 150, 0, 1)
            cv += beams[..., None] * tone * 0.55 * on
        if k == 1:                                                          # shadow: a black well she draws around her
            well = np.exp(-((XX - cx) / 64) ** 2 - ((YY - 110) / 92) ** 2)[..., None]
            cv = cv * (1 - 0.65 * well * on)
        if k == 0:                                                          # smoke behind her
            n = rising(513, 14, t, 1)
            den = np.clip((n - 0.34) * 2.4, 0, 1) * np.clip((YY - 40) / 120, 0, 1) * on * 0.8
            cv[:] = cv * (1 - den[..., None]) + np.array([62.0, 62, 58]) * (0.5 + n[..., None]) * den[..., None]
        head = dict(c=(0.46, 0.085), r=(0.2, 0.1), p=(0.50, 0.17), a=4 * wave(t, 2, phi_phase(1)) - 3 * on * (k % 2 * 2 - 1), soft=0.5)
        neck = dict(c=(0.5, 0.11), r=(0.13, 0.05), p=(0.5, 0.18), a=head["a"] * 0.5, soft=0.6)
        parts = [
            dict(c=(0.47, 0.37), r=(0.16, 0.1), p=(0.47, 0.37), d=(0, -0.006 * wave(t, 5, 0.2)), soft=0.9),    # the ribs
            dict(c=(0.67, 0.50), r=(0.14, 0.09), p=(0.56, 0.40), a=7 * wave(t, 2, phi_phase(2)), soft=0.5),                # the tail flicks
            dict(c=(0.14, 0.44), r=(0.27, 0.36), p=(0.36, 0.22), a=9 * flap, lit=0.14 * flap, soft=0.5),         # left wing and arm
            dict(c=(0.84, 0.50), r=(0.27, 0.42), p=(0.60, 0.24), a=-9 * flap, lit=0.14 * flap, soft=0.5),        # right wing and arm
            dict(c=(0.07, 0.52), r=(0.09, 0.2), p=(0.05, 0.33), a=-4 * lag, soft=0.9),                             # the blades trail
            dict(c=(0.84, 0.66), r=(0.11, 0.2), p=(0.80, 0.50), a=4 * lag, soft=0.9),
            neck, head]
        sway = 5 * wave(t, 1, phi_phase(3))
        rise = 3.5 * flap
        def body(x, y):
            up = np.clip((0.94 - y) / 0.94, 0, 1) ** 0.8
            return -sway * up, rise * up
        rgb, al = rig.pose(parts, light=(math.cos(ang) * 0.9, -0.5), relief=0.3, bend=body)
        rgb = rgb * (0.8 if k == 1 else 1.0)
        cy = 0.36 * h
        rgb = lit_on(rig, rgb, 0.47 * w, cy, w * 0.55, h * 0.55, tone, 0.42 * on)
        over(cv, rgb, al, left - rig.pad, top - rig.pad)
        ex, ey = rig.at(head, 0.42, 0.09); ex, ey = shifted(ex, ey, 0.09, sway, -rise, 0.94)
        glow(cv, left + ex, top + ey, 4, tone, 0.5 + 0.4 * on)
        chest = (left + 0.45 * w + sway * 0.6, top + 0.27 * h)
        lh = rig.at(parts[2], 0.04, 0.33); rh = rig.at(parts[3], 0.82, 0.54)
        lhand = (left + lh[0] + sway * 0.7, top + lh[1]); rhand = (left + rh[0] + sway * 0.5, top + rh[1])
        if k == 0:                                                          # smoke rolls over her feet
            n = noise(515, 12, t * 2)
            den = np.clip(np.exp(-((YY - (H - 16)) / 30) ** 2) * 1.5 * on + (n - 0.5) * 1.3 - 0.35, 0, 1)
            cv[:] = cv * (1 - 0.75 * den[..., None]) + np.array([104.0, 104, 96]) * (0.6 + 0.5 * n[..., None]) * 0.75 * den[..., None]
        elif k == 1:                                                        # shadow: black shards burst from the floor
            for i, (dx, wid, tall) in enumerate(shards):
                g = smooth(0.08 + i * 0.03, 0.35 + i * 0.03, u) * (1 - smooth(0.8, 1.0, u))
                spike(cv, cx + dx, H - 6, wid, tall * g, (20, 12, 30), (95, 60, 150), lean=dx * 0.1)
        elif k == 2:                                                        # blood: drops siphon into her chest
            for t0, (a, rad, _, _, _) in bleed.p:
                v = (u * 1.3 + t0) % 1.0
                r_ = rad * (1 - v) + 3
                stamp(cv, chest[0] + math.cos(a) * r_ * 1.2, chest[1] + math.sin(a) * r_ * 1.3, np.array((255, 40, 40)) * on * (0.5 + 0.5 * v), 2.4)
            glow(cv, chest[0], chest[1], 10 + 6 * on, (190, 20, 20), 0.5 * on)
        elif k == 3:                                                        # ice: a ring across the floor, crystals, sleet
            ring(cv, cx, H - 12, u * 120, 0.26, 5, np.array([140.0, 215, 255]) * on * 0.8)
            for i, (dx, wid, tall) in enumerate(shards):
                g = smooth(0.1 + abs(dx) / 300, 0.3 + abs(dx) / 300, u) * (1 - smooth(0.85, 1.0, u))
                spike(cv, cx + dx * 0.95, H - 6, wid * 0.8, tall * 0.8 * g, (70, 130, 190), (215, 240, 255), lean=dx * 0.06)
            snow.draw(cv, (u * 2) % 1, lambda v: np.array((190, 220, 240)) * on, 1.0)
        else:                                                               # Zaros: bolts arc between the hands and the chest
            f = int(t * FRAMES)
            bolt(cv, lhand[0], lhand[1], chest[0], chest[1], 520 + f, np.array((215, 150, 255)) * on, jag=26, size=1.2)
            bolt(cv, rhand[0], rhand[1], chest[0], chest[1], 560 + f, np.array((215, 150, 255)) * on, jag=26, size=1.2)
            glow(cv, chest[0], chest[1], 12, (170, 80, 255), 0.75 * on)
        return np.clip(cv, 0, 255)
    return "nex", "five phases", scene

RECIPES = [jad_rock, zuk_shield, hydra_phases, cerberus_heads, scurrius_sewer, nex_phases]
