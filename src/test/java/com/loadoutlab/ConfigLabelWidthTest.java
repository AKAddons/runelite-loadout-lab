package com.loadoutlab;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The RuneLite config panel is ~230px wide and sizes each dropdown to its
 * LONGEST option - one overlong label truncates the row's own name (field
 * report 2026-07-23: "Melee ..." / "Ranged b..."). Every dropdown label
 * across the config enums stays within a hard cap so the rows keep their
 * names.
 */
class ConfigLabelWidthTest
{
	private static final int MAX_LABEL = 16;

	@Test
	@DisplayName("every config dropdown label fits the panel (max 16 chars)")
	void dropdownLabelsFit()
	{
		List<String> over = new ArrayList<>();
		for (Class<?> nested : LoadoutLabConfig.class.getDeclaredClasses())
		{
			if (!nested.isEnum())
			{
				continue;
			}
			for (Object constant : nested.getEnumConstants())
			{
				// What the config panel actually renders (core title-cases
				// bare enum names; an overridden toString wins).
				String label = net.runelite.client.util.Text.titleCase((Enum<?>) constant);
				if (label.length() > MAX_LABEL)
				{
					over.add(nested.getSimpleName() + "." + constant + " -> \"" + label + "\"");
				}
			}
		}
		assertTrue(over.isEmpty(), "labels wider than " + MAX_LABEL + " chars: " + over);
	}
}
