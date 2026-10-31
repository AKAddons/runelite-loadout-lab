package com.loadoutlab.data;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import java.util.zip.GZIPInputStream;
import java.lang.reflect.Type;

// Loaders for the bundled JSON data tables (the hub token cap keeps data
// in resources, not source). All fail SOFT - a malformed table must never
// take the client down; the table's tests fail loudly on emptiness instead.
// The one exception is {@link #gz}: the gzipped corpus is not optional.
public class JsonResources {
    // The client's Gson, handed over by LoadoutLabPlugin at startup (the hub
    // bans new Gson()); the test tree registers a GsonSource for its own.
    public interface GsonSource { Gson gson(); }

    static Gson gson;

    public static void use(Gson g) { gson = g; }

    public static Gson gson() {
        if (gson == null) for (GsonSource s : ServiceLoader.load(GsonSource.class)) gson = s.gson();
        if (gson == null) throw new IllegalStateException("no Gson handed to JsonResources");
        return gson;
    }

    // The player-facing prose - tooltips, captions, notes - by key (render's
    // text.tsv; the Plugin Hub's review bot counts source tokens, and words
    // are data, not code). A missing key reads null, which UiTextTest catches.
    static final Map<String, String> TEXT = tsv("/com/loadoutlab/render/text.tsv");

    public static String text(String key) { return TEXT.get(key); }

    // key<TAB>value rows of a resource ('#' lines are comments; a value keeps
    // its edge spaces - a sentence fragment joins its neighbours).
    public static Map<String, String> tsv(String name) {
        Map<String, String> out = new LinkedHashMap<>();
        try (InputStream in = JsonResources.class.getResourceAsStream(name)) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n")) {
                    int tab = line.indexOf('\t');
                    if (tab > 0 && !line.startsWith("#")) out.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    // A resource (gzipped when it ends in .gz) read straight into a type
    // (ISS-01M65SN2SAXVSR9EBVB1R831SV, slim 12: Gson fills the row classes).
    public static <T> T read(String resource, Class<T> type) { return read(resource, (Type) type); }

    public static <T> T read(String resource, Type type) {
        try (InputStream in = JsonResources.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            InputStream src = resource.endsWith(".gz") ? new GZIPInputStream(in) : in;
            return gson().fromJson(new InputStreamReader(src, StandardCharsets.UTF_8), type);
        } catch (IOException ex) { throw new IllegalStateException("Could not load " + resource, ex); }
    }

    // The parsed resource, or null when missing/malformed.
    static JsonElement parse(String resource) {
        try (InputStream in = JsonResources.class.getResourceAsStream(resource)) {
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
    }

    // The parsed root object, or null when missing/malformed.
    public static JsonObject object(String resource) {
        JsonElement root = parse(resource);
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    // The parsed root object, or an IllegalStateException when the resource is
    // missing or malformed. For tables where an empty map would silently
    // change results (rather than merely degrade a display), which is the one
    // case the fail-soft object() above must not be used for.
    public static JsonObject objectOrThrow(String resource) {
        JsonObject root = object(resource);
        if (root == null) throw new IllegalStateException("Could not load " + resource);
        return root;
    }

    // The gzipped JSON resource beside this class, parsed; a missing or
    // unreadable bundle throws - the corpus is not optional.
    public static JsonElement gz(String resource) {
        try (InputStream in = JsonResources.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            return new JsonParser().parse(new InputStreamReader( new GZIPInputStream(in), StandardCharsets.UTF_8));
        } catch (IOException ex) { throw new IllegalStateException("Could not load " + resource, ex); }
    }

    // Copy the string array at root[key] into the collection.
    public static void strings(JsonObject root, String key, Collection<String> into) {
        each(root, key, JsonElement::getAsString, into);
    }

    // Copy the int array at root[key] into the collection.
    public static void ints(JsonObject root, String key, Collection<Integer> into) {
        each(root, key, JsonElement::getAsInt, into);
    }

    static <T> void each(JsonObject root, String key, Function<JsonElement, T> read, Collection<T> into) {
        if (root != null && root.has(key)) root.getAsJsonArray(key).forEach(e -> into.add(read.apply(e)));
    }

    // A JSON int array as an int[].
    public static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt();
        return out;
    }

    // Copy the {"name": "text"} object at root[key] into the map.
    public static void stringMap(JsonObject root, String key, Map<String, String> into) {
        entries(root, key, (k, v) -> into.put(k, v.getAsString()));
    }

    // Copy the {"name": int} object at root[key] into the map.
    public static void stringIntMap(JsonObject root, String key, Map<String, Integer> into) {
        entries(root, key, (k, v) -> into.put(k, v.getAsInt()));
    }

    // Each member of the object at root[key]; nothing when absent.
    static void entries(JsonObject root, String key, BiConsumer<String, JsonElement> fn) {
        JsonObject obj = root == null ? null : root.getAsJsonObject(key);
        if (obj != null) obj.entrySet().forEach(e -> fn.accept(e.getKey(), e.getValue()));
    }

    // A JSON-safe node from key/value pairs, in argument order (the rows
    // the trip kit and the pages carry; a LinkedHashMap, so gson's reflective
    // adapter and the canonical page JSON both see a plain ordered map).
    public static Map map(Object... kv) {
        Map node = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) node.put((String) kv[i], kv[i + 1]);
        return node;
    }
}
