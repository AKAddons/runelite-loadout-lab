// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import com.google.gson.*;
import java.util.*;
import lombok.*;

@RequiredArgsConstructor
@Getter
public class PrayerBonuses {
    public static final PrayerBonuses NONE = new PrayerBonuses(1.0, 1.0, 1.0, 1.0, 1.0, 0.0);

    final double meleeAccuracy;
    final double meleeStrength;
    final double rangedAccuracy;
    public final double rangedStrength;
    final double magicAccuracy;
    String meleeName = "";
    String rangedName = "";
    String magicName = "";
    final double magicDamagePercent;

    public static PrayerBonuses bestAvailable(PlayerLevels levels) { return bestAvailable(levels, PrayerUnlocks.ALL); }

    public static PrayerBonuses bestAvailable(PlayerLevels levels, PrayerUnlocks unlocks) {
        String meleeName;
        double meleeAcc = 1.0;
        double meleeStr = 1.0;
        if (levels.prayer >= 84 && levels.attack >= 75 && levels.strength >= 75 && unlocks.zeal()) {
            // Zeal also gates on 75 Attack and Strength.
            meleeAcc = 1.25;
            meleeStr = 1.28;
            meleeName = "Zeal";
        } else if (levels.prayer >= 70 && unlocks.piety()) {
            meleeAcc = 1.20;
            meleeStr = 1.23;
            meleeName = "Piety";
        } else if (levels.prayer >= 60 && unlocks.chivalry()) {
            meleeAcc = 1.15;
            meleeStr = 1.18;
            meleeName = "Chivalry";
        } else {
            // Every applied tier is NAMED - the assumes chip must never fold
            // in a multiplier it does not admit to (audit A2.12).
            List<String> parts = new ArrayList<>();
            if (levels.prayer >= 34) {
                meleeAcc = 1.15;
                parts.add("Incredible Reflexes");
            } else if (levels.prayer >= 16) {
                meleeAcc = 1.10;
                parts.add("Improved Reflexes");
            } else if (levels.prayer >= 7) {
                meleeAcc = 1.05;
                parts.add("Clarity of Thought");
            }
            if (levels.prayer >= 31) {
                meleeStr = 1.15;
                parts.add("Ultimate Strength");
            } else if (levels.prayer >= 13) {
                meleeStr = 1.10;
                parts.add("Superhuman Strength");
            } else if (levels.prayer >= 4) {
                meleeStr = 1.05;
                parts.add("Burst of Strength");
            }
            meleeName = String.join(" + ", parts);
        }

        boolean rigour = levels.prayer >= 74 && unlocks.rigour();
        boolean deadeye = levels.prayer >= 62 && unlocks.deadeye();
        double rangedAccuracy = rigour ? 1.20 : deadeye ? 1.18 : levels.prayer >= 44 ? 1.15 : levels.prayer >= 26 ? 1.10 : levels.prayer >= 8 ? 1.05 : 1.0;
        double rangedStrength = rigour ? 1.23 : deadeye ? 1.18 : levels.prayer >= 44 ? 1.15 : levels.prayer >= 26 ? 1.10 : levels.prayer >= 8 ? 1.05 : 1.0;
        boolean augury = levels.prayer >= 77 && unlocks.augury();
        boolean vigour = levels.prayer >= 77 && unlocks.mysticVigour();
        double magic = augury ? 1.25 : vigour ? 1.18 : levels.prayer >= 45 ? 1.15 : levels.prayer >= 27 ? 1.10 : levels.prayer >= 9 ? 1.05 : 1.0;
        // All magic prayers share one prayer group in game - Augury and
        // Mystic Vigour cannot be active together (the wiki calc engine
        // stacks whatever it is fed, which is how the old 7% slipped past
        // the harness). Augury strictly dominates when both are unlocked.
        double magicDamage = augury ? 4.0 : vigour ? 3.0 : levels.prayer >= 45 ? 2.0 : levels.prayer >= 27 ? 1.0 : 0.0;
        var result = new PrayerBonuses(meleeAcc, meleeStr, rangedAccuracy, rangedStrength, magic, magicDamage);
        result.meleeName = meleeName;
        result.rangedName = rigour ? "Rigour" : deadeye ? "Deadeye" : levels.prayer >= 44 ? "Eagle Eye"
            : levels.prayer >= 26 ? "Hawk Eye" : levels.prayer >= 8 ? "Sharp Eye" : "";
        result.magicName = augury ? "Augury" : vigour ? "Mystic Vigour" : levels.prayer >= 45 ? "Mystic Might"
            : levels.prayer >= 27 ? "Mystic Lore" : levels.prayer >= 9 ? "Mystic Will" : "";
        return result;
    }

