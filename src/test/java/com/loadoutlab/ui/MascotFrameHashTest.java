package com.loadoutlab.ui;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mascot frame-hash gate is only worth anything if it is reproducible,
 * so prove that here rather than assuming it: hashing the whole roster
 * twice in one JVM must agree. If a mood ever picks up a clock read or a
 * random source this fails immediately, which is exactly when the gate
 * would otherwise start lying.
 */
class MascotFrameHashTest
{
	@Test
	@DisplayName("mascot frame hashes are reproducible run to run")
	void framesAreDeterministic() throws Exception
	{
		if (!MascotArt.available())
		{
			return; // sprite not on the classpath in this environment
		}
		List<String> first = MascotFrameHash.hashes();
		List<String> second = MascotFrameHash.hashes();
		assertEquals(first, second, "the same roster must hash identically twice");
		assertFalse(first.isEmpty(), "the roster must not be empty");
		for (String line : first)
		{
			assertTrue(line.matches("[A-Z_]+ [0-9a-f]{64}"), "malformed hash line: " + line);
		}
	}
}
