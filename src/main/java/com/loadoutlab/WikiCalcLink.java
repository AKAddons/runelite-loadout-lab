package com.loadoutlab;

import com.google.gson.*;
import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.LinkBrowser;
import okhttp3.*;
import static com.loadoutlab.data.JsonResources.*;

// One-click "open this exact setup in the official wiki calculator":
// builds the calculator's ImportableData shape from a shown result, POSTs
// it to the wiki's shortlink service (the same endpoint the calculator's
// own Share button uses - the ONLY way a full setup can ride a URL), and
// opens {@code ?id=<shortlink>}. Strictly user-initiated; shapes verified
// against weirdgloop/osrs-dps-calc source 2026-07-23 (IMPORT_VERSION 10).
@Slf4j
@lombok.AllArgsConstructor
public class WikiCalcLink {
static final String SHORTLINK_API = "https://tools.runescape.wiki/osrs-dps/shortlink";
static final String CALC_URL = "https://tools.runescape.wiki/osrs-dps/?id=";

// Their Prayer enum ordinals by the tier names our labels use, and
// their Potion enum ordinals by OUR boost labels (the calculator has
// no divine variants - identical numbers - so divines map to the
// base). Data-driven from wiki_calc_ids.json (a token-free resource).
// Fail SOFT like AssumeIcons: this class is constructed in startUp, so
// a static-init throw would kill the whole plugin over a share button;
// WikiCalcLinkTest pins the exact ordinals loudly instead.
static final Map<String, Integer> PRAYER_IDS = new LinkedHashMap<>();
static final Map<String, int[]> POTION_IDS = new LinkedHashMap<>();

static class Ids {
    Map<String, Integer> prayerIds = Map.of();
    Map<String, int[]> potionIds = Map.of();
}

static {
    try {
        Ids ids = read("wiki_calc_ids.json", Ids.class);
        PRAYER_IDS.putAll(ids.prayerIds);
        POTION_IDS.putAll(ids.potionIds);
    } catch (RuntimeException ex) {
        log.warn("wiki calc ids unavailable", ex);
    }
}

final OkHttpClient http;
final Gson gson;

// POST the setup to the shortlink service, then open the calculator
// on the returned id. Fire-and-forget; failures only log.
void open(Monster mob, DpsResult shown, int dartId, String assumes,
    PlayerLevels real, PlayerLevels live, boolean onSlayerTask, boolean inWilderness) {
    Map data = payload(mob, shown, dartId, assumes, real, live, onSlayerTask, inWilderness);
    Request request = new Request.Builder() .url(SHORTLINK_API)
        .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(data))) .build();
    http.newCall(request).enqueue(new Callback() {
        public void onFailure(Call call, IOException e) { log.warn("wiki calc shortlink failed", e); }

        public void onResponse(Call call, Response response) throws IOException {
            try (Response r = response) {
                if (!r.isSuccessful() || r.body() == null) {
                    log.warn("wiki calc shortlink HTTP {}", r.code());
                    return;
                }
                JsonObject body = gson.fromJson(r.body().string(), JsonObject.class);
                if (body == null || !body.has("data")) {
                    log.warn("wiki calc shortlink: unexpected response shape");
                    return;
                }
                LinkBrowser.browse(CALC_URL + body.get("data").getAsString());
            }
        }
    });
}

