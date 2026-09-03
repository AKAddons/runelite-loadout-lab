"""Sprite -> text art for the compute-animation moods (pure Python, no
Pillow). Decodes an 8-bit PNG (RGB, RGBA or palette, non-interlaced),
masks the flattened wiki background by exact colour, and renders a crop
as Braille dithering with keyboard strokes on the silhouette and '@'
eyes on the brightest specks. Palette: ASCII, Braille U+2800-28FF,
blocks U+2580-259F, box doubles U+2550-256C, U+2261, U+00A5 (the
AsciiLoaderTest pin)."""
import struct, zlib

def decode(path):
    d = open(path, "rb").read(); assert d[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8; idat = b""; plte = None; trns = None
    while pos < len(d):
        n = struct.unpack(">I", d[pos:pos + 4])[0]; kind = d[pos + 4:pos + 8]; body = d[pos + 8:pos + 8 + n]
        if kind == b"IHDR": w, h, bd, ct, _, _, il = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE": plte = body
        elif kind == b"tRNS": trns = body
        elif kind == b"IDAT": idat += body
        pos += 12 + n
    assert bd == 8 and il == 0, (bd, il)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat); stride = w * ch; rows = []; prev = bytearray(stride); p = 0
    for _ in range(h):
        f = raw[p]; line = bytearray(raw[p + 1:p + 1 + stride]); p += 1 + stride
        for i in range(stride):
            a = line[i - ch] if i >= ch else 0; b = prev[i]; c = prev[i - ch] if i >= ch else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line)); prev = line
    px = []
    for line in rows:
        out = []
        for x in range(w):
            if ct == 3:
                i = line[x]; r, g, b = plte[3 * i:3 * i + 3]; a = trns[i] if trns and i < len(trns) else 255
            elif ct == 2: r, g, b = line[3 * x:3 * x + 3]; a = 255
            elif ct == 6: r, g, b, a = line[4 * x:4 * x + 4]
            elif ct == 0: r = g = b = line[x]; a = 255
            else: r = g = b = line[2 * x]; a = line[2 * x + 1]
            out.append((r, g, b, a))
        px.append(out)
    return w, h, px

_CACHE = {}
def load(path):
    if path not in _CACHE:
        w, h, px = decode(path)
        bg = px[0][0][:3]
        fg = [[(p[3] > 0 and p[:3] != bg) for p in row] for row in px]
        lum = [[(0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]) for p in row] for row in px]
        # colour class: green specks (eyes, mouth, crystals) read as accents
        green = [[(p[1] > 70 and p[1] > 1.25 * p[0] and p[1] > 1.4 * p[2]) for p in row] for row in px]
        vals = sorted(lum[y][x] for y in range(h) for x in range(w) if fg[y][x])
        lo = vals[len(vals) // 20] if vals else 0; hi = vals[-max(1, len(vals) // 20)] if vals else 1
        _CACHE[path] = (w, h, fg, lum, lo, max(hi, lo + 1), green)
    return _CACHE[path]

BRAILLE = [[0x01, 0x08], [0x02, 0x10], [0x04, 0x20], [0x40, 0x80]]
# ordered dither thresholds per dot (2 wide x 4 tall): eight levels
THRESH = [[0.30, 0.62], [0.78, 0.46], [0.54, 0.86], [0.70, 0.38]]

def render(path, cols, rows, crop=(0, 0, 1, 1), xscale=1.0, mirror=False, eyes=True, eye_lum=0.93, dx0=0, eye_points=()):
    """One frame: the crop (fractions x0,y0,x1,y1) fitted to cols x rows
    cells of 2x4 dots. xscale < 1 squashes the width (a turning head),
    dx0 shifts the drawing right. Silhouette cells get keyboard strokes
    by edge direction, interior cells Braille dither by luminance, eyes
    '@' where the sprite's brightest specks are."""
    w, h, fg, lum, lo, hi, green = load(path)
    x0, y0, x1, y1 = int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)
    cw, chh = x1 - x0, y1 - y0
    used = max(1, int(round(cols * xscale))); off = (cols - used) // 2 + dx0
    grid = [[" "] * cols for _ in range(rows)]
    def src(ax, ay, dx=1, dy=2):
        sx = x0 + int((ax * 2 + dx) / (used * 2) * cw); sy = y0 + int((ay * 4 + dy) / (rows * 4) * chh)
        if mirror: sx = x0 + x1 - 1 - (sx - x0) - 0
        return sx, sy
    def greenfrac(ax, ay):
        """share of the cell's pixel block that is green (all pixels, not the 8 dots)"""
        if ax < 0 or ay < 0 or ax >= used or ay >= rows: return 0.0
        xa, ya = src(ax, ay, 0, 0); xb, yb = src(ax, ay, 2, 4)
        if mirror: xa, xb = xb, xa
        n = 0; g = 0
        for sy in range(max(0, ya), min(h, yb)):
            for sx in range(max(0, xa), min(w, xb)):
                if fg[sy][sx]:
                    n += 1
                    if green[sy][sx]: g += 1
        return g / n if n else 0.0
    def greencell(ax, ay):
        return greenfrac(ax, ay) >= 0.25
    def filled(ax, ay):
        if ax < 0 or ay < 0 or ax >= used or ay >= rows: return False
        sx, sy = src(ax, ay); return 0 <= sx < w and 0 <= sy < h and fg[sy][sx]
    for cy in range(rows):
        for cx in range(used):
            dots = 0; n = 0; light = 0.0; g = 0
            for dy in range(4):
                for dx in range(2):
                    sx, sy = src(cx, cy, dx + 0.5, dy + 0.5)
                    if 0 <= sx < w and 0 <= sy < h and fg[sy][sx]:
                        n += 1; l = (lum[sy][sx] - lo) / (hi - lo); light += l
                        if green[sy][sx]: g += 1
                        # pure white keeps a sparse weave so the shading around it reads
                        if l > THRESH[dy][dx] and (l < 0.93 or (dx + dy) % 2 == 0): dots |= BRAILLE[dy][dx]
            if n == 0: continue
            up, down, left, right = filled(cx, cy - 1), filled(cx, cy + 1), filled(cx - 1, cy), filled(cx + 1, cy)
            ch = None
            xa, ya = src(cx, cy, 0, 0); xb, yb = src(cx, cy, 2, 4)
            if mirror: xa, xb = xb, xa
            if eyes and any(xa <= ex < xb and ya <= ey < yb for ex, ey in eye_points):
                ch = "@"   # an eye pinned by sprite coordinate
            elif greenfrac(cx, cy) >= 0.3:
                ch = "="   # the green mouth / crystal
            elif n < 3: ch = "'" if not up else "." if not down else "-"
            elif not up and not down: ch = "="
            elif not left and not right: ch = "|"
            elif not up and not left: ch = "/"
            elif not up and not right: ch = "\\"
            elif not down and not left: ch = "\\"
            elif not down and not right: ch = "/"
            elif not up: ch = "-"
            elif not down: ch = "_"
            elif not left or not right: ch = "|"
            if ch is None:
                ch = chr(0x2800 + dots) if dots else " "
            if 0 <= off + cx < cols: grid[cy][off + cx] = ch
    return ["".join(r) for r in grid]

if __name__ == "__main__":
    import sys
    for line in render(sys.argv[1], 31, 12, crop=(0.12, 0.0, 0.44, 0.52)): print(line)
    print("-" * 31)
    for line in render(sys.argv[1], 31, 12, crop=(0.0, 0.0, 1.0, 1.0)): print(line)
