"""Sea cannon mood, regenerated (Andrew 2026-09-04: "the cannon one is
looking low quality compared to the rest"): a block barrel on a carriage
swings up from the deck, fires with a flash and smoke, and the shot arcs
across the water to a splash. Coloured frames."""
import math, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
W, H = 31, 12
IRON, IRON_DARK, WOOD, DECK, WAVE, FOAM, FLASH, SMOKE, BALL, SPLASH = (
    "#9a9aa4", "#6a6a74", "#8a5a2a", "#a07040", "#3e8fd0", "#9ad4ff", "#ffe060", "#b8b8b8", "#3a3a40", "#9ad4ff")
PIVOT = (8, 5)      # (row, col) of the trunnion
SPRITES = pathlib.Path(__file__).parent / "sprites"
HIT = "#ff4a3a"

def shark():
    """the target off the bow: a bull shark, rendered small, facing the ship"""
    path = str(SPRITES / "Bull_shark.png")
    w, h, fg, *_ = st.load(path)
    xs = [x for y in range(h) for x in range(w) if fg[y][x]]; ys = [y for y in range(h) for x in range(w) if fg[y][x]]
    box = (min(xs) / w, min(ys) / h, (max(xs) + 1) / w, (max(ys) + 1) / h)
    paint = {}
    rows = st.hybrid(path, 11, 4, crop=box, mirror=True, edge_blocks=True, crop_norm=False, paint=paint, auto_eyes=False)
    return rows, paint
SHARK = shark()
LENGTH = 11
DECK_END = 17       # the bow: open water beyond

def frame(t, angle, fired, ball, splash, smoke, hit=0):
    rows = [" " * W for _ in range(H)]; paint = {}
    def put(r, c, text, col):
        for i, ch in enumerate(text):
            cc = c + i
            if 0 <= r < H and 0 <= cc < W:
                rows[r] = rows[r][:cc] + ch + rows[r][cc + 1:]; paint[(r, cc)] = col
    # the deck to the bow, open water beyond; the hull's edge
    for c in range(DECK_END + 1): put(9, c, "═", DECK)
    put(9, DECK_END + 1, "╗", WOOD)
    wave = "~^~=~^~=~^~=~^~=~^~=~^~=~^~=~^~"
    for c in range(DECK_END + 2, W): put(9, c, wave[(c + t + 5) % len(wave)], WAVE)
    for c in range(W): put(10, c, wave[(c + t) % len(wave)], WAVE); put(11, c, "⠈⠁ "[(c + t) % 3] if (c + t) % 3 < 2 else " ", FOAM)
    # the enemy: the shark bobs in the water off the bow, flinching when hit
    art, spaint = SHARK; top = 5 + (1 if (t // 3) % 2 else 0) + (1 if hit == 1 else 0)
    for i, line in enumerate(art):
        for j, ch in enumerate(line):
            if ch != " ": put(top + i, 20 + j, ch, HIT if hit and (i + j + t) % 2 else spaint.get((i, j)))
    # the carriage: a wooden wedge with a wheel under the trunnion
    for c in range(1, 12): put(8, c, "▄", WOOD)
    for c in range(2, 10): put(7, c, "▀" if c in (2, 9) else "▓", WOOD)
    put(8, 3, "@", IRON_DARK)
    # the barrel: three cells thick along a line from the pivot, lit on top, dark below
    pr, pc = PIVOT
    for i in range(LENGTH + 1):
        x = pc + i * math.cos(angle) * 1.15; y = pr - i * math.sin(angle) * 0.62
        r = int(round(y)); c = int(round(x))
        tip = i == LENGTH
        put(r, c, "▐" if tip else "█", IRON)
        put(r, c + 1, "▐" if tip else "█", IRON_DARK)
        put(r - 1, c + (1 if angle > 0.35 else 0), "▀" if tip else "▓", IRON)
        if i > 1 and not tip and r + 1 <= 8: put(r + 1, c - (1 if angle > 0.35 else 0), "▄", IRON_DARK)
    put(pr, pc + 1, "@", IRON_DARK)
    mr = int(round(pr - LENGTH * math.sin(angle) * 0.62)); mc = int(round(pc + LENGTH * math.cos(angle) * 1.15))
    if fired:
        for (dr, dc, ch) in ((0, 2, "*"), (-1, 2, "*"), (0, 3, "*"), (-1, 3, "'"), (1, 2, "'")):
            put(mr + dr, mc + dc, ch, FLASH)
    for (r, c, ch) in smoke: put(r, c, ch, SMOKE)
    if ball: put(ball[0], ball[1], "@", BALL)
    if splash:
        for (dr, dc, ch) in ((0, 0, "*"), (-1, -1, "*"), (-1, 1, "*"), (-2, 0, "'"), (0, -2, "'"), (0, 2, "'")):
            put(6 + dr - (1 if splash > 1 else 0), 25 + dc, ch, FLASH if splash == 1 else SMOKE)
    if hit:
        # the hitsplat, drawn last so nothing overwrites the number: red brackets, a white number, rising
        r = 3 - (hit - 1)
        put(r - 1, 18, "▄▄▄", HIT)
        put(r, 18, "▐", HIT); put(r, 19, "6", "#ffffff"); put(r, 20, "▌", HIT)
        put(r + 1, 18, "▀▀▀", HIT)
    for r in rows: assert len(r) == W
    return st.emit(rows, paint)

def build():
    out = []
    angles = [0.0, 0.15, 0.3, 0.45, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.45, 0.3, 0.15]
    for t, a in enumerate(angles):
        fired = t == 5
        ball = None; splash = 0; smoke = []
        if 5 <= t <= 11:
            k = t - 5
            # a parabola from the muzzle to the splash column
            c = 18 + int(k * 1.5); r = int(round(3 - 2.4 * math.sin(math.pi * k / 6) + k * 0.7))
            ball = (max(0, r), min(W - 1, c)) if k < 5 else None
            if k >= 5: splash = k - 4
        if 5 <= t <= 10:
            age = t - 5
            for j in range(3):
                smoke.append((2 - age // 2 + j % 2, 17 + age + j * 2, "oO."[(age + j) % 3]))
        out.append(frame(t, a, fired, ball, splash, smoke, hit=(t - 9 if 10 <= t <= 12 else 0)))
    return out

frames = build()
if __name__ == "__main__":
    import re
    if "show" in sys.argv:
        for t in (2, 5, 8, 11):
            print("\n".join(re.sub(r"<[^>]*>", "", l) for l in frames[t])); print("-" * W)
    else:
        print("==sea cannon"); print("\n--\n".join("\n".join(f) for f in frames))
