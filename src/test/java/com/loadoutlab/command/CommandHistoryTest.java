package com.loadoutlab.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CommandHistory}.
 *
 * <p>Uses a tiny stub Command that pushes/pops to a shared list so we can
 * verify exactly which commands ran (and in which direction) without
 * touching the real stores.
 */
class CommandHistoryTest {
	private CommandHistory history;
	/** Trace of "apply:N" / "revert:N" calls in the order they happened. */
	private List<String> trace;

	@BeforeEach
	void setUp() {
		history = new CommandHistory();
		trace = new ArrayList<>();
	}

	private Command stubCmd(int id) {
		return new Command() {
			@Override public boolean apply() { trace.add("apply:" + id); return true; }
			@Override public boolean revert() { trace.add("revert:" + id); return true; }
			@Override public String getDescription() { return "cmd-" + id; }
		};
	}

	private Command failingApply(int id) {
		return new Command() {
			@Override public boolean apply() { trace.add("apply:" + id); return false; }
			@Override public boolean revert() { trace.add("revert:" + id); return true; }
			@Override public String getDescription() { return "cmd-" + id; }
		};
	}

	private Command failingRevert(int id) {
		return new Command() {
			@Override public boolean apply() { trace.add("apply:" + id); return true; }
			@Override public boolean revert() { trace.add("revert:" + id); return false; }
			@Override public String getDescription() { return "cmd-" + id; }
		};
	}

	private Command throwingRevert(int id) {
		return new Command() {
			@Override public boolean apply() { trace.add("apply:" + id); return true; }
			@Override public boolean revert() { throw new RuntimeException("boom"); }
			@Override public String getDescription() { return "cmd-" + id; }
		};
	}

	@Test
	@DisplayName("execute runs apply and pushes onto undo stack")
	void executePushes() {
		assertTrue(history.execute(stubCmd(1)));
		assertEquals(List.of("apply:1"), trace);
		assertTrue(history.canUndo());
		assertFalse(history.canRedo());
		assertEquals("cmd-1", history.peekUndoDescription());
	}


	@Test
	@DisplayName("undo pops the most recent entry and reverts it, moving to redo stack")
	void undoMovesToRedo() {
		history.execute(stubCmd(1));
		history.execute(stubCmd(2));
		assertTrue(history.undo());
		assertEquals(List.of("apply:1", "apply:2", "revert:2"), trace);
		assertTrue(history.canUndo());
		assertTrue(history.canRedo());
		assertEquals("cmd-1", history.peekUndoDescription());
		assertEquals("cmd-2", history.peekRedoDescription());
	}

	@Test
	@DisplayName("redo re-applies the entry and moves it back to undo stack")
	void redoMovesBack() {
		history.execute(stubCmd(1));
		history.undo();
		assertTrue(history.redo());
		assertEquals(List.of("apply:1", "revert:1", "apply:1"), trace);
		assertTrue(history.canUndo());
		assertFalse(history.canRedo());
	}

	@Test
	@DisplayName("a new execute clears the redo stack (forking history)")
	void newExecuteClearsRedo() {
		history.execute(stubCmd(1));
		history.execute(stubCmd(2));
		history.undo(); // 2 → redo
		assertTrue(history.canRedo());
		history.execute(stubCmd(3)); // forks: redo cleared
		assertFalse(history.canRedo());
	}

	@Test
	@DisplayName("undo on empty stack returns false")
	void emptyUndo() {
		assertFalse(history.undo());
	}

	@Test
	@DisplayName("redo on empty stack returns false")
	void emptyRedo() {
		assertFalse(history.redo());
	}

	@Test
	@DisplayName("execute returning false (no-op) is not pushed onto the stack")
	void failingApplyNotPushed() {
		assertFalse(history.execute(failingApply(1)));
		assertFalse(history.canUndo());
	}

	@Test
	@DisplayName("undo with failing revert drops the entry and returns false")
	void failingRevertDropped() {
		history.execute(stubCmd(1));
		history.execute(failingRevert(2));
		assertFalse(history.undo()); // revert returned false
		// Entry 2 dropped — cmd-1 is next to undo, nothing to redo
		assertEquals("cmd-1", history.peekUndoDescription());
		assertFalse(history.canRedo());
	}

	@Test
	@DisplayName("undo with throwing revert drops the entry and returns false")
	void throwingRevertDropped() {
		history.execute(stubCmd(1));
		history.execute(throwingRevert(2));
		assertFalse(history.undo());
		assertEquals("cmd-1", history.peekUndoDescription());
		assertFalse(history.canRedo());
	}

	@Test
	@DisplayName("undo stack is bounded at CAP entries — oldest are trimmed")
	void capTrim() {
		for (int i = 0; i < CommandHistory.CAP + 5; i++) {
			history.execute(stubCmd(i));
		}
		int undone = 0;
		while (history.undo()) {
			undone++;
		}
		assertEquals(CommandHistory.CAP, undone);
	}

	@Test
	@DisplayName("clear wipes both stacks")
	void clearWipes() {
		history.execute(stubCmd(1));
		history.execute(stubCmd(2));
		history.undo();
		history.clear();
		assertFalse(history.canUndo());
		assertFalse(history.canRedo());
	}

	@Test
	@DisplayName("multi-step undo/redo round-trip preserves order")
	void roundTrip() {
		history.execute(stubCmd(1));
		history.execute(stubCmd(2));
		history.execute(stubCmd(3));
		history.undo();
		history.undo();
		history.redo();
		// Sequence: apply 1,2,3 → revert 3, revert 2 → apply 2
		assertEquals(List.of("apply:1", "apply:2", "apply:3",
			"revert:3", "revert:2", "apply:2"), trace);
		assertTrue(history.canUndo()); // 1, 2 remain
		assertTrue(history.canRedo()); // 3 in redo
	}
}
