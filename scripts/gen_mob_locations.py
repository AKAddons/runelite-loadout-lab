"""Build mob_locations.json: every corpus mob name -> its spawn points, from the
OSRS Wiki's {{LocLine}} tables (ISS-01M5TCXDV8HF2V5C627SZEY2W3, Andrew 2026-10-25:
"link shortest path to boss mobs at least and maybe for other slayer mobs too";
"all mobs if possible").

Each point is packed the way Shortest Path packs one (its WorldPointUtil:
x | y << 15 | plane << 30, as a signed int), so the plugin forwards them as-is. Up to 64 points per
mob, sampled evenly; Shortest Path walks to the nearest. Run from the repo root:
    python -I scripts/gen_mob_locations.py
"""
import gzip, json, re, time, urllib.parse, urllib.request

DATA = 'src/main/resources/com/loadoutlab/data/'
API = 'https://oldschool.runescape.wiki/api.php'
UA = {'User-Agent': 'LoadoutLab-datagen (github.com/AKAddons/runelite-loadout-lab)'}
POINT = re.compile(r'x:(\d+)(?:[.]\d+)?,y:(\d+)(?:[.]\d+)?|[|] *(\d{3,5}),(\d{3,5}) *(?=[|\n])')
PIN = re.compile(r'[|] *x *= *(\d+)(?:[.]\d+)? *[|] *y *= *(\d+)')
WHERE = re.compile(r'[|] *location *= *[^\n]*?\[\[([^\]|#]+)')
PLANE = re.compile(r'[|]plane *= *(\d)')
CAP = 64

def names():
    with gzip.open(DATA + 'monsters.json.gz', 'rt', encoding='utf-8') as f:
        return sorted({m['name'] for m in json.load(f)})

def fetch(titles):
    q = {'action': 'query', 'prop': 'revisions', 'rvprop': 'content', 'rvslots': 'main',
         'format': 'json', 'redirects': '1', 'titles': '|'.join(titles)}
    req = urllib.request.Request(API + '?' + urllib.parse.urlencode(q), headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)['query']

def pack(x, y, plane):
    v = x | y << 15 | plane << 30
    return v - (1 << 32) if v >= 1 << 31 else v

def points(text):
    out = []
    for block in text.split('{{LocLine')[1:]:
        block = block.split('{{LocLine')[0]
        m = PLANE.search(block)
        plane = int(m.group(1)) if m else 0
        for a, b, c, d in POINT.findall(block):
            x, y = int(a or c), int(b or d)
            if 0 < x < 16384 and 0 < y < 16384:
                out.append(pack(x, y, plane))
    seen = list(dict.fromkeys(out))
    if len(seen) > CAP:
        step = len(seen) / CAP
        seen = [seen[int(i * step)] for i in range(CAP)]
    return seen

MAP = re.compile(r'\{\{Map[^}]*')
PAIR = re.compile(r'x:(\d+)(?:[.]\d+)?,y:(\d+)|[|] *(\d{3,5}),(\d{3,5})\b')
# Groups no member of which has a spawn table (instances, raids): the map pin
# of the page each is entered from. Individual bosses with no infobox location
# likewise. Wiki pages, checked 2026-10-25.
GROUP_PLACES = {
    'Fight Caves': 'TzHaar Fight Cave', 'Inferno': 'The Inferno', 'Zulrah (all forms)': 'Zul-Andra',
    'Chambers of Xeric': 'Chambers of Xeric', 'Theatre of Blood (Entry)': 'Theatre of Blood',
    'Theatre of Blood (Hard)': 'Theatre of Blood', 'Tombs of Amascut': 'Tombs of Amascut',
    'Fortis Colosseum': 'Fortis Colosseum', 'The Gauntlet': 'The Gauntlet',
    'Corrupted Gauntlet': 'The Gauntlet', 'Nex': 'God Wars Dungeon', 'Royal Titans': 'Asgarnian Ice Dungeon',
    'Grotesque Guardians': 'Slayer Tower', 'The Fractured Archive': 'The Fractured Archive',
}
MOB_PLACES = {'hespori': 'Farming Guild', 'zulrah': 'Zul-Andra', 'nex': 'God Wars Dungeon',
              'tztok-jad': 'TzHaar Fight Cave', 'tzkal-zuk': 'The Inferno',
              'the nightmare': 'Slepe', "phosani's nightmare": 'Slepe',
              # The totem is used on the catacombs' central altar; Skotizo's
              # own LocLine sits in its instance (Andrew 2026-10-25).
              'skotizo': 'Altar (Catacombs of Kourend)'}
