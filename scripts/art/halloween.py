"""Halloween moods, Oct 15-31 (Andrew 2026-10-24: "a halloween themed loading
screen that can override the main loadout lab guy" ... "it should have both"
- Halloween AND the lab): the flask on its legs is in every mood - in a witch
hat beside a jack-o'-lantern, meeting a ghost, and boiling over a fire.
    python3 scripts/art/halloween.py <hat|ghost|brew>   prints one pool (==halloween <name> + frames)
    python3 scripts/art/halloween.py show               plain previews, two frames a mood
Splice: python3 scripts/art/splice.py halloween:hat halloween:ghost halloween:brew"""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
W, H = 31, 12

GLASS, LIQUID, BUBBLE = "#7fd48a", "#ffd23a", "#fff6c0"
BREW, BREW_BUBBLE = "#b070e8", "#e8d0ff"
HOT, HOT_BUBBLE = "#ff8c1a", "#ffe08a"
HAT, BAND = "#4a2a6a", "#ff8c1a"
PUMPKIN, RIM, STEM, GLOW, AMBER = "#ff8c1a", "#c85f0a", "#3f8a4a", "#ffd23a", "#c89020"
GHOST, GHOST_SHADE, EYE = "#eeeef6", "#b8b8cc", "#202030"
FIRE, FIRE_HOT, EMBER = "#ff8c1a", "#ffd23a", "#8a3a10"
BAT, STAR, GROUND = "#8a6ab0", "#d8d8e8", "#505050"

# the lab flask, 16 wide x 12 tall (the land mood's own frames, colour by glyph)
FLASK = [
    "     ╔════╗     ",
    "     ╠════╣     ",
    "     ║    ║     ",
    "    ╔╝    ╚╗    ",
    "   ╔╝▂▂▁▁▂▂╚╗   ",
    "  ╔╝▒▒▒▒▒▒▒▒╚╗  ",
    " ╔╝▓▓▓▓▓▓▓▓▓▓╚╗ ",
    "╔╝████████████╚╗",
    "╚═╦═╦══════╦═╦═╝",
    " ╔╝█║     ╔╝█║  ",
    " ╚╗█║═╗   ╚╗█║═╗",
    "  ╚═══╝    ╚═══╝",
]
# bubbles rising through the liquid, three phases (row, col) inside the flask
BUBBLES = [((7, 6, "."), (6, 11, "o"), (5, 4, ":")), ((6, 6, "o"), (5, 11, "."), (7, 12, ":")), ((5, 6, "."), (7, 11, "o"), (6, 4, "O"))]


class Frame:
    def __init__(self):
        self.rows = [" " * W for _ in range(H)]
        self.paint = {}

    def put(self, r, c, text, col):
        for i, ch in enumerate(text):
            cc = c + i
            if 0 <= r < H and 0 <= cc < W and ch != " ":
                self.rows[r] = self.rows[r][:cc] + ch + self.rows[r][cc + 1:]
                self.paint[(r, cc)] = col

    def flask(self, dx, liquid, bubble, phase, surface=None, top=True):
        """the flask with its left edge at dx; surface replaces the liquid's
        top row glyphs; top=False leaves the neck's first two rows for a hat"""
        for r, line in enumerate(FLASK):
            if not top and r < 2:
                continue
            if r == 4 and surface:
                line = line[:5] + surface + line[11:]
            for c, ch in enumerate(line):
                if ch == " ":
                    continue
                self.put(r, dx + c, ch, LIQUID_GLYPHS(ch, liquid))
        for (r, c, ch) in BUBBLES[phase % 3]:
            self.put(r, dx + c, ch, bubble)

    def emit(self):
        for r in self.rows:
            assert len(r) == W
        return st.emit(self.rows, self.paint)


def LIQUID_GLYPHS(ch, liquid):
    return liquid if ch in "▂▁▒▓█" else GLASS


def bats(f, t, cols=(19, 24, 28), row=0):
    for i, c in enumerate(cols):
        wing = "v^v" if (t + i) % 2 == 0 else "^v^"
        f.put(row + (i % 2), c + (t % 3) - 1, wing, BAT)


