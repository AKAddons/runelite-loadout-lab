"""ToA obelisk mood (Andrew 2026-09-03): floor waves run from each edge
INTO the obelisk on an off-beat - left lands, then right, then left -
each landing charges the obelisk one step, and a big orb circles it."""
import math, sys
W, H = 31, 12
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
LEFT = range(0, 13)     # floor cells left of the base
RIGHT = range(18, 31)   # floor cells right of the base
PERIOD = 12             # a wave takes PERIOD ticks to cross its side
FRAMES = 24

def put(rows, r, c, ch):
    if 0 <= r < H and 0 <= c < W:
        rows[r] = rows[r][:c] + ch + rows[r][c + 1:]

def frame(t):
    rows = list(OB)
    # charge: a landing every PERIOD/2 ticks fills the column bottom-up
    landings = t // (PERIOD // 2)
    fill = min(7, landings % 8)
    for k in range(fill):
        put(rows, 9 - k, 14, "*"); put(rows, 9 - k, 15, "*")
    # the floor row: calm sea glyphs, then the wave crest travelling inward
    floor = list("~=~^~=~^~=~^~ /\\ ~^~=~^~=~^~=~ ")
    lt = t % PERIOD                      # left wave phase
    rt = (t + PERIOD // 2) % PERIOD      # right wave, half a period behind
    lx = int(lt / (PERIOD - 1) * (len(LEFT) - 1))            # 0 .. 12, moving right
    rx = 30 - int(rt / (PERIOD - 1) * (len(RIGHT) - 1))     # 30 .. 18, moving left
    for x, d in ((lx, 1), (rx, -1)):
        crest = "((" if d == 1 else "))"
        for i, ch in enumerate(crest):
            xx = x - i * d
            if 0 <= xx < W and floor[xx] not in "/\\ ":
                floor[xx] = ch
        # spray above the crest
        put(rows, 9, x, "'" if (t // 2) % 2 == 0 else ".")
    rows[10] = "".join(floor)
    # the orb: an ellipse around the shaft, behind it on the far side
    a = 2 * math.pi * (t / FRAMES)
    ox = int(round(14.5 + 11 * math.cos(a))); oy = int(round(5 + 3.5 * math.sin(a)))
    behind = 12 <= ox <= 17  # the shaft hides it either way
    if not behind:
        put(rows, oy, ox, "@")
        tail = a - 0.35
        tx = int(round(14.5 + 11 * math.cos(tail))); ty = int(round(5 + 3.5 * math.sin(tail)))
        if rows[ty][tx] == " ": put(rows, ty, tx, "o")
    for r in rows:
        assert len(r) == W, (len(r), r)
    return rows

frames = [frame(t) for t in range(FRAMES)]
if __name__ == "__main__":
    if "show" in sys.argv:
        for t in (0, 3, 6, 9):
            print("\n".join(frames[t])); print("-" * W)
    else:
        print("==toa obelisk"); print("\n--\n".join("\n".join(f) for f in frames))
