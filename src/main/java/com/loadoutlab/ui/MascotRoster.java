package com.loadoutlab.ui;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The mascot loading-animation registry: every mood, WHEN it is eligible
 * (its {@link Window}) and HOW OFTEN it shows relative to the others (its
 * weight). Adding a mood is one entry here plus its render class - no
 * chooser logic to edit.
 *
 * On each compute {@link #pick} filters to the moods whose window includes
 * today and draws one weighted by frequency. So an out-of-season mood is
 * simply not in the running; an in-season one headlines by carrying a big
 * weight while the evergreens sit at a low base and keep the mix varied.
 *
 * Calendar:
 *   - Workout (weight 2) and Skater (weight 1) run all year.
 *   - Chef (weight 1) cooks every month except October, which it cedes to
 *     Halloween (weight 6) - so October is the cauldron's headline month.
 *   - Classroom (weight 1) runs only the school terms: Jan-May and Sep-Nov
 *     (dark over summer break Jun-Aug and the December holidays).
 *   - World Cup (weight 6) fires for June-July of 2026 and 2030 (the two real
 *     tournaments); dark every other year.
 */
enum MascotRoster
{
	// Evergreen - always eligible, low base weight.
	WORKOUT(Window.ALWAYS, 2, MascotSpinner::new),
	SKATER(Window.ALWAYS, 1, MascotSkater::new),
	// Chef cooks year-round except October (Halloween's month).
	CHEF(Window.months(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12), 1, MascotChef::new),
	// Classroom is in session only during the school terms.
	CLASSROOM(Window.months(1, 2, 3, 4, 5, 9, 10, 11), 1, MascotClassroom::new),

	// Seasonal / event - eligible only inside their window, where the big
	// weight makes them the headline act.
	WORLD_CUP(Window.anyOf(
		Window.dates(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31)),
		Window.dates(LocalDate.of(2030, 6, 1), LocalDate.of(2030, 7, 31))),
		6, MascotStriker::new),
	HALLOWEEN(Window.months(10), 6, MascotCauldron::new);

	private final Window window;
	private final int weight;
	private final Supplier<Mascot> factory;

	MascotRoster(Window window, int weight, Supplier<Mascot> factory)
	{
		this.window = window;
		this.weight = weight;
		this.factory = factory;
	}

	int weight()
	{
		return weight;
	}

	/** A fresh instance of this mood - used by the preview harness to render
	 * every mood deterministically (pick() only returns them weighted-random). */
	Mascot create()
	{
		return factory.get();
	}

	/** The moods eligible on the given date, in declaration order. */
	static List<MascotRoster> activeOn(LocalDate date)
	{
		List<MascotRoster> live = new ArrayList<>();
		for (MascotRoster m : values())
		{
			if (m.window.active(date))
			{
				live.add(m);
			}
		}
		return live;
	}

	/**
	 * A fresh mascot for today, chosen weighted-random among the day's
	 * eligible moods - or null if none are (only possible if every window
	 * is closed; the evergreens keep that from happening in practice).
	 */
	static Mascot pick(LocalDate date, Random rng)
	{
		List<MascotRoster> live = activeOn(date);
		if (live.isEmpty())
		{
			return null;
		}
		int total = 0;
		for (MascotRoster m : live)
		{
			total += m.weight;
		}
		int roll = rng.nextInt(total);
		for (MascotRoster m : live)
		{
			roll -= m.weight;
			if (roll < 0)
			{
				return m.factory.get();
			}
		}
		return live.get(live.size() - 1).factory.get(); // unreachable
	}

	/** An eligibility predicate over the calendar. */
	interface Window
	{
		boolean active(LocalDate date);

		/** Every day of every year. */
		Window ALWAYS = date -> true;

		/** A one-off, fully-dated span (inclusive) - for specific-year
		 * events like a World Cup or an Olympics that do not recur annually. */
		static Window dates(LocalDate start, LocalDate end)
		{
			return date -> !date.isBefore(start) && !date.isAfter(end);
		}

		/** Active when ANY of the given windows is - for an event that lands in
		 * several distinct spans (e.g. the World Cup's separate tournament years). */
		static Window anyOf(Window... windows)
		{
			return date ->
			{
				for (Window w : windows)
				{
					if (w.active(date))
					{
						return true;
					}
				}
				return false;
			};
		}

		/** An annual set of whole calendar months (1-12): active every year in
		 * any of the listed months. For school-term or single-month seasons. */
		static Window months(int... months)
		{
			boolean[] active = new boolean[13];
			for (int m : months)
			{
				active[m] = true;
			}
			return date -> active[date.getMonthValue()];
		}

		/** An annual month/day span (inclusive) that recurs every year;
		 * wraps the year boundary when start is after end (e.g. Dec 15 to
		 * Jan 2). For holidays that land on the same dates each year. */
		static Window annual(int startMonth, int startDay, int endMonth, int endDay)
		{
			MonthDay start = MonthDay.of(startMonth, startDay);
			MonthDay end = MonthDay.of(endMonth, endDay);
			boolean wraps = start.isAfter(end);
			return date ->
			{
				MonthDay md = MonthDay.from(date);
				return wraps
					? !md.isBefore(start) || !md.isAfter(end)
					: !md.isBefore(start) && !md.isAfter(end);
			};
		}

		/** Within radiusDays of an annual anchor date, spilling correctly
		 * across the year boundary (e.g. Dec 31 +/- 2 reaching into Jan). */
		static Window around(int month, int day, int radiusDays)
		{
			return date ->
			{
				for (int yr = date.getYear() - 1; yr <= date.getYear() + 1; yr++)
				{
					int d = (month == 2 && day == 29 && !Year.isLeap(yr)) ? 28 : day;
					LocalDate anchor = LocalDate.of(yr, month, d);
					if (Math.abs(ChronoUnit.DAYS.between(anchor, date)) <= radiusDays)
					{
						return true;
					}
				}
				return false;
			};
		}
	}
}
