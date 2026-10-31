package com.loadoutlab.engine;

import static java.lang.Math.*;

// The three multi-combat tools the Multi card ranks (Andrew 2026-10-19,
// design DES-01M5B2AGKDBE6G3J3WR06TBSW2): the ancient burst/barrage
// spells and chinchompas hit every target in the 3x3 around the primary,
// up to nine; the venator bow bounces up to twice, each bounce at
// two-thirds of the max hit on its own accuracy roll (wiki). The engine
// prices one target; the card multiplies by the stack.
@lombok.AllArgsConstructor
public enum MultiTool {
BARRAGE("barrage", Style.MAGIC, "area"), CHINS("chins", Style.RANGED, "area"),
VENATOR("venator", Style.RANGED, "chain"),
// The Mad Angel's two-hander (ISS-01M5TPEWE9AQM3B55ZH02PHGE4, Andrew 2026-10-25: "consider
// hallowfell as a melee aoe option for multimob").
HALLOWFELL("hallowfell", Style.MELEE, "cleave");

// Black, red, grey: the pin takes the best owned, the ceiling black.
public static final int[] CHIN_IDS = {11959, 10034, 10033};
public static final int VENATOR_BOW = 27610;
public static final int VENATOR_BOW_UNCHARGED = 27612;
public static final int HALLOWFELL_SWORD = 34027;
static final int AREA_CAP = 9;

public final String key;
public final Style style;
public final String kind;

// The multiplier on a per-target number at a stack of targets.
public static double factor(String kind, int stack, int maxHit) {
    if ("chain".equals(kind)) return stack >= 2 ? RollMath.venatorFactor(maxHit) : 1;
    // Hallowfell (wiki passive): up to two more unique targets, each its
    // own accuracy roll at half the max hit, rounded down.
    if ("cleave".equals(kind)) return maxHit <= 0 ? 1 : 1 + min(max(stack - 1, 0), 2) * (double) (maxHit / 2) / maxHit;
    return max(1, min(stack, AREA_CAP));
}
}
