"""The Fractured Archive mood (Andrew 2026-10-20: "we need to also make an
animation for TFA"): the two bosses every run meets. The Archive Guardian
floats over its spores while its shield drains, then crashes to the floor;
Ocellus fires orange and blue bolts from a corner while his tendrils
spread, then closes the raid in his last form over void pools. Coloured
frames (HTML runs)."""
import math, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import bosses as B
W, H = 31, 12
SHIELD, SPORE, DUST, FLASH = "#5ad0e0", "#7fd44a", "#b0a090", "#ffffff"
ORANGE, BLUE, TENDRIL, VOID = "#ff9a3a", "#4a8aff", "#5fd46a", "#9a4ae0"

def guardian():
    skull = B.render("Archive_Guardian.png", 25, 10, edge_blocks=True, crop_norm=False, auto_eyes=False)
    out = []
    for t in range(12):
        crashing = t >= 8
        dy = 1 if crashing else (-1, -1, 0, 0)[t % 4]
        f = B.centred(skull, dy=dy)
        if not crashing:
            # the shield bar drains a notch a frame across the top row
            full = 8 - t
            bar = "[" + "=" * full + " " * (8 - full) + "]"
            f = B.put(f, 0, 11, bar, SHIELD)
            # spores pop up and burst along the floor
            for k in range(4):
                c = (3 + k * 8 + t * 3) % W
                if f[H - 1][c] == " ": f = B.put(f, H - 1, c, "o" if (t + k) % 3 else "*", SPORE)
        else:
            # the crash: dust kicks out along the floor
            k = t - 8
            for c in (13 - k * 3, 17 + k * 3):
                if 0 <= c < W and f[H - 1][c] == " ": f = B.put(f, H - 1, c, "_." [k % 2] if k else "_", DUST)
        out.append(B.finish(f))
    return out

def ocellus_bolts():
    p1 = B.render("Ocellus.png", 23, 11, edge_blocks=True, crop_norm=False, auto_eyes=False)
    out = []
    for t in range(14):
        f = B.centred(p1, dx=-4)
        # bolts fly out to the right, orange and blue in turn
        for k in range(3):
            age = (t + k * 4) % 12
            c = 20 + age; r = 3 + k * 3
            if c < W and f[r][c] == " ": f = B.put(f, r, c, "*" if k % 2 else "o", ORANGE if k % 2 else BLUE)
        # tendrils spread across the floor, one more a frame, seven in all
        for k in range(min(7, t // 2 + 1)):
            c = 2 + k * 4
            if f[H - 1][c] == " ": f = B.put(f, H - 1, c, "~", TENDRIL)
        out.append(B.finish(f))
    return out

def ocellus_void():
    p3 = B.render("Ocellus_(phase_3).png", 29, 12, edge_blocks=True, crop_norm=False, auto_eyes=False)
    out = []
    pools = [2, 27, 6, 23, 10, 19]
    for t in range(12):
        f = B.centred(p3, dx=(1 if t % 6 in (2, 3) else 0))
        # void pools spread on the floor and stay
        for c in pools[:t // 2 + 1]:
            for cc in (c, c + 1):
                if f[H - 1][cc] == " ": f = B.put(f, H - 1, cc, "▒" if (cc + t) % 3 else "░", VOID)
        # the spread shot: three void bolts fan out from the hands
        if t % 4 in (1, 2):
            spread = t % 4
            for dc, ch in ((-1, "\\"), (0, "|"), (1, "/")):
                r = 1 + spread; c = 15 + dc * (3 + spread * 3)
                if 0 <= c < W and f[r][c] == " ": f = B.put(f, r, c, ch, VOID)
        out.append(B.finish(f))
    return out

def flash():
    f = B.start()
    for r in range(H):
        f = B.put(f, r, 0, ("*" * 31) if r % 2 else (" *" * 15 + " "), FLASH)
    return [B.finish(f)]

frames = guardian() + flash() + ocellus_bolts() + flash() + ocellus_void()
if __name__ == "__main__":
    import re
    if "show" in sys.argv:
        for t in (2, 10, 16, 24, 33):
            print("\n".join(re.sub(r"<[^>]*>", "", l) for l in frames[t])); print("-" * W)
    else:
        print("==tfa archive"); print("\n--\n".join("\n".join(f) for f in frames))
