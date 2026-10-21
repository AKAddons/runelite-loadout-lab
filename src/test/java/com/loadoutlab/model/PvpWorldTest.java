package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ragnarrok2023, YouTube 2026-10-20: "3 iteming" on PvP worlds - an iron
 * using blighted supplies with skull prevention on wants the 3 kept
 * items as BiS plus a gp allowance for the rest. The wilderness risk
 * model already does exactly that; a PvP world turns it on for ANY mob
 * without the wilderness weapon buff. High Risk worlds keep nothing
 * (wiki: all items lost, Protect Item unusable).
 */
class PvpWorldTest
{

	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static PageState pvp(int mode)
	{
		PageState state = new PageState();
		state.setParam("pvpWorld", mode);
		state.setParam("riskBudgetGp", 75_000);
		return state;
	}

	@Test
	@DisplayName("a PvP world with a bound cap keeps 3 items, 4 with Protect Item")
	void pvpWorldKeepsThree()
	{
		PageState state = pvp(1);
		assertEquals(3, state.computeParams().maxTradeables);
		state.setParam("protectItem", true);
		assertEquals(4, state.computeParams().maxTradeables);
	}

	@Test
	@DisplayName("a High Risk world keeps nothing, Protect Item or not")
	void highRiskKeepsNothing()
	{
		PageState state = pvp(2);
		assertEquals(0, state.computeParams().maxTradeables);
		state.setParam("protectItem", true);
		assertEquals(0, state.computeParams().maxTradeables);
	}

	@Test
	@DisplayName("a PvP world never switches on the wilderness weapon buff")
	void pvpWorldIsNotTheWilderness()
	{
		assertEquals(false, pvp(1).computeParams().inWilderness);
		assertEquals(false, pvp(2).computeParams().inWilderness);
	}

	@Test
	@DisplayName("the mode clamps to off / PvP / High Risk and is off by default")
	void modeClamps()
	{
		PageState state = new PageState();
		assertEquals(0, state.paramsNode().get("pvpWorld"));
		state.setParam("pvpWorld", 7);
		assertEquals(2, state.paramsNode().get("pvpWorld"));
		state.setParam("pvpWorld", -3);
		assertEquals(0, state.paramsNode().get("pvpWorld"));
		state.setParam("riskBudgetGp", 75_000);
		assertEquals(-1, state.computeParams().maxTradeables, "off means unconstrained off the wilderness");
	}

	@Test
	@DisplayName("the client-side flag is its own param, like f2pWorld")
	void hereFlagIsSeparate()
	{
		PageState state = new PageState();
		assertTrue(state.setParam("pvpWorldHere", true));
		assertEquals(Boolean.TRUE, state.paramsNode().get("pvpWorldHere"));
		assertEquals(0, state.paramsNode().get("pvpWorld"), "being on one does not pick the mode");
	}

	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
		}
	}

	/** Graardor's page under the given mode, with a bank and a casting trip. */
	private static Map<?, ?> graardorEntry(int mode, Set<Integer> bank)
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) ->
			{
			},
			link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(bank::contains);
		engine.setSupplyDefaults(() -> Map.of(
			com.loadoutlab.data.TripSupplies.FOOD, "DETECT_BEST"));
		engine.setMagicLevel(99);
		MonsterStats mob = data.searchMonsters("general graardor", 1).get(0);
		state.setParam("pvpWorld", mode);
		engine.execute("select", Map.of("id", mob.getId()));
		assertEquals(mode, state.paramsNode().get("pvpWorld"), "a selection keeps the world mode");
		state.setParam("riskBudgetGp", 75_000);
		state.setParam("thralls", true);
		engine.onResults(mob, WildernessReportTest.styles(data, mob));
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
		assertNotNull(link.published, "a page published");
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		return Map.of("entry", entry, "report", String.valueOf(link.published.get("reportText")));
	}

	@Test
	@DisplayName("a PvP-world report prices a death on a non-wilderness boss")
	void pvpReportCarriesRisk()
	{
		String report = (String) graardorEntry(1, Set.of()).get("report");
		assertTrue(report.contains("Risk:"), "a PvP-world answer must price a death:\n" + report);
		assertTrue(report.contains("PvP world: yes"), report);
		assertTrue(report.contains("Risk cap:"), report);
		String tame = (String) graardorEntry(0, Set.of()).get("report");
		assertFalse(tame.contains("Risk:"), "off means no risk talk");
		assertTrue(((String) graardorEntry(2, Set.of()).get("report")).contains("PvP world: High Risk"));
	}

	@Test
	@DisplayName("a PvP world offers only a locked pouch and prefers banked blighted food")
	void pvpKitAndSupplies()
	{
		Set<Integer> bank = Set.of(27281, 27509, 13441, 24592);
		Map<?, ?> entry = (Map<?, ?>) graardorEntry(1, bank).get("entry");
		Map<?, ?> mobNode = (Map<?, ?>) ((List<?>) entry.get("mobs")).get(0);
		assertEquals(27509, mobNode.get("castingPouch"), "the unlocked divine pouch is lost on a PvP death");
		assertTrue(supplyNames(entry).contains("Blighted anglerfish"), supplyNames(entry).toString());

		Map<?, ?> land = (Map<?, ?>) graardorEntry(0, bank).get("entry");
		assertFalse(supplyNames(land).contains("Blighted anglerfish"), "blighted food stays off normal worlds");
	}

	private static List<String> supplyNames(Map<?, ?> entry)
	{
		List<String> names = new java.util.ArrayList<>();
		for (Object supply : (List<?>) entry.get("supplies"))
		{
			names.add(String.valueOf(((Map<?, ?>) supply).get("name")));
		}
		return names;
	}
}
