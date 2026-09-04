"""Colour the hand-drawn and legacy pools of loader_frames.txt by glyph class
(the sprite-rendered boss pools carry their own pixel colours already).
    python3 scripts/art/colourise.py            rewrites every rule-covered pool in place (idempotent)
    python3 scripts/art/colourise.py show "sea kraken" [frame]
Rules: per pool key, or per full mood name (wins over the key), a list of
(chars, colour, region) - region = None, or (r0, r1, c0, c1, f0, f1) bounds
on row, column and frame index. The first matching rule wins; unlisted
glyphs keep the loader's accent."""
import pathlib, re, sys
RES = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/com/loadoutlab/render/loader_frames.txt"
WATER, FOAM, HULL, DECK, SAIL, SKY, STONE, FIRE, GLASS, LIQUID, BUBBLE, IRON, SMOKE = (
    "#3e8fd0", "#9ad4ff", "#8a5a2a", "#a07040", "#e8e8e0", "#c8d8e8", "#9a9a8a", "#ff8a2a", "#7fd48a", "#ffd23a", "#fff6c0", "#8c8c94", "#b0b0b0")
RED, KRAKEN, OLM_PALE, OLM_MID, OLM_DARK, EYE = "#ff3a3a", "#3fb0a0", "#e6ece6", "#a8c8b0", "#3a7a48", "#ffe060"
SERP, MAGMA, TANZ = "#5fb36a", "#ff5a2a", "#4a9aff"
SEA = [("~^=", WATER, None), ("⠈⠁", FOAM, None), ("▓", HULL, None), ("▒", DECK, None), ("║╦╚╝╔╗", HULL, None), ("═", DECK, None),
       ("⣿⣶⣷⣾⣽⣻⣟⣯⡆⡄⣄⣆⢀⣠⣴⡀⢠⣤⣼", SAIL, None), ("v-", SKY, None), ("@()/_", IRON, None), ("*", FIRE, None), ("o", IRON, None), ("░", SMOKE, None)]
RULES = {
    "": [("╔╗╚╝║═╦╩", GLASS, None), ("▁▂▃", LIQUID, None), ("▒▓█", LIQUID, None), ("oO.:", BUBBLE, None)],
    "sea": SEA,
    # the kraken rises at columns 19+ above the water line: everything there is kraken, not ship
    "sea kraken": [("⠀⠁⠂⠃⠄⠅⠆⠇⠈⠉⠊⠋⠌⠍⠎⠏⠐⠑⠒⠓⠔⠕⠖⠗⠘⠙⠚⠛⠜⠝⠞⠟⠠⠡⠢⠣⠤⠥⠦⠧⠨⠩⠪⠫⠬⠭⠮⠯⠰⠱⠲⠳⠴⠵⠶⠷⠸⠹⠺⠻⠼⠽⠾⠿"
                    "⡀⡁⡂⡃⡄⡅⡆⡇⡈⡉⡊⡋⡌⡍⡎⡏⡐⡑⡒⡓⡔⡕⡖⡗⡘⡙⡚⡛⡜⡝⡞⡟⡠⡡⡢⡣⡤⡥⡦⡧⡨⡩⡪⡫⡬⡭⡮⡯⡰⡱⡲⡳⡴⡵⡶⡷⡸⡹⡺⡻⡼⡽⡾⡿"
                    "⢀⢁⢂⢃⢄⢅⢆⢇⢈⢉⢊⢋⢌⢍⢎⢏⢐⢑⢒⢓⢔⢕⢖⢗⢘⢙⢚⢛⢜⢝⢞⢟⢠⢡⢢⢣⢤⢥⢦⢧⢨⢩⢪⢫⢬⢭⢮⢯⢰⢱⢲⢳⢴⢵⢶⢷⢸⢹⢺⢻⢼⢽⢾⢿"
                    "⣀⣁⣂⣃⣄⣅⣆⣇⣈⣉⣊⣋⣌⣍⣎⣏⣐⣑⣒⣓⣔⣕⣖⣗⣘⣙⣚⣛⣜⣝⣞⣟⣠⣡⣢⣣⣤⣥⣦⣧⣨⣩⣪⣫⣬⣭⣮⣯⣰⣱⣲⣳⣴⣵⣶⣷⣸⣹⣺⣻⣼⣽⣾⣿"
                    "░▒▓█|/\\-=_'.()^v≡¥", KRAKEN, (0, 10, 19, 31, 0, 999)), ("@", EYE, (0, 10, 19, 31, 0, 999))] + SEA,
    # toa: the obelisk paints itself (scripts/art/obelisk.py)
    "tob": [("░▒▓█", "#9a5adf", None), ("≡", "#c89aff", None), ("¥", "#7a3fbf", None), ("@", EYE, None)],
    "cox": [("█▓⣿⣷⣯⣟⣽⣾⣶⣭", OLM_PALE, None), ("▒≡", OLM_MID, None), ("░¥", OLM_DARK, None), ("@", EYE, None)],
    # Zulrah: 19 frames a form - serpentine, magma, tanzanite
    "zulrah": [("@", EYE, None), ("~^=", WATER, None),
               ("░▒▓█≡¥|/\\-_'.()⣿", SERP, (0, 12, 0, 31, 0, 19)), ("░▒▓█≡¥|/\\-_'.()⣿", MAGMA, (0, 12, 0, 31, 19, 38)),
               ("░▒▓█≡¥|/\\-_'.()⣿", TANZ, (0, 12, 0, 31, 38, 999))],
}
SELF_PAINTED = {"sea cannon", "toa obelisk"}

def pick(ch, r, c, f, rules):
    for chars, col, region in rules:
        if ch not in chars: continue
        if region:
            r0, r1, c0, c1, f0, f1 = region
            if not (r0 <= r < r1 and c0 <= c < c1 and f0 <= f < f1): continue
        return col
    return None
def colour_row(line, r, f, rules):
    html = ""; cur = None
    for c, ch in enumerate(line):
        col = None if ch == " " else pick(ch, r, c, f, rules)
        if col != cur:
            if cur is not None: html += "</font>"
            if col is not None: html += "<font color=" + col + ">"
            cur = col
        html += ch.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    if cur is not None: html += "</font>"
    return html
def strip(line):
    return re.sub(r"<[^>]*>", "", line).replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
def run(show=None, frame=0):
    s = RES.read_text(encoding="utf-8")
    out = []; key = None; name = None; f = 0; r = 0
    for line in s.split("\n"):
        if line.startswith("=="):
            name = line[2:].strip(); key = name.split(" ")[0] if name else ""; f = 0; r = 0
            out.append(line); continue
        if line == "--": f += 1; r = 0; out.append(line); continue
        if line.startswith("#") or (name not in RULES and key not in RULES): out.append(line); continue
        # a pool the rules cover: sprite-painted boss pools are NOT in RULES, so their markup is kept
        if name in SELF_PAINTED: out.append(line); continue   # these pools carry their own pixel colours
        rules = RULES.get(name) or RULES[key]
        out.append(colour_row(strip(line), r, f, rules)); r += 1
    text = "\n".join(out)
    if show is not None:
        a = text.index("==" + show); b = text.find("\n==", a + 1)
        frames = text[a:b].split("\n", 1)[1].split("\n--\n")
        print("==" + show, "frame", frame, "of", len(frames)); print(frames[frame]); return
    RES.write_text(text, encoding="utf-8")
    print("coloured pools:", ", ".join(k or "flask" for k in RULES))
if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "show":
        run(show=sys.argv[2], frame=int(sys.argv[3]) if len(sys.argv) > 3 else 0)
    else:
        run()
