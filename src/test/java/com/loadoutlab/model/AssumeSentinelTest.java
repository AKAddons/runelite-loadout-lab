package com.loadoutlab.model;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-31 (Not on Hand): "If i click and remove the prayer
 * shown, when I try to add it back there is no longer an option to do so."
 *
 * <p>The assume icons ARE the pickers. An empty assume label used to return
 * a null node, which erased both icons - picking None removed the only
 * control that could undo it. The empty state is now a real node with
 * sentinel values (-1), which the render side draws as the prayer-book
 * wings and the muted dash, both still clickable.
 */
class AssumeSentinelTest
{
	@Test
	@DisplayName("an empty assume label yields sentinel pickers, never null")
	void emptyLabelKeepsThePickers()
	{
		for (String label : new String[]{null, ""})
		{
			Map<String, Object> node = RenderModel.assumeNode(label);
			assertNotNull(node, "a null node erases the pickers (label=" + label + ")");
			assertEquals(-1, node.get("prayerSprite"), "sentinel prayer sprite");
			assertEquals(-1, node.get("boostItem"), "sentinel boost item");
			assertEquals("No prayer or boost", node.get("text"));
		}
	}

	@Test
	@DisplayName("real labels still resolve their icons")
	void realLabelsUnchanged()
	{
		Map<String, Object> node = RenderModel.assumeNode("Piety + Super combat");
		assertNotNull(node);
		assertTrue((int) node.get("prayerSprite") > 0, "Piety resolves a sprite");
		assertTrue((int) node.get("boostItem") > 0, "Super combat resolves an item");
	}

	/** Andrew 2026-09-02: the boost potion is a supply to bring - unless
	 * the raid hands it out (CoX overloads, ToA salts). */
	@Test
	@DisplayName("a raid-supplied boost is flagged so the supplies row does not pack it")
	void raidSuppliedBoostsAreFlagged()
	{
		Map<String, Object> potion = RenderModel.assumeNode("Rigour + Divine ranging potion");
		assertEquals(23733, potion.get("boostItem"));
		assertEquals(false, potion.get("boostSupplied"));
		assertEquals(true, RenderModel.assumeNode("Piety + Overload (+)").get("boostSupplied"));
		assertEquals(true, RenderModel.assumeNode("Piety + Smelling salts").get("boostSupplied"));
		assertEquals(false, RenderModel.assumeNode("Augury + Saturated heart").get("boostSupplied"));
	}
}
