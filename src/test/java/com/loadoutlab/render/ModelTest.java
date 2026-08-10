package com.loadoutlab.render;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest
{
	private final Map<String, Object> node = Map.of(
		"name", "Twisted bow",
		"dps", 9.19,
		"id", 20997,
		"flag", true,
		"nested", Map.of("k", 1),
		"rows", List.of(Map.of("a", 1)));

	@Test
	@DisplayName("typed accessors read what is there")
	void reads()
	{
		assertEquals("Twisted bow", Model.str(node, "name"));
		assertEquals(9.19, Model.num(node, "dps"));
		assertEquals(20997, Model.id(node, "id"));
		assertTrue(Model.flag(node, "flag"));
		assertEquals(1, Model.list(node, "rows").size());
		assertNotNull(Model.map(node, "nested"));
	}

	@Test
	@DisplayName("missing or oddly-typed fields degrade to benign defaults")
	void degrades()
	{
		assertNull(Model.str(node, "absent"));
		assertNull(Model.str(node, "dps"), "wrong type reads as absent");
		assertEquals(0, Model.num(node, "name"));
		assertFalse(Model.flag(node, "name"));
		assertNull(Model.map(node, "rows"));
		assertTrue(Model.list(node, "nested").isEmpty());
		assertNull(Model.map(null, "k"));
		assertEquals(0, Model.num(null, "k"));
	}
}
