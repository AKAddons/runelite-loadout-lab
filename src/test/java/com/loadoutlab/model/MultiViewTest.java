package com.loadoutlab.model;

import com.loadoutlab.engine.RollMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Multi view on the page (Andrew 2026-10-19): with the switch on, a
 * mob's tool nodes become its style tabs, every per-target number
 * multiplied by the stack - area tools by the stack up to nine, the
 * venator chain by its bounce factor from the max hit.
 */
class MultiViewTest
{
	private static Map<String, Object> card(double dps, int maxHit)
	{
		Map<String, Object> card = new LinkedHashMap<>();
		card.put("dps", dps);
		card.put("maxHit", maxHit);
		card.put("kitBacked", true);
		return card;
	}

	private static Map<String, Object> tool(String kind, double dps, int maxHit)
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("multiKind", kind);
		node.put("tabDps", dps);
		node.put("bisTabDps", dps + 1);
		node.put("yours", card(dps, maxHit));
		node.put("bis", card(dps + 1, maxHit + 4));
		return node;
	}

	private static List<Object> page()
	{
		Map<String, Object> multi = new LinkedHashMap<>();
		multi.put("barrage", tool("area", 5.0, 30));
		multi.put("venator", tool("chain", 4.0, 30));
		Map<String, Object> mob = new LinkedHashMap<>();
		mob.put("styles", new LinkedHashMap<>(Map.of("melee", tool("", 9.0, 40))));
		mob.put("multi", multi);
		List<Object> mobs = new ArrayList<>();
		mobs.add(mob);
		return mobs;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> styles(List<Object> mobs)
	{
		return (Map<String, Object>) ((Map<?, ?>) mobs.get(0)).get("styles");
	}

	@Test
	@DisplayName("at a stack of three the tools replace the styles: area x3, the chain by its bounce factor")
	void stackOfThree()
	{
		List<Object> mobs = page();
		CommandEngine.multiView(mobs, 3);
		Map<String, Object> styles = styles(mobs);
		assertEquals(List.of("barrage", "venator"), new ArrayList<>(styles.keySet()));
		Map<?, ?> barrage = (Map<?, ?>) styles.get("barrage");
		assertEquals(15.0, ((Number) barrage.get("tabDps")).doubleValue(), 1e-9);
		assertEquals(18.0, ((Number) barrage.get("bisTabDps")).doubleValue(), 1e-9);
		assertEquals(15.0, ((Number) ((Map<?, ?>) barrage.get("yours")).get("dps")).doubleValue(), 1e-9);
		assertEquals(3.0, ((Number) barrage.get("multiFactor")).doubleValue(), 1e-9);
		Map<?, ?> venator = (Map<?, ?>) styles.get("venator");
		double chain = RollMath.venatorFactor(30);
		assertEquals(4.0 * chain, ((Number) venator.get("tabDps")).doubleValue(), 1e-9);
		assertEquals(5.0 * RollMath.venatorFactor(34), ((Number) venator.get("bisTabDps")).doubleValue(), 1e-9,
			"the ceiling's chain uses the ceiling's max hit");
	}

	@Test
	@DisplayName("a stack of one leaves every number per target, and a mob without tools keeps its styles")
	void stackOfOneAndNoTools()
	{
		List<Object> mobs = page();
		CommandEngine.multiView(mobs, 1);
		Map<?, ?> venator = (Map<?, ?>) styles(mobs).get("venator");
		assertEquals(4.0, ((Number) venator.get("tabDps")).doubleValue(), 1e-9, "no second target, no bounce");
		List<Object> plain = new ArrayList<>();
		Map<String, Object> mob = new LinkedHashMap<>();
		mob.put("styles", new LinkedHashMap<>(Map.of("melee", tool("", 9.0, 40))));
		plain.add(mob);
		CommandEngine.multiView(plain, 3);
		assertEquals(List.of("melee"), new ArrayList<>(styles(plain).keySet()));
	}
}
