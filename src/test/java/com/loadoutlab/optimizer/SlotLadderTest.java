package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The slot ladder (ISS-01M5GE9EJSX6YWCKMSYEWKZ132, Andrew 2026-10-21): per
 * slot, the two items just above yours (any item) and just below (owned
 * only), ranked by swapping that slot alone under the active mode.
 */
class SlotLadderTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static int id(String nameLower) {
		// Exact name first, else the first base item the name starts
		// (the corpus spells some charged/bonus forms with a suffix).
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst()
			.or(() -> data.getGearItems().stream()
				.filter(g -> g.getNameLower().startsWith(nameLower) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst())
			.orElseThrow(() -> new AssertionError("corpus is missing " + nameLower)).getId();
	}

	/** Specs off unless a test turns them on (the spec swaps). */
	private static boolean withSpec;

	private static OptimizerService.StyleResult run(String mob, String mode, String... owned) throws Exception {
		return run(Style.MELEE, mob, mode, owned);
	}

	private static OptimizerService.StyleResult run(Style style, String mob, String mode, String... owned)
		throws Exception {
		return run(style, data.searchMonsters(mob, 1).get(0), mode, Map.of(), java.util.Set.of(), owned);
	}

	private static OptimizerService.StyleResult run(Style style, Monster monster, String mode,
		Map<GearSlot, Integer> pins, java.util.Set<Integer> dreams, String... owned) throws Exception {
		Map<Integer, Integer> bank = new HashMap<>();
		for (String name : owned) {
			bank.put(id(name), 1);
		}
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ComputeParams p = new ComputeParams();
			p.specWeapon = withSpec;
			p.optimizeMode = mode;
			service.bestPerStyle(monster, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(data.canonicalizeOwned(bank), true), 1, p,
				Collections.emptyMap(), dreams, pins.isEmpty() ? Collections.emptyMap() : Map.of(style, pins), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			return out.get().get(style);
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("Max DPS: two any-item rungs just above the whip, two owned rungs just below")
	void maxDpsLadder() throws Exception {
		OptimizerService.StyleResult melee = run("abyssal demon", "MAX_DPS",
			"abyssal whip", "dragon scimitar", "rune scimitar", "fighter torso", "dragon defender");
		assertEquals("Abyssal whip", melee.owned.get(0).getLoadout().getWeapon().getName());
		OptimizerService.Ladder weapon = melee.ladder.get(GearSlot.WEAPON);
		assertNotNull(weapon, "the weapon slot has a ladder");
		double worn = melee.owned.get(0).getDps();

		assertEquals(2, weapon.up.size(), "two rungs up");
		assertTrue(weapon.up.get(0).dps > worn && weapon.up.get(1).dps > worn, "up is better");
		assertTrue(weapon.up.get(0).dps <= weapon.up.get(1).dps, "the closest step up first");

		assertEquals(List.of("Dragon scimitar", "Rune scimitar"),
			weapon.down.stream().map(r -> r.item.getName()).collect(java.util.stream.Collectors.toList()),
			"down is your own gear, the closest step down first");
		assertTrue(weapon.down.stream().allMatch(r -> r.owned && r.dps < worn));

		// The card's strip reads a signed gain: up is positive, down negative.
		Map<String, Object> node = com.loadoutlab.model.RenderModel.ladderNode(weapon);
		for (Object rung : (List<?>) node.get("up")) {
			assertTrue(((Number) ((Map<?, ?>) rung).get("gain")).doubleValue() > 0);
		}
		for (Object rung : (List<?>) node.get("down")) {
			assertTrue(((Number) ((Map<?, ?>) rung).get("gain")).doubleValue() < 0);
		}
		assertNotNull(node.get("base"), "the worn column's value");
	}

	@Test
	@DisplayName("Tanky ranks the same swaps by damage taken")
	void tankyLadder() throws Exception {
		OptimizerService.StyleResult melee = run("blue moon", "TANKY",
			"abyssal whip", "fighter torso", "dragon defender", "rune platebody", "bandos chestplate");
		OptimizerService.Ladder body = melee.ladder.get(GearSlot.BODY);
		assertNotNull(body);
		double worn = melee.incoming.totalDps;
		for (OptimizerService.Rung rung : body.up) {
			assertTrue(rung.dtps < worn, rung.item.getName() + " takes less damage");
		}
		for (OptimizerService.Rung rung : body.down) {
			assertTrue(rung.owned && rung.dtps > worn, rung.item.getName() + " takes more damage");
		}
	}

	@Test
	@DisplayName("five columns: two each way, a short side's spare spots go to the other side")
	void fillRule() {
		assertArrayEquals(new int[]{2, 2}, OptimizerService.Ladder.spots(4, 4));
		assertArrayEquals(new int[]{0, 4}, OptimizerService.Ladder.spots(0, 4), "BiS worn: four steps below");
		assertArrayEquals(new int[]{4, 0}, OptimizerService.Ladder.spots(4, 0), "nothing owned below: four above");
		assertArrayEquals(new int[]{3, 1}, OptimizerService.Ladder.spots(4, 1));
		assertArrayEquals(new int[]{1, 3}, OptimizerService.Ladder.spots(1, 4));
		assertArrayEquals(new int[]{1, 1}, OptimizerService.Ladder.spots(1, 1), "fewer than five when that is all");
	}

	@Test
	@DisplayName("nothing owned below the whip: four steps above it")
	void fillsUpward() throws Exception {
		OptimizerService.StyleResult melee = run("abyssal demon", "MAX_DPS",
			"abyssal whip", "fighter torso", "dragon defender");
		OptimizerService.Ladder weapon = melee.ladder.get(GearSlot.WEAPON);
		assertNotNull(weapon);
		assertEquals(0, weapon.down.size());
		assertEquals(4, weapon.up.size(), "the spare spots go up");
		for (int i = 1; i < weapon.up.size(); i++) {
			assertTrue(weapon.up.get(i - 1).dps < weapon.up.get(i).dps, "distinct, closest first");
		}
	}

	@Test
	@DisplayName("a bowfa's swaps reach the twisted bow - the arrows come with it")
	void bowfaToTwistedBow() throws Exception {
		OptimizerService.StyleResult ranged = run(Style.RANGED, "zulrah", "MAX_DPS",
			"bow of faerdhinen", "crystal helm", "crystal body", "crystal legs", "dragon arrow");
		assertTrue(ranged.owned.get(0).getLoadout().getWeapon().getNameLower().startsWith("bow of faerdhinen"));
		OptimizerService.Ladder weapon = ranged.ladder.get(GearSlot.WEAPON);
		assertNotNull(weapon);
		OptimizerService.Rung tbow = weapon.up.stream()
			.filter(r -> r.item.getNameLower().equals("twisted bow")).findFirst()
			.orElseThrow(() -> new AssertionError("no twisted bow above the bowfa: "
				+ weapon.up.stream().map(r -> r.item.getName()).collect(java.util.stream.Collectors.toList())));
		assertNotNull(tbow.with, "the twisted bow needs arrows");
		assertTrue(tbow.with.getNameLower().startsWith("dragon arrow"), tbow.with.getName());
		Map<String, Object> node = com.loadoutlab.model.RenderModel.ladderNode(weapon);
		assertTrue(((List<?>) node.get("up")).stream()
			.anyMatch(r -> String.valueOf(((Map<?, ?>) r).get("with")).startsWith("Dragon arrow")),
			"the card names the ammo");
	}

	@Test
	@DisplayName("a twisted bow's swaps reach down to the owned bowfa")
	void twistedBowToBowfa() throws Exception {
		OptimizerService.StyleResult ranged = run(Style.RANGED, "zulrah", "MAX_DPS",
			"twisted bow", "dragon arrow", "bow of faerdhinen");
		assertEquals("twisted bow", ranged.owned.get(0).getLoadout().getWeapon().getNameLower());
		OptimizerService.Ladder weapon = ranged.ladder.get(GearSlot.WEAPON);
		assertNotNull(weapon);
		assertTrue(weapon.down.stream().anyMatch(r -> r.item.getNameLower().startsWith("bow of faerdhinen")),
			weapon.down.stream().map(r -> r.item.getName()).collect(java.util.stream.Collectors.toList()).toString());
	}

	/** Andrew's own Ba-Ba card (2026-10-22 paste): invocation 300, the fang
	 * pinned and simmed with the rapier, his melee kit plus his spare boots. */
	private static OptimizerService.StyleResult baba(String mode, Map<GearSlot, Integer> extraPins) throws Exception {
		Map<GearSlot, Integer> pins = new java.util.EnumMap<>(GearSlot.class);
		pins.put(GearSlot.WEAPON, id("osmumten's fang"));
		pins.putAll(extraPins);
		return run(Style.MELEE, data.searchMonsters("ba-ba", 1).get(0).withToaInvocation(300), mode, pins,
			java.util.Set.of(id("osmumten's fang"), id("ghrazi rapier")),
			"neitiznot faceguard", "fire cape", "amulet of torture", "rada's blessing 4", "bandos chestplate",
			"dragon defender", "blood moon tassets", "ferocious gloves", "primordial boots", "berserker ring (i)",
			"boots of the eye", "rune boots", "climbing boots", "dragon boots");
	}

	private static List<String> names(List<OptimizerService.Rung> rungs) {
		return rungs.stream().map(r -> r.item.getName()).collect(java.util.stream.Collectors.toList());
	}

	@Test
	@DisplayName("equal-DPS boots of yours stand side by side, best stats first - not the no-stats pair")
	void tiedBootsUnfold() throws Exception {
		OptimizerService.StyleResult melee = baba("MAX_DPS", Map.of());
		assertEquals("Primordial boots", melee.owned.get(0).getLoadout().get(GearSlot.FEET).getName());
		OptimizerService.Ladder feet = melee.ladder.get(GearSlot.FEET);
		assertEquals(List.of("Dragon boots", "Rune boots"), names(feet.down),
			"Dragon, Rune and Climbing boots tie at one max hit - the strongest two show (Rune: same +2 str as Climbing, more defence), not Boots of the Eye");
	}

	@Test
	@DisplayName("a body tied on DPS but with better stats is a 'same' step up")
	void tiedBetterStatsStepUp() throws Exception {
		OptimizerService.StyleResult melee = baba("MAX_DPS", Map.of());
		Gear bandos = melee.owned.get(0).getLoadout().get(GearSlot.BODY);
		assertEquals("Bandos chestplate", bandos.getName());
		double worn = melee.owned.get(0).getDps();
		OptimizerService.Ladder body = melee.ladder.get(GearSlot.BODY);
		OptimizerService.Rung same = body.up.stream().filter(r -> Math.abs(r.dps - worn) < 1e-9).findFirst()
			.orElseThrow(() -> new AssertionError("no equal-DPS step up: " + names(body.up)));
		assertTrue(same.item.roughScore(Style.MELEE) >= bandos.roughScore(Style.MELEE), same.item.getName());
		Map<?, ?> node = (Map<?, ?>) ((List<?>) com.loadoutlab.model.RenderModel.ladderNode(body).get("up")).get(0);
		assertEquals(Boolean.TRUE, node.get("same"), "the card says it is the same result here");
	}

	@Test
	@DisplayName("a tried step keeps at least the DPS it promised")
	void triedStepKeepsItsDps() throws Exception {
		OptimizerService.StyleResult melee = baba("MAX_DPS", Map.of());
		OptimizerService.Ladder body = melee.ladder.get(GearSlot.BODY);
		OptimizerService.Rung top = body.up.get(body.up.size() - 1);
		OptimizerService.StyleResult tried = baba("MAX_DPS", Map.of(GearSlot.BODY, top.item.getId()));
		assertEquals(top.item.getId(), tried.owned.get(0).getLoadout().get(GearSlot.BODY).getId());
		assertTrue(tried.owned.get(0).getDps() >= top.dps - 1e-6,
			top.item.getName() + " promised " + top.dps + ", the card shows " + tried.owned.get(0).getDps());
	}

	@Test
	@DisplayName("Balanced steps carry both the DPS change and the damage-taken change")
	void balancedShowsBoth() {
		Gear torva = data.getGear(id("torva platebody"));
		OptimizerService.Ladder ladder = new OptimizerService.Ladder(OptimizerService.OptimizeMode.BALANCED,
			8.0, 2.0, List.of(new OptimizerService.Rung(torva, 8.5, 1.8, false, null)), List.of());
		Map<String, Object> node = com.loadoutlab.model.RenderModel.ladderNode(ladder);
		assertEquals("8.00 DPS", node.get("base"));
		assertEquals("2.00 taken/s", node.get("base2"));
		Map<?, ?> rung = (Map<?, ?>) ((List<?>) node.get("up")).get(0);
		assertEquals("+0.50 DPS", rung.get("delta"));
		assertEquals("-0.20 taken/s", rung.get("delta2"));
	}

	@Test
	@DisplayName("the spec weapon has swaps too: better specs above, your weaker ones below, ranked by DPS added")
	void specSwaps() throws Exception {
		OptimizerService.StyleResult melee;
		withSpec = true;
		try {
			melee = run("abyssal demon", "MAX_DPS",
				"abyssal whip", "fighter torso", "dragon defender", "dragon dagger", "dragon claws");
		} finally {
			withSpec = false;
		}
		assertNotNull(melee.specWeapon, "a spec weapon is carried");
		OptimizerService.Ladder spec = melee.specLadder;
		assertNotNull(spec, "Andrew 2026-10-22: 'not seeing the ladder view for the spec weapon slot'");
		for (OptimizerService.Rung rung : spec.up) {
			assertTrue(rung.dps > spec.baseDps, rung.item.getName() + " adds more");
		}
		for (OptimizerService.Rung rung : spec.down) {
			assertTrue(rung.owned && rung.dps < spec.baseDps, rung.item.getName() + " is yours and adds less");
		}
		assertFalse(spec.up.isEmpty(), "something adds more than " + melee.specWeapon.getName());
		assertTrue(spec.up.size() + spec.down.size() <= OptimizerService.Ladder.SPOTS);
	}

	@Test
	@DisplayName("a one-hander competes as weapon + offhand: worn a two-hander, every one-handed step brings your shield")
	void oneHanderBringsShield() throws Exception {
		// Andrew 2026-10-22: "evaluate the weapon + shield as one slot because
		// it competes directly against the 2h options" - scored alone, an
		// empty shield slot skewed the strip toward two-handers.
		Monster demon = data.searchMonsters("abyssal demon", 1).get(0);
		OptimizerService.StyleResult melee = run(Style.MELEE, demon, "MAX_DPS",
			Map.of(GearSlot.WEAPON, id("saradomin sword")), java.util.Set.of(),
			"saradomin sword", "abyssal whip", "dragon defender", "fighter torso");
		assertTrue(melee.owned.get(0).getLoadout().getWeapon().isTwoHanded(), "the pinned two-hander is worn");
		OptimizerService.Ladder weapon = melee.ladder.get(GearSlot.WEAPON);
		assertNotNull(weapon);
		List<OptimizerService.Rung> oneHanded = new java.util.ArrayList<>();
		for (OptimizerService.Rung rung : weapon.up) {
			if (!rung.item.isTwoHanded()) {
				oneHanded.add(rung);
			}
		}
		for (OptimizerService.Rung rung : weapon.down) {
			if (!rung.item.isTwoHanded()) {
				oneHanded.add(rung);
			}
		}
		assertFalse(oneHanded.isEmpty(), "one-handers are among the steps: " + names(weapon.up) + " / " + names(weapon.down));
		for (OptimizerService.Rung rung : oneHanded) {
			assertNotNull(rung.offhand, rung.item.getName() + " is scored with a shield");
			assertEquals("Dragon defender", rung.offhand.getName(), rung.item.getName());
		}
		Map<String, Object> node = com.loadoutlab.model.RenderModel.ladderNode(weapon);
		List<?> rungs = new java.util.ArrayList<>((List<?>) node.get("up"));
		((List<Object>) rungs).addAll((List<?>) node.get("down"));
		assertTrue(rungs.stream().anyMatch(r -> String.valueOf(((Map<?, ?>) r).get("with")).contains("Dragon defender")),
			"the step names the shield it brings");
	}
}
