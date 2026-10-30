package com.loadoutlab.engine;

import java.util.List;

// How long a set takes to kill (ISS-01M6415DTE84ZZ45C4YD3MZRBJ, Andrew
// 2026-10-29: "make the time to kill as low as possible and not
// necessarily output the most dps"): the expected time is hp over NET dps
// (the boss's heal off the set taken off, the sustained spec value added);
// the band is a table over the set's attacks, each dealing its per-attack
// distribution (HitDist; ISS-01M655THWG41VPTM1ZTRA5B3YE), the heal and
// the spec riding between attacks as expectations. The boss's own hit
// variance is not in the table (v1). Times in seconds.
public final class KillTime {
    // The percentiles ("90% of runs fall between") and the all-max-hits
    // floor; infinite when the set cannot outpace the heal.
    public final double p05;
    public final double p50;
    public final double p95;
    public final double perfect;
    // The band is not the set's own distribution (none built, or a model
    // with no per-attack form).
    public final boolean approximate;
    // The kill's mass per 0.6 s tick, the tail folded into the last tick;
    // null when the set never kills. A trip convolves its members'.
    final double[] pmf;

    static final double TICK = 0.6;
    // The table stops here: a kill slower than this reads never.
    static final int MAX_TICKS = 2000;

    KillTime(double p05, double p50, double p95, double perfect, boolean approximate, double[] pmf) {
        this.p05 = p05;
        this.p50 = p50;
        this.p95 = p95;
        this.perfect = perfect;
        this.approximate = approximate;
        this.pmf = pmf;
    }

    // The expected kill time: hp over net dps, infinite at or below 0.
    public static double of(int hp, double dps, double healDps, double specDpsAdded) {
        double net = dps + specDpsAdded - healDps;
        return net <= 1e-9 ? Double.POSITIVE_INFINITY : hp / net;
    }

    // The table without a distribution: one uniform 0..max roll per attack,
    // paced to the set's mean and read approximate when its dps is not
    // accuracy x max / 2.
    public static KillTime table(int hp, double accuracy, int maxHit, int speedTicks, double dps, double healDps, double specDpsAdded) {
        double interval = Math.max(1, speedTicks) * TICK;
        double[] pmf = HitDist.rolled(accuracy, 0, maxHit, k -> k);
        double uniform = HitDist.mean(pmf);
        return table(hp, pmf, interval, dps, healDps, specDpsAdded, Math.abs(dps * interval - uniform) > 1e-6 * Math.max(1, uniform));
    }

    // The table over the set's own distribution: each attack paced at its
    // mean over the set's dps, Perfect at the weapon's interval.
    public static KillTime table(int hp, double[] pmf, double interval, double dps, double healDps, double specDpsAdded, boolean approximate) {
        int top = HitDist.top(pmf);
        double perfect = top <= 0 ? Double.POSITIVE_INFINITY : Math.ceil((double) hp / top) * interval;
        double mean = of(hp, dps, healDps, specDpsAdded);
        double pace = dps <= 0 ? Double.POSITIVE_INFINITY : HitDist.mean(pmf) / dps;
        if (Double.isInfinite(mean) || Double.isInfinite(pace) || top <= 0) return new KillTime(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, perfect, approximate, null);
        double drift = (healDps - specDpsAdded) * pace;
        // Damage in buckets keeps the table small on big-hp bosses.
        int b = 1;
        while ((double) hp / b * (top / b + 1) > 60_000) b *= 2;
        double[] hit = new double[top / b + 1];
        for (int d = 0; d <= top; d++) hit[d / b] += pmf[d];
        int attacks = (int) Math.min(Math.ceil(MAX_TICKS * TICK / pace), Math.ceil(3 * mean / pace) + 20);
        int size = (int) Math.ceil((hp + attacks * Math.max(0, drift)) / b) + hit.length + 2;
        double[] alive = new double[size];
        double[] next = new double[size];
        alive[0] = 1;
        double[] out = new double[MAX_TICKS + 1];
        double dead = 0;
        int high = 0;
        for (int k = 1; k <= attacks && dead < 0.9995; k++) {
            java.util.Arrays.fill(next, 0, Math.min(size, high + hit.length + 1), 0);
            for (int s = 0; s <= high; s++) {
                double p = alive[s];
                if (p == 0) continue;
                for (int j = 0; j < hit.length; j++) next[s + j] += p * hit[j];
            }
            high = Math.min(size - 1, high + hit.length - 1);
            // Dead once the damage so far clears the hp plus what she healed.
            int threshold = (int) Math.ceil((hp + k * drift) / b);
            double killed = 0;
            for (int s = Math.max(0, threshold); s <= high; s++) {
                killed += next[s];
                next[s] = 0;
            }
            if (threshold <= high) high = Math.max(0, threshold - 1);
            dead += killed;
            out[Math.min(MAX_TICKS, (int) Math.round(k * pace / TICK))] += killed;
            double[] swap = alive;
            alive = next;
            next = swap;
        }
        if (dead < 0.5) return new KillTime(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, perfect, approximate, null);
        out[MAX_TICKS] += 1 - dead;
        return from(out, perfect, approximate);
    }

    // Percentiles off a tick pmf; a percentile the table never reaches
    // (the mass folded into the last tick) reads infinite.
    static KillTime from(double[] pmf, double perfect, boolean approximate) {
        double[] q = {0.05, 0.5, 0.95};
        double[] out = new double[3];
        double cum = 0;
        int qi = 0;
        for (int t = 0; t < pmf.length && qi < 3; t++) {
            cum += pmf[t];
            while (qi < 3 && cum >= q[qi] - 1e-12) out[qi++] = t >= MAX_TICKS ? Double.POSITIVE_INFINITY : t * TICK;
        }
        while (qi < 3) out[qi++] = Double.POSITIVE_INFINITY;
        return new KillTime(out[0], out[1], out[2], perfect, approximate, pmf);
    }

    // A trip: the members' tables convolved, Perfect the sum of theirs;
    // never when any member never dies.
    public static KillTime sum(List<KillTime> members) {
        double perfect = 0;
        boolean approximate = false;
        double[] acc = {1};
        for (KillTime member : members) {
            perfect += member.perfect;
            approximate |= member.approximate;
            if (member.pmf == null) acc = null;
            if (acc == null) continue;
            double[] m = trimmed(member.pmf);
            double[] out = new double[Math.min(MAX_TICKS + 1, acc.length + m.length - 1)];
            for (int i = 0; i < acc.length; i++) {
                if (acc[i] == 0) continue;
                for (int j = 0; j < m.length; j++) out[Math.min(MAX_TICKS, i + j)] += acc[i] * m[j];
            }
            acc = out;
        }
        if (acc == null) return new KillTime(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, perfect, approximate, null);
        double[] pmf = new double[MAX_TICKS + 1];
        System.arraycopy(acc, 0, pmf, 0, acc.length);
        return from(pmf, perfect, approximate);
    }

    // The pmf up to its last tick with mass: the convolution stays short.
    static double[] trimmed(double[] pmf) {
        int last = pmf.length - 1;
        while (last > 0 && pmf[last] == 0) last--;
        return java.util.Arrays.copyOf(pmf, last + 1);
    }
}
