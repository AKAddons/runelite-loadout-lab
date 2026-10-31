package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import com.google.gson.*;
import java.util.*;

// Gear earned by QUESTING rather than bought: quest rewards, reward-chest
// purchases and quest-unlocked creations (barrows gloves, Iban's staff,
// the vampyre flails, Ava's devices, god books...). With the upgrade
// budget active ("show me obtainable gear") these join the candidate
// pool at 0 gp - they cost effort, not coins - and the panel labels them
// with their source quest instead of a price.
// Entries in quest_rewards.json are keyed by lowercase item name
// (Gear.name, no version suffix). Every entry is wiki-verified
// as a quest reward or quest-unlocked acquisition; tradeable boss drops
// merely wield-locked behind a quest (dragon scimitar, anguish) and
// minigame/slayer unlocks (void, defenders, slayer helm) do NOT belong
// here - the curation test enforces that every key resolves to a real
// corpus item.
public class QuestRewardItems {
static final String RESOURCE = "quest_rewards.json";

static final Map<String, String> QUEST_BY_NAME = new HashMap<>();

static {
    // Fail-loud on purpose: a silently empty table would quietly drop
    // every quest reward out of the obtainable-gear pool.
    JsonObject root = JsonResources.objectOrThrow(RESOURCE);
    for (var entry : root.entrySet()) QUEST_BY_NAME.put(Names.lower(entry.getKey()), entry.getValue().getAsJsonObject().get("quest").getAsString());
}


// True when this item is earned through a quest rather than bought.
static boolean isQuestReward(Gear item) { return questFor(item) != null; }

// The source quest's name, or null when the item is not a quest reward.
static String questFor(Gear item) {
    if (item == null) return null;
    return QUEST_BY_NAME.get(item.nameLower);
}
}
