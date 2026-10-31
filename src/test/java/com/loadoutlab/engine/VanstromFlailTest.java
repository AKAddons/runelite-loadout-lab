package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanstrom Klause in Sins of the Father (ISS-01M5RGMT70PG86MWHPF28DBJ3V,
 * raw on Discord 2026-10-24: "it tells me no usable melee set but I was
 * expecting it to tell me to use my blisterwood flail as the quest needs
 * you to defeat him with it"). The wiki files the flails and the rod of
 * Ivandis as one-handed staves that fight in melee (Bash/Pound/Focus,
 * crush); "no spells are capable of dealing damage to vampyres", and the
 * reference calculator makes a tier-3 vampyre immune to anything but a
 * melee flail/sickle/sunspear or a thrown blisterwood stake.
 */
class VanstromFlailTest {
	private static final int VANSTROM_SOTF = 9567;
	private static final int BLISTERWOOD_FLAIL = 24699;
	private static final int STAFF_OF_FIRE = 1387;
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster vanstrom() {
		return data.searchMonsters("vanstrom klause", 10).stream()
			.filter(m -> m.getId() == VANSTROM_SOTF).findFirst()
			.orElseThrow(() -> new AssertionError("corpus is missing Vanstrom Klause (Sins of the Father)"));
	}

	private static Gear named(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	/** An ironman mid-quest: 70s in combat, Sins of the Father not finished. */
	private static OptimizationRequest request(Style style, CandidateMode mode, Map<Integer, Integer> owned) {
		PlayerLevels levels = new PlayerLevels(70, 70, 70, 70, 70, 52, 70);
		RequirementProfile profile = new RequirementProfile(Map.of(Skill.ATTACK, 70, Skill.STRENGTH, 70,
			Skill.DEFENCE, 70, Skill.RANGED, 70, Skill.MAGIC, 70, Skill.PRAYER, 52, Skill.HITPOINTS, 70), Set.of());
		return new OptimizationRequest(vanstrom(), style, levels, PrayerBonuses.bestAvailable(levels), null, 0,
			mode, true, false, new OwnedItems(owned, true), profile, 5);
	}

	@Test
	@DisplayName("the blisterwood flail, the Ivandis flail and the rod of Ivandis fight in melee; a plain staff still does not")
	void vampyreStavesAreMeleeWeapons() {
		for (String name : List.of("blisterwood flail", "ivandis flail", "rod of ivandis")) {
			assertTrue(named(name).isWeaponFor(Style.MELEE), name + " fights in melee");
		}
		assertTrue(named("blisterwood flail").isWeaponFor(Style.MAGIC), "and the flail still autocasts");
		assertFalse(named("staff of fire").isWeaponFor(Style.MELEE), "an ordinary staff stays out of melee");
	}

	@Test
	@DisplayName("an ironman owning the blisterwood flail gets a melee set for Vanstrom (Sins of the Father) that wields it")
	void ironmanMeleeSetWieldsTheFlail() {
		List<DpsResult> melee = new LoadoutOptimizer().optimize(data,
			request(Style.MELEE, CandidateMode.OWNED_ONLY, Map.of(BLISTERWOOD_FLAIL, 1)));
		assertFalse(melee.isEmpty(), "a melee set exists");
		assertEquals("Blisterwood flail", melee.get(0).getLoadout().getWeapon().getName());
		assertTrue(melee.get(0).getDps() > 0);
	}

	@Test
	@DisplayName("no spell damages a tier-3 vampyre: magic has no set vs Vanstrom, owned flail or best in slot")
	void magicCannotHurtVanstrom() {
		assertTrue(new LoadoutOptimizer().optimize(data, request(Style.MAGIC, CandidateMode.OWNED_ONLY,
			Map.of(BLISTERWOOD_FLAIL, 1, STAFF_OF_FIRE, 1))).isEmpty(), "the flail cannot autocast past the immunity");
		assertTrue(new LoadoutOptimizer().optimize(data, request(Style.MAGIC, CandidateMode.ALL_STANDARD,
			Map.of())).isEmpty(), "nor can any staff in the game");
		Monster vanstrom = vanstrom();
		assertFalse(VampyreRules.canDamage(vanstrom, Style.MAGIC, named("blisterwood flail")));
		assertTrue(VampyreRules.canDamage(vanstrom, Style.MELEE, named("blisterwood flail")));
		assertTrue(VampyreRules.canDamage(vanstrom, Style.RANGED, named("blisterwood stake")));
		assertFalse(VampyreRules.canDamage(vanstrom, Style.MELEE, named("abyssal whip")));
	}
}
