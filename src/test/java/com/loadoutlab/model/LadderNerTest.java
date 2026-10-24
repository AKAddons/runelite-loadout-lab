package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A ladder rung opens Not Enough Runes on its item (ISS-01M5GE9EJSX6YWCKMSYEWKZ132). */
class LadderNerTest
{
	private static CommandEngine engine(PageState state)
	{
		return new CommandEngine(new DataService().load(), state,
			(mob, p, onDone) ->
			{
			},
			new CompanionLink());
	}

	@Test
	@DisplayName("ner-open hands the item to Not Enough Runes when it is enabled")
	void opensNer()
	{
		PageState state = new PageState();
		CommandEngine engine = engine(state);
		List<Integer> opened = new ArrayList<>();
		engine.setNerLink(() -> true, opened::add);
		assertTrue(engine.execute("ner-open", Map.of("itemId", 4151)));
		assertEquals(List.of(4151), opened);
		assertEquals(Boolean.TRUE, state.paramsNode().get("nerAvailable"));
	}

	@Test
	@DisplayName("without Not Enough Runes the command refuses and nothing is sent")
	void refusesWithoutNer()
	{
		PageState state = new PageState();
		CommandEngine engine = engine(state);
		List<Integer> opened = new ArrayList<>();
		engine.setNerLink(() -> false, opened::add);
		assertFalse(engine.execute("ner-open", Map.of("itemId", 4151)));
		assertTrue(opened.isEmpty());
		assertEquals(Boolean.FALSE, state.paramsNode().get("nerAvailable"));
	}
}
