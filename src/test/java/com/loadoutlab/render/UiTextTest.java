package com.loadoutlab.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The surface's prose lives in text.tsv and is read by key (Ui.text): a key
 * the source names but the file lacks would render as "null" in a tooltip
 * nobody hovers in a test, so this pins the two to each other both ways.
 */
class UiTextTest {
	// Every main source: since slim 16 the model and engine read the table too.
	private static final Path RENDER = Paths.get("src/main/java/com/loadoutlab");

	/** Every key the render sources ask Ui.text for, with the file naming it:
	 * Ui.text("key"), or text("key") where Ui is statically imported. */
	private static List<String[]> referencedKeys() throws IOException {
		List<String[]> keys = new ArrayList<>();
		Pattern call = Pattern.compile("(?<![\\w.])(?:Ui\\.)?text\\(\"([^\"]+)\"\\)");
		try (Stream<Path> files = Files.walk(RENDER)) {
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
				Matcher m = call.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				while (m.find()) {
					keys.add(new String[]{m.group(1), file.getFileName().toString()});
				}
			}
		}
		assertFalse(keys.isEmpty(), "no Ui.text(...) calls found under " + RENDER);
		return keys;
	}

	@Test
	@DisplayName("every key the source asks for resolves to a non-blank text")
	void everyReferencedKeyResolves() throws IOException {
		for (String[] key : referencedKeys()) {
			String text = Ui.text(key[0]);
			assertNotNull(text, key[1] + " asks for \"" + key[0] + "\", which text.tsv does not hold");
			assertFalse(text.isBlank(), key[0] + " is blank");
		}
	}

	@Test
	@DisplayName("every text.tsv key is asked for somewhere, so the file carries no dead prose")
	void everyKeyIsReferenced() throws IOException {
		Map<String, String> table = Ui.tsv("text.tsv");
		assertFalse(table.isEmpty(), "text.tsv missing or empty");
		TreeSet<String> referenced = new TreeSet<>();
		for (String[] key : referencedKeys()) {
			referenced.add(key[0]);
		}
		for (Map.Entry<String, String> row : table.entrySet()) {
			assertTrue(referenced.contains(row.getKey()), "text.tsv key \"" + row.getKey() + "\" is never asked for");
			// The tofu-glyph gate (scripts/check_glyphs.py) reads Java literals
			// only, so the file keeps itself to plain ASCII here.
			assertTrue(row.getValue().chars().allMatch(c -> c < 128), row.getKey() + " carries a non-ASCII glyph");
		}
	}

	@Test
	@DisplayName("the loader reads key-tab-value rows and skips comments, as the mood routes rely on")
	void loaderShape() {
		Map<String, String> routes = Ui.tsv("mood_routes.tsv");
		assertEquals("zulrah", routes.get("Zulrah"));
		assertFalse(routes.containsKey("# monster-name prefix -> compute-animation pool"), "comment lines skipped");
		assertTrue(Ui.tsv("no-such-file.tsv").isEmpty(), "a missing resource reads as empty, never throws");
	}
}
