"""Regenerate keyed mood sections of loader_frames.txt from the art
generators: python3 scripts/art/splice.py obelisk olm"""
import pathlib, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/com/loadoutlab/render/loader_frames.txt"
HEADERS = {"obelisk": "==toa obelisk", "olm": "==cox olm"}   # others: the generator names its own header
s = RES.read_text(encoding="utf-8")
names = sys.argv[1:]
if names == ["all"]:
    # every generator in turn: the standalone ones, then every bosses.py recipe
    import importlib.util
    spec = importlib.util.spec_from_file_location("bosses", ROOT / "scripts/art/bosses.py"); mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
    names = ["obelisk", "cox", "tob", "cannon"] + ["bosses:" + k for k in mod.RECIPES]
for name in names:
    script, _, arg = name.partition(":")   # "bosses:kbd" runs bosses.py kbd
    out = subprocess.run([sys.executable, str(ROOT / "scripts/art" / (script + ".py"))] + ([arg] if arg else []), capture_output=True, text=True, check=True).stdout
    head = HEADERS.get(name) or out.split("\n", 1)[0]
    assert out.startswith(head + "\n") and head.startswith("=="), out[:40]
    if head + "\n" in s:
        start = s.index(head + "\n")
        nxt = s.find("\n==", start + 1)
        end = len(s) if nxt < 0 else nxt + 1
        s = s[:start] + out.rstrip("\n") + "\n" + s[end:]
    else:
        s = s.rstrip("\n") + "\n" + out.rstrip("\n") + "\n"   # a new pool appends
    print(name, "spliced,", out.count("\n--\n") + 1, "frames")
RES.write_text(s, encoding="utf-8")
print("headers:", s.count("\n=="), "frames:", s.count("\n--\n"))
if sys.argv[1:] == ["all"]:
    subprocess.run([sys.executable, str(ROOT / "scripts/art/colourise.py")], check=True)   # the legacy pools' colour rules
