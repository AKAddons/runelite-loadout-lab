package com.loadoutlab.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Per-NPC combat mechanics the stat sheets cannot express: style
 * immunities, the NPCs whose magic defence uses their Defence level, and
 * damage-modifier rules. The id lists are vendored from the official
 * calculator's constants (game facts, regenerated with the data - see
 * scripts/official-harness/README.md); the weapon rules are wiki-sourced.
 */
public final class MonsterMechanics
{
	private static final String RESOURCE = "/com/loadoutlab/data/npc_mechanics.json.gz";

	private static final Set<Integer> IMMUNE_MAGIC = new HashSet<>();
	private static final Set<Integer> IMMUNE_RANGED = new HashSet<>();
	private static final Set<Integer> IMMUNE_MELEE = new HashSet<>();
	private static final Set<Integer> SALAMANDER_ONLY_MELEE = new HashSet<>();
	private static final Set<Integer> MAGIC_DEFENCE_BY_DEF_LEVEL = new HashSet<>();
	private static final Set<Integer> ZULRAH = new HashSet<>();
	private static final Set<Integer> VESPULA = new HashSet<>();
	private static final Set<Integer> GUARDIANS = new HashSet<>();
	private static final Set<Integer> TEKTON = new HashSet<>();
	private static final Set<Integer> ICE_DEMON = new HashSet<>();

	static
	{
		try (InputStream stream = MonsterMechanics.class.getResourceAsStream(RESOURCE);
			InputStreamReader reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			com.loadoutlab.data.JsonResources.ints(root, "immuneMagic", IMMUNE_MAGIC);
			com.loadoutlab.data.JsonResources.ints(root, "immuneRanged", IMMUNE_RANGED);
			com.loadoutlab.data.JsonResources.ints(root, "immuneMelee", IMMUNE_MELEE);
			com.loadoutlab.data.JsonResources.ints(root, "immuneNonSalamanderMelee", SALAMANDER_ONLY_MELEE);
			com.loadoutlab.data.JsonResources.ints(root, "usesDefenceLevelForMagic", MAGIC_DEFENCE_BY_DEF_LEVEL);
			com.loadoutlab.data.JsonResources.ints(root, "zulrah", ZULRAH);
			com.loadoutlab.data.JsonResources.ints(root, "vespula", VESPULA);
			com.loadoutlab.data.JsonResources.ints(root, "guardians", GUARDIANS);
			com.loadoutlab.data.JsonResources.ints(root, "tekton", TEKTON);
			com.loadoutlab.data.JsonResources.ints(root, "iceDemon", ICE_DEMON);
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("Could not load " + RESOURCE, ex);
		}
	}


	private MonsterMechanics()
	{
	}

	/** Style-level immunity, independent of the weapon (panel messaging). */
	public static boolean styleImmune(MonsterStats monster, CombatStyle style)
	{
		if (monster == null)
		{
			return false;
		}
		// Data-driven immunity (synthetic group variants - the tormented
		// demon shield phases): the attribute wins before the id lists.
		if (monster.hasAttribute("immune_" + style.name().toLowerCase(Locale.ROOT)))
		{
			return true;
		}
		switch (style)
		{
			case MAGIC: return IMMUNE_MAGIC.contains(monster.getId());
			case RANGED: return IMMUNE_RANGED.contains(monster.getId());
			case MELEE: return IMMUNE_MELEE.contains(monster.getId()) && !ZULRAH.contains(monster.getId());
			default: return false;
		}
	}

	/** Full immunity check for a concrete loadout (mirrors the official calc). */
	public static boolean isImmune(MonsterStats monster, CombatStyle style, Loadout loadout, SpellStats spell)
	{
		if (monster == null)
		{
			return false;
		}
		// Data-driven immunity (synthetic group variants) gates the
		// calculator too - no loadout reaches a shielded style.
		if (monster.hasAttribute("immune_" + style.name().toLowerCase(Locale.ROOT)))
		{
			return true;
		}
		int id = monster.getId();
		GearItem weapon = loadout.getWeapon();
		String category = weapon == null ? "" : weapon.getCategory();
		if (style == CombatStyle.MAGIC && IMMUNE_MAGIC.contains(id))
		{
			return true;
		}
		if (style == CombatStyle.RANGED && IMMUNE_RANGED.contains(id))
		{
			return true;
		}
		if (style == CombatStyle.MELEE)
		{
			if (IMMUNE_MELEE.contains(id))
			{
				// Zulrah can be reached with a polearm.
				return !(ZULRAH.contains(id) && "Polearm".equals(category));
			}
			if (VESPULA.contains(id))
			{
				return true; // immune to melee despite the polearm rule for flying
			}
			if (SALAMANDER_ONLY_MELEE.contains(id) && !"Salamander".equals(category))
			{
				return true;
			}
			if (GUARDIANS.contains(id) && !"Pickaxe".equals(category))
			{
				return true;
			}
		}
		if (monster.hasAttribute("leafy") && !leafBladed(style, loadout, spell))
		{
			return true;
		}
		return false;
	}

