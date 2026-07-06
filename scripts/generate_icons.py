#!/usr/bin/env python3
"""Generate the plugin icons. Stdlib only - no Pillow required.

Outputs (paths relative to the repo root):
  src/main/resources/com/loadoutlab/icon.png   16x16 sidebar icon. RuneLite's
      ClientUI resizes every NavigationButton icon to 16x16 (TAB_SIZE), so the
      art is authored as an exact 16x16 pixel grid - no resampling blur.
  icon.png                                     48x72 Plugin Hub listing icon
      (the hub's documented maximum size; filename + repo-root location are
      what the hub README requires).

Theme: gear. A great helm in the panel's selected-item green
(140,200,140) with a gold four-point star - the best-in-slot find. The
T-slit is knocked out to the background so the helm silhouette reads at
any size. The sidebar icon is frameless pixel art on transparency; the
hub icon sets the same helm on a dark card a step above RuneLite's
DARK_GRAY (#28282d) with gold sparkles.

Usage:
  python3 scripts/generate_icons.py [--preview DIR]

--preview also writes 8x nearest-neighbour upscales to DIR for eyeballing.
"""

import math
import os
import struct
import sys
import zlib

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------- palette

CARD = (45, 45, 52, 255)       # card fill - slightly lighter than #28282d
EDGE = (64, 64, 74, 255)       # card border
GREEN = (140, 200, 140, 255)   # helm - the panel's selected-item green
GOLD = (208, 178, 102, 255)    # star + sparkles

# ---------------------------------------------------------------- png writer


def write_png(path, width, height, rows):
    """rows: list of `height` lists of `width` (r,g,b,a) tuples."""
    raw = b"".join(
        b"\x00" + b"".join(bytes(px) for px in row) for row in rows
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(
            ">I", zlib.crc32(body) & 0xFFFFFFFF
        )

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# ------------------------------------------------- 16x16 sidebar pixel grid

SIDEBAR_PALETTE = {
    ".": (0, 0, 0, 0),
    "G": GREEN,
    "y": GOLD,
}

# Potion with legs: a gold-corked, HOLLOW green bottle - dark glass
# interior with golden potion pooled at the bottom and one bubble
# rising - standing on two chunky L's as its little boots. Bottle = the
# Lab, LL legs = Loadout Lab, gold = the best-in-slot loot; gold star
# sparkles beside the cork.
SIDEBAR_GRID = [
    ".......yy....y..",
    ".......yy...yyy.",
    "......GGGG...y..",
    ".......GG.......",
    ".....GGGGGG.....",
    "....G..y...G....",
    "....G......G....",
    "....GyyyyyyG....",
    "....GyyyyyyG....",
    "....GGGGGGGG....",
    ".....GG..GG.....",
    ".....GG..GG.....",
    ".....GG..GG.....",
    ".....GGG.GGG....",
    ".....GGG.GGG....",
    "................",
]


def render_grid(grid, palette):
    assert all(len(row) == len(grid[0]) for row in grid)
    return [[palette[ch] for ch in row] for row in grid]


# ------------------------------------------------- 48x72 hub icon (vector-ish)

HUB_W, HUB_H = 48, 72


def rounded_rect(x, y, cx, cy, hw, hh, radius, inset):
    qx = max(abs(x - cx) - (hw - radius), 0.0)
    qy = max(abs(y - cy) - (hh - radius), 0.0)
    return math.hypot(qx, qy) <= radius - inset


def diamond(x, y, cx, cy, r):
    """Four-point sparkle."""
    return abs(x - cx) + abs(y - cy) <= r


# The hub icon is the dark card plus the SAME sidebar pixel grid pasted at
# 3x (48x48, vertically centred) - one source of truth for the mark, and
# the stepped pixel silhouette reads "helmet" far better than a smooth
# vector dome did. Sparkles fill the free corners.
HUB_LAYERS = [
    (lambda x, y: rounded_rect(x, y, 24, 36, 24, 36, 8, 0), EDGE),
    (lambda x, y: rounded_rect(x, y, 24, 36, 24, 36, 8, 1.5), CARD),
    (lambda x, y: diamond(x, y, 8.0, 9.5, 1.8), GOLD),
    (lambda x, y: diamond(x, y, 39.5, 64.0, 1.6), GOLD),
    (lambda x, y: diamond(x, y, 9.0, 62.0, 1.3), GOLD),
]

GRID_SCALE = 3
GRID_Y = (HUB_H - 16 * GRID_SCALE) // 2  # vertical centring on the card


def paste_grid(card_rows, grid_rows):
    """Overlay non-transparent upscaled grid pixels onto the card."""
    for gy, row in enumerate(grid_rows):
        for gx, px in enumerate(row):
            if px[3] > 0:
                card_rows[GRID_Y + gy][gx] = px
    return card_rows


def sample(layers, x, y):
    for test, color in reversed(layers):
        if test(x, y):
            return color
    return None


def render_layers(width, height, layers, subsamples=4):
    offsets = [(i + 0.5) / subsamples for i in range(subsamples)]
    total = subsamples * subsamples
    rows = []
    for py in range(height):
        row = []
        for px in range(width):
            r = g = b = hits = 0
            for oy in offsets:
                for ox in offsets:
                    c = sample(layers, px + ox, py + oy)
                    if c is not None:
                        r += c[0]
                        g += c[1]
                        b += c[2]
                        hits += 1
            if hits == 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((
                    round(r / hits),
                    round(g / hits),
                    round(b / hits),
                    round(255 * hits / total),
                ))
        rows.append(row)
    return rows


# ---------------------------------------------------------------- main


def upscale(rows, factor):
    return [
        [px for px in row for _ in range(factor)]
        for row in rows for _ in range(factor)
    ]


def main():
    preview_dir = None
    if "--preview" in sys.argv:
        preview_dir = sys.argv[sys.argv.index("--preview") + 1]

    sidebar = render_grid(SIDEBAR_GRID, SIDEBAR_PALETTE)
    sidebar_path = os.path.join(
        REPO_ROOT, "src/main/resources/com/loadoutlab/icon.png")
    write_png(sidebar_path, 16, 16, sidebar)
    print(f"wrote {sidebar_path} (16x16)")

    hub = render_layers(HUB_W, HUB_H, HUB_LAYERS)
    hub = paste_grid(hub, upscale(sidebar, GRID_SCALE))
    assert HUB_W <= 48 and HUB_H <= 72, "Plugin Hub cap is 48x72"
    hub_path = os.path.join(REPO_ROOT, "icon.png")
    write_png(hub_path, HUB_W, HUB_H, hub)
    print(f"wrote {hub_path} ({HUB_W}x{HUB_H})")

    if preview_dir:
        write_png(os.path.join(preview_dir, "sidebar_x8.png"),
                  16 * 8, 16 * 8, upscale(sidebar, 8))
        write_png(os.path.join(preview_dir, "hub_x8.png"),
                  HUB_W * 8, HUB_H * 8, upscale(hub, 8))
        print(f"previews in {preview_dir}")


if __name__ == "__main__":
    main()
