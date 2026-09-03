"""CoX Olm mood. Andrew (2026-09-03) on the Braille rebuild: "scary - his
face should be the same on both sides. tbh before was better" - so this
prints the pass-seven frames verbatim (they live in olm_frames.txt beside
this script); sprite2text.py remains for a future attempt."""
import pathlib, sys
FRAMES = pathlib.Path(__file__).parent / "olm_frames.txt"
if __name__ == "__main__":
    text = FRAMES.read_text(encoding="utf-8").rstrip("\n")
    if "show" in sys.argv:
        print(text.split("\n--\n")[0])
    else:
        print(text)