	/** Leafy monsters (turoths/kurasks): leaf-bladed melee, broad ammo, or Magic Dart. */
	private static boolean leafBladed(CombatStyle style, Loadout loadout, SpellStats spell)
	{
		GearItem weapon = loadout.getWeapon();
		String weaponName = weapon == null ? "" : weapon.getNameLower();
		if (style == CombatStyle.MELEE)
		{
			return weaponName.startsWith("leaf-bladed");
		}
		if (style == CombatStyle.RANGED)
		{
			GearItem ammo = loadout.get(GearSlot.AMMO);
			String ammoName = ammo == null ? "" : ammo.getNameLower();
			return ammoName.contains("broad");
		}
		return spell != null && "Magic Dart".equals(spell.getName());
	}

	/** Weapon-level pre-filter for candidate selection (conservative: only
	 * prunes when the weapon alone decides; ammo/spell-dependent cases pass). */
	public static boolean weaponCanEverWork(MonsterStats monster, CombatStyle style, GearItem weapon)
	{
		if (monster == null || style != CombatStyle.MELEE)
		{
			return true;
		}
		int id = monster.getId();
		String category = weapon == null ? "" : weapon.getCategory();
		if (IMMUNE_MELEE.contains(id) && !(ZULRAH.contains(id) && "Polearm".equals(category)))
		{
			return false;
		}
		if (VESPULA.contains(id))
		{
			return false;
		}
		if (SALAMANDER_ONLY_MELEE.contains(id) && !"Salamander".equals(category))
		{
			return false;
		}
		if (GUARDIANS.contains(id) && !"Pickaxe".equals(category))
		{
			return false;
		}
		if (monster.hasAttribute("leafy")
			&& !weapon.getNameLower().startsWith("leaf-bladed"))
		{
			return false;
		}
		return true;
	}

	/** Some NPCs' magic defence rolls use their Defence level, not Magic. */
	public static boolean magicDefenceUsesDefenceLevel(MonsterStats monster)
	{
		return monster != null && MAGIC_DEFENCE_BY_DEF_LEVEL.contains(monster.getId());
	}

	/**
	 * Damage scale from per-monster rules (applied like the vampyre factor):
	 * Corporeal Beast halves everything except stab spears/halberds/fang and
	 * magic; Kraken takes 1/7 from ranged; Tekton 1/5 from magic; the CoX Ice
	 * demon 1/3 unless fire spells or demonbane; Slagilith 1/3 without a
	 * pickaxe; zogres quarter damage (Crumble Undead: half).
	 */
	public static double damageFactor(MonsterStats monster, CombatStyle style,
		Loadout loadout, String attackType, SpellStats spell)
	{
		if (monster == null)
		{
			return 1.0;
		}
		String name = monster.getName();
		GearItem weapon = loadout.getWeapon();
		String weaponName = weapon == null ? "" : weapon.getNameLower();
		if ("Corporeal Beast".equalsIgnoreCase(name) && !corpbane(style, weaponName, attackType))
		{
			return 0.5;
		}
		if (("Kraken".equalsIgnoreCase(name) || "Cave kraken".equalsIgnoreCase(name))
			&& style == CombatStyle.RANGED)
		{
			return 1.0 / 7.0;
		}
		if (TEKTON.contains(monster.getId()) && style == CombatStyle.MAGIC)
		{
			return 0.2;
		}
		if (ICE_DEMON.contains(monster.getId()))
		{
			boolean fire = spell != null && "fire".equals(spell.getElement());
			if (!fire)
			{
				return 1.0 / 3.0;
			}
		}
		if ("Slagilith".equalsIgnoreCase(name) && !"Pickaxe".equals(weapon == null ? "" : weapon.getCategory()))
		{
			return 1.0 / 3.0;
		}
		if ("Zogre".equalsIgnoreCase(name) || "Skogre".equalsIgnoreCase(name) || "Slash Bash".equalsIgnoreCase(name))
		{
			if (spell != null && "Crumble Undead".equals(spell.getName()))
			{
				return 0.5;
			}
			return 0.25;
		}
		return 1.0;
	}

	private static boolean corpbane(CombatStyle style, String weaponName, String attackType)
	{
		if (style == CombatStyle.MAGIC)
		{
			return true;
		}
		if (style != CombatStyle.MELEE || attackType == null || !attackType.startsWith("stab"))
		{
			return false;
		}
		return weaponName.contains("osmumten's fang")
			|| weaponName.endsWith("halberd")
			|| (weaponName.contains("spear") && !weaponName.equals("blue moon spear"));
	}
}
