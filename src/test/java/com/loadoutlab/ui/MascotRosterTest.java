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
	private static final LocalDate SUMMER = LocalDate.of(2026, 7, 1);
	private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

	@Test
	@DisplayName("evergreen moods are eligible every day")
	void eligibility()
	{
		assertTrue(MascotRoster.activeOn(SUMMER).contains(MascotRoster.WORKOUT));
		assertTrue(MascotRoster.activeOn(SUMMER).contains(MascotRoster.SKATER));
		assertTrue(MascotRoster.activeOn(SEPTEMBER).contains(MascotRoster.WORKOUT));
	}

	@Test
	@DisplayName("each active mood is picked in proportion to its weight")
	void weightedMix()
	{
		int draws = 120_000;
		for (LocalDate date : new LocalDate[]{SUMMER, SEPTEMBER})
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
	@DisplayName("only eligible moods are ever picked")
	void onlyEligibleMoodsArePicked()
	{
		// Every mood is evergreen right now (the seasonal ones are benched in
		// the attic), so the guarantee to hold is the weaker one: pick never
		// returns anything outside activeOn.
		for (LocalDate date : new LocalDate[]{SUMMER, SEPTEMBER, LocalDate.of(2026, 10, 15)})
		{
			java.util.Set<Class<?>> eligible = new java.util.HashSet<>();
			for (MascotRoster mood : MascotRoster.activeOn(date))
			{
				eligible.add(mood.create().getClass());
			}
			for (Class<?> picked : sample(date, 5_000).keySet())
			{
				assertTrue(eligible.contains(picked),
					picked.getSimpleName() + " was picked on " + date + " while dormant");
			}
		}
	}

	@Test
	@DisplayName("the months window still gates by calendar month (the benched moods' restore)")
	void monthsWindowGates()
	{
		// Window.months has no caller in main source since the chef was
		// benched, but the chef, classroom and cauldron restores all depend
		// on it - so it stays, and this is what keeps it honest.
		MascotRoster.Window october = MascotRoster.Window.months(10);
		assertTrue(october.active(LocalDate.of(2026, 10, 15)));
		assertFalse(october.active(LocalDate.of(2026, 9, 30)));
		assertFalse(october.active(LocalDate.of(2026, 11, 1)));

		MascotRoster.Window terms = MascotRoster.Window.months(1, 2, 3, 4, 5, 9, 10, 11);
		assertTrue(terms.active(LocalDate.of(2026, 5, 31)));
		assertFalse(terms.active(LocalDate.of(2026, 6, 1)));
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
