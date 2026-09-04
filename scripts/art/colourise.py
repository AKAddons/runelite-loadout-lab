"""Colour the hand-drawn and legacy pools of loader_frames.txt by glyph class
(the sprite-rendered boss pools carry their own pixel colours already).
    python3 scripts/art/colourise.py            rewrites every rule-covered pool in place
    python3 scripts/art/colourise.py show sea   prints one coloured frame's markup
Rules: per pool key (or the land flask ''), a list of (chars, colour) - the
first rule whose chars contain the glyph wins; unlisted glyphs keep the
loader's accent. Frames already carrying markup are left alone."""
import pathlib, re, sys
RES = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/com/loadoutlab/render/loader_frames.txt"
WATER, FOAM, HULL, DECK, SAIL, SKY, STONE, FIRE, ORB, GLASS, LIQUID, BUBBLE, IRON, SMOKE, LEG = (
    "#3e8fd0", "#9ad4ff", "#8a5a2a", "#a07040", "#e8e8e0", "#c8d8e8", "#9a9a8a", "#ff8a2a", "#c090ff", "#7fd48a", "#ffd23a", "#fff6c0", "#8c8c94", "#b0b0b0", "#7fd48a")
RULES = {
    "": [("╔╗╚╝║═╦╩", GLASS), ("▁▂▃", LIQUID), ("▒▓█", LIQUID), ("oO.:", BUBBLE)],
    "sea": [("~^=", WATER), ("⠈⠁", FOAM), ("▓", HULL), ("▒", DECK), ("║╦╚╝╔╗", HULL), ("═", DECK),
            ("⣿⣶⣷⣾⣽⣻⣟⣯⡆⡄⣄⣆⢀⣠⣴⡀⢠⣤⣼", SAIL), ("v-", SKY), ("@()/_", IRON), ("*", FIRE), ("o", IRON), ("░", SMOKE)],
    "toa": [("/\\|_", STONE), ("*", ORB), ("~^=()'.", WATER), ("@o", ORB)],
    "tob": [("░▒▓█", "#9a5adf"), ("≡", "#c89aff"), ("¥", "#7a3fbf"), ("@", "#ffe060")],
    "cox": [("░▒▓█", "#5fb36a"), ("≡", "#8fd49a"), ("¥", "#3f8a4a"), ("@", "#ffe060"), ("⣿⣷⣯⣟⣽⣾", "#5fb36a")],
    "zulrah": [("░▒▓█", "#5fb36a"), ("≡", "#8fd49a"), ("¥", "#3f8a4a"), ("@", "#ffe060"), ("~^=", WATER)],
}
def colour_row(line, rules):
    html = ""; cur = None
    for ch in line:
        col = None
        if ch != " ":
            for chars, c in rules:
                if ch in chars: col = c; break
        if col != cur:
            if cur is not None: html += "</font>"
            if col is not None: html += "<font color=" + col + ">"
            cur = col
        html += ch.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    if cur is not None: html += "</font>"
    return html
def run(show=None):
    s = RES.read_text(encoding="utf-8")
    out = []; key = None; shown = False
    for line in s.split("\n"):
        if line.startswith("=="):
            name = line[2:].strip(); key = name.split(" ")[0] if name else ""
            out.append(line); continue
        if line == "--" or line.startswith("#") or "<font" in line or key not in RULES:
            out.append(line); continue
        out.append(colour_row(line, RULES[key]))
    text = "\n".join(out)
    if show is not None:
        a = text.index("==" + show); b = text.find("\n--\n", a)
        print(text[a:b]); return
    RES.write_text(text, encoding="utf-8")
    print("coloured pools:", ", ".join(k or "flask" for k in RULES))
if __name__ == "__main__":
    run(show=sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == "show" else None)
