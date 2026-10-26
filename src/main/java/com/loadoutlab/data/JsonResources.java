package com.loadoutlab.data;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/**
 * Loaders for the bundled JSON data tables (the hub token cap keeps data
 * in resources, not source). All fail SOFT - a malformed table must never
 * take the client down; the table's tests fail loudly on emptiness instead.
 * The one exception is {@link #gz}: the gzipped corpus is not optional.
 */
public final class JsonResources {
    private JsonResources() {
    }

    /** The parsed resource, or null when missing/malformed. */
    private static JsonElement parse(String resource) {
        try (InputStream in = JsonResources.class.getResourceAsStream(resource)) {
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
    }

    /** The parsed root object, or null when missing/malformed. */
    public static JsonObject object(String resource) {
        JsonElement root = parse(resource);
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    /**
     * The parsed root object, or an IllegalStateException when the resource is
     * missing or malformed. For tables where an empty map would silently
     * change results (rather than merely degrade a display), which is the one
     * case the fail-soft object() above must not be used for.
     */
    public static JsonObject objectOrThrow(String resource) {
        JsonObject root = object(resource);
        if (root == null) throw new IllegalStateException("Could not load " + resource);
        return root;
    }

    /** The parsed root array, or an empty one when missing/malformed. */
    public static JsonArray array(String resource) {
        JsonElement root = parse(resource);
        return root != null && root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
    }

    /** The gzipped JSON resource beside this class, parsed; a missing or
     * unreadable bundle throws - the corpus is not optional. */
    public static JsonElement gz(String resource) {
        try (InputStream in = JsonResources.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            return new JsonParser().parse(new InputStreamReader( new java.util.zip.GZIPInputStream(in), StandardCharsets.UTF_8));
        } catch (java.io.IOException ex) { throw new IllegalStateException("Could not load " + resource, ex); }
    }

    /** Copy the string array at root[key] into the collection. */
    public static void strings(JsonObject root, String key, Collection<String> into) {
        each(root, key, JsonElement::getAsString, into);
    }

    /** Copy the int array at root[key] into the collection. */
    public static void ints(JsonObject root, String key, Collection<Integer> into) {
        each(root, key, JsonElement::getAsInt, into);
    }

    private static <T> void each(JsonObject root, String key, Function<JsonElement, T> read, Collection<T> into) {
        if (root != null && root.has(key)) root.getAsJsonArray(key).forEach(e -> into.add(read.apply(e)));
    }

    /** A JSON int array as an int[]. */
    public static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt();
        return out;
    }

    /** A JSON array of string arrays as String[][]. */
    public static String[][] rows(JsonArray array) {
        String[][] out = new String[array.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray row = array.get(i).getAsJsonArray();
            out[i] = new String[row.size()];
            for (int j = 0; j < out[i].length; j++) out[i][j] = row.get(j).getAsString();
        }
        return out;
    }

    /** Copy the {"name": "text"} object at root[key] into the map. */
    public static void stringMap(JsonObject root, String key, Map<String, String> into) {
        entries(root, key, (k, v) -> into.put(k, v.getAsString()));
    }

    /** Copy the {"name": int} object at root[key] into the map. */
    public static void stringIntMap(JsonObject root, String key, Map<String, Integer> into) {
        entries(root, key, (k, v) -> into.put(k, v.getAsInt()));
    }

    /** Copy the {"id": int} object at root[key] into the map. */
    public static void intMap(JsonObject root, String key, Map<Integer, Integer> into) {
        entries(root, key, (k, v) -> into.put(Integer.parseInt(k), v.getAsInt()));
    }

    /** Each member of the object at root[key]; nothing when absent. */
    private static void entries(JsonObject root, String key, BiConsumer<String, JsonElement> fn) {
        JsonObject obj = root == null ? null : root.getAsJsonObject(key);
        if (obj != null) obj.entrySet().forEach(e -> fn.accept(e.getKey(), e.getValue()));
    }

    /** A JSON-safe node from key/value pairs, in argument order (the rows
     * the trip kit and the pages carry; a LinkedHashMap, so gson's reflective
     * adapter and the canonical page JSON both see a plain ordered map). */
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> node = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) node.put((String) kv[i], kv[i + 1]);
        return node;
    }

    // The row accessors every table loader shares: a missing or null field
    // reads as the fallback, never a NullPointerException.

    public static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element == null || element.isJsonNull() ? new JsonObject() : element.getAsJsonObject();
    }

    public static JsonArray array(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element == null || element.isJsonNull() ? new JsonArray() : element.getAsJsonArray();
    }

    public static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    public static Integer nullableInteger(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsInt();
    }

    public static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsInt();
        } catch (NumberFormatException ex) { return fallback; }
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }
}
