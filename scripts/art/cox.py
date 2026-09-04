"""Chambers of Xeric mood (Andrew 2026-09-04: "cox needs the same treatment
as tob with some cuts and freeze frames. i'd love a zoom cut to olm's
fucked up right hand"): a wide shot of the Great Olm with the crystals
glinting, a hard cut to the right claw held in close-up as it flexes and
drips, a cut to the head, and back out. Coloured frames."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
import bosses as B
W, H = 31, 12
CRYSTAL, ACID, FLASH = "#b8ff3a", "#8fd44a", "#ffffff"
SPRITE = "Great_Olm.png"
HAND = (0.50, 0.66, 0.80, 1.0)     # image fractions: the right claw, bottom right
HEAD = (0.19, 0.02, 0.47, 0.70)    # the head and neck, top left

def wide():
    olm = B.render(SPRITE, 31, 12, edge_blocks=True, crop_norm=False)
    out = []
    for t in range(8):
        f = B.centred(olm)
        # the crystals on the rocks glint in turn
        for k, (r, c) in enumerate(((0, 14), (1, 22), (2, 27), (0, 26), (3, 9))):
            if (t + k) % 4 == 0 and f[r][c] != " ": f = B.put(f, r, c, "*", CRYSTAL)
        out.append(B.finish(f))
    return out

def flash():
    f = B.start()
    for r in range(H):
        f = B.put(f, r, 0, ("*" * 31) if r % 2 else (" *" * 15 + " "), FLASH)
    return [B.finish(f)]

# the region-fraction crops need the image box, not the foreground box: patch render's box handling
def render_image_box(sprite, cols, rows, box, **kw):
    path = B.A / sprite
    w, h, *_ = st.load(str(path))
    x0, y0, x1, y1 = box
    pw, ph = (x1 - x0) * w, (y1 - y0) * h
    scale = min(cols * 2 / pw, rows * 4 / ph)
    c = max(1, int(pw * scale / 2)); r = max(1, int(ph * scale / 4))
    paint = {}
    rows_ = st.hybrid(str(path), c, r, crop=box, paint=paint, **kw)
    return B.Sprite(rows_, paint)

def hand():
    out = []
    for t in range(12):
        claw = render_image_box(SPRITE, 31, 12, HAND, edge_blocks=True, crop_norm=True, bg_dark=0.38, xscale=(1.0 if t % 6 < 3 else 0.94))
        f = B.centred(claw, dy=(1 if 3 <= t % 6 <= 4 else 0))
        for k in range(3):
            age = (t + k * 4) % 12
            if age < 5:
                r = 7 + age; c = 6 + k * 10
                if r < H and f[r][c] == " ": f = B.put(f, r, c, "~" if age % 2 else "'", ACID)
        out.append(B.finish(f))
    return out

def head():
    out = []
    for t in range(8):
        face = render_image_box(SPRITE, 31, 12, HEAD, edge_blocks=True, crop_norm=False, eye_points=((298, 133), (252, 160)), xscale=(1.0 if t < 4 else 0.96))
        out.append(B.finish(B.centred(face, dx=(1 if t >= 4 else 0))))
    return out

frames = wide() + flash() + hand() + flash() + head() + flash()
if __name__ == "__main__":
    import re
    if "show" in sys.argv:
        for t in (0, 11, 24):
            print("\n".join(re.sub(r"<[^>]*>", "", l) for l in frames[t])); print("-" * W)
    else:
        print("==cox olm"); print("\n--\n".join("\n".join(f) for f in frames))
