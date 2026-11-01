"""Writes render/holidays.tsv: start, end, pool. Rows with a year are the moving holidays and come first, so one that
lands on a fixed window wins that week; rows without repeat every year. Past the last year only the fixed rows and the
flask play. Eid and Holi are predictions (the moon decides), so their windows run a day or so wide.
    python scripts/art/holidays.py"""
import datetime as dt, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
YEARS = range(2027, 2037)

LUNAR = ["02-06", "01-26", "02-13", "02-03", "01-23", "02-11", "01-31", "02-19", "02-08", "01-28"]
HOLI = ["03-22", "03-11", "03-01", "03-20", "03-09", "03-27", "03-16", "03-05", "03-24", "03-12"]
EID = {2027: ["03-10", "05-16"], 2028: ["02-27", "05-05"], 2029: ["02-15", "04-24"], 2030: ["02-05", "04-13"],
       2031: ["01-25", "04-02"], 2032: ["01-15", "03-22"], 2033: ["01-03", "03-11", "12-23"], 2034: ["03-01", "12-12"],
       2035: ["02-18", "12-01"], 2036: ["02-07", "11-19"]}

def easter(y):
    """the anonymous Gregorian computus"""
    a, b, c = y % 19, y // 100, y % 100
    d, e = b // 4, b % 4
    g = (8 * b + 13) // 25
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    return dt.date(y, (h + l - 7 * m + 114) // 31, (h + l - 7 * m + 114) % 31 + 1)

def around(y, md, before, after):
    return dt.date.fromisoformat(f"{y}-{md}") - dt.timedelta(before), dt.date.fromisoformat(f"{y}-{md}") + dt.timedelta(after)

moving = []
for n, y in enumerate(YEARS):
    moving.append((*around(y, LUNAR[n], 1, 4), "lunar"))
    moving.append((*around(y, HOLI[n], 2, 1), "holi"))
    moving.append((easter(y) - dt.timedelta(2), easter(y) + dt.timedelta(1), "easter"))
    for md in EID[y]:
        moving.append((*around(y, md, 1, 3), "eid"))
moving.sort()

FIXED = [("01-01", "01-02", "newyear"), ("02-11", "02-14", "valentine"), ("03-15", "03-17", "stpatrick"), ("04-20", "04-24", "earth"),
         ("06-19", "06-22", "solstice"), ("10-15", "10-31", "halloween"), ("12-19", "12-22", "solstice"),
         ("12-23", "12-27", "christmas"), ("12-30", "12-31", "newyear")]

rows = [f"{a}\t{b}\t{k}" for a, b, k in moving] + [f"{a}\t{b}\t{k}" for a, b, k in FIXED]
out = ROOT / "src/main/resources/com/loadoutlab/render/holidays.tsv"
out.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="\n")
print(len(rows), "rows", out)