// The ImportableData document, built pure for testability.
static Map payload(Monster mob, DpsResult shown, int dartId,
    String assumes, PlayerLevels real, PlayerLevels live, boolean onSlayerTask, boolean inWilderness) {
    List<Integer> prayers = new ArrayList<>();
    BoostProfile boost = null;
    List<Integer> potions = new ArrayList<>();
    for (String token : (assumes == null ? "" : assumes).split(" \\+ ")) {
        Integer prayerId = PRAYER_IDS.get(token);
        if (prayerId != null) {
            prayers.add(prayerId);
            continue;
        }
        for (int id : POTION_IDS.getOrDefault(token, new int[0])) potions.add(id);
        for (BoostProfile profile : BoostProfile.values()) if (profile.toString().equals(token)) boost = profile;
    }

    Map equipment = new LinkedHashMap<>();
    Gear quiverAmmo = shown.loadout.quiverAmmo;
    for (GearSlot slot : GearSlot.values()) {
        Gear worn = shown.loadout.get(slot);
        // The calc has no quiver concept: the bolts the quiver carries
        // must ride the AMMO slot or their side prices an empty
        // crossbow (field report 2026-08-21). The displaced blessing
        // only carries prayer bonus - nothing the dps math reads.
        if (slot == GearSlot.AMMO && quiverAmmo != null) worn = quiverAmmo;
        int id = worn != null ? worn.id : -1;
        if (id > 0) {
            // Plain maps/lists ONLY in this document (JsonResources.map
            // is a LinkedHashMap): gson's map adapter reflects the
            // runtime type, and the JDK's private
            // Collections$*/ImmutableCollections classes throw
            // InaccessibleObjectException under the module system
            // (field-found 2026-07-23 - the button silently died).
            Map idMap = map("id", id);
            if (slot == GearSlot.WEAPON && dartId > 0) {
                // The blowpipe's dart rides the WEAPON's itemVars -
                // never the ammo slot (their own harness note: an
                // ammo-slot dart is a hand-thrown weapon; and the
                // blessing usually occupies ammo anyway, so the dart
                // silently vanished - field report 2026-08-21: cow
                // showed a dartless max 20 vs our dragon-dart 28).
                idMap.put("itemVars", map("blowpipeDartId", dartId));
            }
            equipment.put(slot.jsonName, idMap);
        }
    }

    Map buffs = map("potions", potions, "onSlayerTask", onSlayerTask, "inWilderness", inWilderness);
    Gear soulWeapon = shown.loadout.getWeapon();
    if (soulWeapon != null && soulWeapon.nameLower.contains("soulreaper")) {
        // Our sustained model prices the 5-stack steady state - the
        // calc must open there too or it prices ZERO stacks (field
        // 2026-08-21: Jad BiS -23%).
        buffs.put("soulreaperStacks", 5);
    }
    Map loadout = map("name", "Loadout Lab", "skills", skillsMap(real),
        "boosts", boostsMap(boost, real, live), "equipment", equipment, "prayers", prayers, "buffs", buffs);
    Map<String, String> style = styleOf(shown.attackType);
    String spell = shown.getSpellName();
    if (style != null && "Autocast".equals(style.get("stance")) && (spell == null || spell.isEmpty())) {
        // A powered staff: its attackType wears the "magic: <name>"
        // prefix like an autocast, but their POWERED_STAFF category
        // has no Autocast stance - the import fell back to LONGRANGE,
        // 5 ticks (field report 2026-08-21: trident vs cow showed
        // exactly 4/5 of our dps). A real autocast always carries a
        // spell name; a powered staff never does.
        style.put("stance", "Accurate");
    }
    if (style != null) loadout.put("style", style);
    if (spell != null && !spell.isEmpty()) loadout.put("spell", map("name", spell));

    Map inputs = map("defenceReductions", new LinkedHashMap<>());
    if (mob.getToaInvocationLevel() > 0) {
        // The card's Invocation chip - the calculator must open at the
        // same raid level or the cross-check numbers cannot match.
        inputs.put("toaInvocationLevel", mob.getToaInvocationLevel());
    }
    // profileId: a group's synthetic phase variant maps back to the
    // real row - the calculator only knows real npc ids. The RAW wiki
    // version - their import keys on exact strings from the shared
    // weirdgloop data ("Post-quest, Awake") and silently falls back to
    // the FIRST same-id row on a miss (field 2026-08-21: the post-quest
    // Duke opened as Awakened, def 316 vs 275 - a -24% phantom). Our
    // display labels are normalized and must never travel.
    Map monster = map("id", mob.profileId(), "version", mob.getWikiVersion(), "inputs", inputs);
    List<Map> loadouts = new ArrayList<>();
    loadouts.add(loadout);
    return map("serializationVersion", 10, "loadouts", loadouts, "selectedLoadout", 0, "monster", monster);
}

// Their PlayerSkills shape. Mining/herblore are not tracked here -
// 99 keeps the rare pickaxe-dependent checks permissive.
static Map skillsMap(PlayerLevels levels) {
    return map("atk", levels.attack, "str", levels.strength, "def", levels.defence,
        "hp", levels.hitpoints, "ranged", levels.ranged, "magic", levels.magic,
        "prayer", levels.prayer, "mining", 99, "herblore", 99);
}

// The per-skill boost DELTAS the calculator adds to the base skills -
// computed from our own boost model so the math matches exactly.
static Map boostsMap(BoostProfile boost, PlayerLevels real, PlayerLevels live) {
    PlayerLevels boosted = boost == null ? real : boost.apply(real, live);
    return map("atk", boosted.attack - real.attack,
        "str", boosted.strength - real.strength, "def", boosted.defence - real.defence,
        "hp", 0, "ranged", boosted.ranged - real.ranged, "magic", boosted.magic - real.magic,
        "prayer", 0, "mining", 0, "herblore", 0);
}

// Their PlayerCombatStyle {type, stance} from our attack-type strings:
// "slash (aggressive)", "ranged rapid - dragon dart", "magic: Fire
// Surge". The style NAME is omitted - the calculator's math reads only
// type and stance.
static Map<String, String> styleOf(String attackType) {
    if (attackType == null || attackType.isEmpty()) return null;
    Map<String, String> style = new LinkedHashMap<>();
    if (attackType.startsWith("magic")) {
        style.put("type", "magic");
        style.put("stance", attackType.startsWith("magic:") ? "Autocast" : "Accurate");
        return style;
    }
    if (attackType.startsWith("ranged")) {
        style.put("type", "ranged");
        style.put("stance", attackType.contains("rapid") ? "Rapid" : "Accurate");
        return style;
    }
    int cut = attackType.indexOf(" (");
    style.put("type", cut < 0 ? attackType : attackType.substring(0, cut));
    String stance = cut < 0 ? "aggressive" : attackType.substring(cut + 2, attackType.indexOf(')', cut));
    style.put("stance", Names.cap(stance));
    return style;
}
}
