package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.MonsterGroups;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.ComputeParams;
import com.ll.engine.Dps;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
	private static Tables data;
	private static Service service;
	/** Akkha "Magic only" and Elidinis' Warden "P2 (magic only)": both can only be maged. */
	private static List<Monster> magicOnlyPair;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new Service(data);
		MonsterGroups.MonsterGroup toa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tombs of Amascut")).findFirst()
			.orElseThrow(() -> new AssertionError("no Tombs of Amascut group"));
		Monster akkha = member(toa, "Akkha", "Magic only");
		Monster warden = member(toa, "Elidinis' Warden", "P2 (magic only)");
		magicOnlyPair = List.of(akkha, warden);
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static Monster member(MonsterGroups.MonsterGroup group, String name, String label) {
		return group.getMobs().stream()
			.filter(m -> m.getName().equals(name) && m.label().contains(label)).findFirst()
			.orElseThrow(() -> new AssertionError("no " + name + " " + label + " in " + group.getName()));
	}

	private static Map<Integer, Integer> kit(String... names) {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : names) {
			int id = data.getGearItems().stream()
				.filter(g -> g.getLname().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
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
	private static Service.RosterResult run(Map<Integer, Integer> owned, boolean thralls,
		int deathCharge, String lock) throws InterruptedException {
		ComputeParams p = new ComputeParams();
		p.thralls = thralls;
		p.deathCharge = deathCharge;
		p.spellbookLock = lock;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Service.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(magicOnlyPair, Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, new Owned(owned, true), owned.hashCode(), p, Map.of(), Set.of(), Map.of(), Map.of(),
			Map.of(), Map.of(), Map.of(), Set.of(), roster -> {
				out.set(roster);
				done.countDown();
			});
		assertTrue(done.await(180, TimeUnit.SECONDS), "roster compute timed out");
		return out.get();
	}

	private static Dps magicOf(Service.RosterResult roster, int mob) {
		Service.Pick magic = roster.pm.get(mob).get(Style.MAGIC);
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
		Service.RosterResult roster = run(kit(with(AUTOCAST_STAVES)), true, 1, "");
		assertEquals("standard", roster.groupBook, roster.bookReason);
		assertTrue(roster.bookReason.contains("Warden"), "the reason names the demanding mob: " + roster.bookReason);
		for (int i = 0; i < 2; i++) {
			Dps set = magicOf(roster, i);
			assertNotNull(set.getSpellName(), "mob " + i + " autocasts");
			assertEquals("standard", bookOf(set.getSpellName()), "mob " + i + " casts " + set.getSpellName());
		}
	}

	/** Akkha "Magic only" and Ba-Ba: neither has an elemental weakness, and a
	 * mage-only kit makes magic Ba-Ba's only usable style. */
	private static List<Monster> weaknessFreePair() {
		MonsterGroups.MonsterGroup toa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("Tombs of Amascut")).findFirst().orElseThrow();
		return List.of(member(toa, "Akkha", "Magic only"), member(toa, "Ba-Ba", ""));
	}

	private static Service.RosterResult run(List<Monster> mobs, Map<Integer, Integer> owned,
		boolean thralls, int deathCharge, String lock) throws InterruptedException {
		ComputeParams p = new ComputeParams();
		p.thralls = thralls;
		p.deathCharge = deathCharge;
		p.spellbookLock = lock;
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Service.RosterResult> out = new AtomicReference<>();
		service.bestPerStyleAcross(mobs, Levels.MAXED, Levels.MAXED, Prayers.ALL,
			RequirementProfile.MAXED, new Owned(owned, true), owned.hashCode(), p, Map.of(), Set.of(), Map.of(), Map.of(),
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
		Service.RosterResult roster = run(weaknessFreePair(), kit(with("trident of the seas", "ancient staff")), true, 0, "");
		assertEquals("arceuus", roster.groupBook, roster.bookReason);
		for (int i = 0; i < 2; i++) {
			Dps set = magicOf(roster, i);
			assertNull(set.getSpellName(), "mob " + i + " casts from the trident: " + roster.bookReason);
			assertTrue(set.getLoadout().getWeapon().getLname().contains("trident"), set.getLoadout().getWeapon().getName());
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
		Service.RosterResult roster = run(kit(with("smoke battlestaff")), true, 0, "");
		assertEquals("standard", roster.groupBook, roster.bookReason);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d+) vs (\\d+) weighted dps\\)")
			.matcher(roster.bookReason);
		assertTrue(m.find(), "the reason shows both weighted totals: " + roster.bookReason);
		assertTrue(Double.parseDouble(m.group(2)) > Double.parseDouble(m.group(1)), roster.bookReason);
		assertTrue(roster.bookReason.contains("would lose"), roster.bookReason);
		assertEquals("standard", bookOf(magicOf(roster, 1).getSpellName()), "the Warden autocasts from the standard book");
	}

	@Test
	@DisplayName("the comparison itself: Arceuus stays while powered staves plus the summons match or beat the autocast total; only a strictly higher autocast total wins the book")
	void arceuusIfNotBeaten() {
		String kept = Service.arceuusIfNotBeaten(1900, 1900, "thralls", "Elidinis' Warden (Damaged)", "Earth Surge");
		assertNotNull(kept, "a tie stays Arceuus");
		assertTrue(kept.contains("Arceuus casts with thralls on the magic cards outweigh Elidinis' Warden (Damaged)'s Earth Surge"), kept);
		assertTrue(kept.contains("(1900 vs 1900 weighted dps)"), kept);
		assertNotNull(Service.arceuusIfNotBeaten(2500, 1800, "Death Charge", "Akkha", "Fire Surge"));
		assertNull(Service.arceuusIfNotBeaten(1899, 1900, "thralls", "Akkha", "Fire Surge"),
			"a strictly higher autocast total wins the book");
	}

	@Test
	@DisplayName("powered staves everywhere with thralls on: Arceuus is the group's book and no set autocasts")
	void arceuusByDefault() throws Exception {
		Service.RosterResult roster = run(kit(with("tumeken's shadow")), true, 0, "");
		assertEquals("arceuus", roster.groupBook, roster.bookReason);
		for (int i = 0; i < 2; i++) {
			assertNull(magicOf(roster, i).getSpellName(), "mob " + i + " casts from the staff");
		}
	}

	@Test
	@DisplayName("nothing wants a book - thralls and Death Charge off, powered staff - and nothing is pinned")
	void nothingPinned() throws Exception {
		Service.RosterResult roster = run(kit(with("tumeken's shadow")), false, 0, "");
		assertEquals("", roster.groupBook);
	}

	@Test
	@DisplayName("the player's own lock wins: Ancients stays Ancients and every autocast is an Ancients spell off the kodai wand")
	void ownLockWins() throws Exception {
		Service.RosterResult roster = run(kit(with(AUTOCAST_STAVES)), true, 1, "ancient");
		assertEquals("ancient", roster.groupBook);
		for (int i = 0; i < 2; i++) {
			Dps set = magicOf(roster, i);
			assertNotNull(set.getSpellName(), "mob " + i + " autocasts");
			assertEquals("ancient", bookOf(set.getSpellName()), "mob " + i + " casts " + set.getSpellName());
		}
	}
}
