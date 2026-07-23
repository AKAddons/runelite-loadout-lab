package com.loadoutlab.ui;

import java.time.LocalDate;
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
 *   - Chef (weight 1) cooks every month except October, which stays
 *     reserved for the Halloween cauldron (weight 6) - BENCHED in
 *     ~/Development/loadout-lab-attic until October (token budget;
 *     restore per that repo's README before the month starts).
 *   - Classroom (weight 1, school terms Jan-May and Sep-Nov) is BENCHED
 *     in ~/Development/loadout-lab-attic until September (token budget;
 *     restore per that repo's README before the term starts).
 *   - The World Cup striker (weight 6, June-July of tournament years) is
 *     RETIRED to ~/Development/loadout-lab-attic after the 2026 final -
 *     restore per that repo's README for 2030. The dated-window factories
 *     it needed (Window.dates/anyOf, plus annual/around) were retired with
 *     it once nothing called them; their BODIES are pasted verbatim into
 *     that README, so a restore is copy-paste, not archaeology.
 */
enum MascotRoster
{
	// Evergreen - always eligible, low base weight.
	WORKOUT(Window.ALWAYS, 2, MascotSpinner::new),
	SKATER(Window.ALWAYS, 1, MascotSkater::new),
	// Chef cooks year-round except October (kept clear for the benched
	// cauldron's return - see the attic note below).
	CHEF(Window.months(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12), 1, MascotChef::new);

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
	}
}
