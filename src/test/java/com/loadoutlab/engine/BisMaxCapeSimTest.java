package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Skill;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Best in game wears the combo max capes under the max cape sim
 * (ISS-01M5T53FBMHVMJTDPS5ZW4WS50, Andrew 2026-10-25: "the max + bis should be
 * showing a quiver max cape not just the quiver"): the ceiling assumes every
 * item obtainable, so its waiver cannot hinge on owning the combo's other half.
 */
class BisMaxCapeSimTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static String capeName(OptimizerService.StyleResult result) {
		Gear cape = result.overallBest.getLoadout().get(GearSlot.CAPE);
		return cape == null ? "none" : cape.getNameLower();
	}

	@Test
	@DisplayName("a 99-combat, 70-elsewhere player owning nothing, max cape simmed: Best in game wears Dizana's and the infernal max cape")
	void ceilingWearsComboCapes() throws Exception {
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values()) { levels.put(skill, 70); }
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
			Skill.PRAYER, Skill.HITPOINTS}) {
			levels.put(skill, 99);
		}
		OwnedItems owned = new OwnedItems(Map.of(4151, 1), true);
		assertCeilingCapes(new RequirementProfile(levels, RequirementProfile.MAXED.getCompletedQuests())
			.waiving(MaxCapeSims.ids(data, owned::owns)), owned, true);
	}

	@Test
	@DisplayName("a maxed account owning nothing, no sim: Best in game wears the combo max capes too")
	void maxedCeilingWearsComboCapes() throws Exception {
		assertCeilingCapes(RequirementProfile.MAXED, new OwnedItems(Map.of(4151, 1), true), false);
	}

	private static void assertCeilingCapes(RequirementProfile profile, OwnedItems owned, boolean simMaxCape)
		throws Exception {
		Monster elf = data.searchMonsters("elf warrior", 1).get(0);
		RequirementProfile simmed = profile;
		ComputeParams p = new ComputeParams();
		p.simMaxCape = simMaxCape;
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(elf, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				simmed, owned, 1, p, Collections.emptyMap(), Collections.emptySet(),
				Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			assertEquals("dizana's max cape", capeName(out.get().get(Style.RANGED)));
			assertEquals("infernal max cape", capeName(out.get().get(Style.MELEE)));
		} finally {
			service.shutdown();
		}
	}
}
