# Hi-res moods: the art bar

Andrew, 2026-10-31. The look: "they look very PS2 which i like" - chunky lit models
over dim textured backdrops. The grid is 43 x 24 cells of 5 x 10 px (215 x 240 px),
32 frames at 140 ms. A recipe is `def key_name():` returning `(key, mood name, scene)`
where `scene(t)` paints an H x W x 3 float image for t in [0, 1).

## Every boss must have

1. **A backdrop of its own**, from where it is fought. Dim and low-contrast (mostly
   below brightness 90), feathered into the panel with `feather`. The boss is always
   the brightest thing in frame. "more of a background texture layer ... for even more depth"
2. **Parts that move on their own.** "the staleness of ... the high quality character
   model just moving uniformly". Use `Rig`: head, jaw, wings, arms, tail, cloth each
   get a soft part with its own pivot and timing, a little out of step. No whole-body
   bob: feet stay planted. A moving light via `pose(light=...)`.
3. **Motion scaled to its size.** Graardor: "too little motion for how big he is".
   A big body needs lean, drop and anticipation through `bend`, not just small rotations.
4. **Its own mechanic** as the effect (see the legacy mood name), lighting the model
   and starting from the moving part, using `Rig.at`. Vary them: plume, bolts, particles,
   rings, noise fluids; do not reuse one effect everywhere.
5. **Composed**: head and face well inside the frame, "more artistic centering". A
   tail or wing may run off the edge if the body gets larger for it.

6. **Variety above all.** Andrew: "it doesn't need to be present for EVERY animation -
   we should encourage variety". Golden geometry is one tool among many, for the bosses
   it suits ("love the use of the spiral on kril, more geometric/curvature patterns
   please ; use the golden ratio"), not a requirement. Kit: `phi_spiral`/`phi_stroke`,
   `phi_field`, `phyllotaxis`, `phi_radii`, `phi_phase`, `GOLD_X`/`GOLD_Y`. No two
   Andrew: "not everything has to be a swirl. play with concentricity, shapes, pentagonal,
   icosohedron, hexagon. the swirl pattern on scurrius is a bit gatuitious". Prefer shapes
   over spirals: `nested` and `rings` (concentric), `pentagram`, `polygon`, `hex_mask`,
   `wire_solid` (a turning icosahedron or dodecahedron).
   "think about it in orientation placement too not just shape": geometry also decides
   where things sit and how they face. Place pillars, adds, totems or impacts on the
   vertices of a pentagon or hexagon or at golden-angle steps, tip a figure into the
   floor's perspective with `squash`, rotate a form to face its attacker, or put the
   boss and its effect on the frame's golden sections, with no drawn lines at all.
   Andrew's reference 2026-10-31 (a chakra mandala behind a stream of light): "a pattern like
   this which is a combination of shapes and patterns". Layer different shapes on one centre
   rather than repeating one: `mandala` nests a disc, lotus lobes, a petal fringe, rings, a
   dotted ring, a polygon and an inner figure. Vary petals/sides/inner, `squash` it onto a
   floor, `reveal` to inscribe it, `spin` to counter-rotate. Pair it with light moving through
   it. Use it on a few bosses only, each differently.
   neighbouring bosses should share a signature effect; leave it out when it feels pasted on.

## References Andrew praised

The Whisperer's effects (hires_batch1b.py) and Muspah (hires_batch1c.py): "extremely well
done". The Hunllef too ("exceptional quality"; not shipped, it is not simulated): the whole body and room relit between attack colours, with an overhead icon. The demonic gorilla ("i'm in love", "the essence of hunleff"; hires_batch2c.py): the whole body and room re-ramp together with a bold overhead icon on each switch, while boulders keep falling. Graardor's body motion, Vorkath's composition, Kril's spiral.

## Limits learned the hard way

- A sprite is one flat image. Rotations past about 15 degrees on a limb moving into
  empty space, or about 10 over the body, fold it. Never slide a limb over the body:
  compress it through `bend` instead (see Graardor's leg).
- Everything loops: use whole-number cycles (`wave(t, n)` with integer n), and noise via `noise`.
- `relief` about 0.3. Higher punches black holes in the model.
- Dark sprites: raise `fit(exposure=, gamma=)` and lower `sat` (see kbd).
- Backdrop first too bright is the usual failure: check the boss still pops.
- Check `python scripts/art/hires.py sheet <key>` at every step and read the image.

## Frame

Every mood gets the same one-pixel rim in eighth blocks with blank corner cells, in one colour for the whole lap (`rim_colour`: the hue covering the most lit cells, or a hand pick in `RIM_COLOURS` where that lands on a flash). Applied in `hires.py` build, so recipes never draw it. Keep the outer cells free of anything vital. Andrew picked this 2026-10-31 over logo cut-outs, which crop the bosses.
