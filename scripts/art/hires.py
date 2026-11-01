"""Hi-res moods (43x24 painted cells, one file per pool in render/moods/, loaded on
demand). Recipes live in hires_<batch>.py, each with a RECIPES list; the kit and the
art bar are hires_kit.py and HIRES.md.
    python scripts/art/hires.py                 every recipe, writes the resources
    python scripts/art/hires.py zulrah kbd      only those pool keys
    python scripts/art/hires.py sheet zulrah    build/art/zulrah.png: eight frames, double size
Needs Pillow and numpy (dev only; nothing here ships)."""
import importlib, pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from hires_kit import *

def recipes():
    out = []
    for f in sorted(pathlib.Path(__file__).parent.glob("hires_*.py")):
        if f.stem != "hires_kit": out += importlib.import_module(f.stem).RECIPES
    return out

def build(fn):
    made = fn()                                   # a recipe may name its own length: raids run long
    key, name, scene = made[:3]
    frames = made[3] if len(made) > 3 else FRAMES
    prev, cells = None, []
    for i in list(range(frames)) + [0]:           # a lap in hand, so the glyph hold bridges the wrap
        best, col = asciify(scene(i / frames), prev); prev = best; cells.append((best, col))
    colour = rim_colour(key, cells[1:])
    return key, name, [framed(b, c, colour) for b, c in cells[1:]]

def write(moods):
    """One file per key; a key may hold several moods."""
    OUT.mkdir(exist_ok=True)
    for key, ms in moods.items():
        text = "".join("==" + key + " " + name + "\n" + "\n--\n".join("\n".join(emit(b, c)) for b, c in cells) + "\n" for name, cells in ms)
        (OUT / (key + ".txt")).write_text(text, encoding="utf-8", newline="\n")
        print(key, len(ms), "moods", len(text.encode()) // 1024, "KB")

def sheet(key):
    d = ROOT / "build/art"; d.mkdir(parents=True, exist_ok=True)
    built = [b for b in map(build, [f for f in recipes() if f.__name__.split("_")[0] == key])]
    assert built, key
    pick = range(0, FRAMES, FRAMES // 8)
    o = Image.new("RGB", (4 * (W + 8), len(built) * 2 * (H + 8)), (20, 20, 20))
    for m, (_, _, cells) in enumerate(built):
        for n, i in enumerate(pick):
            o.paste(Image.fromarray(draw(*cells[i])), ((n % 4) * (W + 8), (m * 2 + n // 4) * (H + 8)))
    o.resize((o.width * 2, o.height * 2), Image.NEAREST).save(d / (key + ".png"))
    print(d / (key + ".png"))

if __name__ == "__main__":
    args = sys.argv[1:]
    if args[:1] == ["sheet"]:
        for k in args[1:]: sheet(k)
    else:
        moods = {}
        for fn in recipes():
            if not args or fn.__name__.split("_")[0] in args:
                key, name, cells = build(fn); moods.setdefault(key, []).append((name, cells))
        write(moods)
