package com.loadoutlab.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	@DisplayName("hub builds never show ${version}: the source fallback matches build.gradle and the stamp is never a placeholder")
	void fallbackMatchesBuild() throws Exception
	{
		String gradle = java.nio.file.Files.readString(java.nio.file.Path.of("build.gradle"));
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("version = '([^']+)'").matcher(gradle);
		assertTrue(m.find(), "build.gradle declares the version");
		assertEquals(m.group(1), com.loadoutlab.PluginVersion.FALLBACK, "bump FALLBACK with the release");
		assertFalse(com.loadoutlab.PluginVersion.VERSION.contains("${"), com.loadoutlab.PluginVersion.VERSION);
		assertEquals(com.loadoutlab.PluginVersion.FALLBACK, com.loadoutlab.PluginVersion.stamp("version=${version}"));
		assertEquals("9.9.9", com.loadoutlab.PluginVersion.stamp("version=9.9.9"));
	}
}
