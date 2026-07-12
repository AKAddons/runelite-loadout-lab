package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Revenant / wilderness gear conditionals, mirrored from the wiki calc:
 * avarice heads the non-stacking salve/slayer chain (6/5 vs revenants),
 * charged wilderness weapons get 3/2 accuracy AND damage vs wilderness
 * monsters, and a charged bracelet of ethereum zeroes revenant damage.
 */
class RevenantConditionalsTest
{
	private static LoadoutData data;
	private static MonsterStats revDemon;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		revDemon = data.searchMonsters("revenant demon", 1).get(0);
	}

	private static GearItem byName(String name, String version)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(name)
				&& (version == null || version.equalsIgnoreCase(item.getVersionLower())))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + name + " (" + version + ")");
	}

	private static Loadout worn(GearItem... items)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (GearItem item : items)
		{
			gear.put(item.getSlot(), item);
		}
		return new Loadout(gear);
	}

	private static OptimizationRequest request(MonsterStats monster, CombatStyle style)
	{
		return new OptimizationRequest(monster, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("the wilderness weapon buff needs a wilderness monster AND the charged version")
	void revWeaponBuffGates()
	{
		OptimizationRequest atRevs = request(revDemon, CombatStyle.RANGED);
		OptimizationRequest atGoblin =
			request(data.searchMonsters("goblin", 1).get(0), CombatStyle.RANGED);

		Loadout charged = worn(byName("craw's bow", "charged"));
		Loadout uncharged = worn(byName("craw's bow", "uncharged"));

		assertTrue(DpsCalculator.revWeaponBuff(atRevs, charged, "craw's bow", "webweaver bow"));
		assertFalse(DpsCalculator.revWeaponBuff(atRevs, uncharged, "craw's bow", "webweaver bow"),
			"uncharged gets no buff");
		assertFalse(DpsCalculator.revWeaponBuff(atGoblin, charged, "craw's bow", "webweaver bow"),
			"no buff outside the wilderness");
		assertTrue(DpsCalculator.isRevenant(atRevs));
		assertFalse(DpsCalculator.isRevenant(atGoblin));
	}

	@Test
	@DisplayName("a charged craw's bow at revenants outputs more dps than the raw stats say")
	void crawsBowBuffShowsUpInDps()
	{
		Loadout charged = worn(byName("craw's bow", "charged"));
		DpsResult atRevs = new DpsCalculator().calculate(request(revDemon, CombatStyle.RANGED), charged);
		assertNotNull(atRevs);

		// The same loadout against the same defensive stat sheet without
		// the wilderness gate cannot be built from data (the gate IS the
		// monster), so pin the observable instead: with 3/2 on accuracy
		// and damage the charged bow must clear the strongest unbuffed
		// owned-tier bow in the corpus at revs - the meta this models.
		Loadout msb = worn(byName("magic shortbow", null), byName("amethyst arrow", null));
		DpsResult msbAtRevs = new DpsCalculator().calculate(request(revDemon, CombatStyle.RANGED), msb);
		assertNotNull(msbAtRevs);
		assertTrue(atRevs.getDps() > msbAtRevs.getDps() * 1.4,
			"buffed craw's should dominate: craws=" + atRevs.getDps() + " msb=" + msbAtRevs.getDps());
	}

	@Test
	@DisplayName("avarice heads the salve chain at revenants: it beats the same neck stats unbuffed")
	void avariceBeatsFuryAtRevenants()
	{
		Loadout base = worn(byName("magic shortbow", null), byName("amethyst arrow", null),
			byName("amulet of fury", null));
		Loadout avarice = worn(byName("magic shortbow", null), byName("amethyst arrow", null),
			byName("amulet of avarice", null));

		DpsResult withFury = new DpsCalculator().calculate(request(revDemon, CombatStyle.RANGED), base);
		DpsResult withAvarice = new DpsCalculator().calculate(request(revDemon, CombatStyle.RANGED), avarice);
		assertTrue(withAvarice.getDps() > withFury.getDps(),
			"avarice=" + withAvarice.getDps() + " fury=" + withFury.getDps());
	}

	@Test
	@DisplayName("a charged bracelet of ethereum zeroes revenant incoming damage")
	void ethereumZeroesIncoming()
	{
		Loadout with = worn(byName("bracelet of ethereum", "charged"));
		Loadout without = worn(byName("bracelet of ethereum", "uncharged"));

		IncomingDpsCalculator.Result blocked =
			IncomingDpsCalculator.calculate(revDemon, with, 99, 99);
		IncomingDpsCalculator.Result normal =
			IncomingDpsCalculator.calculate(revDemon, without, 99, 99);

		assertEquals(0.0, blocked.totalDps);
		assertEquals(0.0, blocked.unprayedDps);
		assertTrue(normal.unprayedDps > 0, "uncharged bracelet does not block");
	}
}
