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

def put(rows, r, c, text):
    if not (0 <= r < H): return rows
    rows = list(rows); line = rows[r].ljust(W)
    for i, ch in enumerate(text):
        cc = c + i
        if 0 <= cc < W: line = line[:cc] + ch + line[cc + 1:]
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
    return st.hybrid(str(path), c, r, crop=(x0, y0, x1, y1), **kw)

def place(canvas, frame, top, left):
    for i, line in enumerate(frame):
        rr = top + i
        if 0 <= rr < H:
            for j, ch in enumerate(line):
                cc = left + j
                if ch != " " and 0 <= cc < W:
                    canvas = put(canvas, rr, cc, ch)
    return canvas

def centred(frame, dx=0, dy=0):
    rows = len(frame); cols = max(len(l) for l in frame)
    return place(blank(), frame, (H - rows) // 2 + dy, (W - cols) // 2 + dx)

# ---- recipes ---------------------------------------------------------------
def cerberus():
    # the three heads: the top-left of the image; eyes pinned (yellow, not red)
    heads = render("Cerberus.png", 31, 12, box=(0.0, 0.0, 0.58, 0.52), eye_points=((520, 385), (650, 385), (190, 450), (55, 290)), auto_eyes=False, edge_blocks=True)
    base = centred(heads, dy=0)
    out = []
    # the flame front: height in rows, 0 = clear, 12 = engulfed; rises, holds, falls
    heights = [0, 0, 2, 4, 6, 8, 10, 12, 12, 12, 10, 8, 6, 4, 2, 0, 0, 0]
    for t, hgt in enumerate(heights):
        f = [r for r in base]
        # a flame skyline: every column burns to its own height, tips flicker
        for c in range(W):
            tall = hgt + ((c * 5 + t * 3) % 4) - 2 if hgt > 0 else 0
            for r in range(max(0, H - tall), H):
                depth = H - r
                ch = "▓" if depth <= 2 else "▒" if depth <= 4 else "░" if depth <= tall - 2 else ("^" if (c + t) % 2 else "*")
                f[r] = f[r][:c] + ch + f[r][c + 1:]
        out.append(ok(f))
    return "cerberus", "three heads in the fire", out

def brutus():
    # the face: horns, the red eye, the snout - tossing its head and snorting
    out = []
    for t in range(12):
        head = render("Brutus.png", 31, 12, edge_blocks=True, auto_eyes=True)
        f = centred(head, dy=(-1 if t % 6 in (2, 3) else 0), dx=(1 if t % 6 in (3, 4) else 0))
        if t % 6 in (4, 5):
            for k, ch in enumerate("~~"):
                r = 8 + k; c = 2 - k - (t % 2)
                if 0 <= c < W and f[r][c] == " ": f = put(f, r, c, ch)
        out.append(ok(f))
    return "brutus", "snort", out

def madangel():
    angel = render("Mad_Angel.png", 24, 12, box=(0, 0, 1, 0.82))
    out = []
    for t in range(12):
        dy = (0, 0, -1, -1, -1, 0, 0, 1, 1, 1, 0, 0)[t]
        f = centred(angel, dy=dy)
        # debris floats under her, out of phase with the hover
        for k, col in enumerate((11, 15, 19)):
            r = H - 1 - ((t // 2 + k) % 3)
            if f[r][col] == " ": f = put(f, r, col, "^" if k % 2 else "'")
        out.append(ok(f))
    return "madangel", "hover", out

def guardians():
    dusk = render("Dusk.png", 16, 9); dawn = render("Dawn.png", 14, 5)
    out = []
    for t in range(16):
        f = place(blank(), dusk, 3, 1 + (1 if (t // 4) % 2 else 0))
        # Dawn crosses the sky right to left, then comes back
        x = 30 - int(t * 44 / 15) if t < 8 else -14 + int((t - 8) * 44 / 7)
        f = place(f, dawn, 0 if t % 2 else 1, x)
        out.append(ok(f))
    return "guardians", "dusk and dawn", out

def kraken():
    body = render("Kraken.png", 27, 10)
    water = ["~^~=~^~=~^~=~^~=~^~=~^~=~^~=~^~", "^~=~^~=~^~=~^~=~^~=~^~=~^~=~^~="]
    out = []
    seq = [0.2, 0.4, 0.6, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.8, 0.6, 0.4, 0.2]
    for t, rv in enumerate(seq):
        shown = [l for l in body][:max(1, int(len(body) * rv))]
        f = place(blank(), shown, H - 1 - len(shown), 2 + (1 if 4 <= t <= 9 and t % 2 else 0))
        f = put(f, H - 1, 0, water[t % 2])
        if 4 <= t <= 9:
            # magic balls leave the tentacle tips and arc outward
            for k, (col, d) in enumerate(((9, -1), (17, 1), (24, 1))):
                age = (t - 4 + k * 2) % 6
                r = 1 - age // 2 + 1; c = col + d * age * 2
                ch = "O" if age % 2 == 0 else "o"
                if 0 <= r < H and 0 <= c < W and f[r][c] == " ": f = put(f, r, c, ch)
        out.append(ok(f))
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
                f = put(f, r, c, ch)
        f = place(f, dev, (H - len(dev)) // 2, (W - max(len(l) for l in dev)) // 2)
        out.append(ok(f))
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
                f = put(f, r, col + (r % 2) * (1 if t < 6 else -1), "\\|/|\\|"[r] if t < 6 else "/|\\|/|"[r])
            f = put(f, 6, col - 1 if t < 6 else col, "***")
        out.append(ok(f))
    return "vetion", "lightning", out

def kbd():
    # heads and wings fill the frame; the three heads breathe from the lower left
    dragon = render("King_Black_Dragon.png", 31, 12, box=(0.0, 0.0, 0.74, 1.0), edge_blocks=True, crop_norm=False)
    out = []
    breaths = ["*", "~", "="]   # fire, poison, shock
    for t in range(12):
        f = centred(dragon, dx=3)
        ch = breaths[t // 4]; k = t % 4
        for r0 in (7, 9, 11):
            for i in range(k * 3):
                c = 3 - i; r = r0 - (i // 3)
                if 0 <= c < W and 0 <= r < H and f[r][c] == " ": f = put(f, r, c, ch)
        out.append(ok(f))
    return "kbd", "three breaths", out

def kq():
    crawl = centred(render("Kalphite_Queen.png", 27, 9), dy=1); fly = centred(render("Kalphite_Queen_2nd_form.png", 27, 11))
    out = []
    for t in range(5):
        out.append(ok(centred(render("Kalphite_Queen.png", 27, 9), dx=(1 if t % 2 else 0), dy=1)))
    # metamorphosis: the crawler dissolves into a chitin shimmer that resolves into the winged form
    for k in range(6):
        f = blank()
        for r in range(H):
            line = ""
            for c in range(W):
                h = (r * 31 + c * 17 + 7) % 12
                a, b = crawl[r][c], fly[r][c]
                if k <= 2:
                    line += a if h >= k * 4 else ("▒" if a != " " or b != " " else " ")
                else:
                    line += b if h < (k - 2) * 4 else ("▒" if a != " " or b != " " else " ")
            f[r] = line
        out.append(ok(f))
    for t in range(8):
        wings = render("Kalphite_Queen_2nd_form.png", 27, 11, xscale=(1.0 if t % 2 else 0.9))
        out.append(ok(centred(wings, dy=(0, -1, -1, 0, 0, 1, 1, 0)[t])))
    return "kq", "metamorphosis", out

def muspah():
    # a stoic shot of the face: the upper half of the melee form, breathing slowly
    out = []
    for t in range(16):
        face = render("Phantom_Muspah_(melee).png", 31, 12, box=(0.05, 0.0, 0.95, 0.52), xscale=(0.97 if 4 <= t % 16 < 8 else 1.0))
        out.append(ok(centred(face, dx=(1 if 8 <= t < 12 else 0))))
    return "muspah", "stoic", out

RECIPES = {"cerberus": cerberus, "brutus": brutus, "madangel": madangel, "guardians": guardians, "kraken": kraken,
           "thermy": thermy, "vetion": vetion, "kbd": kbd, "kq": kq, "muspah": muspah}
if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else "show"
    if arg == "show":
        for key, fn in RECIPES.items():
            k, name, frames = fn()
            print("==", k, name, len(frames), "frames"); print("\n".join(frames[0])); print("-" * W); print("\n".join(frames[len(frames) // 2])); print("-" * W)
    else:
        k, name, frames = RECIPES[arg]()
        print("==" + k + " " + name); print("\n--\n".join("\n".join(f) for f in frames))
