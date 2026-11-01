package com.ll.render;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsciiLoaderTest {
	private static List<List<String>> parse(String text) {
		InputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
		return AsciiLoader.load(in);
	}

	@Test
	@DisplayName("a pool file parses into moods of frames, and a run of dashes in the art is not a separator")
	void parsesMoodsAndFrames() {
		List<List<String>> moods = parse("==a one\nxx\nyy\n--\n--- wave\nzz\n==a two\nqq\n--\nrr\n");
		assertEquals(2, moods.size());
		assertEquals(List.of("xx\nyy", "--- wave\nzz"), moods.get(0));
		assertEquals(List.of("qq", "rr"), moods.get(1));
	}

	@Test
	@DisplayName("an empty pool file is no moods, and the loader falls back to a plain line rather than crashing")
	void emptyFileIsSoft() {
		assertTrue(parse("").isEmpty());
		assertEquals(List.of("Computing..."), AsciiLoader.PLAIN);
	}

	@Test
	@DisplayName("every route and every lens or raid key has painted art, except the bosses the plugin does not simulate")
	void everyKeyHasArt() throws Exception {
		Set<String> keys = new HashSet<>(List.of("sea", "toa", "tob", "cox", "tfa", "flask", "halloween"));
		for (String line : Files.readAllLines(new File("src/main/resources/com/ll/render/mood_routes.tsv").toPath())) {
			if (!line.startsWith("#") && !line.isBlank()) keys.add(line.split("\t")[1]);
		}
		keys.remove("hunllef");
		for (String key : keys) {
			assertNotNull(AsciiLoader.pool(key), key + " has no moods file");
		}
	}

	@Test
	@DisplayName("each holiday plays on its days, a moving one wins its week, and the table's every pool has art")
	void holidays() throws Exception {
		assertEquals("christmas", AsciiLoader.seasonal(LocalDate.of(2031, 12, 25)));
		assertEquals("solstice", AsciiLoader.seasonal(LocalDate.of(2027, 6, 21)));
		assertEquals("solstice", AsciiLoader.seasonal(LocalDate.of(2027, 12, 21)));
		assertEquals("newyear", AsciiLoader.seasonal(LocalDate.of(2027, 12, 31)));
		assertEquals("newyear", AsciiLoader.seasonal(LocalDate.of(2028, 1, 1)));
		assertEquals("valentine", AsciiLoader.seasonal(LocalDate.of(2027, 2, 14)));
		assertEquals("stpatrick", AsciiLoader.seasonal(LocalDate.of(2027, 3, 17)));
		assertEquals("earth", AsciiLoader.seasonal(LocalDate.of(2027, 4, 22)));
		assertEquals("lunar", AsciiLoader.seasonal(LocalDate.of(2027, 2, 6)));
		assertEquals("holi", AsciiLoader.seasonal(LocalDate.of(2028, 3, 11)));
		assertEquals("easter", AsciiLoader.seasonal(LocalDate.of(2027, 3, 28)));
		assertEquals("eid", AsciiLoader.seasonal(LocalDate.of(2027, 3, 10)));
		assertEquals("lunar", AsciiLoader.seasonal(LocalDate.of(2034, 2, 19)), "a moving row outranks a fixed window");
		assertEquals("flask", AsciiLoader.seasonal(LocalDate.of(2027, 7, 4)));
		assertEquals("flask", AsciiLoader.seasonal(LocalDate.of(2040, 3, 10)), "past the table only fixed rows play");
		try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(AsciiLoader.class.getResourceAsStream("holidays.tsv")))) {
			r.lines().map(l -> l.split("\t")[2]).distinct().forEach(k -> assertNotNull(AsciiLoader.pool(k), k));
		}
	}

	@Test
	@DisplayName("Halloween plays from Oct 15 to Oct 31 and the flask the rest of the year")
	void season() {
		assertEquals("flask", AsciiLoader.seasonal(LocalDate.of(2026, 10, 14)));
		assertEquals("halloween", AsciiLoader.seasonal(LocalDate.of(2026, 10, 15)));
		assertEquals("halloween", AsciiLoader.seasonal(LocalDate.of(2026, 10, 31)));
		assertEquals("flask", AsciiLoader.seasonal(LocalDate.of(2026, 11, 1)));
	}
}
