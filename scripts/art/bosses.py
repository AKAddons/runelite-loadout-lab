"""Boss moods (Andrew 2026-09-03): ten bosses keyed off the monster, each
rendered from its wiki image in the pass-seven style with its own motion.
    python3 bosses.py <key>       prints the pool  (==<key> <name> + frames)
    python3 bosses.py show        contact sheet, two frames per boss
Keys: cerberus brutus madangel guardians kraken thermy vetion kbd kq muspah"""
import sys, pathlib, math
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
A = pathlib.Path(__file__).parent / "sprites"
W, H = 31, 12

def ok(rows):
    rows = [r.ljust(W)[:W] for r in rows]; assert len(rows) == H, len(rows)
    # a row may not look like a header, a frame break or a comment to the loader
    rows = [("-" + r[1:]) if r.startswith("==") else (" " + r[1:]) if r.startswith("#") else r for r in rows]
    rows = [(" " + r[1:]) if r.strip() == "--" else r for r in rows]
    for r in rows:
        assert not r.startswith("==") and r.strip() != "--" and not r.startswith("#"), r
        for c in r:
            o = ord(c)
            assert (32 <= o < 127) or (0x2800 <= o <= 0x28FF) or (0x2580 <= o <= 0x259F) or (0x2550 <= o <= 0x256C) or o in (0x2261, 0xA5), (hex(o), r)
    return rows

def blank(): return [" " * W for _ in range(H)]

PAINT = {}   # the paint map of the frame being built: (r, c) -> '#rrggbb'

def put(rows, r, c, text, colour=None):
    if not (0 <= r < H): return rows
    rows = list(rows); line = rows[r].ljust(W)
    for i, ch in enumerate(text):
        cc = c + i
        if 0 <= cc < W:
            line = line[:cc] + ch + line[cc + 1:]
            if colour: PAINT[(r, cc)] = colour
            elif (r, cc) in PAINT and ch == " ": del PAINT[(r, cc)]
    rows[r] = line[:W]; return rows

def bbox(path):
    w, h, fg, lum, lo, hi, *_ = st.load(str(path))
    xs = [x for y in range(h) for x in range(w) if fg[y][x]]; ys = [y for y in range(h) for x in range(w) if fg[y][x]]
    return (min(xs) / w, min(ys) / h, (max(xs) + 1) / w, (max(ys) + 1) / h)

def render(sprite, cols, rows, box=None, **kw):
    """the sprite's foreground box (or a sub-box, fractions of it) fitted into cols x rows, aspect kept"""
    path = A / sprite
    x0, y0, x1, y1 = bbox(path)
    if box:
        bx0, by0, bx1, by1 = box
        x0, y0, x1, y1 = x0 + (x1 - x0) * bx0, y0 + (y1 - y0) * by0, x0 + (x1 - x0) * bx1, y0 + (y1 - y0) * by1
    w, h, *_ = st.load(str(path))
    pw, ph = (x1 - x0) * w, (y1 - y0) * h
    scale = min(cols * 2 / pw, rows * 4 / ph)
    c = max(1, int(pw * scale / 2)); r = max(1, int(ph * scale / 4))
    paint = {}
    rows_ = st.hybrid(str(path), c, r, crop=(x0, y0, x1, y1), paint=paint, **kw)
    return Sprite(rows_, paint)

class Sprite(list):
    """rendered rows plus their paint map (cell colours from the sprite)"""
    def __init__(self, rows, paint):
        super().__init__(rows); self.paint = paint

def place(canvas, frame, top, left):
    paint = getattr(frame, "paint", {})
    for i, line in enumerate(frame):
        rr = top + i
        if 0 <= rr < H:
            for j, ch in enumerate(line):
                cc = left + j
                if ch != " " and 0 <= cc < W:
                    canvas = put(canvas, rr, cc, ch, paint.get((i, j)))
    return canvas

def start():
    """a fresh frame: clear the paint map, return a blank canvas"""
    PAINT.clear(); return blank()

