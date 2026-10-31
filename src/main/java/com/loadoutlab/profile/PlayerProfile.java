package com.loadoutlab.profile;

import com.loadoutlab.data.JsonResources;
import com.loadoutlab.engine.*;
import java.util.*;
import net.runelite.api.Skill;

// A player as data: levels (real and live-boosted), prayer unlocks,
// equip requirements (skills + quests), and owned items. Everything a
// query needs - so the optimizer can be driven by a MOCK environment
// (fixtures, the headless runner, replayed bug reports) without a
// running client. The plugin exports the real account to
// .runelite/loadout-lab/profile.json on every query.
public class PlayerProfile {
public final PlayerLevels realLevels;
public final PlayerLevels boostedLevels;
public final PrayerUnlocks prayerUnlocks;
public final RequirementProfile requirements;
public final Map<Integer, Integer> owned;
final boolean bankScanned;
// Optional provenance: origin label ("bank", "STASH", ...) -> items.
// The flat owned map stays the source of truth for the optimizer.
final Map<String, Map<Integer, Integer>> ownedBySource;

public PlayerProfile(PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks, RequirementProfile requirements,
    Map<Integer, Integer> owned, boolean bankScanned) {
    this(realLevels, boostedLevels, prayerUnlocks, requirements, owned, bankScanned, null);
}

public PlayerProfile(PlayerLevels realLevels, PlayerLevels boostedLevels,
    PrayerUnlocks prayerUnlocks, RequirementProfile requirements, Map<Integer, Integer> owned, boolean bankScanned,
    Map<String, Map<Integer, Integer>> ownedBySource) {
    this.realLevels = realLevels;
    this.boostedLevels = boostedLevels == null ? realLevels : boostedLevels;
    this.prayerUnlocks = prayerUnlocks == null ? PrayerUnlocks.ALL : prayerUnlocks;
    this.requirements = requirements == null ? RequirementProfile.MAXED : requirements;
    this.owned = owned == null ? Map.of() : Map.copyOf(owned);
    this.bankScanned = bankScanned;
    var copy = new LinkedHashMap<String, Map<Integer, Integer>>();
    if (ownedBySource != null) for (var e : ownedBySource.entrySet()) copy.put(e.getKey(), Map.copyOf(e.getValue()));
    this.ownedBySource = copy;
}

public OwnedItems ownedItems() { return new OwnedItems(owned, bankScanned); }

// The file's shape, Gson both ways: the levels and the prayer unlocks
// are their own classes' fields; the requirements write as skills and
// quests. Plain maps and sets only (Gson cannot see into Map.of).
static class Raw {
    public PlayerLevels realLevels, boostedLevels;
    public PrayerUnlocks prayerUnlocks;
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
    raw.requirements.quests.addAll(requirements.getCompletedQuests());
    raw.owned = new LinkedHashMap<>(owned);
    if (!ownedBySource.isEmpty()) {
        raw.ownedBySource = new LinkedHashMap<>();
        ownedBySource.forEach((origin, held) -> raw.ownedBySource.put(origin, new LinkedHashMap<>(held)));
    }
    raw.bankScanned = bankScanned;
    return JsonResources.gson().toJson(raw);
}

static PlayerProfile fromJson(String json) {
    Raw raw = JsonResources.gson().fromJson(json, Raw.class);
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
