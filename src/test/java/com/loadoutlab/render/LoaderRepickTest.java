package com.loadoutlab.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Field report (Andrew, 2026-09-02): "when i interrupt a search before it
 * completes it seems to use the first search's animation". A compute that
 * starts while the loader is already running must re-pick from the NEW
 * selection's pool. */
class LoaderRepickTest
{
	@Test
	@DisplayName("a compute started mid-animation plays the new selection's pool")
	void repicksWhenTheKeyChanges()
	{
		AsciiLoader loader = new AsciiLoader();
		try
		{
			loader.setKey("sea");
			loader.setRunning(true);
			assertEquals("sea", loader.playingKey());
			// The user searched again before the first compute finished.
			loader.setKey("toa");
			loader.setRunning(true);
			assertEquals("toa", loader.playingKey());
			// Land falls back to the flask pool (null key).
			loader.setKey(null);
			loader.setRunning(true);
			assertNull(loader.playingKey());
		}
		finally
		{
			loader.setRunning(false);
		}
	}
}
