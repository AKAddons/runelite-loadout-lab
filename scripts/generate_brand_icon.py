#!/usr/bin/env python3
"""Generate the AKAddons brand mark: a blue-enamel engraved "AK" (Big Caslon,
interlocked) on a brushed-steel plate stamped with a topographic-contour
landscape and a cluster of meshing gears.

Writes a self-contained SVG page to ./akmetal.html; render it to PNG with
headless Chrome (needs the macOS 'Big Caslon' font):

  python3 generate_brand_icon.py
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \\
    --headless=new --force-device-scale-factor=2 --default-background-color=00000000 \\
    --screenshot=akaddons.png --window-size=512,512 "file://$PWD/akmetal.html"

Tunables: BUMPS (terrain peaks/basins), NLEVELS (contour density), GEARS
(cog cluster), the engrave/steel filters, and the AK transforms in HTML.
"""
import math

# Peaks (+) and basins (-) of the height field: (x, y, amplitude, sigma).
BUMPS = [(150, 180, 1.0, 120), (382, 342, -0.9, 145), (300, 108, 0.62, 95),
         (112, 412, 0.72, 110), (432, 150, -0.55, 100), (250, 300, 0.4, 80)]
NLEVELS = 13
# Meshing cogs: (cx, cy, r_tip, r_valley, teeth, hub_r)
GEARS = [(176, 336, 94, 75, 14, 27), (322, 250, 66, 52, 10, 20),
         (312, 402, 52, 40, 8, 16), (420, 348, 60, 47, 9, 18)]


def gear_elements():
    out = []
    for cx, cy, ro, ri, teeth, hub in GEARS:
        steps = teeth * 20
        pts = []
        for k in range(steps + 1):
            th = 2 * math.pi * k / steps
            frac = ((th * teeth) / (2 * math.pi)) % 1.0
            if frac < 0.40:
                r = ro
            elif frac < 0.50:
                r = ri + (ro - ri) * (0.50 - frac) / 0.10
            elif frac < 0.90:
                r = ri
            else:
                r = ri + (ro - ri) * (frac - 0.90) / 0.10
            pts.append((cx + r * math.cos(th), cy + r * math.sin(th)))
        out.append('<path d="M' + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + ' Z"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{hub:.1f}"/>')
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{hub * 0.42:.1f}"/>')
    return out


def _height(x, y):
    v = 0.35 * math.sin(x * 0.02 + 0.5) * math.cos(y * 0.018 - 0.3)
    for bx, by, amp, sig in BUMPS:
        v += amp * math.exp(-((x - bx) ** 2 + (y - by) ** 2) / (2 * sig * sig))
    return v


def field_paths():
    """Topographic contours of a height field via marching squares."""
    x0, y0, h = -12.0, -12.0, 6.5
    nx = int((536 - x0) / h) + 1
    ny = int((536 - y0) / h) + 1
    xs = [x0 + i * h for i in range(nx)]
    ys = [y0 + j * h for j in range(ny)]
    F = [[_height(xs[i], ys[j]) for j in range(ny)] for i in range(nx)]
    lo = min(min(c) for c in F)
    hi = max(max(c) for c in F)
    levels = [lo + (hi - lo) * (k + 0.5) / NLEVELS for k in range(NLEVELS)]

    def crs(pa, va, pb, vb, L):
        t = (L - va) / (vb - va)
        return (pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t)

    d = []
    for i in range(nx - 1):
        for j in range(ny - 1):
            p00, v00 = (xs[i], ys[j]), F[i][j]
            p10, v10 = (xs[i + 1], ys[j]), F[i + 1][j]
            p11, v11 = (xs[i + 1], ys[j + 1]), F[i + 1][j + 1]
            p01, v01 = (xs[i], ys[j + 1]), F[i][j + 1]
            for L in levels:
                pts = []
                if (v00 - L) * (v10 - L) < 0: pts.append(crs(p00, v00, p10, v10, L))
                if (v10 - L) * (v11 - L) < 0: pts.append(crs(p10, v10, p11, v11, L))
                if (v01 - L) * (v11 - L) < 0: pts.append(crs(p01, v01, p11, v11, L))
                if (v00 - L) * (v01 - L) < 0: pts.append(crs(p00, v00, p01, v01, L))
                if len(pts) == 2:
                    d.append(f"M{pts[0][0]:.1f},{pts[0][1]:.1f}L{pts[1][0]:.1f},{pts[1][1]:.1f}")
                elif len(pts) == 4:
                    d.append(f"M{pts[0][0]:.1f},{pts[0][1]:.1f}L{pts[1][0]:.1f},{pts[1][1]:.1f}")
                    d.append(f"M{pts[2][0]:.1f},{pts[2][1]:.1f}L{pts[3][0]:.1f},{pts[3][1]:.1f}")
    return [f'<path d="{"".join(d)}"/>'] + gear_elements()


