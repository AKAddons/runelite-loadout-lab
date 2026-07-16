package com.loadoutlab.ui;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.SpriteID;

/**
 * Icon lookups for the compact "assumes" rows: prayer names to prayer-book
 * sprites, boost labels to potion/heart item ids, and spell names to
 * spellbook sprites - so the panel can show a small icon instead of a
 * sentence. Names must match PrayerBonuses / BoostProfile / SpellStats
 * exactly; unmapped names fall back to plain text.
 */
final class AssumeIcons
{
	private static final Map<String, Integer> PRAYERS = new HashMap<>();
	private static final Map<String, Integer> BOOST_ITEMS = new HashMap<>();
	private static final Map<String, Integer> SPELLS = new HashMap<>();

	static final int MARK_OF_DARKNESS = SpriteID.MagicNecroOn.MARK_OF_DARKNESS;

	static
	{
		PRAYERS.put("Piety", SpriteID.Prayeron.PIETY);
		PRAYERS.put("Chivalry", SpriteID.Prayeron.CHIVALRY);
		PRAYERS.put("Ultimate Strength", SpriteID.Prayeron.ULTIMATE_STRENGTH);
		PRAYERS.put("Incredible Reflexes", SpriteID.Prayeron.INCREDIBLE_REFLEXES);
		PRAYERS.put("Superhuman Strength", SpriteID.Prayeron.SUPERHUMAN_STRENGTH);
		PRAYERS.put("Improved Reflexes", SpriteID.Prayeron.IMPROVED_REFLEXES);
		PRAYERS.put("Burst of Strength", SpriteID.Prayeron.BURST_OF_STRENGTH);
		PRAYERS.put("Clarity of Thought", SpriteID.Prayeron.CLARITY_OF_THOUGHT);
		PRAYERS.put("Sharp Eye", SpriteID.Prayeron.SHARP_EYE);
		PRAYERS.put("Hawk Eye", SpriteID.Prayeron.HAWK_EYE);
		PRAYERS.put("Mystic Will", SpriteID.Prayeron.MYSTIC_WILL);
		PRAYERS.put("Mystic Lore", SpriteID.Prayeron.MYSTIC_LORE);
		PRAYERS.put("Rigour", SpriteID.Prayeron.RIGOUR);
		PRAYERS.put("Deadeye", SpriteID.Prayeron.DEADEYE);
		PRAYERS.put("Eagle Eye", SpriteID.Prayeron.EAGLE_EYE);
		PRAYERS.put("Augury", SpriteID.Prayeron.AUGURY);
		PRAYERS.put("Mystic Vigour", SpriteID.Prayeron.MYSTIC_VIGOUR);
		PRAYERS.put("Mystic Might", SpriteID.Prayeron.MYSTIC_MIGHT);
		PRAYERS.put("Protect from Melee", SpriteID.Prayeron.PROTECT_FROM_MELEE);
		PRAYERS.put("Protect from Missiles", SpriteID.Prayeron.PROTECT_FROM_MISSILES);
		PRAYERS.put("Protect from Magic", SpriteID.Prayeron.PROTECT_FROM_MAGIC);

		// BoostProfile labels -> the item the numbers assume you drink.
		BOOST_ITEMS.put("Super combat", 12695);
		BOOST_ITEMS.put("Attack & strength potions", 113); // strength potion(4)
		BOOST_ITEMS.put("Ranging potion", 2444);
		BOOST_ITEMS.put("Super ranging", 11722);
		BOOST_ITEMS.put("Magic potion", 3040);
		BOOST_ITEMS.put("Super magic", 11726);
		BOOST_ITEMS.put("Saturated heart", 27641);
		BOOST_ITEMS.put("Imbued heart", 20724);

		// Standard book (Magicon / Magicon2).
		SPELLS.put("Wind Strike", SpriteID.Magicon.WIND_STRIKE);
		SPELLS.put("Water Strike", SpriteID.Magicon.WATER_STRIKE);
		SPELLS.put("Earth Strike", SpriteID.Magicon.EARTH_STRIKE);
		SPELLS.put("Fire Strike", SpriteID.Magicon.FIRE_STRIKE);
		SPELLS.put("Wind Bolt", SpriteID.Magicon.WIND_BOLT);
		SPELLS.put("Water Bolt", SpriteID.Magicon.WATER_BOLT);
		SPELLS.put("Earth Bolt", SpriteID.Magicon.EARTH_BOLT);
		SPELLS.put("Fire Bolt", SpriteID.Magicon.FIRE_BOLT);
		SPELLS.put("Wind Blast", SpriteID.Magicon.WIND_BLAST);
		SPELLS.put("Water Blast", SpriteID.Magicon.WATER_BLAST);
		SPELLS.put("Earth Blast", SpriteID.Magicon.EARTH_BLAST);
		SPELLS.put("Fire Blast", SpriteID.Magicon.FIRE_BLAST);
		SPELLS.put("Wind Wave", SpriteID.Magicon.WIND_WAVE);
		SPELLS.put("Water Wave", SpriteID.Magicon.WATER_WAVE);
		SPELLS.put("Earth Wave", SpriteID.Magicon.EARTH_WAVE);
		SPELLS.put("Fire Wave", SpriteID.Magicon.FIRE_WAVE);
		SPELLS.put("Wind Surge", SpriteID.Magicon2.WIND_SURGE);
		SPELLS.put("Water Surge", SpriteID.Magicon2.WATER_SURGE);
		SPELLS.put("Earth Surge", SpriteID.Magicon2.EARTH_SURGE);
		SPELLS.put("Fire Surge", SpriteID.Magicon2.FIRE_SURGE);
		SPELLS.put("Crumble Undead", SpriteID.Magicon.CRUMBLE_UNDEAD);
		SPELLS.put("Iban Blast", SpriteID.Magicon.IBAN_BLAST);
		SPELLS.put("Magic Dart", SpriteID.Magicon2.MAGIC_DART);
		SPELLS.put("Flames of Zamorak", SpriteID.Magicon.FLAMES_OF_ZAMORAK);
		SPELLS.put("Claws of Guthix", SpriteID.Magicon.CLAWS_OF_GUTHIX);
		SPELLS.put("Saradomin Strike", SpriteID.Magicon.SARADOMIN_STRIKE);
		SPELLS.put("Confuse", SpriteID.Magicon.CONFUSE);
		SPELLS.put("Weaken", SpriteID.Magicon.WEAKEN);
		SPELLS.put("Curse", SpriteID.Magicon.CURSE);
		SPELLS.put("Vulnerability", SpriteID.Magicon.VULNERABILITY);
		SPELLS.put("Enfeeble", SpriteID.Magicon.ENFEEBLE);
		SPELLS.put("Stun", SpriteID.Magicon.STUN);
		SPELLS.put("Bind", SpriteID.Magicon2.BIND);
		SPELLS.put("Snare", SpriteID.Magicon2.SNARE);
		SPELLS.put("Entangle", SpriteID.Magicon2.ENTANGLE);
		SPELLS.put("Tele Block", SpriteID.Magicon2.TELE_BLOCK);

		// Ancients.
		SPELLS.put("Smoke Rush", SpriteID.Magicon2.SMOKE_RUSH);
		SPELLS.put("Smoke Burst", SpriteID.Magicon2.SMOKE_BURST);
		SPELLS.put("Smoke Blitz", SpriteID.Magicon2.SMOKE_BLITZ);
		SPELLS.put("Smoke Barrage", SpriteID.Magicon2.SMOKE_BARRAGE);
		SPELLS.put("Shadow Rush", SpriteID.Magicon2.SHADOW_RUSH);
		SPELLS.put("Shadow Burst", SpriteID.Magicon2.SHADOW_BURST);
		SPELLS.put("Shadow Blitz", SpriteID.Magicon2.SHADOW_BLITZ);
		SPELLS.put("Shadow Barrage", SpriteID.Magicon2.SHADOW_BARRAGE);
		SPELLS.put("Blood Rush", SpriteID.Magicon2.BLOOD_RUSH);
		SPELLS.put("Blood Burst", SpriteID.Magicon2.BLOOD_BURST);
		SPELLS.put("Blood Blitz", SpriteID.Magicon2.BLOOD_BLITZ);
		SPELLS.put("Blood Barrage", SpriteID.Magicon2.BLOOD_BARRAGE);
		SPELLS.put("Ice Rush", SpriteID.Magicon2.ICE_RUSH);
		SPELLS.put("Ice Burst", SpriteID.Magicon2.ICE_BURST);
		SPELLS.put("Ice Blitz", SpriteID.Magicon2.ICE_BLITZ);
		SPELLS.put("Ice Barrage", SpriteID.Magicon2.ICE_BARRAGE);

		// Arceuus.
		SPELLS.put("Inferior Demonbane", SpriteID.MagicNecroOn.INFERIOR_DEMONBANE);
		SPELLS.put("Superior Demonbane", SpriteID.MagicNecroOn.SUPERIOR_DEMONBANE);
		SPELLS.put("Dark Demonbane", SpriteID.MagicNecroOn.DARK_DEMONBANE);
		SPELLS.put("Ghostly Grasp", SpriteID.MagicNecroOn.GHOSTLY_GRASP);
		SPELLS.put("Skeletal Grasp", SpriteID.MagicNecroOn.SKELETAL_GRASP);
		SPELLS.put("Undead Grasp", SpriteID.MagicNecroOn.UNDEAD_GRASP);
		SPELLS.put("Lesser Corruption", SpriteID.MagicNecroOn.LESSER_CORRUPTION);
		SPELLS.put("Greater Corruption", SpriteID.MagicNecroOn.GREATER_CORRUPTION);
		SPELLS.put("Dark Lure", SpriteID.MagicNecroOn.DARK_LURE);
	}

	private AssumeIcons()
	{
	}

	/** Prayer-book sprite id for this prayer name, or -1. */
	static int prayerSprite(String name)
	{
		return PRAYERS.getOrDefault(name, -1);
	}

	/** Item id whose icon represents this boost label, or -1. */
	static int boostItem(String label)
	{
		return BOOST_ITEMS.getOrDefault(label, -1);
	}

	/** Spellbook sprite id for this spell name, or -1. */
	static int spellSprite(String name)
	{
		return SPELLS.getOrDefault(name, -1);
	}
}
