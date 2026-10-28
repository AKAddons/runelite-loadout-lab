package com.loadoutlab;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;


/** The build-stamped plugin version (processResources writes
 * version.properties; VersionStampTest pins it to the hub manifest).
 * Neutral home - the report builder, seam hello and UIs all read it. */
public class PluginVersion {
    /** Bumped with every release (VersionStampTest pins it to build.gradle):
     * hub builds ship the resource unexpanded, so this is what players see. */
    public static final String FALLBACK = "0.7.1";
    public static final String VERSION = load();

    public static String stamp(String properties) {
        Matcher m = Pattern.compile("version=(\\S+)").matcher(properties);
        return m.find() && !m.group(1).contains("${") ? m.group(1) : FALLBACK;
    }


    static String load() {
        try (InputStream in = PluginVersion.class.getResourceAsStream( "/com/loadoutlab/version.properties")) {
            return stamp(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) { return "unknown"; }
    }
}
