package com.loadoutlab.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue report's version is build-stamped from build.gradle via
 * processResources; runelite-plugin.properties is still hand-bumped at
 * release. This pins the two together so the desync class that shipped
 * "v0.3.3" reports from a 0.3.4 client (field report 2026-08-07) fails
 * the gate instead of reaching users.
 */
class VersionStampTest
{
	@Test
	@DisplayName("the report's stamped version matches runelite-plugin.properties")
	void stampMatchesPluginProperties() throws Exception
	{
		Properties plugin = new Properties();
		plugin.load(Files.newInputStream(Path.of("runelite-plugin.properties")));
		String released = plugin.getProperty("version");
		assertNotNull(released, "runelite-plugin.properties must declare a version");
		assertEquals(released, com.loadoutlab.PluginVersion.VERSION,
			"build.gradle's version (the stamp) and runelite-plugin.properties"
				+ " have drifted - bump them together");
		assertNotEquals("unknown", com.loadoutlab.PluginVersion.VERSION,
			"the stamped resource must be readable at runtime");
	}
}
