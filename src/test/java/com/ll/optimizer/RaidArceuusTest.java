package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.MonsterGroups;
import com.ll.data.Monster;
import com.ll.engine.*;
import com.ll.model.CommandEngine;
import com.ll.model.CompanionLink;
import com.ll.model.PageState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Raids run Arceuus with thralls whenever possible (ISS-01M5TMC6SK50VPV8713B33SPPM,
 * Andrew 2026-10-25: "toa is NOT defaulting to thralls which lowers the dps on
 * everything"; "all raids should try to use arceuss+thralls until its not
 * possible").
 */
class RaidArceuusTest {
	private static Tables data;
	private static Service service;
	/** Akkha "Magic only" and Elidinis' Warden "P2 (magic only)". */
	private static List<Monster> pair;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new Service(data);
		MonsterGroups.MonsterGroup toa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tombs of Amascut")).findFirst().orElseThrow();
		pair = List.of(member(toa, "Akkha", "Magic only"), member(toa, "Elidinis' Warden", "P2 (magic only)"));
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static Monster member(MonsterGroups.MonsterGroup group, String name, String label) {
		return group.getMobs().stream().filter(m -> m.getName().equals(name) && m.label().contains(label))
			.findFirst().orElseThrow();
	}

	/** Ancestral robes and a smoke battlestaff, every surge rune: the kit whose
	 * Earth Surge on the Warden pulls a plain group to the standard book. */
	private static Map<Integer, Integer> smokeKit() {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"ancestral hat", "ancestral robe top", "ancestral robe bottom", "occult necklace",
			"tormented bracelet", "eternal boots", "imbued zamorak cape", "magus ring", "rada's blessing 4",
			"smoke battlestaff"}) {
			owned.put(data.getGearItems().stream()
				.filter(g -> g.getLname().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst().orElseThrow().getId(), 1);
		}
		for (int rune : new int[]{554, 555, 556, 557, 560, 565, 566, 21880}) { owned.put(rune, 10000); }
		return owned;
	}

	private static Service.RosterResult run(boolean raid) throws InterruptedException {
		Map<Integer, Integer> owned = smokeKit();
		ComputeParams p = new ComputeParams();
		p.thralls = true;
		p.raid = raid;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Service.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(pair, Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, new Owned(owned, true), owned.hashCode() + (raid ? 1 : 0), p, Map.of(), Set.of(),
			Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), roster -> {
				out.set(roster);
				done.countDown();
			});
		assertTrue(done.await(180, TimeUnit.SECONDS), "roster compute timed out");
		return out.get();
	}

	@Test
	@DisplayName("a raid with thralls on runs Arceuus even where the Warden's Earth Surge would win a plain group")
	void raidKeepsArceuus() throws Exception {
		Service.RosterResult raid = run(true);
		assertEquals("arceuus", raid.groupBook, raid.bookReason);
		Service.RosterResult plain = run(false);
		assertEquals("standard", plain.groupBook, "a non-raid group keeps the favoured-not-forced rule: " + plain.bookReason);
	}

	@Test
	@DisplayName("the engine marks a raid selection's compute as a raid, and a single boss's not")
	void engineMarksRaids() {
		AtomicReference<ComputeParams> roster = new AtomicReference<>();
		AtomicReference<ComputeParams> single = new AtomicReference<>();
		CommandEngine engine = new CommandEngine(data, new PageState(), (m, p, onDone) -> single.set(p), new CompanionLink());
		engine.setRosterCompute((mobs, p, onDone) -> roster.set(p));
		engine.execute("select", Map.of("query", "Tombs of Amascut"));
		assertNotNull(roster.get(), "the raid computed as a roster");
		assertTrue(roster.get().raid, "Tombs of Amascut is a raid");
		engine.execute("select", Map.of("id", data.searchMonsters("vorkath", 1).get(0).getId()));
		assertNotNull(single.get(), "Vorkath computed");
		assertFalse(single.get().raid);
	}
}
