package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.model.RenderModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A group ladder rendered for one mob reads that mob's own numbers and
 * names the group figure beside them (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW, Andrew
 * 2026-10-24: "3.97 but in the card it is showing 5.54 which is a confusing
 * mismatch"); the weapon ladder carries every option ranked ("it should
 * evaluate all options and show the result of all based on dps").
 */
class GroupLadderNodeTest {
	@Test
	@DisplayName("the strip for mob 1 shows its own base and step, the group figure rides in groupBase/groupDelta, and the weapon ladder lists every option")
	@SuppressWarnings("unchecked")
	void ownNumbersGroupRanking() {
		Tables data = new DataService().load();
		List<Gear> weapons = data.getGearItems(Slot.WEAPON);
		Gear worn = weapons.get(0);
		Gear step = weapons.get(1);
		Gear far = weapons.get(2);
		Service.Step up = new Service.Step(step, 5.5, 0, true, null);
		up.pm = new double[]{7.0, 5.0};
		Service.Step farRung = new Service.Step(far, 3.0, 0, false, null);
		farRung.pm = new double[]{3.5, 2.5};
		Service.Ladder ladder = new Service.Ladder(Service.OptimizeMode.MAX_DPS, 5.0, 0,
			List.of(up), List.of());
		ladder.group = true;
		ladder.basePerMob = new double[]{6.0, 4.0};
		ladder.all = List.of(up, farRung);

		Map<String, Object> node = RenderModel.ladderNode(ladder, 1);
		assertEquals("4.00 DPS", node.get("base"), "mob 1's own base, not the group's 5.00");
		assertEquals("group 5.00 DPS", node.get("groupBase"));
		Map<String, Object> rung = ((List<Map>) node.get("up")).get(0);
		assertEquals("+1.00 DPS", rung.get("delta"), "mob 1 gains 5.0 - 4.0");
		assertEquals("group +0.50 DPS", rung.get("groupDelta"));
		assertFalse((Boolean) rung.get("same"));
		List<Map> all = (List<Map>) node.get("all");
		assertEquals(2, all.size());
		assertEquals(step.getName(), all.get(0).get("name"));
		assertEquals(5.0, (Double) all.get(0).get("dps"), 1e-9);
		assertEquals("-1.50 DPS", all.get(1).get("delta"), "the far option read for mob 1: 2.5 - 4.0");

		Map<String, Object> solo = RenderModel.ladderNode(ladder, null);
		assertEquals("5.00 DPS", solo.get("base"), "no mob: the group figure as before");
		assertNull(solo.get("groupBase"));
		assertTrue(((List<?>) solo.get("all")).size() == 2);
		assertNull(solo.get("kinds"), "a plain ladder has no kinds");
	}

	@Test
	@DisplayName("the steps past the strip's two each way ride separately as upMore/downMore (ISS-01M5QD7PNMCMNRZD92RPRMHWRP: show more); a ladder without them carries no such keys")
	@SuppressWarnings("unchecked")
	void furtherStepsRideSeparately() {
		Tables data = new DataService().load();
		List<Gear> weapons = data.getGearItems(Slot.WEAPON);
		Service.Step near = new Service.Step(weapons.get(1), 5.2, 0, false, null);
		Service.Step far = new Service.Step(weapons.get(2), 6.0, 0, false, null);
		Service.Step farther = new Service.Step(weapons.get(3), 7.5, 0, true, null);
		Service.Ladder ladder = new Service.Ladder(Service.OptimizeMode.MAX_DPS, 5.0, 0,
			List.of(near), List.of());
		ladder.upMore = List.of(far, farther);

		Map<String, Object> node = RenderModel.ladderNode(ladder, null);
		List<Map> more = (List<Map>) node.get("upMore");
		assertEquals(2, more.size(), "the further steps up, closest first");
		assertEquals("+1.00 DPS", more.get(0).get("delta"), "still read against the worn item");
		assertEquals(weapons.get(3).getName(), more.get(1).get("name"));
		assertTrue((Boolean) more.get(1).get("owned"));
		assertNull(node.get("downMore"), "no further steps down: no key");

		Service.Ladder plain = new Service.Ladder(Service.OptimizeMode.MAX_DPS, 5.0, 0,
			List.of(near), List.of());
		assertNull(RenderModel.ladderNode(plain, null).get("upMore"));
	}

	@Test
	@DisplayName("a magic weapon ladder carries its powered and autocast sub-ladders as kinds, each a full ladder node")
	@SuppressWarnings("unchecked")
	void magicWeaponLadderHasTwoKinds() {
		Tables data = new DataService().load();
		List<Gear> weapons = data.getGearItems(Slot.WEAPON);
		Service.Step staff = new Service.Step(weapons.get(1), 5.5, 0, true, null);
		Service.Step trident = new Service.Step(weapons.get(2), 4.8, 0, true, null);
		Service.Ladder ladder = new Service.Ladder(Service.OptimizeMode.MAX_DPS, 5.0, 0,
			List.of(staff), List.of(trident));
		ladder.autocast = new Service.Ladder(Service.OptimizeMode.MAX_DPS, 5.0, 0, List.of(staff), List.of());
		ladder.powered = new Service.Ladder(Service.OptimizeMode.MAX_DPS, 5.0, 0, List.of(), List.of(trident));

		Map<String, Object> node = RenderModel.ladderNode(ladder, null);
		Map<String, Object> kinds = (Map<String, Object>) node.get("kinds");
		assertNotNull(kinds, "the magic weapon ladder names its kinds");
		Map<String, Object> powered = (Map<String, Object>) kinds.get("powered");
		Map<String, Object> autocast = (Map<String, Object>) kinds.get("autocast");
		assertEquals("5.00 DPS", powered.get("base"));
		assertEquals(1, ((List<?>) powered.get("down")).size(), "the trident sits below the worn staff on the powered row");
		assertEquals(1, ((List<?>) autocast.get("up")).size(), "the staff sits above on the autocast row");
	}
}
