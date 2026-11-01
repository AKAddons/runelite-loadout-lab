"""The mascot at the party (Andrew 2026-11-01: "can you make it look like the mascot is celebrating the holiday?"): the
logo's flask guy stands in the scene through the whole film, in holiday dress, full of the holiday's colour, hopping."""
from hires_kit import *
from hires_batch2c import poly
from hires_theme import logo, outline

SCALE = 0.42
S_ = 0.62                                                                       # dress is drawn for a bigger guy
GOLD = np.array((236.0, 200, 110))

def over_(cv, pts, colour):
    m = poly([(x, y) for x, y in pts])
    cv[:] = cv * (1 - m) + np.array(colour, float) * m

# what he wears: each paints above his crown at (x, y)
def santa(cv, x, y, t):
    s = S_
    over_(cv, [(x - 21 * s, y + 3 * s), (x + 21 * s, y + 3 * s), (x + 9 * s, y - 19 * s), (x - 7 * s, y - 25 * s)], (206, 34, 40))
    over_(cv, [(x - 24 * s, y - 3 * s), (x + 24 * s, y - 3 * s), (x + 24 * s, y + 6 * s), (x - 24 * s, y + 6 * s)], (245, 245, 240))
    stamp(cv, x - 10 * s, y - 25 * s, (250, 250, 245), 3)

def party(cv, x, y, t):
    s = S_
    over_(cv, [(x - 16 * s, y + 3 * s), (x + 16 * s, y + 3 * s), (x, y - 27 * s)], (120, 80, 220))
    line(cv, (x - 8 * s, y - 6 * s), (x + 8 * s, y - 6 * s), (170, 140, 50), 1.2)
    stamp(cv, x, y - 28 * s, (250, 220, 90), 2.6)

def heart(cv, x, y, t):
    s = S_ * (1 + 0.16 * wave(t, 8))
    for dx in (-6, 6): stamp(cv, x + dx * s, y - 16 * S_, (240, 70, 110), 7.5 * s)
    over_(cv, [(x - 12.5 * s, y - 13 * S_), (x + 12.5 * s, y - 13 * S_), (x, y + 2)], (240, 70, 110))

def tophat(cv, x, y, t):
    s = S_
    over_(cv, [(x - 25 * s, y - 2 * s), (x + 25 * s, y - 2 * s), (x + 25 * s, y + 4 * s), (x - 25 * s, y + 4 * s)], (30, 130, 70))
    over_(cv, [(x - 15 * s, y - 26 * s), (x + 15 * s, y - 26 * s), (x + 15 * s, y), (x - 15 * s, y)], (30, 150, 80))
    over_(cv, [(x - 15 * s, y - 8 * s), (x + 15 * s, y - 8 * s), (x + 15 * s, y - 2 * s), (x - 15 * s, y - 2 * s)], (245, 206, 80))

def ears(cv, x, y, t):
    s = S_
    for sx in (-1, 1):
        lean = sx * (9 + 3 * wave(t, 8, 0.2 * sx)) * s
        b = x + sx * 12 * s
        over_(cv, [(b - 7 * s, y + 3 * s), (b + 7 * s, y + 3 * s), (b + lean + 5 * s, y - 28 * s), (b + lean - 5 * s, y - 28 * s)], (244, 240, 232))
        over_(cv, [(b - 3 * s, y), (b + 3 * s, y), (b + lean + 2 * s, y - 23 * s), (b + lean - 2 * s, y - 23 * s)], (244, 160, 180))

def lantern(cv, x, y, t):
    s = S_
    line(cv, (x, y), (x, y - 6 * s), (240, 200, 100), 1.0)
    glow(cv, x, y - 16 * s, 12, (255, 110, 50), 0.5)
    stamp(cv, x, y - 16 * s, (226, 40, 36), 7)
    for dy in (-27, -6): line(cv, (x - 4, y + dy * s), (x + 4, y + dy * s), (250, 206, 90), 1.2)

def spatter(cv, x, y, t):
    for i, c in enumerate(((240, 60, 170), (250, 210, 50), (60, 210, 110), (60, 130, 240))):
        a = i * GOLDEN_ANGLE + 2 * math.pi * t * 4
        stamp(cv, x + 10 * math.cos(a), y - 8 + 6 * math.sin(a), c, 4.5)

def sprout(cv, x, y, t):
    s = S_
    line(cv, (x, y + 2), (x, y - 15 * s), (90, 190, 80), 1.4)
    for sx in (-1, 1):
        w_ = sx * (17 + 3 * wave(t, 8)) * s
        over_(cv, [(x, y - 11 * s), (x + w_ * 0.5, y - 24 * s), (x + w_, y - 20 * s), (x + w_ * 0.55, y - 9 * s)], (80, 200, 90))

