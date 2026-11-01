"""Poi (Andrew 2026-11-01: "i'd love to add some poi spinning patterns into some of the animations as well as a
variation for some of the more boring ones", then "can you do more fun antispin patterns"). A poi head is two circles
added together: the hand round the shoulder, the head round the hand. Spun opposite ways they draw antispin flowers, and
the ratio is the petal count. A second mood plays the boss with a pair spinning behind it (on top they get lost on a
bright body), a routine of moves, one a lap, that melt into each other because every lap starts with both circles level."""
from hires_kit import *

LAP_FRAMES = 16
BOSS_LAPS = 2                                                                   # the boss's own 32 frame loop, twice through

def petals(n, arm, tether, bloom=0.0, inward=False):
    """one lap of an n petal antispin. bloom opens and shuts the flower; inward swaps the circles, so the petals loop to the middle."""
    if inward: arm, tether = tether, arm
    def at(u):
        a = 2 * math.pi * u
        reach = 1 - bloom * 0.5 * (1 - math.cos(2 * a))
        return arm * math.cos(a) + tether * reach * math.cos(-(n - 1) * a), arm * math.sin(a) + tether * reach * math.sin(-(n - 1) * a)
    return at

def routine(moves, turns=1):
    """moves in turn, one a lap; the whole figure wheels round `turns` times a routine. T runs 0 to 1."""
    laps = len(moves)
    def at(T):
        T = T % 1
        i = int(T * laps); f = T * laps - i
        x, y = moves[i](f)
        k = max(0.0, (f - 0.8) / 0.2)                                           # the last of a lap leans into the next move
        if k > 0:
            k = k * k * (3 - 2 * k)
            nx, ny = moves[(i + 1) % laps](f - 1)
            x, y = x * (1 - k) + nx * k, y * (1 - k) + ny * k
        r = 2 * math.pi * turns * T
        return x * math.cos(r) - y * math.sin(r), x * math.sin(r) + y * math.cos(r)
    return at

def trail(cv, cx, cy, path, T, span, colour, flip=1, offset=0.0, n=360, hot=(255, 245, 210)):
    """one poi: a comet along the path, its tail the last `span` of the routine. flip mirrors it."""
    c = np.array(colour, float)
    for i in range(n, -1, -1):
        k = i / n
        x, y = path(T + offset - k * span)
        stamp(cv, cx + flip * x, cy + y, c * (1 - k) ** 1.1, 2.4 - 1.2 * k)
    x, y = path(T + offset)
    glow(cv, cx + flip * x, cy + y, 9, colour, 0.7)
    stamp(cv, cx + flip * x, cy + y, hot, 2.8)

def behind(fn, name, hands, moves, colours, turns=1, offset=0.0):
    """a recipe that takes under=, as a second mood. offset: the right hand's time ahead of the left, as a part of a lap (0 mirrors them)."""
    laps = len(moves)
    frames = laps * LAP_FRAMES
    def recipe():
        clock = [0.0]
        path = routine(moves, turns)
        def under(cv, _):
            T = clock[0]
            trail(cv, hands[0][0], hands[0][1], path, T, 0.9 / laps, colours[0])
            trail(cv, hands[1][0], hands[1][1], path, T, 0.9 / laps, colours[1], flip=-1, offset=offset / laps)
        made = fn(under=under)
        film = made[2]
        def scene(t):
            clock[0] = t
            return film((t * frames / (BOSS_LAPS * 16)) % 1)
        return made[0], name, scene, frames
    recipe.__name__ = fn.__name__ + "_poi"
    return recipe

from hires_batch1b import duke_vents
from hires_batch3b import crab_glints, fanatic_bolt
from hires_batch3c import hespori_seeds

RECIPES = [behind(crab_glints, "gem poi", ((107, 80), (107, 80)),
                  [petals(4, 42, 46), petals(3, 42, 46, bloom=1.0), petals(5, 34, 54), petals(4, 46, 42, inward=True)],
                  ((90, 230, 255), (255, 90, 220)), offset=0.5),
           behind(duke_vents, "fire poi", ((107, 100), (107, 100)),
                  [petals(5, 52, 50), petals(4, 56, 46, bloom=0.5), petals(6, 50, 52), petals(3, 60, 42)],
                  ((255, 150, 50), (255, 210, 90)), offset=0.5),
           behind(fanatic_bolt, "chaos poi", ((107, 100), (107, 100)),
                  [petals(3, 46, 46), petals(4, 38, 56, bloom=0.8), petals(6, 46, 44), petals(3, 56, 38, inward=True)],
                  ((120, 255, 90), (190, 90, 255)), offset=0.25),
           behind(hespori_seeds, "petal poi", ((107, 95), (107, 95)),
                  [petals(5, 42, 46, bloom=0.6), petals(7, 44, 44), petals(4, 36, 54, bloom=1.0), petals(6, 48, 38)],
                  ((255, 120, 190), (250, 215, 90)), turns=2)]
