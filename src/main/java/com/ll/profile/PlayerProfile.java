package com.ll.profile;

import com.ll.data.Json;
import com.ll.engine.*;
import java.util.*;
import net.runelite.api.Skill;

// A player as data: levels (real and live-boosted), prayer unl,
// equip requirements (skills + quests), and owned items. Everything a
// query needs - so the optimizer can be driven by a MOCK environment
// (fixtures, the headless runner, replayed bug reports) without a
// running client. The plugin exports the real account to
// .runelite/loadout-lab/profile.json on every query.
public class PlayerProfile {
public Levels realLevels;
public Levels boostedLevels;
public Prayers prayerUnlocks;
public RequirementProfile requirements;
public Map<Integer, Integer> owned;
boolean bankScanned;
// Optional provenance: origin label ("bank", "STASH", ...) -> items.
// The flat owned map stays the source of truth for the optimizer.
Map<String, Map<Integer, Integer>> ownedBySource;

public PlayerProfile(Levels realLevels, Levels boostedLevels, Prayers prayerUnlocks, RequirementProfile requirements,
    Map<Integer, Integer> owned, boolean bankScanned) {
    this(realLevels, boostedLevels, prayerUnlocks, requirements, owned, bankScanned, null);
}

public PlayerProfile(Levels realLevels, Levels boostedLevels,
    Prayers prayerUnlocks, RequirementProfile requirements, Map<Integer, Integer> owned, boolean bankScanned,
    Map<String, Map<Integer, Integer>> ownedBySource) {
    this.realLevels = realLevels;
    this.boostedLevels = boostedLevels == null ? realLevels : boostedLevels;
    this.prayerUnlocks = prayerUnlocks == null ? Prayers.ALL : prayerUnlocks;
    this.requirements = requirements == null ? RequirementProfile.MAXED : requirements;
    this.owned = owned == null ? Map.of() : Map.copyOf(owned);
    this.bankScanned = bankScanned;
    var copy = new LinkedHashMap<String, Map<Integer, Integer>>();
    if (ownedBySource != null) for (var e : ownedBySource.entrySet()) copy.put(e.getKey(), Map.copyOf(e.getValue()));
    this.ownedBySource = copy;
}

public Owned oi() { return new Owned(owned, bankScanned); }

// The file's shape, Gson both ways: the levels and the prayer unl
// are their own classes' fields; the requirements write as skills and
// quests. Plain maps and sets only (Gson cannot see into Map.of).
static class Raw {
    public Levels realLevels, boostedLevels;
    public Prayers prayerUnlocks;
    public Reqs requirements;
    public Map<Integer, Integer> owned;
    Map<String, Map<Integer, Integer>> ownedBySource;
    boolean bankScanned;
}

static class Reqs {
    Map<Skill, Integer> skills = new LinkedHashMap<>();
    Set<String> quests = new HashSet<>();
}

public String toJson() {
    var raw = new Raw();
    raw.realLevels = realLevels;
    raw.boostedLevels = boostedLevels;
    raw.prayerUnlocks = prayerUnlocks;
    raw.requirements = new Reqs();
    raw.requirements.skills.putAll(requirements.getLevels());
    raw.requirements.quests.addAll(requirements.getCq());
    raw.owned = new LinkedHashMap<>(owned);
    if (!ownedBySource.isEmpty()) {
        raw.ownedBySource = new LinkedHashMap<>();
        ownedBySource.forEach((origin, held) -> raw.ownedBySource.put(origin, new LinkedHashMap<>(held)));
    }
    raw.bankScanned = bankScanned;
    return Json.gson().toJson(raw);
}

static PlayerProfile fromJson(String json) {
    Raw raw = Json.gson().fromJson(json, Raw.class);
    return new PlayerProfile(raw.realLevels, raw.boostedLevels, raw.prayerUnlocks,
        new RequirementProfile(raw.requirements.skills, raw.requirements.quests), raw.owned, raw.bankScanned, raw.ownedBySource);
}

// Whether the plugin's cached level/prayer/quest snapshot, taken while
// {@code snapshotAccount} was logged in, may still serve now that the
// client reports {@code currentAccount} (-1 = logged out).
public static boolean snapshotSurvives(long snapshotAccount, long currentAccount) {
    // Logout keeps the last character (field report 2026-09-01: Piety at
    // 45 prayer once the snapshot was dropped and MAXED filled in); only
    // a DIFFERENT account replaces it - including a hashless snapshot
    // taken before the login hash arrived.
    return currentAccount == -1 || currentAccount == snapshotAccount;
}
}
