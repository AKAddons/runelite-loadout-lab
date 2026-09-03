"""CoX Olm mood: the head and neck, turning slowly left to right and back
(Andrew 2026-09-02/03: "slower movements", "eyes that the user could make
out, braille might be better than the big full blocks")."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import sprite2text as st
SPRITE = str(pathlib.Path(__file__).parent / "sprites" / "Great_Olm.png")
W, H = 31, 12
HEAD = (0.20, 0.02, 0.45, 0.70)
# a slow turn: full face, squash to a profile, hold, open back out mirrored
SEQ = [(1.0, False)] * 5 + [(0.9, False), (0.8, False), (0.7, False)] + [(0.62, False)] * 3 \
    + [(0.7, True), (0.8, True), (0.9, True)] + [(1.0, True)] * 5 + [(0.9, True), (0.8, True), (0.7, True)] \
    + [(0.62, True)] * 3 + [(0.7, False), (0.8, False), (0.9, False)]
EYES = ((298, 133), (252, 160))   # sprite pixels: the visible eye and the far one
frames = [st.render(SPRITE, W, H, crop=HEAD, xscale=x, mirror=m, eye_points=EYES) for x, m in SEQ]
for f in frames:
    for r in f:
        assert len(r) == W
if __name__ == "__main__":
    if "show" in sys.argv:
        for i in (0, 8, 13):
            print("\n".join(frames[i])); print("-" * W)
    else:
        print("==cox olm"); print("\n--\n".join("\n".join(f) for f in frames))
