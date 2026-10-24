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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A group runs one spellbook (ISS-01M5PB4R0MP149NXWNFSEEHVP8, Andrew
 * 2026-10-24: "whenever a group is considered, the spellbook has to be one
 * total choice for everything ... when a group is present and one mob demands
 * a specific spellbook, that specific spellbook needs to be pinned for the
 * entire group ... arceuss is the default options for raids because it
 * includes thralls but if the player needs to run a standard spellbook to use
 * smoke battlestafff on wardens it should correctly pin the standard spellbook
 * over arceuss for the whole set").
 */
class GroupSpellbookTest {
	private static LoadoutData data;
	private static OptimizerService service;
	/** Akkha "Magic only" and Elidinis' Warden "P2 (magic only)": both can only be maged. */
	private static List<MonsterStats> magicOnlyPair;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new OptimizerService(data);
		MonsterGroups.MonsterGroup toa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tombs of Amascut")).findFirst()
			.orElseThrow(() -> new AssertionError("no Tombs of Amascut group"));
		MonsterStats akkha = member(toa, "Akkha", "Magic only");
		MonsterStats warden = member(toa, "Elidinis' Warden", "P2 (magic only)");
		magicOnlyPair = List.of(akkha, warden);
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static MonsterStats member(MonsterGroups.MonsterGroup group, String name, String label) {
		return group.getMobs().stream()
			.filter(m -> m.getName().equals(name) && m.label().contains(label)).findFirst()
			.orElseThrow(() -> new AssertionError("no " + name + " " + label + " in " + group.getName()));
	}

	private static Map<Integer, Integer> kit(String... names) {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : names) {
			int id = data.getGearItems().stream()
				.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name)).getId();
			owned.put(id, 1);
		}
		// Every rune a standard surge or an Ancients barrage could want.
		for (int rune : new int[]{554, 555, 556, 557, 560, 565, 566, 21880}) {
			owned.put(rune, 10000);
		}
		return owned;
	}

	private static final String[] ROBES = {"ancestral hat", "ancestral robe top", "ancestral robe bottom",
		"occult necklace", "tormented bracelet", "eternal boots", "imbued zamorak cape", "magus ring",
		"rada's blessing 4"};

	private static String[] with(String... weapons) {
		String[] all = new String[ROBES.length + weapons.length];
		System.arraycopy(ROBES, 0, all, 0, ROBES.length);
		System.arraycopy(weapons, 0, all, ROBES.length, weapons.length);
		return all;
	}

	/** A smoke battlestaff (standard autocasts, +10% to them) and a kodai
	 * wand (the Ancients autocast a battlestaff cannot provide). */
	private static final String[] AUTOCAST_STAVES = {"smoke battlestaff", "kodai wand"};

	/** The collection fingerprint keys the per-style cache: each kit gets its
	 * own, or one test's answer is verified against the next test's bank. */
	private static OptimizerService.RosterResult run(Map<Integer, Integer> owned, boolean thralls,
		int deathCharge, String lock) throws InterruptedException {
		ComputeParams p = new ComputeParams();
		p.thralls = thralls;
		p.deathCharge = deathCharge;
		p.spellbookLock = lock;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(magicOnlyPair, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), owned.hashCode(), p, Map.of(), Set.of(), Map.of(), Map.of(),
			Map.of(), Map.of(), Map.of(), Set.of(), roster -> {
				out.set(roster);
				done.countDown();
			});
		assertTrue(done.await(180, TimeUnit.SECONDS), "roster compute timed out");
		return out.get();
	}

	private static DpsResult magicOf(OptimizerService.RosterResult roster, int mob) {
		OptimizerService.StyleResult magic = roster.perMob.get(mob).get(CombatStyle.MAGIC);
		assertNotNull(magic, "mob " + mob + " has a magic result");
		assertTrue(!magic.owned.isEmpty(), "mob " + mob + " has an owned magic set");
		return magic.owned.get(0);
	}

	private static String bookOf(String spell) {
		return data.getSpells().stream().filter(s -> s.getName().equals(spell)).findFirst()
			.orElseThrow(() -> new AssertionError("unknown spell " + spell)).getSpellbook();
	}

	@Test
	@DisplayName("a magic-only Warden that wants Earth Surge pins the standard book on the whole group - Akkha casts from it too, no Ice Barrage")
	void demandPinsTheGroup() throws Exception {
		OptimizerService.RosterResult roster = run(kit(with(AUTOCAST_STAVES)), true, 1, "");
		assertEquals("standard", roster.groupBook, roster.groupBookReason);
		assertTrue(roster.groupBookReason.contains("Warden"), "the reason names the demanding mob: " + roster.groupBookReason);
		for (int i = 0; i < 2; i++) {
			DpsResult set = magicOf(roster, i);
			assertNotNull(set.getSpellName(), "mob " + i + " autocasts");
			assertEquals("standard", bookOf(set.getSpellName()), "mob " + i + " casts " + set.getSpellName());
		}
	}

	/** Akkha "Magic only" and Ba-Ba: neither has an elemental weakness, and a
	 * mage-only kit makes magic Ba-Ba's only usable style. */
	private static List<MonsterStats> weaknessFreePair() {
		MonsterGroups.MonsterGroup toa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tombs of Amascut")).findFirst().orElseThrow();
		return List.of(member(toa, "Akkha", "Magic only"), member(toa, "Ba-Ba", ""));
	}

	private static OptimizerService.RosterResult run(List<MonsterStats> mobs, Map<Integer, Integer> owned,
		boolean thralls, int deathCharge, String lock) throws InterruptedException {
		ComputeParams p = new ComputeParams();
		p.thralls = thralls;
		p.deathCharge = deathCharge;
		p.spellbookLock = lock;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(mobs, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), owned.hashCode(), p, Map.of(), Set.of(), Map.of(), Map.of(),
			Map.of(), Map.of(), Map.of(), Set.of(), roster -> {
				out.set(roster);
				done.countDown();
			});
		assertTrue(done.await(180, TimeUnit.SECONDS), "roster compute timed out");
		return out.get();
	}

	@Test
	@DisplayName("a trident outranks an ancient staff's Ice Barrage outright: nobody demands a book, so thralls on means Arceuus, trident worn")
	void poweredStaffWinsOutrightArceuusByDefault() throws Exception {
		OptimizerService.RosterResult roster = run(weaknessFreePair(), kit(with("trident of the seas", "ancient staff")), true, 0, "");
		assertEquals("arceuus", roster.groupBook, roster.groupBookReason);
		for (int i = 0; i < 2; i++) {
			DpsResult set = magicOf(roster, i);
			assertNull(set.getSpellName(), "mob " + i + " casts from the trident: " + roster.groupBookReason);
			assertTrue(set.getLoadout().getWeapon().getNameLower().contains("trident"), set.getLoadout().getWeapon().getName());
		}
	}

	@Test
	@DisplayName("Arceuus is favoured, not forced: a smoke battlestaff alone (no powered staff) with thralls on still loses to the Warden's Earth Surge - the standard book, and the reason prints both weighted totals")
	void thrallsWeighedAgainstTheAutocast() throws Exception {
		// Measured 2026-10-24: every 4-tick powered staff the corpus holds outranks a
		// kodai's Ice Barrage on Akkha and Ba-Ba, and a trident beats Earth Surge on
		// the Warden too - with a powered staff owned there is simply no demand (the
		// test above). The comparison bites when the player owns none: thralls on
		// every card against Earth Surge on a magic-only Warden.
		OptimizerService.RosterResult roster = run(kit(with("smoke battlestaff")), true, 0, "");
		assertEquals("standard", roster.groupBook, roster.groupBookReason);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d+) vs (\\d+) weighted dps\\)")
			.matcher(roster.groupBookReason);
		assertTrue(m.find(), "the reason shows both weighted totals: " + roster.groupBookReason);
		assertTrue(Double.parseDouble(m.group(2)) > Double.parseDouble(m.group(1)), roster.groupBookReason);
		assertTrue(roster.groupBookReason.contains("would lose"), roster.groupBookReason);
		assertEquals("standard", bookOf(magicOf(roster, 1).getSpellName()), "the Warden autocasts from the standard book");
	}

	@Test
	@DisplayName("the comparison itself: Arceuus stays while powered staves plus the summons match or beat the autocast total; only a strictly higher autocast total wins the book")
	void arceuusIfNotBeaten() {
		String kept = OptimizerService.arceuusIfNotBeaten(1900, 1900, "thralls", "Elidinis' Warden (Damaged)", "Earth Surge");
		assertNotNull(kept, "a tie stays Arceuus");
		assertTrue(kept.contains("Arceuus casts with thralls on the magic cards outweigh Elidinis' Warden (Damaged)'s Earth Surge"), kept);
		assertTrue(kept.contains("(1900 vs 1900 weighted dps)"), kept);
		assertNotNull(OptimizerService.arceuusIfNotBeaten(2500, 1800, "Death Charge", "Akkha", "Fire Surge"));
		assertNull(OptimizerService.arceuusIfNotBeaten(1899, 1900, "thralls", "Akkha", "Fire Surge"),
			"a strictly higher autocast total wins the book");
	}

	@Test
	@DisplayName("powered staves everywhere with thralls on: Arceuus is the group's book and no set autocasts")
	void arceuusByDefault() throws Exception {
		OptimizerService.RosterResult roster = run(kit(with("tumeken's shadow")), true, 0, "");
		assertEquals("arceuus", roster.groupBook, roster.groupBookReason);
		for (int i = 0; i < 2; i++) {
			assertNull(magicOf(roster, i).getSpellName(), "mob " + i + " casts from the staff");
		}
	}

	@Test
	@DisplayName("nothing wants a book - thralls and Death Charge off, powered staff - and nothing is pinned")
	void nothingPinned() throws Exception {
		OptimizerService.RosterResult roster = run(kit(with("tumeken's shadow")), false, 0, "");
		assertEquals("", roster.groupBook);
	}

	@Test
	@DisplayName("the player's own lock wins: Ancients stays Ancients and every autocast is an Ancients spell off the kodai wand")
	void ownLockWins() throws Exception {
		OptimizerService.RosterResult roster = run(kit(with(AUTOCAST_STAVES)), true, 1, "ancient");
		assertEquals("ancient", roster.groupBook);
		for (int i = 0; i < 2; i++) {
			DpsResult set = magicOf(roster, i);
			assertNotNull(set.getSpellName(), "mob " + i + " autocasts");
			assertEquals("ancient", bookOf(set.getSpellName()), "mob " + i + " casts " + set.getSpellName());
		}
	}
}
