package com.loadoutlab;

import java.util.Properties;

/** The build-stamped plugin version (processResources writes
 * version.properties; VersionStampTest pins it to the hub manifest).
 * Neutral home - the report builder, seam hello and UIs all read it. */
public final class PluginVersion
{
	public static final String VERSION = load();

	private PluginVersion()
	{
	}

	private static String load()
	{
		try (java.io.InputStream in = PluginVersion.class.getResourceAsStream(
			"/com/loadoutlab/version.properties"))
		{
			Properties props = new Properties();
			props.load(in);
			return props.getProperty("version", "unknown");
		}
		catch (Exception ex)
		{
			return "unknown";
		}
	}
}