    /** The selectable named tiers per style, best first - the assume-chip
     * picker (field direction 2026-07-21: "I don't use Piety against every
     * mob"). Factors mirror bestAvailable's cascade exactly. */
    public static String[] optionsFor(CombatStyle style) {
        Object[][] picks = picksFor(style);
        String[] names = new String[picks.length];
        for (int i = 0; i < picks.length; i++) names[i] = (String) picks[i][0];
        return names;
    }

    /** name -> {accuracy, strength-or-damage, Defence %, Magic-defence %} per
     * style, best first - mirrors bestAvailable's cascade exactly. */
    static final Map<String, Object[][]> PICKS = new HashMap<>();

    static {
        JsonObject root = JsonResources.objectOrThrow("prayer_picks.json");
        for (String style : new String[]{"melee", "ranged", "magic"}) {
            JsonArray rows = root.getAsJsonArray(style);
            Object[][] picks = new Object[rows.size()][];
            for (int i = 0; i < picks.length; i++) {
                JsonArray row = rows.get(i).getAsJsonArray();
                picks[i] = new Object[]{row.get(0).getAsString(), row.get(1).getAsDouble(), row.get(2).getAsDouble(),
                    row.get(3).getAsInt(), row.get(4).getAsInt()};
            }
            PICKS.put(style, picks);
        }
    }

    static Object[][] picksFor(CombatStyle style) { return PICKS.get(Names.lower(style.name())); }

    /** A specific pick's bonuses for ONE style: the fallback's factors with
     * this style's replaced by the named tier (the other styles' values are
     * irrelevant to a single-style request). Unknown names fall back. */
    public static PrayerBonuses forPick(CombatStyle style, String pick, PrayerBonuses fallback) {
        for (Object[] row : picksFor(style)) {
            if (!row[0].equals(pick)) continue;
            // The fallback's six factors with this style's pair replaced.
            double[] f = {fallback.meleeAccuracy, fallback.meleeStrength, fallback.rangedAccuracy,
                fallback.rangedStrength, fallback.magicAccuracy, fallback.magicDamagePercent};
            int at = style == CombatStyle.RANGED ? 2 : style == CombatStyle.MAGIC ? 4 : 0;
            f[at] = (Double) row[1];
            f[at + 1] = (Double) row[2];
            var result = new PrayerBonuses(f[0], f[1], f[2], f[3], f[4], f[5]);
            result.meleeName = style == CombatStyle.MELEE ? pick : fallback.meleeName;
            result.rangedName = style == CombatStyle.RANGED ? pick : fallback.rangedName;
            result.magicName = style == CombatStyle.MAGIC ? pick : fallback.magicName;
            return result;
        }
        return fallback;
    }

    /** The prayer tier the numbers assume for a style ("Piety", "Rigour"). */
    public String nameFor(CombatStyle style) {
        switch (style) {
            case RANGED: return rangedName;
            case MAGIC: return magicName;
            default: return meleeName;
        }
    }

    /** {Defence, Magic} a boss rolls against under the style's named prayer:
     * Piety +25% Defence, Augury +25% both (ISS-01M6415DFFW34P8QY468GA74KY). */
    public static int[] defending(CombatStyle style, String prayer, int defence, int magic) {
        for (Object[] row : picksFor(style)) if (row[0].equals(prayer)) return new int[]{defence * (int) row[3] / 100, magic * (int) row[4] / 100};
        return new int[]{defence, magic};
    }
}