def hat_mood():
    """the flask in a witch hat beside a jack-o'-lantern; the lantern flickers, bats cross"""
    out = []
    for t in range(6):
        f = Frame()
        dx = 1
        f.flask(dx, BREW, BREW_BUBBLE, t, top=False)
        # the hat over the neck: a cone, then the brim with its orange band
        f.put(0, dx + 5, " ▄██▄ ", HAT)
        f.put(1, dx + 2, "▀▀▀", HAT); f.put(1, dx + 5, "██████", BAND); f.put(1, dx + 11, "▀▀▀", HAT)
        # the jack-o'-lantern on the ground to the right
        px = 20
        lit = t % 2 == 0
        glow = GLOW if lit else AMBER
        f.put(6, px + 4, "█", STEM)
        f.put(7, px + 1, "▄███████▄", RIM)
        f.put(8, px, "▐", RIM); f.put(8, px + 1, "██", PUMPKIN); f.put(8, px + 3, "▀", glow); f.put(8, px + 4, "█", PUMPKIN)
        f.put(8, px + 5, "▀", glow); f.put(8, px + 6, "██", PUMPKIN); f.put(8, px + 8, "▌", RIM)
        f.put(9, px, "▐", RIM); f.put(9, px + 1, "███████", PUMPKIN); f.put(9, px + 8, "▌", RIM)
        f.put(10, px, "▐", RIM); f.put(10, px + 1, "█", PUMPKIN); f.put(10, px + 2, "▀████▀", glow); f.put(10, px + 7, "█", PUMPKIN); f.put(10, px + 8, "▌", RIM)
        f.put(11, px + 1, "▀▀▀▀▀▀▀", RIM)
        bats(f, t, cols=(19, 25), row=0)
        f.put(3 - (t % 2), 29, "*", STAR)
        out.append(f.emit())
    return out


def ghost_mood():
    """a ghost drifts up beside the flask and says boo; the brew sloshes"""
    out = []
    for t in range(6):
        f = Frame()
        slosh = "▂▂▁▁▂▂" if t % 2 == 0 else "▁▂▂▂▁▁"
        f.flask(0, LIQUID, BUBBLE, t, surface=slosh)
        gx = 20
        top = 4 - (t % 3)            # the ghost bobs
        f.put(top, gx + 1, " ▄█████▄ ", GHOST)
        f.put(top + 1, gx, "▐", GHOST_SHADE); f.put(top + 1, gx + 1, "█", GHOST); f.put(top + 1, gx + 2, "█", EYE)
        f.put(top + 1, gx + 3, "███", GHOST); f.put(top + 1, gx + 6, "█", EYE); f.put(top + 1, gx + 7, "█", GHOST); f.put(top + 1, gx + 8, "▌", GHOST_SHADE)
        f.put(top + 2, gx, "▐████", GHOST); f.put(top + 2, gx + 4, "▄" if t >= 3 else "█", EYE if t >= 3 else GHOST); f.put(top + 2, gx + 5, "████▌", GHOST)
        f.put(top + 3, gx, "▐█████████▌", GHOST)
        f.put(top + 4, gx, " ▀ █▀ ▀█ ▀ ", GHOST_SHADE)
        if t >= 3:
            f.put(top - 1 if top > 0 else 0, gx + 2, "Boo!", GHOST)
        f.put(1, 18, "*" if t % 2 else ".", STAR)
        out.append(f.emit())
    return out


def brew_mood():
    """the flask boils over a fire; bubbles climb the neck and burst out"""
    out = []
    for t in range(6):
        f = Frame()
        dx = 7
        f.flask(dx, HOT, HOT_BUBBLE, t)
        # bubbles climbing the neck's interior (rows 1-3, cols dx+6..dx+9)
        for k, (r, c) in enumerate(((3, 7), (2, 8), (1, 6))):
            if (t + k) % 3 != 2:
                f.put(r - (t % 2 if k == 0 else 0), dx + c, "oO."[(t + k) % 3], HOT_BUBBLE)
        # the fire between the legs (three cells wide there), licking up to the base
        flame = ["▄█▄", "▄██", "██▄"][t % 3]
        f.put(10, dx + 7, flame, FIRE)
        f.put(9, dx + 6 + (t % 3), "^" if t % 2 else "'", FIRE_HOT)
        f.put(11, dx + 7, "▄██▄", EMBER)
        f.put(11, dx + 8, "██", FIRE if t % 2 else FIRE_HOT)
        # the ground either side of the fire
        f.put(11, 0, "░░", GROUND); f.put(11, W - 2, "░░", GROUND)
        bats(f, t, cols=(24, 28), row=1)
        f.put(2 + (t % 2), 26, "*", STAR)
        out.append(f.emit())
    return out


MOODS = {"hat": hat_mood, "ghost": ghost_mood, "brew": brew_mood}

if __name__ == "__main__":
    import re
    arg = sys.argv[1] if len(sys.argv) > 1 else "show"
    if arg == "show":
        for name, build in MOODS.items():
            frames = build()
            print("==", name, len(frames), "frames")
            for t in (0, 3):
                print("\n".join("|" + re.sub(r"<[^>]*>", "", l) + "|" for l in frames[t])); print("-" * (W + 2))
    else:
        frames = MOODS[arg]()
        print("==halloween " + arg); print("\n--\n".join("\n".join(f) for f in frames))
