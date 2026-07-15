package com.loadoutlab.ui;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mascot roster's selection logic - eligibility windows and weighted
 * frequency - is pure and easy to get subtly wrong (a wrapped window, an
 * off-by-a-day season, a weight that no longer matches the intended mix).
 * These lock the calendar behaviour the pixels can't self-check.
 */
class MascotRosterTest
{
	private static final LocalDate IN_WORLD_CUP = LocalDate.of(2026, 7, 1);
	private static final LocalDate AFTER_WORLD_CUP = LocalDate.of(2026, 9, 1);

	@Test
	@DisplayName("evergreen moods are eligible every day; the World Cup only in its window")
	void eligibility()
	{
		assertTrue(MascotRoster.activeOn(IN_WORLD_CUP).contains(MascotRoster.WORKOUT));
		assertTrue(MascotRoster.activeOn(IN_WORLD_CUP).contains(MascotRoster.CHEF));
		assertTrue(MascotRoster.activeOn(IN_WORLD_CUP).contains(MascotRoster.WORLD_CUP));

		assertTrue(MascotRoster.activeOn(AFTER_WORLD_CUP).contains(MascotRoster.WORKOUT));
		assertFalse(MascotRoster.activeOn(AFTER_WORLD_CUP).contains(MascotRoster.WORLD_CUP),
			"the striker retires after the final");
		// A one-off dated window does not recur the year after.
		assertFalse(MascotRoster.activeOn(LocalDate.of(2031, 7, 1)).contains(MascotRoster.WORLD_CUP));
	}

	@Test
	@DisplayName("each active mood is picked in proportion to its weight")
	void weightedMix()
	{
		int draws = 120_000;
		for (LocalDate date : new LocalDate[]{IN_WORLD_CUP, AFTER_WORLD_CUP})
		{
			java.util.List<MascotRoster> active = MascotRoster.activeOn(date);
			int total = active.stream().mapToInt(MascotRoster::weight).sum();
			Map<Class<?>, Integer> counts = sample(date, draws);
			for (MascotRoster mood : active)
			{
				assertClose(counts.getOrDefault(mood.create().getClass(), 0),
					draws * mood.weight() / total);
			}
		}
	}

	@Test
	@DisplayName("a dormant mood is never picked (the striker is gone out of season)")
	void dormantNeverPicked()
	{
		Map<Class<?>, Integer> counts = sample(AFTER_WORLD_CUP, 90_000);
		assertEquals(0, counts.getOrDefault(MascotStriker.class, 0),
			"a dormant mood must never be picked");
	}

	@Test
	@DisplayName("Halloween is eligible around Oct 31 and dormant the rest of the year")
	void halloweenSeason()
	{
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 10, 31)).contains(MascotRoster.HALLOWEEN));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2027, 10, 25)).contains(MascotRoster.HALLOWEEN),
			"recurs every year");
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 9, 1)).contains(MascotRoster.HALLOWEEN));
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 12, 1)).contains(MascotRoster.HALLOWEEN));
	}

	@Test
	@DisplayName("the classroom runs the school terms and is dark over summer and December")
	void classroomTerm()
	{
		assertTrue(MascotRoster.activeOn(LocalDate.of(2027, 2, 15)).contains(MascotRoster.CLASSROOM));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 10, 1)).contains(MascotRoster.CLASSROOM));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 9, 30)).contains(MascotRoster.CLASSROOM));
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 7, 15)).contains(MascotRoster.CLASSROOM),
			"summer break");
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 8, 20)).contains(MascotRoster.CLASSROOM),
			"summer break");
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 12, 15)).contains(MascotRoster.CLASSROOM),
			"December holidays");
	}

	@Test
	@DisplayName("the chef cooks every month except October, which it cedes to Halloween")
	void chefSkipsOctober()
	{
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 10, 15)).contains(MascotRoster.CHEF),
			"October belongs to the cauldron");
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 10, 15)).contains(MascotRoster.HALLOWEEN));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 9, 30)).contains(MascotRoster.CHEF));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 11, 1)).contains(MascotRoster.CHEF));
	}

	@Test
	@DisplayName("the World Cup covers June-July of 2026 and 2030 and nothing in between")
	void worldCupWindow()
	{
		// 2026 tournament.
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 5, 31)).contains(MascotRoster.WORLD_CUP));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 6, 1)).contains(MascotRoster.WORLD_CUP));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2026, 7, 31)).contains(MascotRoster.WORLD_CUP));
		assertFalse(MascotRoster.activeOn(LocalDate.of(2026, 8, 1)).contains(MascotRoster.WORLD_CUP));
		// Dark in the years between tournaments.
		assertFalse(MascotRoster.activeOn(LocalDate.of(2028, 7, 1)).contains(MascotRoster.WORLD_CUP));
		// 2030 tournament.
		assertTrue(MascotRoster.activeOn(LocalDate.of(2030, 6, 1)).contains(MascotRoster.WORLD_CUP));
		assertTrue(MascotRoster.activeOn(LocalDate.of(2030, 7, 31)).contains(MascotRoster.WORLD_CUP));
		assertFalse(MascotRoster.activeOn(LocalDate.of(2030, 8, 1)).contains(MascotRoster.WORLD_CUP));
	}

	@Test
	@DisplayName("annual windows recur every year and wrap the year boundary")
	void annualWindowWraps()
	{
		MascotRoster.Window winter = MascotRoster.Window.annual(12, 15, 1, 2);
		assertTrue(winter.active(LocalDate.of(2026, 12, 20)));
		assertTrue(winter.active(LocalDate.of(2027, 1, 1)), "wraps into the new year");
		assertFalse(winter.active(LocalDate.of(2027, 1, 10)));
		assertTrue(winter.active(LocalDate.of(2030, 12, 31)), "recurs, unlike a dated window");

		MascotRoster.Window fall = MascotRoster.Window.annual(10, 20, 11, 1);
		assertTrue(fall.active(LocalDate.of(2026, 10, 25)));
		assertFalse(fall.active(LocalDate.of(2026, 11, 5)));
	}

	@Test
	@DisplayName("an anchored window spills across the year boundary by its radius")
	void aroundWindowSpills()
	{
		MascotRoster.Window newYear = MascotRoster.Window.around(12, 31, 2);
		assertTrue(newYear.active(LocalDate.of(2026, 12, 30)));
		assertTrue(newYear.active(LocalDate.of(2027, 1, 2)), "two days into January");
		assertFalse(newYear.active(LocalDate.of(2027, 1, 5)));
	}

	private static Map<Class<?>, Integer> sample(LocalDate date, int draws)
	{
		Random rng = new Random(1234);
		Map<Class<?>, Integer> counts = new HashMap<>();
		for (int i = 0; i < draws; i++)
		{
			Mascot m = MascotRoster.pick(date, rng);
			counts.merge(m.getClass(), 1, Integer::sum);
		}
		return counts;
	}

	private static void assertClose(int actual, int expected)
	{
		double tolerance = expected * 0.05 + 50;
		assertTrue(Math.abs(actual - expected) <= tolerance,
			"expected ~" + expected + " but got " + actual);
	}
}
