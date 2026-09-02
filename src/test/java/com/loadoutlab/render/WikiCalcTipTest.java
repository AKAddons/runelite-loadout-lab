package com.loadoutlab.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew 2026-09-02: the Wiki calc chip lost its "you will see this
 * number in their engine" line on cards without a spec or thrall fold.
 * The ledger is the subtext that says we know what they will show. */
class WikiCalcTipTest
{
	@Test
	@DisplayName("the Wiki calc tooltip always names the set dps the calculator will show")
	void tipAlwaysCarriesTheLedger()
	{
		Map<String, Object> yours = new LinkedHashMap<>();
		yours.put("dps", 6.344);
		Map<String, Object> melee = new LinkedHashMap<>();
		melee.put("yours", yours);
		melee.put("tabDps", 6.344);
		Map<String, Object> styles = new LinkedHashMap<>();
		styles.put("melee", melee);
		Map<String, Object> mob = new LinkedHashMap<>();
		mob.put("styles", styles);
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("lensIndex", 0);
		params.put("selectedTab", "melee");
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("params", params);
		entry.put("mobs", List.of(mob));
		Map<String, Object> page = new LinkedHashMap<>();
		page.put("entries", List.of(entry));
		String tip = RenderSurface.wikiCalcTip(page);
		assertTrue(tip.contains("what the calc shows"), tip);
		assertTrue(tip.contains("6.344"), tip);
		assertFalse(tip.contains("Invocation"), "a land mob has no raid level line");
		// A ToA mob at the card's raid level says so (Andrew 2026-09-02).
		mob.put("invocationScaled", true);
		params.put("toaInvocation", 300);
		String toa = RenderSurface.wikiCalcTip(page);
		assertTrue(toa.contains("opens at Invocation 300"), toa);
	}
}
