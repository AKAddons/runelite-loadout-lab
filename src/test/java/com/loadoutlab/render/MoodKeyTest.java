package com.loadoutlab.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The compute animation follows the selection: sea, a raid, Zulrah, or
 * the land flask (Andrew 2026-09-02: "definitely want to hit raids first"). */
class MoodKeyTest
{
	private static Map<String, Object> page(String rosterName, String mobName, boolean naval)
	{
		Map<String, Object> mob = new LinkedHashMap<>();
		mob.put("name", mobName);
		mob.put("naval", naval);
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("lensIndex", 0);
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("params", params);
		entry.put("mobs", List.of(mob));
		if (rosterName != null)
		{
			entry.put("rosterName", rosterName);
		}
		Map<String, Object> page = new LinkedHashMap<>();
		page.put("entries", List.of(entry));
		return page;
	}

	@Test
	@DisplayName("the animation pool follows the selection")
	void poolFollowsTheSelection()
	{
		assertEquals("sea", RenderSurface.moodKey(page(null, "Hammerhead shark", true)));
		assertEquals("toa", RenderSurface.moodKey(page("Tombs of Amascut", "Ba-Ba", false)));
		assertEquals("tob", RenderSurface.moodKey(page("Theatre of Blood (Hard)", "Verzik Vitur", false)));
		assertEquals("cox", RenderSurface.moodKey(page("Chambers of Xeric", "Tekton", false)));
		assertEquals("zulrah", RenderSurface.moodKey(page(null, "Zulrah", false)));
		assertNull(RenderSurface.moodKey(page(null, "General Graardor", false)), "land keeps the flask");
		assertNull(RenderSurface.moodKey(page("Custom roster", "Vorkath", false)));
	}
}