def sun(cv, x, y, t):
    s = S_
    glow(cv, x, y - 14 * s, 13, (255, 196, 80), 0.55)
    for i in range(8):
        a = i * math.pi / 4 + 2 * math.pi * t * 2
        line(cv, (x + 11 * s * math.cos(a), y - 14 * s + 11 * s * math.sin(a)), (x + 19 * s * math.cos(a), y - 14 * s + 19 * s * math.sin(a)), (255, 210, 90), 1.2)
    stamp(cv, x, y - 14 * s, (255, 226, 130), 5.5)

def crescent(cv, x, y, t):
    s = S_
    d = np.hypot(XX - x, YY - (y - 15 * s)); e = np.hypot(XX - (x + 6 * s), YY - (y - 18 * s))
    m = ((d < 13 * s) & (e > 11.5 * s))[..., None]
    glow(cv, x, y - 15 * s, 11, (250, 220, 130), 0.3)
    cv[:] = cv * (1 - m) + np.array((252.0, 232, 160)) * m

def celebrating(fn, wears, fluid, spot=0.8):
    """the film, with him in it. fluid: three stops, low to high. spot: where he stands across the frame."""
    def recipe():
        made = fn()
        key, name, film = made[:3]
        frames = made[3] if len(made) > 3 else FRAMES
        hops = max(2, frames // 16)
        sil, gold, cap = (np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize((int(m.shape[1] * SCALE), int(m.shape[0] * SCALE)), Image.NEAREST)) > 128 for m in logo())
        edge = outline(sil, 1)
        bh, bw = sil.shape
        crown = int(np.nonzero(cap.any(1))[0][0])
        gx, ground = int(W * spot - bw / 2), H - 24
        rows, cols = np.mgrid[0:bh, 0:bw]
        burst = [(math.cos(i * GOLDEN_ANGLE), math.sin(i * GOLDEN_ANGLE), 12 + 7 * math.sqrt(i)) for i in range(1, 15)]
        def scene(t):
            cv = film(t).copy()
            vis = min(1.0, float(np.abs(cv - BG).mean()) / 14)                  # he goes out with the film's own fades
            if vis < 0.05: return np.clip(cv, 0, 255)
            q = (t * hops) % 1
            hop = abs(math.sin(math.pi * q))
            lift = 15 * hop
            squash = 7 * (1 - hop) ** 4 - 3 * hop                               # lands low, stretches rising
            lean = 4 * math.sin(2 * math.pi * t * hops / 2)
            up = (bh - rows) / bh
            sy = np.clip(rows - np.round(squash * up).astype(int), 0, bh - 1)
            sx = np.clip(cols - np.round(lean * up).astype(int), 0, bw - 1)
            top = int(ground - lift) - bh
            place = lambda m: np.pad(m[sy, sx], ((max(top, 0), max(H - top - bh, 0)), (gx, W - gx - bw)))[:H, :W][..., None]
            Sm, Em, Gm, Cm = place(sil), place(edge), place(gold), place(cap)
            shadow_ = np.exp(-((XX - (gx + bw / 2)) / (bw * 0.5)) ** 2 - ((YY - ground) / 4) ** 2)[..., None] * (0.55 - 0.3 * hop)
            layer = cv * (1 - shadow_)
            n = noise(3811, 8, t)
            surface = top + bh * 0.28 + 2 * np.sin(XX * 0.3 + 2 * math.pi * t * hops)
            body = ramp(fluid, np.clip(n * 0.6 + 0.5 * (1 - (YY - surface) / 45), 0, 1))[..., :3]
            inner = np.where((YY > surface)[..., None], body, np.array((34.0, 48, 46)))
            layer = layer * (1 - Sm) + inner * Sm
            layer = layer * (1 - Em) + (np.array(fluid[1], float) * 0.5 + 100) * Em
            layer = layer * (1 - Gm) + GOLD * Gm
            layer = layer * (1 - Cm) + GOLD * Cm
            wears(layer, gx + bw / 2 + lean, top + crown + squash, t)
            for i, (dx, dy, rr) in enumerate(burst):                            # sparkles thrown off each landing
                d = rr * (0.5 + 1.8 * q)
                x, y = gx + bw / 2 + dx * d, ground - 30 + dy * d - 10 * q
                s = 1.5 + (i % 3 == 0)
                tw = GOLD * (1 - q) ** 1.5
                line(layer, (x - s, y), (x + s, y), tw, 1.0)
                line(layer, (x, y - s), (x, y + s), tw, 1.0)
            return np.clip(cv * (1 - vis) + layer * vis, 0, 255)
        return (key, name, scene) + tuple(made[3:])
    recipe.__name__ = fn.__name__
    return recipe

RECIPES = []
