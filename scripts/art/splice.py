"""Regenerate keyed mood sections of loader_frames.txt from the art
generators: python3 scripts/art/splice.py obelisk olm"""
import pathlib, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/com/loadoutlab/render/loader_frames.txt"
HEADERS = {"obelisk": "==toa obelisk", "olm": "==cox olm"}
s = RES.read_text(encoding="utf-8")
for name in sys.argv[1:]:
    head = HEADERS[name]
    out = subprocess.run([sys.executable, str(ROOT / "scripts/art" / (name + ".py"))], capture_output=True, text=True, check=True).stdout
    assert out.startswith(head + "\n"), out[:40]
    start = s.index(head + "\n")
    nxt = s.find("\n==", start + 1)
    end = len(s) if nxt < 0 else nxt + 1
    s = s[:start] + out.rstrip("\n") + "\n" + s[end:]
    print(name, "spliced,", out.count("\n--\n") + 1, "frames")
RES.write_text(s, encoding="utf-8")
print("headers:", s.count("\n=="), "frames:", s.count("\n--\n"))
