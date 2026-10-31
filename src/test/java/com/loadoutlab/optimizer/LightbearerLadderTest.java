package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.*;
import java.util.ArrayList;
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
 * Andrew 2026-10-30: "when i sim lightbearer i'm not seeing how it compares
 * with my ring swaps" (ISS-01M65RVX7GAEXPRYHWW529ZQ19). The ring ladder is
 * priced with the spec, so the stat-less Lightbearer stands as a step.
 */
class LightbearerLadderTest {
	private static LoadoutData data;
	private static Monster cerberus;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		cerberus = data.searchMonsters("cerberus", 1).get(0);
	}

	private static int id(String name) {
		return data.getGearItems().stream().filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError(name)).getId();
	}

	private static OptimizerService.StyleResult melee(Map<Integer, Integer> owned) throws Exception {
		ComputeParams p = new ComputeParams();
		p.maxSwaps = 3;
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(cerberus, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 7, p, Collections.emptyMap(),
				Collections.emptySet(), Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			return out.get().get(Style.MELEE);
		} finally {
			service.shutdown();
		}
	}

	private static List<OptimizerService.Rung> steps(OptimizerService.Ladder ladder) {
		List<OptimizerService.Rung> all = new ArrayList<>();
		all.addAll(ladder.up);
		all.addAll(ladder.down);
		all.addAll(ladder.upMore);
		all.addAll(ladder.downMore);
		return all;
	}

	@Test
	@DisplayName("with dragon claws in play, the ring ladder is priced with the spec and lists the owned Lightbearer as a step against the dps rings")
	void lightbearerIsARingStep() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"abyssal whip", "avernic defender", "bandos chestplate", "bandos tassets", "dragon claws",
			"ultor ring", "berserker ring (i)", "lightbearer"}) {
			owned.put(id(name), 1);
		}
		OptimizerService.StyleResult melee = melee(owned);
		assertNotNull(melee.spec, "the claws seat a spec");
		OptimizerService.Ladder ring = melee.ladder.get(GearSlot.RING);
		assertNotNull(ring, "a ring ladder");
		assertTrue(ring.spec, "priced with the spec");
		Gear worn = melee.owned.get(0).loadout.get(GearSlot.RING);
		List<String> names = new ArrayList<>();
		for (OptimizerService.Rung r : steps(ring)) names.add(r.item.getNameLower());
		String other = worn.getNameLower().equals("lightbearer") ? "ultor ring" : "lightbearer";
		assertTrue(names.contains(other), "the ring the arbitration did not seat is a step: worn " + worn.getNameLower() + ", steps " + names);
	}
}
