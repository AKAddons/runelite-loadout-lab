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
 *   - Workout (weight 2) runs all year and is currently the whole
 *     roster. The Skater (weight 1, evergreen) is BENCHED in
 *     ~/Development/loadout-lab-attic - cut 2026-08-09 to hold under
 *     the hub submit gate; it comes home with the mascot companion
 *     plugin (ADR-0008). Restore per that repo's README.
 *   - Chef (weight 1, every month but October) is BENCHED in
 *     ~/Development/loadout-lab-attic - the largest of the three moods,
 *     cut 2026-08-02 to buy hub-cap headroom for the 0.3.4/0.3.5 slices.
 *     Restore per that repo's README whenever the budget allows.
 *   - The Halloween cauldron (weight 6, October) is BENCHED there too
 *     until October (token budget; restore before the month starts).
 *   - {@code Window.months} is deliberately KEPT despite having no caller
 *     now: the benched chef, classroom and cauldron all need it back on
 *     restore, and a dead factory is cheaper than three broken restores.
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
	WORKOUT(Window.ALWAYS, 2, MascotSpinner::new);

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
