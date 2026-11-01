# Holiday films: the brief

Andrew 2026-10-31: "can we make some more holiday theme cinematics?" These play on searches with no boss art of their own, during the dates in `holidays.tsv` (`holidays.py`). Warm, respectful, celebratory; no religious figures or sacred text depicted, ever. Celebrate through light, nature, food, colour, and community.

Each is a short film in the raid manner: `hires_raids.py` (FILM=112 frames, `letterbox`, `play`, `push`, `blackout`, `rays`) and `hires_tfa.py` are the models. Read `HIRES.md` first; `hires_kit.py` is the toolkit. Set in Gielinor where possible, PS2 chunky-lit look, dim backdrops, a hero that is the brightest thing in frame, three to five shots with one strong idea, a seamless loop. Use the kit's `ink` (in `hires_last.py`) for dark lines: `line`/`stamp` ADD light. Existing sprites in `sprites/` are fine; download nothing. Keep the outer cells free of anything vital (the rim goes there).

Key = the function name before the first `_`.

| key | idea |
|---|---|
| christmas | A snowed-in village at night; a lit tree, presents, a santa-hat-wearing goblin or snow imp, a party-hat cracker pull with a burst of paper |
| newyear | A city skyline at midnight; a countdown of bells, then fireworks in distinct shapes over water reflecting them |
| valentine | A rose garden at dusk; petals on the wind, two lanterns or two small birds meeting, a heart drawn in light that opens |
| easter | A spring meadow at dawn; eggs painted in bold patterns hidden in the grass, a rabbit that hops through finding them, a chick breaking out |
| lunar | A lantern street at night; a dragon dance winding through, red lanterns rising, firecrackers, gold coins |
| holi | Daylight courtyard; clouds of pigment (magenta, yellow, green, blue) thrown and bursting, drifting across each other, everything ending drenched in colour |
| stpatrick | Rolling green hills in rain; the sun breaks and a rainbow arcs down to a pot of gold, a shamrock field in the wind, coins spilling, a leprechaun-hatted gnome dancing a jig |
| earth | The world seen whole, a globe turning in space, cutting to a seedling growing into a forest with birds and rain, back out to a greener globe |
| solstice | A ring of standing stones; the sun rises through the gap and the shadows sweep, the sky turning through the whole day to stars, usable in June and in December |
| eid | A desert city at dusk; the thin crescent moon appears, lanterns light along rooftops street by street, a table of sweets and tea, stars. No figures praying, no calligraphy |

Environment: Windows, Git Bash. Run python as
`PYTHONUTF8=1 PYTHONPATH="<scratchpad>/lib" /c/Users/Andrew/AppData/Local/Programs/Python/Python312/python.exe`
where `<scratchpad>` is `/c/Users/Andrew/AppData/Local/Temp/claude/C--Users-Andrew--claude-spares-spare-GGxYFm/4f6bd61f-22bb-4607-b076-45f970f35873/scratchpad`, from `C:/Users/Andrew/Development/ll-art`.

Other painters work at once and `hires.recipes()` imports every `hires_*.py`, so a half-written neighbour can crash it. Use only your own recipes, with a small runner kept in the scratchpad:

    import sys; sys.path.insert(0, "scripts/art")
    import hires, hires_holiday_X as b
    hires.recipes = lambda: b.RECIPES
    hires.sheet(sys.argv[1])       # build/art/<key>.png: eight frames; for a bake: hires.write({key: [(name, cells)]}) as in hires.py's main

A film costs a few minutes to bake; test on `sheet`, and look at every sheet with the Read tool. Iterate until it reads at a glance at this size. Touch only your own `hires_holiday_X.py` and the baked `moods/<key>.txt`. Do not commit.
