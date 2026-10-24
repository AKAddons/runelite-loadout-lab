package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A style's shared kit is weighed by the mobs that USE the style
 * (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "when i pin my trident
 * of the seas in that spot, i get 4.6 dps so for some reason it is missing
 * trident of the seas in this calculation"). The Warden's Core is meleed; its
 * earth weakness must not hand the MAGIC kit to the Purging staff's Earth
 * Surge when the trident is better on the mob that actually gets maged.
 */
class KitWeightsByStyleUseTest {
	private static LoadoutData data;
	private static OptimizerService service;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new OptimizerService(data);
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static MonsterStats member(MonsterGroups.MonsterGroup group, String name, String label) {
		return group.getMobs().stream()
			.filter(m -> m.getName().equals(name) && m.label().contains(label)).findFirst()
			.orElseThrow(() -> new AssertionError("no " + name + " " + label));
	}

	@Test
	@DisplayName("Akkha (Magic only) and the Warden's Core: the Core is meleed, so the magic kit wears the trident for Akkha, not the Purging staff the Core's earth weakness would pick")
	void magicKitFollowsTheMagedMob() throws Exception {
		MonsterGroups.MonsterGroup toa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tombs of Amascut")).findFirst().orElseThrow();
		MonsterStats akkha = member(toa, "Akkha", "Magic only");
		MonsterStats core = member(toa, "Tumeken's Warden", "Core");
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"trident of the seas", "purging staff", "ancestral hat", "ancestral robe top",
			"ancestral robe bottom", "occult necklace", "tormented bracelet", "eternal boots", "imbued zamorak cape",
			"magus ring", "rada's blessing 4", "noxious halberd", "torva full helm", "torva platebody", "torva platelegs",
			"amulet of torture", "ferocious gloves", "primordial boots", "infernal cape", "ultor ring"}) {
			int id = data.getGearItems().stream()
				.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name)).getId();
			owned.put(id, 1);
		}
		for (int rune : new int[]{554, 555, 556, 557, 560, 565, 566, 21880}) {
			owned.put(rune, 10000);
		}
		ComputeParams p = new ComputeParams();
		p.thralls = true; // Andrew's raid setting: the trident keeps the group on Arceuus with thralls
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(List.of(akkha, core), PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), owned.hashCode(), p, Map.of(), Set.of(), Map.of(), Map.of(),
			Map.of(), Map.of(), Map.of(), Set.of(), roster -> {
				out.set(roster);
				done.countDown();
			});
		assertTrue(done.await(180, TimeUnit.SECONDS), "roster compute timed out");
		OptimizerService.RosterResult roster = out.get();

		OptimizerService.StyleResult coreMelee = roster.perMob.get(1).get(CombatStyle.MELEE);
		OptimizerService.StyleResult coreMagic = roster.perMob.get(1).get(CombatStyle.MAGIC);
		assertNotNull(coreMelee);
		assertTrue(coreMelee.owned.get(0).getDps() > coreMagic.owned.get(0).getDps(),
			"the Core is a melee fight: " + coreMelee.owned.get(0).getDps() + " vs magic " + coreMagic.owned.get(0).getDps());

		DpsResult akkhaMagic = roster.perMob.get(0).get(CombatStyle.MAGIC).owned.get(0);
		assertTrue(akkhaMagic.getLoadout().getWeapon().getNameLower().contains("trident"),
			"the magic kit follows Akkha, the mob that gets maged: " + akkhaMagic.getLoadout().getWeapon().getName()
				+ " / " + akkhaMagic.getSpellName() + " = " + akkhaMagic.getDps());
		assertEquals("arceuus", roster.groupBook, "a powered staff everywhere: " + roster.groupBookReason);
	}
}
