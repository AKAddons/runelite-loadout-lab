package com.loadoutlab.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The contract's transport rules (docs/COMPANION_CONTRACT.md): pages
 * are versioned, serialization is canonical and deterministic, and
 * non-JSON-safe values fail loudly instead of leaking classes across
 * the classloader seam.
 */
class ModelShapeTest
{
	@Test
	@DisplayName("every page carries the contract version")
	void pageCarriesVersion()
	{
		Map<String, Object> page = RenderModel.page(List.of());
		assertEquals(RenderModel.VERSION, page.get("v"));
		assertTrue(Json.write(page).startsWith("{\"v\":" + RenderModel.VERSION));
	}

	@Test
	@DisplayName("canonical JSON is byte-stable and escapes strictly")
	void canonicalAndEscaped()
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("name", "Rada's \"blessing\"\n4\\");
		node.put("dps", 9.196969696969697d);
		node.put("hp", 240);
		node.put("none", null);
		node.put("flag", true);
		String once = Json.write(node);
		assertEquals(once, Json.write(node), "same input, same bytes");
		assertEquals("{\"name\":\"Rada's \\\"blessing\\\"\\n4\\\\\","
			+ "\"dps\":9.196969696969697,\"hp\":240,\"none\":null,\"flag\":true}", once);
	}

	@Test
	@DisplayName("non-JSON-safe values fail loudly, never cross the seam")
	void unsafeValuesRejected()
	{
		assertThrows(IllegalArgumentException.class,
			() -> Json.write(Map.of("bad", new Object())));
		assertThrows(IllegalArgumentException.class,
			() -> Json.write(Map.of("bad", Double.NaN)));
	}
}
