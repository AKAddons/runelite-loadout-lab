package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.MultiTool;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Multi card's rows (Andrew 2026-10-19): with the Multi switch on, a
 * multi-able mob's compute also ranks the three multi tools - the best
 * ancient burst/barrage set, the best chinchompa set, the venator bow
 * set - each a full per-target StyleResult; a mob outside the multi
 * list computes nothing extra, and a tool the player does not own has
 * no Yours side.
 */
class MultiToolsTest
{
	private static final int BLACK_CHIN = 11959;
	private static final int VENATOR = 27610;
	private static final int ADAMANT_ARROW = 890;
	private static final int DRAGON_2H = 7158;
	private static final int DRAGON_CLAWS = 13652;
	private static final int BLOOD_RUNE = 565;
	private static final int DEATH_RUNE = 560;
	private static final int WATER_RUNE = 555;
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static Map<CombatStyle, OptimizerService.StyleResult> compute(String query, Map<Integer, Integer> owned, boolean multiView) throws Exception
	{
		return compute(query, owned, multiView, 3);
	}

	private static Map<CombatStyle, OptimizerService.StyleResult> compute(String query, Map<Integer, Integer> owned, boolean multiView, int stack) throws Exception
	{
		MonsterStats mob = data.searchMonsters(query, 1).get(0);
		OptimizerService service = new OptimizerService(data);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		ComputeParams p = new ComputeParams();
		p.multiView = multiView;
		p.multiStack = stack;
		service.bestPerStyle(mob, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, new OwnedItems(owned, true), 1, p,
			Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
			Collections.<Integer>emptySet(), result ->
			{
				out.set(result);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS), "compute timed out");
		return out.get();
	}

	private static Map<Integer, Integer> kit(boolean chins, boolean venator, boolean runes)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"rune scimitar", "rune full helm", "rune platebody", "rune platelegs",
			"ancient staff", "mystic robe top", "mystic robe bottom", "magic shortbow", "black d'hide body", "black d'hide chaps"})
		{
			data.getGearItems().stream().filter(g -> g.getNameLower().equals(name) && g.isStandardGear())
				.findFirst().ifPresent(g -> owned.put(g.getId(), 1));
		}
		if (chins)
		{
			owned.put(BLACK_CHIN, 5000);
		}
		if (venator)
		{
			// The bow fires arrows (wiki: any arrow up to dragon); without
			// them the owned row is rightly empty.
			owned.put(VENATOR, 1);
			owned.put(ADAMANT_ARROW, 1000);
		}
		if (runes)
		{
			owned.put(BLOOD_RUNE, 1000);
			owned.put(DEATH_RUNE, 1000);
			owned.put(WATER_RUNE, 1000);
		}
		return owned;
	}

	private static Map<String, OptimizerService.StyleResult> tools(Map<CombatStyle, OptimizerService.StyleResult> results)
	{
		for (OptimizerService.StyleResult r : results.values())
		{
			if (r.multiTools != null)
			{
				return r.multiTools;
			}
		}
		return null;
	}

	@Test
	@DisplayName("Graardor with the switch on ranks barrage, chins and the venator bow, each a per-target set")
	void graardorHasThreeTools() throws Exception
	{
		Map<String, OptimizerService.StyleResult> tools = tools(compute("general graardor", kit(true, true, true), true));
		assertNotNull(tools, "the multi tools ride the results");
		assertEquals(3, tools.size());
		OptimizerService.StyleResult barrage = tools.get(MultiTool.BARRAGE.key);
		assertEquals("Ice Barrage", barrage.owned.get(0).getSpellName(), "a maxed mage barrages");
		assertEquals(BLACK_CHIN, tools.get(MultiTool.CHINS.key).owned.get(0).getLoadout().get(GearSlot.WEAPON).getId());
		assertEquals(VENATOR, tools.get(MultiTool.VENATOR.key).owned.get(0).getLoadout().get(GearSlot.WEAPON).getId());
		assertEquals(VENATOR, tools.get(MultiTool.VENATOR.key).overallBest.getLoadout().get(GearSlot.WEAPON).getId(), "the ceiling wears it too");
	}

	@Test
	@DisplayName("a tool you do not own has no Yours side; the ceiling still shows it")
	void unownedToolHasNoYours() throws Exception
	{
		Map<String, OptimizerService.StyleResult> tools = tools(compute("general graardor", kit(true, false, true), true));
		assertNotNull(tools);
		assertTrue(tools.get(MultiTool.VENATOR.key).owned.isEmpty(), "no venator bow, no Yours row");
		assertNotNull(tools.get(MultiTool.VENATOR.key).overallBest);
		assertFalse(tools.get(MultiTool.CHINS.key).owned.isEmpty());
	}

	@Test
	@DisplayName("at a stack of nine the chins row's spec is the d2h over the claws; at one it is not (Andrew 2026-10-19)")
	void multiSpecAtTheStack() throws Exception
	{
		Map<Integer, Integer> owned = kit(true, false, false);
		owned.put(DRAGON_2H, 1);
		owned.put(DRAGON_CLAWS, 1);
		// At three the claws' four hits per 50% still edge three Powerstabs
		// per 60%; at nine the stack is decisive.
		OptimizerService.StyleResult atNine = tools(compute("general graardor", owned, true, 9)).get(MultiTool.CHINS.key);
		assertNotNull(atNine.specWeapon, "a spec rides the chins row");
		assertEquals(DRAGON_2H, atNine.specWeapon.getId(), "Powerstab hits the whole stack");
		OptimizerService.StyleResult atOne = tools(compute("general graardor", owned, true, 1)).get(MultiTool.CHINS.key);
		assertNotEquals(DRAGON_2H, atOne.specWeapon == null ? 0 : atOne.specWeapon.getId(), "one target: the claws win as ever");
	}

	@Test
	@DisplayName("the switch off, or a mob outside the multi list, computes no tools")
	void offOrNotMultiAble() throws Exception
	{
		assertNull(tools(compute("general graardor", kit(true, true, true), false)), "switch off");
		assertNull(tools(compute("vorkath", kit(true, true, true), true)), "Vorkath is single-way");
	}
}
