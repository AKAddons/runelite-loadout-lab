package com.loadoutlab.engine;

import static java.lang.Math.*;
import java.util.function.IntUnaryOperator;

// A set's damage per attack as a pmf (index = damage, 0 holds the miss),
// built beside DpsCalculator's expected-hit maths for the shown card's
// kill time (ISS-01M655THWG41VPTM1ZTRA5B3YE). Null in, null out: the
// optimizer's trials never ask and pay nothing. Each helper mirrors one
// RollMath rule and keeps its mean.
public final class HitDist {
// One roll lo..hi landing with the accuracy, each outcome's damage by the rule.
static double[] rolled(double accuracy, int lo, int hi, IntUnaryOperator damage) {
    if (hi < lo || accuracy <= 0) return new double[]{1};
    int top = 0;
    for (int k = lo; k <= hi; k++) top = max(top, damage.applyAsInt(k));
    double[] pmf = new double[top + 1];
    pmf[0] = 1 - accuracy;
    double p = accuracy / (hi - lo + 1);
    for (int k = lo; k <= hi; k++) pmf[max(0, damage.applyAsInt(k))] += p;
    return pmf;
}

// RollMath.expectedHitWithFlatArmour's roll: positive armour clamps each
// roll, a landed 0 counts as 1, negative armour is a flat bonus.
static double[] armoured(double accuracy, int lo, int hi, int armour) {
    int from = max(0, min(lo, hi));
    return rolled(accuracy, from, hi, k -> armour > 0 ? max(0, k - armour) : (from > 0 ? k : max(1, k)) - armour);
}

// Two independent hits in one attack.
static double[] plus(double[] a, double[] b) {
    if (a == null || b == null) return null;
    double[] out = new double[a.length + b.length - 1];
    for (int i = 0; i < a.length; i++) {
        if (a[i] == 0) continue;
        for (int j = 0; j < b.length; j++) out[i + j] += a[i] * b[j];
    }
    return out;
}

// A second hit that rolls only when the first lands.
static double[] chained(double[] first, double accuracy, double[] second) {
    if (first == null || second == null) return null;
    double[] landed = first.clone();
    landed[0] -= 1 - accuracy;
    double[] out = plus(landed, second);
    out[0] += 1 - accuracy;
    return out;
}

// RollMath.bestOfExpected's roll: the best of n rolls on one landed hit.
static double[] bestOf(double accuracy, int maxHit, int rolls, int armour) {
    if (maxHit <= 0 || accuracy <= 0) return new double[]{1};
    double[] pmf = new double[maxHit + 1 + max(0, -armour)];
    pmf[0] = 1 - accuracy;
    double below = 0;
    for (int v = 0; v <= maxHit; v++) {
        double atMost = pow((v + 1.0) / (maxHit + 1.0), rolls);
        pmf[max(0, armour > 0 ? v - armour : max(1, v) - armour)] += accuracy * (atMost - below);
        below = atMost;
    }
    return pmf;
}

// With the chance, the attack is b instead of a.
static double[] mix(double[] a, double chance, double[] b) {
    if (a == null || b == null) return null;
    double[] out = new double[max(a.length, b.length)];
    for (int i = 0; i < a.length; i++) out[i] += a[i] * (1 - chance);
    for (int i = 0; i < b.length; i++) out[i] += b[i] * chance;
    return out;
}

// Every outcome's damage remapped; the miss stays at 0.
static double[] mapped(double[] pmf, double miss, IntUnaryOperator damage) {
    if (pmf == null) return null;
    int top = 0;
    for (int d = 1; d < pmf.length; d++) if (pmf[d] > 0) top = max(top, damage.applyAsInt(d));
    double[] out = new double[max(pmf.length, top + 1)];
    out[0] = miss;
    double zeros = pmf[0] - miss;
    if (zeros > 0) out[max(0, damage.applyAsInt(0))] += zeros;
    for (int d = 1; d < pmf.length; d++) out[max(0, damage.applyAsInt(d))] += pmf[d];
    return out;
}

// Every damage scaled, the fraction split between its two neighbours so
// the mean scales exactly.
static double[] scaled(double[] pmf, double factor) {
    if (pmf == null) return null;
    double[] out = new double[(int) ceil((pmf.length - 1) * factor) + 2];
    for (int d = 0; d < pmf.length; d++) {
        double v = d * factor;
        int lo = (int) v;
        out[lo] += pmf[d] * (1 - (v - lo));
        out[lo + 1] += pmf[d] * (v - lo);
    }
    return out;
}

// Every roll lands (the tormented demons).
static double[] sure(double[] pmf, double accuracy) {
    if (pmf == null || accuracy <= 0) return pmf;
    double[] out = new double[pmf.length];
    for (int d = 0; d < pmf.length; d++) out[d] = pmf[d] / accuracy;
    out[0] -= (1 - accuracy) / accuracy;
    return out;
}

public static double mean(double[] pmf) {
    double m = 0;
    for (int d = 1; d < pmf.length; d++) m += d * pmf[d];
    return m;
}

static int top(double[] pmf) {
    int last = pmf.length - 1;
    while (last > 0 && pmf[last] <= 0) last--;
    return last;
}
}
