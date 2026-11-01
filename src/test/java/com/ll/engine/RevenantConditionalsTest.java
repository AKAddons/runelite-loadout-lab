package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
class RevenantConditionalsTest {
	private static Tables data;
	private static Monster revDemon;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		revDemon = data.searchMonsters("revenant demon", 1).get(0);
	}

	private static Gear byName(String name, String version) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(name)
				&& (version == null || version.equalsIgnoreCase(item.getVer()))) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + name + " (" + version + ")");
	}

	private static Kit worn(Gear... items) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (Gear item : items) {
			gear.put(item.getSlot(), item);
		}
		return new Kit(gear);
	}

	private static Req request(Monster monster, Style style) {
		return new Req(monster, style, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("the wilderness weapon buff needs a wilderness monster AND the charged version")
	void revWeaponBuffGates() {
		Req atRevs = request(revDemon, Style.RANGED);
		Req atGoblin =
			request(data.searchMonsters("goblin", 1).get(0), Style.RANGED);

		Kit charged = worn(byName("craw's bow", "charged"));
		Kit uncharged = worn(byName("craw's bow", "uncharged"));

		assertTrue(Calc.revWeaponBuff(atRevs, charged, "craw's bow", "webweaver bow"));
		assertFalse(Calc.revWeaponBuff(atRevs, uncharged, "craw's bow", "webweaver bow"),
			"uncharged gets no buff");
		assertFalse(Calc.revWeaponBuff(atGoblin, charged, "craw's bow", "webweaver bow"),
			"no buff outside the wilderness");
		assertTrue(Calc.isRevenant(atRevs));
		assertFalse(Calc.isRevenant(atGoblin));
	}

	@Test
	@DisplayName("a charged craw's bow at revenants outputs more dps than the raw stats say")
	void crawsBowBuffShowsUpInDps() {
		Kit charged = worn(byName("craw's bow", "charged"));
		Dps atRevs = new Calc().calculate(request(revDemon, Style.RANGED), charged);
		assertNotNull(atRevs);

		// The same loadout against the same defensive stat sheet without
		// the wilderness gate cannot be built from data (the gate IS the
		// monster), so pin the observable instead: with 3/2 on accuracy
		// and damage the charged bow must clear the strongest unbuffed
		// owned-tier bow in the corpus at revs - the meta this models.
		Kit msb = worn(byName("magic shortbow", null), byName("amethyst arrow", null));
		Dps msbAtRevs = new Calc().calculate(request(revDemon, Style.RANGED), msb);
		assertNotNull(msbAtRevs);
		assertTrue(atRevs.getDps() > msbAtRevs.getDps() * 1.4,
			"buffed craw's should dominate: craws=" + atRevs.getDps() + " msb=" + msbAtRevs.getDps());
		assertTrue(atRevs.getCountedBonuses().contains("wilderness weapon: +50% accuracy, +50% damage"),
			"the counted-bonus assurance must name the buff with its exact numbers, got: "
				+ atRevs.getCountedBonuses());
	}

	@Test
	@DisplayName("avarice heads the salve chain at revenants: it beats the same neck stats unbuffed")
	void avariceBeatsFuryAtRevenants() {
		Kit base = worn(byName("magic shortbow", null), byName("amethyst arrow", null),
			byName("amulet of fury", null));
		Kit avarice = worn(byName("magic shortbow", null), byName("amethyst arrow", null),
			byName("amulet of avarice", null));

		Dps withFury = new Calc().calculate(request(revDemon, Style.RANGED), base);
		Dps withAvarice = new Calc().calculate(request(revDemon, Style.RANGED), avarice);
		assertTrue(withAvarice.getDps() > withFury.getDps(),
			"avarice=" + withAvarice.getDps() + " fury=" + withFury.getDps());
	}

	@Test
	@DisplayName("Max DPS keeps the offensive gloves over the ethereum bracelet at revenants")
	void maxDpsKeepsGloves() {
		// msb + amethyst arrows + barrows gloves + charged ethereum owned:
		// the small glove dps gain must beat the zero-offense bracelet.
		Owned owned = new Owned(java.util.Map.of(
			861, 1, 21326, 100, 7462, 1, 21816, 1), true);
		Req base = new Req(revDemon, Style.RANGED,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false, owned, RequirementProfile.MAXED, 1);

		Gear dpsHands = new LoadoutOptimizer()
			.optimize(data, base)
			.get(0).getLoadout().get(Slot.HANDS);
		assertNotNull(dpsHands);
		assertEquals("barrows gloves", dpsHands.getLname(),
			"Max DPS must keep the offensive gloves, got: " + dpsHands.getLname());
	}

	@Test
	@DisplayName("a charged bracelet of ethereum zeroes revenant incoming damage")
	void ethereumZeroesIncoming() {
		Kit with = worn(byName("bracelet of ethereum", "charged"));
		Kit without = worn(byName("bracelet of ethereum", "uncharged"));

		Incoming.Result blocked =
			Incoming.calculate(revDemon, with, 99, 99, 0);
		Incoming.Result normal =
			Incoming.calculate(revDemon, without, 99, 99, 0);

		assertEquals(0.0, blocked.tot);
		assertEquals(0.0, blocked.unprayedDps);
		assertTrue(normal.unprayedDps > 0, "uncharged bracelet does not block");
	}
}