PATHS = "\n      ".join(field_paths())

HTML = f'''<div style="width:512px;height:512px">
<svg width="512" height="512" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="sheet" x1="0" y1="0" x2="0.15" y2="1">
      <stop offset="0" stop-color="#c3c6cb"/><stop offset="0.45" stop-color="#a0a4aa"/>
      <stop offset="0.55" stop-color="#969ba2"/><stop offset="1" stop-color="#7a7f85"/>
    </linearGradient>
    <g id="field" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
      {PATHS}
    </g>
    <filter id="stampRough" x="-5%" y="-5%" width="110%" height="110%">
      <feTurbulence type="fractalNoise" baseFrequency="0.05" numOctaves="2" seed="9" result="t"/>
      <feDisplacementMap in="SourceGraphic" in2="t" scale="2.2" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <filter id="roughen" x="-20%" y="-20%" width="140%" height="140%">
      <feTurbulence type="fractalNoise" baseFrequency="0.045 0.07" numOctaves="2" seed="11" result="t"/>
      <feDisplacementMap in="SourceGraphic" in2="t" scale="3.6" xChannelSelector="R" yChannelSelector="G"/>
    </filter>
    <filter id="engrave" x="-25%" y="-25%" width="150%" height="150%">
      <feFlood flood-color="#25599f" result="fc"/>
      <feComposite in="fc" in2="SourceAlpha" operator="in" result="face"/>
      <feComponentTransfer in="SourceAlpha" result="inv"><feFuncA type="table" tableValues="1 0"/></feComponentTransfer>
      <feGaussianBlur in="inv" stdDeviation="1.9" result="invb"/>
      <feOffset in="invb" dx="3" dy="3" result="sMask"/>
      <feFlood flood-color="#0e1116" flood-opacity="0.92" result="sCol"/>
      <feComposite in="sCol" in2="sMask" operator="in" result="s0"/>
      <feComposite in="s0" in2="SourceAlpha" operator="in" result="innerShadow"/>
      <feOffset in="invb" dx="-3" dy="-3" result="hMask"/>
      <feFlood flood-color="#cfe2ff" flood-opacity="0.7" result="hCol"/>
      <feComposite in="hCol" in2="hMask" operator="in" result="h0"/>
      <feComposite in="h0" in2="SourceAlpha" operator="in" result="innerHi"/>
      <feMerge><feMergeNode in="face"/><feMergeNode in="innerHi"/><feMergeNode in="innerShadow"/></feMerge>
    </filter>
    <filter id="brush"><feTurbulence type="fractalNoise" baseFrequency="0.004 0.55" numOctaves="2" seed="7"/><feColorMatrix type="saturate" values="0"/></filter>
    <filter id="mottle"><feTurbulence type="fractalNoise" baseFrequency="0.013" numOctaves="3" seed="21"/><feColorMatrix type="saturate" values="0"/></filter>
    <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency="0.8" numOctaves="2" seed="4"/><feColorMatrix type="saturate" values="0"/></filter>
  </defs>
  <rect width="512" height="512" fill="url(#sheet)"/>
  <rect width="512" height="512" filter="url(#mottle)" style="mix-blend-mode:overlay" opacity="0.6"/>
  <rect width="512" height="512" filter="url(#brush)" style="mix-blend-mode:soft-light" opacity="0.85"/>
  <g filter="url(#stampRough)">
    <g color="#181c22" transform="translate(-0.8,-0.8)" opacity="0.5" style="mix-blend-mode:multiply"><use href="#field"/></g>
    <g color="#ffffff" transform="translate(0.9,0.9)" opacity="0.6" style="mix-blend-mode:screen"><use href="#field"/></g>
  </g>
  <g filter="url(#engrave)">
    <g filter="url(#roughen)">
      <g transform="translate(6,-52)" font-family="'Big Caslon',serif" font-weight="700"
         font-size="236" text-anchor="start" fill="#000">
        <text x="64" y="300">A</text>
        <text x="240" y="300" transform="translate(-85,172)">K</text>
      </g>
    </g>
  </g>
  <rect width="512" height="512" filter="url(#grain)" style="mix-blend-mode:overlay" opacity="0.55"/>
</svg>
</div>'''

with open("akmetal.html", "w") as f:
    f.write(HTML)
print("wrote akmetal.html")