# Groups reached through another mob's floor: the Grotesque Guardians sit on
# the Slayer Tower roof, up from the gargoyles.
GROUP_LIKE = {'Grotesque Guardians': 'gargoyle'}

def pin(text):
    """A page's first map pin: {{Map|x=..|y=..}}, {{Map|x:..,y:..}} or {{Map|x,y}}."""
    for block in MAP.findall(text):
        m = PIN.search(block)
        if m:
            return [pack(int(m.group(1)), int(m.group(2)), 0)]
        m = PAIR.search(block)
        if m:
            return [pack(int(m.group(1) or m.group(3)), int(m.group(2) or m.group(4)), 0)]
    return []

def pins_of(places):
    pins = {}
    places = sorted(set(places))
    for i in range(0, len(places), 50):
        q = fetch(places[i:i + 50])
        back = {}
        for n in q.get('normalized', []) + q.get('redirects', []):
            back[n['to']] = back.get(n['from'], n['from'])
        for page in q.get('pages', {}).values():
            revs = page.get('revisions')
            if revs:
                text = revs[0]['slots']['main']['*']
                pts = pin(text) or points(text)
                if pts:
                    pins[back.get(page['title'], page['title'])] = pts[:CAP]
        time.sleep(1)
    return pins

def main():
    all_names = names()
    result = {}
    where = {}
    for i in range(0, len(all_names), 50):
        batch = all_names[i:i + 50]
        q = fetch(batch)
        back = {}
        for n in q.get('normalized', []):
            back[n['to']] = back.get(n['from'], n['from'])
        for r in q.get('redirects', []):
            back[r['to']] = back.get(r['from'], r['from'])
        for page in q.get('pages', {}).values():
            revs = page.get('revisions')
            if not revs:
                continue
            title = page['title']
            asked = back.get(title, title)
            text = revs[0]['slots']['main']['*']
            pts = points(text) or pin(text)
            if pts:
                result[asked.lower()] = pts
            else:
                m = WHERE.search(text)
                if m:
                    where[asked.lower()] = m.group(1).strip()
        time.sleep(1)
    # No spawn table (instanced bosses): the map pin of the place the
    # infobox names - the lair or its entrance.
    pins = pins_of(where.values())
    for mob, place in where.items():
        if place in pins:
            result[mob] = pins[place]
    # A group's unlocated members share their mates' points (one fight, one
    # place); a group none of whose members is located takes its entry pin.
    with open(DATA + 'monster_groups.json', encoding='utf-8') as f:
        groups = json.load(f)
    entry = pins_of(list(GROUP_PLACES.values()) + list(MOB_PLACES.values()))
    # Instanced fights always take their entry pin, even where the wiki lists
    # points inside the instance (Andrew 2026-10-25: "pathing not working for
    # ToA" - its LocLines sit in the raid's own region, which no path reaches).
    for mob, place in MOB_PLACES.items():
        if place in entry:
            result[mob] = entry[place]
    for group in groups:
        members = {m['name'].lower() for m in group['members']}
        if entry.get(GROUP_PLACES.get(group['name'])):
            for m in members:
                result[m] = entry[GROUP_PLACES[group['name']]]
            continue
        shared = list(dict.fromkeys(p for m in members for p in result.get(m, [])))[:CAP]
        if not shared:
            shared = result.get(GROUP_LIKE.get(group['name']), []) or entry.get(GROUP_PLACES.get(group['name']), [])
        for m in members:
            if m not in result and shared:
                result[m] = shared
    out = {'_comment': 'Mob spawn points for the Shortest Path link (ISS-01M5TCXDV8HF2V5C627SZEY2W3). '
           'Generated by scripts/gen_mob_locations.py from the OSRS Wiki LocLine tables; '
           'each int is a Shortest Path packed point (x | y << 15 | plane << 30). Lower-case corpus mob name -> points.'}
    out.update(dict(sorted(result.items())))
    with open(DATA + 'mob_locations.json', 'w', encoding='utf-8', newline='') as f:
        json.dump(out, f, separators=(',', ':'))
        f.write('\n')
    print(len(result), 'of', len(all_names), 'mobs located')

main()
