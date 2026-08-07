package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Equivalence sweep for the table-driven BoostProfile.apply(): the OLD
 * hardcoded per-constant switch is kept here verbatim and compared against
 * the live one over every constant at every level 1..126. Test source is
 * free of the plugin-hub token cap, so this stays as the permanent guard
 * that the (flat, factor, skills) encoding still reproduces the original
 * numbers - including MAGIC's flat +4 as factor 0.0 and NONE's same-instance
 * identity return.
 */
public class BoostProfileSweepTest
{
	private static int boost(int level, int base, double factor)
	{
		return (int) Math.floor(base + level * factor);
	}

	/** Verbatim copy of the pre-refactor apply() body. */
	private static PlayerLevels oldApply(BoostProfile profile, PlayerLevels base, PlayerLevels current)
	{
		PlayerLevels source = base == null ? PlayerLevels.MAXED : base;
		switch (profile)
		{
			case LIVE_CURRENT:
				return current == null ? source : current;
			case F2P_COMBAT:
				return source.withBoosts(
					boost(source.getAttack(), 3, 0.10),
					boost(source.getStrength(), 3, 0.10),
					0,
					0,
					0);
			case SUPER_COMBAT:
			case DIVINE_SUPER_COMBAT: // added 2026-07-21: same numbers as the base
				return source.withBoosts(
					boost(source.getAttack(), 5, 0.15),
					boost(source.getStrength(), 5, 0.15),
					boost(source.getDefence(), 5, 0.15),
					0,
					0);
			case RANGING:
			case DIVINE_RANGING: // added 2026-07-21: same numbers as the base
				return source.withBoosts(0, 0, 0, boost(source.getRanged(), 4, 0.10), 0);
			case SUPER_RANGING:
				return source.withBoosts(0, 0, 0, boost(source.getRanged(), 5, 0.15), 0);
			case SATURATED_HEART:
				return source.withBoosts(0, 0, 0, 0, boost(source.getMagic(), 4, 0.10));
			case IMBUED_HEART:
				return source.withBoosts(0, 0, 0, 0, boost(source.getMagic(), 1, 0.10));
			case MAGIC:
			case DIVINE_MAGIC: // added 2026-07-21: same numbers as the base
				return source.withBoosts(0, 0, 0, 0, 4);
			case SUPER_MAGIC:
				return source.withBoosts(0, 0, 0, 0, boost(source.getMagic(), 5, 0.15));
			case OVERLOAD:
				return source.withBoosts(
					boost(source.getAttack(), 5, 0.13),
					boost(source.getStrength(), 5, 0.13),
					boost(source.getDefence(), 5, 0.13),
					boost(source.getRanged(), 5, 0.13),
					boost(source.getMagic(), 5, 0.13));
			case OVERLOAD_PLUS:
				return source.withBoosts(
					boost(source.getAttack(), 6, 0.16),
					boost(source.getStrength(), 6, 0.16),
					boost(source.getDefence(), 6, 0.16),
					boost(source.getRanged(), 6, 0.16),
					boost(source.getMagic(), 6, 0.16));
			case SMELLING_SALTS:
				return source.withBoosts(
					boost(source.getAttack(), 11, 0.16),
					boost(source.getStrength(), 11, 0.16),
					boost(source.getDefence(), 11, 0.16),
					boost(source.getRanged(), 11, 0.16),
					boost(source.getMagic(), 11, 0.16));
			case NONE:
			default:
				return source;
		}
	}

	@Test
	public void tableDrivenBoostsMatchTheOldPerConstantSwitchAtEveryLevel()
	{
		for (BoostProfile profile : BoostProfile.values())
		{
			for (int level = 1; level <= 126; level++)
			{
				// Asymmetric levels so a mis-wired skill letter cannot hide.
				PlayerLevels base = new PlayerLevels(level, level + 1, level + 2,
					level + 3, level + 4, level + 5, level + 6);
				PlayerLevels expected = oldApply(profile, base, null);
				PlayerLevels actual = profile.apply(base, null);
				String at = profile + " @ " + level;
				Assert.assertEquals(at, expected.getAttack(), actual.getAttack());
				Assert.assertEquals(at, expected.getStrength(), actual.getStrength());
				Assert.assertEquals(at, expected.getDefence(), actual.getDefence());
				Assert.assertEquals(at, expected.getRanged(), actual.getRanged());
				Assert.assertEquals(at, expected.getMagic(), actual.getMagic());
			}
		}
		// NONE must hand back the SAME instance, not an equal copy.
		PlayerLevels base = new PlayerLevels(70, 70, 70, 70, 70, 70, 70);
		Assert.assertSame(base, BoostProfile.NONE.apply(base, null));
		// LIVE_CURRENT still prefers the live levels, and falls back to base.
		PlayerLevels live = new PlayerLevels(80, 80, 80, 80, 80, 80, 80);
		Assert.assertSame(live, BoostProfile.LIVE_CURRENT.apply(base, live));
		Assert.assertSame(base, BoostProfile.LIVE_CURRENT.apply(base, null));
	}
}
