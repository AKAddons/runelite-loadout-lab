"""Theatre of Blood mood (Andrew 2026-09-04: "tob now feels pedestrian ...
something more spectacular"): Verzik through her three phases - on the
throne raining magic, flying with lightning and nylocas swarming in, then
the final form at full size under orbiting tornadoes over blood pools.
Coloured frames (HTML runs)."""
import math, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
import bosses as B
W, H = 31, 12
PURPLE, MAGIC, BOLT, BLOOD, NYLO, TORNADO, FLASH = "#8a5adf", "#c090ff", "#e8d0ff", "#c8202a", "#5fd46a", "#ffe060", "#ffffff"

def phase_one():
    throne = B.render("Verzik_Vitur.png", 29, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(8):
        f = B.centred(throne)
        # magic rains from her hands toward the floor
        for k in range(5):
            age = (t + k * 2) % 8
            r = age + 2; c = 3 + k * 6 + (age % 2)
            if r < H and f[r][c] == " ": f = B.put(f, r, c, "*" if age % 2 else "'", MAGIC)
        out.append(B.finish(f))
    return out

def phase_two():
    fly = B.render("Verzik_Vitur_(flying).png", 25, 10, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(8):
        f = B.centred(fly, dy=(-1, -1, 0, 0, 1, 1, 0, 0)[t], dx=(0, 1, 1, 0, -1, -1, 0, 0)[t])
        # purple lightning from above, nylocas crawl in along the floor
        if t % 4 in (1, 2):
            col = 4 if t < 4 else 26
            for r in range(0, 5):
                if f[r][col + (r % 2)] == " ": f = B.put(f, r, col + (r % 2), "|", BOLT)
        for k in range(4):
            c = (k * 8 + t * 2) % W
            if f[H - 1][c] == " ": f = B.put(f, H - 1, c, "o", NYLO)
        out.append(B.finish(f))
    return out

def phase_three():
    final = B.render("Verzik_Vitur_(final_form).png", 31, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(16):
        f = B.centred(final, dx=(1 if t % 8 in (3, 4) else 0))
        # blood pools on the floor
        for c in (2, 3, 4, 12, 13, 26, 27, 28):
            if f[H - 1][c] == " ": f = B.put(f, H - 1, c, "▒" if (c + t) % 3 else "░", BLOOD)
        # three tornadoes orbit her
        for k in range(3):
            a = 2 * math.pi * ((t + k * 5) / 16)
            r = int(round(5 + 4.5 * math.sin(a))); c = int(round(15 + 13.5 * math.cos(a)))
            if 0 <= r < H and 0 <= c < W and f[r][c] == " ": f = B.put(f, r, c, "@", TORNADO)
            if 0 <= r + 1 < H and 0 <= c < W and f[r + 1][c] == " ": f = B.put(f, r + 1, c, "'", TORNADO)
        out.append(B.finish(f))
    return out

def flash():
    f = B.start()
    for r in range(H):
        f = B.put(f, r, 0, ("*" * 31) if r % 2 else (" *" * 15 + " "), FLASH)
    return [B.finish(f)]

frames = phase_one() + flash() + phase_two() + flash() + phase_three()
if __name__ == "__main__":
    import re
    if "show" in sys.argv:
        for t in (0, 12, 22):
            print("\n".join(re.sub(r"<[^>]*>", "", l) for l in frames[t])); print("-" * W)
    else:
        print("==tob verzik"); print("\n--\n".join("\n".join(f) for f in frames))
