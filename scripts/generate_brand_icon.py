#!/usr/bin/env python3
"""Render the AKAddons monogram avatar: a serif "AK" whose terminals
bloom into comets - a bright head with a tapering gold tail streaking
outward - over a cosmic gradient and starfield. 512x512 PNG, pure
stdlib (zlib writer + analytic distance/glow/comet math), no deps.

    python3 scripts/generate_brand_icon.py [out.png]

Tunables: gradient (BG_*), palette (LETTER / COMET / STAR), the letter
STROKES, and the COMET_TERMINALS the tails stream from.
"""
import math
import struct
import sys
import zlib

SIZE = 512

BG_INNER = (46, 40, 84)      # indigo core of the sky
BG_OUTER = (10, 11, 26)      # deep navy at the edges
LETTER = (250, 245, 232)     # warm cream letterforms
COMET = (255, 214, 150)      # warm gold comet light
STAR = (238, 242, 255)       # cool starfield
STROKE = 18.0                # half-width of the letter strokes
TAIL_LEN = 92.0              # comet tail length (px)

Y_TOP, Y_BOT = 156.0, 356.0
Y_MID = (Y_TOP + Y_BOT) / 2
STROKES = [
    (112, Y_BOT, 179, Y_TOP),   # A left leg
    (179, Y_TOP, 246, Y_BOT),   # A right leg
    (130, 304, 228, 304),       # A crossbar
    (302, Y_TOP, 302, Y_BOT),   # K stem
    (302, Y_MID, 402, Y_TOP),   # K upper arm
    (302, Y_MID, 408, Y_BOT),   # K lower leg
]

# Terminals whose serifs become comets; tails stream radially outward.
# Third value scales tail length - the upper terminals lead, baseline trails.
COMET_TERMINALS = [
    (112, Y_BOT, 0.78), (246, Y_BOT, 0.78), (179, Y_TOP, 1.4),
    (302, Y_TOP, 1.4), (302, Y_BOT, 0.78), (402, Y_TOP, 1.4), (408, Y_BOT, 0.9),
]
CX, CY = 256.0, 256.0


def _dirs():
    out = []
    for tx, ty, scale in COMET_TERMINALS:
        dx, dy = tx - CX, ty - CY
        n = math.hypot(dx, dy) or 1.0
        out.append((tx, ty, dx / n, dy / n, TAIL_LEN * scale))
    return out


COMETS = _dirs()


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def comet_value(px, py):
    """Brightness [0,1+] of every comet head+tail at a pixel."""
    total = 0.0
    for hx, hy, dx, dy, tlen in COMETS:
        vx, vy = px - hx, py - hy
        d2 = vx * vx + vy * vy
        tip = 1.6 * math.exp(-d2 / (2 * 3.4 ** 2))   # hot pinpoint at the serif
        head = 0.7 * math.exp(-d2 / (2 * 7.0 ** 2))  # tight coma
        along = vx * dx + vy * dy
        tail = 0.0
        if 0.0 < along < tlen:
            perp = abs(vx * -dy + vy * dx)
            frac = along / tlen
            w = 1.8 + 5.0 * frac                     # narrow at head, flaring out
            fall = (1.0 - frac) ** 2.2               # and fading to nothing
            tail = 1.25 * fall * math.exp(-(perp * perp) / (2 * w * w))
        total += tip + head + tail
    return total


STARS = [(74, 96, 3.2), (255, 52, 2.4), (150, 116, 1.8), (466, 210, 2.6),
         (56, 250, 2.0), (92, 420, 3.0), (446, 432, 2.4), (386, 300, 1.7),
         (34, 168, 1.7), (488, 336, 1.7), (210, 470, 1.7), (330, 60, 1.6)]


def star_value(px, py):
    total = 0.0
    for sx, sy, s in STARS:
        dx, dy = px - sx, py - sy
        total += math.exp(-(dx * dx + dy * dy) / (2 * (s * 0.6) ** 2))
    return total


def lerp(a, b, t):
    return [a[i] + (b[i] - a[i]) * t for i in range(3)]


def screen(base, add, amt):
    return [min(255.0, base[i] + add[i] * amt) for i in range(3)]


def render():
    maxd = math.hypot(CX, CY)
    rows = []
    for y in range(SIZE):
        row = bytearray()
        py = y + 0.5
        for x in range(SIZE):
            px = x + 0.5
            t = min(1.0, math.hypot(px - CX, py - CY) / maxd)
            col = lerp(BG_INNER, BG_OUTER, t ** 1.3)
            sv = star_value(px, py)
            if sv > 0.0:
                col = screen(col, STAR, min(1.0, sv))
            cv = comet_value(px, py)
            if cv > 0.0:
                col = screen(col, COMET, min(1.0, cv))
            d = min(seg_dist(px, py, *s) for s in STROKES) - STROKE
            col = screen(col, COMET, math.exp(-max(0.0, d) / 13.0) * 0.18)
            core = max(0.0, min(1.0, (0.0 - d) / 1.4 + 0.5))
            col = lerp(col, LETTER, core)
            row += bytes(int(round(max(0.0, min(255.0, c)))) for c in col) + b"\xff"
        rows.append(row)
    return rows


def write_png(path, rows):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))
    raw = bytearray()
    for row in rows:
        raw += b"\x00" + row
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/akaddons-icon.png"
    write_png(out, render())
    print("wrote", out)
