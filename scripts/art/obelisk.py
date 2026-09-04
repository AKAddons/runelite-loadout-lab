"""ToA obelisk mood: the Wardens flank the obelisk (Andrew 2026-09-03:
"could feature the wardens on each side"); floor waves run from each
Warden into the obelisk on an off-beat, each landing charges it a step,
and an orb circles the shaft. Coloured frames (HTML runs)."""
import math, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
A = pathlib.Path(__file__).parent / "sprites"
W, H = 31, 12
STONE, WAVE, ORB, CHARGE, FLOOR = "#9a9a8a", "#3e8fd0", "#ff3a3a", "#ff5a3a", "#6a6a60"
OB = [
    "              /\\               ",
    "             /  \\              ",
    "             |  |              ",
    "             |  |              ",
    "             |  |              ",
    "             |  |              ",
    "             |  |              ",
    "             |  |              ",
    "             |  |              ",
    "             |  |              ",
    "              /\\               ",
    "____________/____\\_____________",
]
PERIOD, FRAMES = 12, 24

def warden(sprite, mirror):
    paint = {}
    w, h, fg, *_ = st.load(str(A / sprite))
    xs = [x for y in range(h) for x in range(w) if fg[y][x]]; ys = [y for y in range(h) for x in range(w) if fg[y][x]]
    box = (min(xs) / w, min(ys) / h, (max(xs) + 1) / w, (max(ys) + 1) / h)
    rows = st.hybrid(str(A / sprite), 9, 9, crop=box, mirror=mirror, edge_blocks=True, crop_norm=False, paint=paint, auto_eyes=False)
    return rows, paint

TUM = warden("Tumeken's_Warden_(level-489).png", False)
ELI = warden("Elidinis'_Warden_(level-489).png", True)

def frame(t):
    rows = list(OB); paint = {}
    for r, line in enumerate(rows):
        for c, ch in enumerate(line):
            if ch != " ": paint[(r, c)] = FLOOR if r == H - 1 else STONE
    def put(r, c, ch, col):
        nonlocal rows
        if 0 <= r < H and 0 <= c < W:
            rows[r] = rows[r][:c] + ch + rows[r][c + 1:]; paint[(r, c)] = col
    # the Wardens, standing on the floor to each side
    for (art, pmap), left in ((TUM, 1), (ELI, 21)):
        for i, line in enumerate(art):
            for j, ch in enumerate(line):
                if ch != " " and rows[2 + i][left + j] == " ": put(2 + i, left + j, ch, pmap.get((i, j)) or STONE)
    fill = min(7, (t // (PERIOD // 2)) % 8)
    for k in range(fill):
        put(9 - k, 14, "*", CHARGE); put(9 - k, 15, "*", CHARGE)
    floor = list("~=~^~=~^~=~^~ /\\ ~^~=~^~=~^~=~ ")
    lt = t % PERIOD; rt = (t + PERIOD // 2) % PERIOD
    lx = int(lt / (PERIOD - 1) * 12); rx = 30 - int(rt / (PERIOD - 1) * 12)
    for x, d in ((lx, 1), (rx, -1)):
        crest = "((" if d == 1 else "))"
        for i, ch in enumerate(crest):
            xx = x - i * d
            if 0 <= xx < W and floor[xx] not in "/\\ ": floor[xx] = ch
        if rows[9][x] == " ": put(9, x, "'" if (t // 2) % 2 == 0 else ".", WAVE)
    rows[10] = "".join(floor)
    for c, ch in enumerate(rows[10]):
        if ch not in " /\\": paint[(10, c)] = WAVE
    a = 2 * math.pi * (t / FRAMES)
    ox = int(round(14.5 + 11 * math.cos(a))); oy = int(round(5 + 3.5 * math.sin(a)))
    if not (12 <= ox <= 17):
        put(oy, ox, "@", ORB)
        tail = a - 0.35
        tx = int(round(14.5 + 11 * math.cos(tail))); ty = int(round(5 + 3.5 * math.sin(tail)))
        if 0 <= ty < H and 0 <= tx < W and rows[ty][tx] == " ": put(ty, tx, "o", ORB)
    for r in rows:
        assert len(r) == W, (len(r), r)
    return st.emit(rows, paint)

frames = [frame(t) for t in range(FRAMES)]
if __name__ == "__main__":
    import re
    if "show" in sys.argv:
        for t in (0, 6):
            print("\n".join(re.sub(r"<[^>]*>", "", l) for l in frames[t])); print("-" * W)
    else:
        print("==toa obelisk"); print("\n--\n".join("\n".join(f) for f in frames))