def finish(rows):
    """validate, then freeze the frame as HTML rows with its colours"""
    rows = ok(rows); html = st.emit(rows, dict(PAINT)); PAINT.clear(); return html

def centred(frame, dx=0, dy=0):
    rows = len(frame); cols = max(len(l) for l in frame)
    return place(start(), frame, (H - rows) // 2 + dy, (W - cols) // 2 + dx)

# ---- recipes ---------------------------------------------------------------
def cerberus():
    # the three heads: the top-left of the image; eyes pinned (yellow, not red)
    heads = render("Cerberus.png", 31, 12, box=(0.0, 0.14, 0.53, 0.50), eye_points=((520, 385), (650, 385), (190, 450), (55, 290)), auto_eyes=False, edge_blocks=False, crop_norm=True, bg_dark=0.22)
    out = []
    # the flame front: height in rows, 0 = clear, 12 = engulfed; rises, holds, falls
    heights = [0, 0, 2, 4, 6, 8, 10, 12, 12, 12, 10, 8, 6, 4, 2, 0, 0, 0]
    for t, hgt in enumerate(heights):
        f = centred(heads, dy=0)
        # a flame skyline: every column burns to its own height, tips flicker
        for c in range(W):
            tall = hgt + ((c * 5 + t * 3) % 4) - 2 if hgt > 0 else 0
            for r in range(max(0, H - tall), H):
                depth = H - r
                ch = "▓" if depth <= 2 else "▒" if depth <= 4 else "░" if depth <= tall - 2 else ("^" if (c + t) % 2 else "*")
                f = put(f, r, c, ch, "#ff4a1a" if depth <= 2 else "#ff8a2a" if depth <= 4 else "#ffc040")
        out.append(finish(f))
    return "cerberus", "three heads in the fire", out

def brutus(demonic=False):
    # the whole bull with a flat hide, tossing its head and snorting; the
    # demonic one is the same beast in a hellish palette (Andrew 2026-09-03)
    hide, spot, snort = ("#4a1020", "#ff7a2a", "#ff4a1a") if demonic else ("#7a4a2e", "#f0ece4", "#d0d0d0")
    out = []
    for t in range(12):
        bull = render("Brutus.png", 31, 12, edge_blocks=True, auto_eyes=True, crop_norm=False, solid="▓")
        # a cow: white patches on the flat hide, kept off the horns and the eye
        # a brown hide: the sprite's near-black reads as no colour on the panel
        for (r, c) in list(bull.paint):
            if bull[r][c] == "▓": bull.paint[(r, c)] = hide
        seeds = [(3, 9), (4, 14), (6, 11), (5, 20), (8, 16), (3, 24), (7, 24), (9, 20)]
        for (sr, sc) in seeds:
            for (r, c) in ((sr, sc), (sr, sc + 1), (sr, sc + 2), (sr + 1, sc + 1), (sr + 1, sc + 2), (sr - 1, sc + 1)):
                if 0 <= r < len(bull) and 0 <= c < len(bull[r]) and bull[r][c] == "▓":
                    bull[r] = bull[r][:c] + "█" + bull[r][c + 1:]; bull.paint[(r, c)] = spot
        f = centred(bull, dy=(-1 if t % 6 in (2, 3) else 0), dx=(1 if t % 6 in (3, 4) else 0))
        if t % 6 in (4, 5):
            for k, ch in enumerate("~~"):
                r = 8 + k; c = 2 - k - (t % 2)
                if 0 <= c < W and f[r][c] == " ": f = put(f, r, c, ch, snort)
        out.append(finish(f))
    return ("dbrutus", "demonic snort", out) if demonic else ("brutus", "snort", out)

def dbrutus():
    return brutus(demonic=True)

def madangel():
    angel = render("Mad_Angel.png", 24, 12, box=(0, 0, 1, 0.82))
    out = []
    for t in range(12):
        dy = (0, 0, -1, -1, -1, 0, 0, 1, 1, 1, 0, 0)[t]
        f = centred(angel, dy=dy)
        # debris floats under her, out of phase with the hover
        for k, col in enumerate((11, 15, 19)):
            r = H - 1 - ((t // 2 + k) % 3)
            if f[r][col] == " ": f = put(f, r, col, "^" if k % 2 else "'", "#c8a84a")
        out.append(finish(f))
    return "madangel", "hover", out

def guardians():
    dusk = render("Dusk.png", 16, 9); dawn = render("Dawn.png", 14, 5)
    out = []
    for t in range(16):
        f = place(start(), dusk, 3, 1 + (1 if (t // 4) % 2 else 0))
        # Dawn crosses the sky right to left, then comes back
        x = 30 - int(t * 44 / 15) if t < 8 else -14 + int((t - 8) * 44 / 7)
        f = place(f, dawn, 0 if t % 2 else 1, x)
        out.append(finish(f))
    return "guardians", "dusk and dawn", out

def kraken():
    body = render("Kraken.png", 27, 10)
    water = ["~^~=~^~=~^~=~^~=~^~=~^~=~^~=~^~", "^~=~^~=~^~=~^~=~^~=~^~=~^~=~^~="]
    out = []
    seq = [0.2, 0.4, 0.6, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.8, 0.6, 0.4, 0.2]
    for t, rv in enumerate(seq):
        shown = Sprite(list(body)[:max(1, int(len(body) * rv))], body.paint)
        f = place(start(), shown, H - 1 - len(shown), 2 + (1 if 4 <= t <= 9 and t % 2 else 0))
        f = put(f, H - 1, 0, water[t % 2], "#3e8fd0")
        if 4 <= t <= 9:
            # magic balls leave the tentacle tips and arc outward
            for k, (col, d) in enumerate(((9, -1), (17, 1), (24, 1))):
                age = (t - 4 + k * 2) % 6
                r = 1 - age // 2 + 1; c = col + d * age * 2
                ch = "O" if age % 2 == 0 else "o"
                if 0 <= r < H and 0 <= c < W and f[r][c] == " ": f = put(f, r, c, ch, "#5ab8ff")
        out.append(finish(f))
    return "kraken", "rising", out

def thermy():
    out = []
    for t in range(12):
        dev = render("Thermonuclear_smoke_devil.png", 31, 12, xscale=(1.0 if t % 4 < 2 else 0.94), edge_blocks=True, eye_points=((580, 450),))
        f = blank()
        # a smoke field behind it, drifting up and right
        for k in range(18):
            r = (H - 1 - (t + k * 5) % 14); c = (k * 7 + (t + k) // 2) % W
            if 0 <= r < H:
                ch = "░" if k % 3 == 0 else "o" if k % 3 == 1 else "."
                f = put(f, r, c, ch, "#8c8c8c")
        f = place(f, dev, (H - len(dev)) // 2, (W - max(len(l) for l in dev)) // 2)
        out.append(finish(f))
    return "thermy", "smoke", out

def vetion():
    skel = render("Vet'ion_(enraged).png", 18, 12)
    out = []
    for t in range(12):
        bolt = t in (3, 4, 9, 10)
        f = centred(skel, dx=((1 if t in (4, 10) else 0)))
        if bolt:
            col = 4 if t < 6 else 26
            for r in range(0, 6):
                f = put(f, r, col + (r % 2) * (1 if t < 6 else -1), "\\|/|\\|"[r] if t < 6 else "/|\\|/|"[r], "#ffe98a")
            f = put(f, 6, col - 1 if t < 6 else col, "***", "#ffffff")
        out.append(finish(f))
    return "vetion", "lightning", out

def kbd():
    # heads and wings fill the frame; the three heads breathe from the lower left
    dragon = render("King_Black_Dragon.png", 31, 12, box=(0.0, 0.0, 0.74, 1.0), edge_blocks=True, crop_norm=False)
    out = []
    breaths = [("*", "#ff6a2a"), ("~", "#5ad04a"), ("=", "#6ad0ff")]   # fire, poison, shock
    for t in range(12):
        f = centred(dragon, dx=3)
        (ch, colour) = breaths[t // 4]; k = t % 4
        for r0 in (7, 9, 11):
            for i in range(k * 3):
                c = 3 - i; r = r0 - (i // 3)
                if 0 <= c < W and 0 <= r < H and f[r][c] == " ": f = put(f, r, c, ch, colour)
        out.append(finish(f))
    return "kbd", "three breaths", out

def kq():
    crawl = centred(render("Kalphite_Queen.png", 27, 9), dy=1); crawl = Sprite(crawl, dict(PAINT))
    fly = centred(render("Kalphite_Queen_2nd_form.png", 27, 11)); fly = Sprite(fly, dict(PAINT))
    out = []
    for t in range(5):
        out.append(finish(centred(render("Kalphite_Queen.png", 27, 9), dx=(1 if t % 2 else 0), dy=1)))
    # metamorphosis: the crawler dissolves into a chitin shimmer that resolves into the winged form
    for k in range(6):
        f = start()
        for r in range(H):
            for c in range(W):
                h = (r * 31 + c * 17 + 7) % 12
                a, b = crawl[r][c], fly[r][c]
                if k <= 2:
                    ch = a if h >= k * 4 else ("▒" if a != " " or b != " " else " ")
                else:
                    ch = b if h < (k - 2) * 4 else ("▒" if a != " " or b != " " else " ")
                f = put(f, r, c, ch, "#c8a84a" if ch == "▒" else (crawl.paint if (k <= 2 and ch == a) else fly.paint).get((r, c)))
        out.append(finish(f))
    for t in range(8):
        wings = render("Kalphite_Queen_2nd_form.png", 27, 11, xscale=(1.0 if t % 2 else 0.9))
        out.append(finish(centred(wings, dy=(0, -1, -1, 0, 0, 1, 1, 0)[t])))
    return "kq", "metamorphosis", out

def muspah():
    # a stoic shot of the face: the upper half of the melee form, breathing slowly
    out = []
    for t in range(16):
        face = render("Phantom_Muspah_(melee).png", 31, 12, box=(0.05, 0.0, 0.95, 0.52), xscale=(0.97 if 4 <= t % 16 < 8 else 1.0))
        out.append(finish(centred(face, dx=(1 if 8 <= t < 12 else 0))))
    return "muspah", "stoic", out


# ---- the group punch list (Andrew 2026-09-03: "that's our next punch list") --

def tint(sprite, colour, mix=0.55):
    """a copy of a Sprite with every cell colour pulled toward one colour"""
    tr, tg, tb = int(colour[1:3], 16), int(colour[3:5], 16), int(colour[5:7], 16)
    out = Sprite(list(sprite), {})
    for k, v in sprite.paint.items():
        r, g, bb = int(v[1:3], 16), int(v[3:5], 16), int(v[5:7], 16)
        out.paint[k] = "#%02x%02x%02x" % (int(r + (tr - r) * mix), int(g + (tg - g) * mix), int(bb + (tb - bb) * mix))
    return out

def jad():
    jad = render("TzTok-Jad.png", 31, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(jad, dy=(-1 if 3 <= t <= 6 else 0))
        # the mage attack: a rock arcs down and bursts on the floor
        age = (t + 6) % 12
        if age < 5:
            r = age * 2; c = 4 + age * 2
            if f[r][c] == " ": f = put(f, r, c, "O", "#c8c0a0")
        elif age < 7 and f[H - 1][14] == " ":
            f = put(f, H - 1, 12, "*  *" if age == 5 else " ** ", "#ff8a2a")
        out.append(finish(f))
    return "jad", "the mage rock", out

def zuk():
    zuk = render("TzKal-Zuk.png", 31, 11, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(zuk, dy=-1)
        # the glyph shield slides in front of him
        x = 2 + int(abs(6 - t) * 20 / 6)
        for i in range(7):
            if 0 <= x + i < W: f = put(f, H - 1, x + i, "═", "#6ad0ff")
        out.append(finish(f))
    return "zuk", "the glyph shield", out

def dks():
    out = []
    for sprite in ("Dagannoth_Rex.png", "Dagannoth_Prime.png", "Dagannoth_Supreme.png"):
        king = render(sprite, 31, 12, edge_blocks=True, crop_norm=False)
        for t in range(5): out.append(finish(centred(king, dx=(1 if t % 2 else 0))))
    return "dks", "three kings", out

def barrows():
    out = []
    for sprite in ("Dharok_the_Wretched.png", "Ahrim_the_Blighted.png", "Karil_the_Tainted.png"):
        bro = render(sprite, 31, 12, edge_blocks=True, crop_norm=False)
        for rv in (0.3, 0.55, 0.8, 1.0, 1.0, 1.0, 0.7, 0.4):
            shown = Sprite(list(bro)[-max(1, int(len(bro) * rv)):], {(r - (len(bro) - max(1, int(len(bro) * rv))), c): v for (r, c), v in bro.paint.items() if r >= len(bro) - max(1, int(len(bro) * rv))})
            f = place(start(), shown, H - len(shown), (W - max(len(l) for l in shown)) // 2)
            f = put(f, H - 1, 0, "_" * W, "#6a5a4a")
            out.append(finish(f))
    return "barrows", "the brothers rise", out

def moons():
    out = []
    phases = [" (  ) ", " (( ) ", " (()) ", " ( )) "]
    for i, (sprite, glow) in enumerate((("Blood_Moon.png", "#ff4a4a"), ("Blue_Moon.png", "#5ab8ff"), ("Eclipse_Moon.png", "#c8a84a"))):
        moon = render(sprite, 31, 11, edge_blocks=True, crop_norm=False)
        for t in range(5):
            f = centred(moon, dy=1)
            f = put(f, 0, 12, phases[(t + i) % 4], glow)
            out.append(finish(f))
    return "moons", "three moons", out

def tormented():
    demon = render("Tormented_Demon_(1).png", 27, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(demon)
        if t % 4 < 2:
            for r in range(1, H - 1):
                for c in (1 + (t % 2), W - 2 - (t % 2)):
                    if f[r][c] == " ": f = put(f, r, c, "(" if c < 15 else ")", "#ff8a2a")
        out.append(finish(f))
    return "tormented", "the shield", out

def gorilla():
    ape = render("Demonic_gorilla.png", 29, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(ape, dx=(1 if t % 2 else 0))
        # boulders drop from above, one every third frame
        for k in range(2):
            age = (t + k * 6) % 12
            if age < 6:
                r = age * 2; c = 5 + k * 20
                if f[r][c] == " ": f = put(f, r, c, "O", "#a09070")
        out.append(finish(f))
    return "gorilla", "boulders", out

def sire():
    sire = render("Abyssal_Sire_(phase_1).png", 31, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(sire, dx=(1 if t % 4 in (1, 2) else 0))
        for k, col in enumerate((3, 15, 27)):
            r = H - 1 - ((t + k * 4) % 8)
            if f[r][col] == " ": f = put(f, r, col, "^" if (t + k) % 2 else "'", "#9ad46a")
        out.append(finish(f))
    return "sire", "the vents", out

def sol():
    sol = render("Sol_Heredit.png", 29, 12, edge_blocks=True, crop_norm=False)
    out = []
    lunge = (0, 0, 0, 2, 4, 4, 2, 0, 0, 0, 0, 0)
    for t in range(12):
        f = centred(sol, dx=lunge[t])
        if lunge[t] == 4:
            for r in (4, 6, 8):
                if f[r][W - 2] == " ": f = put(f, r, W - 2, "*", "#ffe060")
        out.append(finish(f))
    return "sol", "the lunge", out

def hunllef():
    hun = render("Crystalline_Hunllef.png", 29, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(16):
        form = tint(hun, "#5ab8ff" if (t // 4) % 2 == 0 else "#5fd46a", 0.45)
        f = centred(form)
        a = 2 * math.pi * (t / 16)
        r = int(round(5 + 4 * math.sin(a))); c = int(round(15 + 13 * math.cos(a)))
        if 0 <= r < H and 0 <= c < W and f[r][c] == " ": f = put(f, r, c, "@", "#e8e8ff")
        out.append(finish(f))
    return "hunllef", "prayer swaps", out

def huey():
    body = render("Hueycoatl_body.png", 31, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(body, dx=(0, 1, 2, 1, 0, -1, -2, -1, 0, 1, 2, 1)[t])
        if t in (4, 10):
            col = 6 if t == 4 else 24
            for r in range(0, 5):
                if f[r][col] == " ": f = put(f, r, col, "|", "#6ad0ff")
        out.append(finish(f))
    return "huey", "the serpent", out

def nex():
    out = []
    phases = ["#9a9a9a", "#8a5adf", "#ff3a3a", "#5ab8ff", "#ffd24a"]   # smoke, shadow, blood, ice, zaros
    for t in range(20):
        nex = render("Nex.png", 31, 12, edge_blocks=True, crop_norm=False, xscale=(1.0 if t % 2 else 0.92))
        out.append(finish(centred(tint(nex, phases[t // 4], 0.5))))
    return "nex", "five phases", out

def titans():
    fire = render("Branda_the_Fire_Queen.png", 14, 11, edge_blocks=True, crop_norm=False)
    ice = render("Eldric_the_Ice_King.png", 14, 11, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = place(start(), fire, 1, 1); f = place(f, ice, 1, 16)
        hot = (t // 3) % 2 == 0
        for k in range(3):
            c = (3 if hot else 19) + k * 4
            if f[0][c] == " ": f = put(f, 0, c, "*" if (t + k) % 2 else "'", "#ff7a2a" if hot else "#9ae0ff")
        out.append(finish(f))
    return "titans", "fire and ice", out

def maggot():
    out = []
    for t in range(12):
        king = render("Maggot_King.png", 29, 11, edge_blocks=True, crop_norm=False, xscale=(1.0 if t % 4 < 2 else 0.95))
        f = centred(king, dy=-1)
        for k in range(4):
            c = (k * 8 + t * 2) % W
            if f[H - 1][c] == " ": f = put(f, H - 1, c, "~", "#e8e0b0")
        out.append(finish(f))
    return "maggot", "maggots", out

def yama():
    yama = render("Yama.png", 25, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(12):
        f = centred(yama)
        for side, col in ((0, 1), (1, W - 2)):
            hgt = ((t + side * 6) % 12)
            hgt = hgt if hgt <= 6 else 12 - hgt
            for r in range(H - hgt, H):
                ch = "^" if r == H - hgt else "|"
                if f[r][col] == " ": f = put(f, r, col, ch, "#ff5a2a" if r % 2 else "#ffb03a")
        out.append(finish(f))
    return "yama", "fire pillars", out

RECIPES = {"cerberus": cerberus, "brutus": brutus, "dbrutus": dbrutus, "madangel": madangel, "guardians": guardians, "kraken": kraken,
           "thermy": thermy, "vetion": vetion, "kbd": kbd, "kq": kq, "muspah": muspah,
           "jad": jad, "zuk": zuk, "dks": dks, "barrows": barrows, "moons": moons, "tormented": tormented, "gorilla": gorilla,
           "sire": sire, "sol": sol, "hunllef": hunllef, "huey": huey, "nex": nex, "titans": titans, "maggot": maggot, "yama": yama}
if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else "show"
    if arg == "show":
        for key, fn in RECIPES.items():
            k, name, frames = fn()
            import re
            plain = lambda fr: [re.sub(r"<[^>]*>", "", l) for l in fr]
            print("==", k, name, len(frames), "frames"); print("\n".join(plain(frames[0]))); print("-" * W); print("\n".join(plain(frames[len(frames) // 2]))); print("-" * W)
    else:
        k, name, frames = RECIPES[arg]()
        print("==" + k + " " + name); print("\n--\n".join("\n".join(f) for f in frames))
