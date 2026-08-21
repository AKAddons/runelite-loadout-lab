package com.loadoutlab.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssumeIconsTest
{
	@Test
	@DisplayName("a compound prayer label wears its lead prayer's icon")
	void compoundLabelsResolveToTheLeadPrayer()
	{
		// The picker offers the paired tiers; the icon is the strength
		// prayer's (field ask 2026-08-20: no more U/S/B letters).
		assertEquals(AssumeIcons.prayerSprite("Ultimate Strength"),
			AssumeIcons.prayerSprite("Ultimate Strength + Incredible Reflexes"));
		assertEquals(AssumeIcons.prayerSprite("Superhuman Strength"),
			AssumeIcons.prayerSprite("Superhuman Strength + Improved Reflexes"));
		assertEquals(AssumeIcons.prayerSprite("Burst of Strength"),
			AssumeIcons.prayerSprite("Burst of Strength + Clarity of Thought"));
		assertTrue(AssumeIcons.prayerSprite("Ultimate Strength + Incredible Reflexes") > 0);
	}

	@Test
	@DisplayName("exact names still hit their own row and unknowns still miss")
	void exactAndUnknownBehaviourUnchanged()
	{
		assertEquals(946, AssumeIcons.prayerSprite("Piety"));
		assertEquals(-1, AssumeIcons.prayerSprite("Not a prayer"));
		assertEquals(-1, AssumeIcons.prayerSprite("Not a prayer + Also not one"));
	}
}
