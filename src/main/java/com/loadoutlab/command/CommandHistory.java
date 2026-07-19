package com.loadoutlab.command;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.ArrayList;

/**
 * In-memory undo / redo stack for {@link Command} entries.
 *
 * <p>Standard semantics:
 * <ul>
 *   <li>{@link #execute(Command)} runs the command and pushes onto the
 *       undo stack. Any pending redo entries are cleared (forking history).
 *   <li>{@link #undo()} pops the most recent undo entry, calls
 *       {@code revert()}, and pushes onto the redo stack. If revert returns
 *       false the entry is dropped without being pushed onto redo.
 *   <li>{@link #redo()} pops the most recent redo entry, calls
 *       {@code apply()}, and pushes back onto the undo stack. If apply
 *       returns false the entry is dropped.
 * </ul>
 *
 * <p>The undo stack is bounded - once it exceeds {@link #CAP} entries, the
 * oldest entries are dropped (the redo stack is implicitly bounded by what
 * the user has undone). Both stacks are session-only; they are never
 * persisted. Cleared wholesale on config-profile change, since entries
 * captured against another profile's stores would corrupt this one.
 *
 * <p>Not thread-safe - all calls must come from the EDT (or whichever
 * single thread owns the plugin's panel hooks).
 *
 * <p>Ported from goal planner's command module - keep in lockstep.
 */
@Slf4j
public class CommandHistory
{
	public static final int CAP = 50;

	private final Deque<Command> undoStack = new ArrayDeque<>();
	private final Deque<Command> redoStack = new ArrayDeque<>();

	/** When non-null, execute() appends to this buffer instead of
	 *  pushing to undoStack. endCompound() collapses the buffer into a single
	 *  CompositeCommand and pushes it. */
	private List<Command> compoundBuffer = null;
	private String compoundDescription = null;
	/** Nesting depth counter for begin/endCompound. Without ref-counting,
	 *  an inner endCompound prematurely closes the outer compound. */
	private int compoundDepth = 0;

	/**
	 * Run a command and push it onto the undo stack on success.
	 * Forking behavior: any pending redo entries are cleared.
	 *
	 * @return whatever {@link Command#apply()} returned
	 */
	public boolean execute(Command cmd)
	{
		boolean ok;
		try
		{
			ok = cmd.apply();
		}
		catch (Exception ex)
		{
			log.warn("Command apply threw, dropping: {}", cmd.getDescription(), ex);
			return false;
		}
		if (!ok) return false;
		if (compoundBuffer != null)
		{
			compoundBuffer.add(cmd);
		}
		else
		{
			undoStack.push(cmd);
			redoStack.clear();
			trim();
		}
		return true;
	}

	/**
	 * Begin a compound command. Subsequent {@link #execute} calls collect
	 * into a buffer instead of pushing individually. {@link #endCompound}
	 * collapses the buffer into a single undo entry with the given
	 * description. Nested compounds are ref-counted: only the outermost
	 * end actually closes the buffer.
	 */
	public void beginCompound(String description)
	{
		compoundDepth++;
		if (compoundBuffer != null) return; // already in a compound - just bump the depth
		compoundBuffer = new ArrayList<>();
		compoundDescription = description;
	}

	/** True when a compound is active (between begin/endCompound). */
	public boolean isInCompound()
	{
		return compoundBuffer != null;
	}

	/**
	 * Close the current compound, wrap its commands in a CompositeCommand,
	 * and push it onto the undo stack as a single entry. No-op if no
	 * compound is active or if the buffer is empty.
	 */
	public void endCompound()
	{
		if (compoundDepth > 0) compoundDepth--;
		if (compoundDepth > 0) return; // still inside a nested compound
		if (compoundBuffer == null) return;
		List<Command> buf = compoundBuffer;
		String desc = compoundDescription;
		compoundBuffer = null;
		compoundDescription = null;
		if (buf.isEmpty()) return;
		undoStack.push(new CompositeCommand(buf, desc));
		redoStack.clear();
		trim();
	}

	/**
	 * Pop the most recent undo entry and revert it. On success the entry
	 * moves to the redo stack. On failure the entry is dropped - fail-open.
	 *
	 * @return true if an entry was reverted, false if the stack was empty
	 *         or the revert failed
	 */
	public boolean undo()
	{
		if (undoStack.isEmpty()) return false;
		Command cmd = undoStack.pop();
		boolean ok;
		try
		{
			ok = cmd.revert();
		}
		catch (Exception ex)
		{
			log.warn("Command revert threw, dropping: {}", cmd.getDescription(), ex);
			return false;
		}
		if (!ok)
		{
			log.warn("Command revert returned false, dropping: {}", cmd.getDescription());
			return false;
		}
		redoStack.push(cmd);
		return true;
	}

	/**
	 * Pop the most recent redo entry and re-apply it. On success the entry
	 * moves back to the undo stack. On failure the entry is dropped.
	 */
	public boolean redo()
	{
		if (redoStack.isEmpty()) return false;
		Command cmd = redoStack.pop();
		boolean ok;
		try
		{
			ok = cmd.apply();
		}
		catch (Exception ex)
		{
			log.warn("Command re-apply threw, dropping: {}", cmd.getDescription(), ex);
			return false;
		}
		if (!ok)
		{
			log.warn("Command re-apply returned false, dropping: {}", cmd.getDescription());
			return false;
		}
		undoStack.push(cmd);
		return true;
	}

	public boolean canUndo() { return !undoStack.isEmpty(); }
	public boolean canRedo() { return !redoStack.isEmpty(); }

	/** Description of the next undo target, or null if the stack is empty. */
	public String peekUndoDescription()
	{
		return undoStack.isEmpty() ? null : undoStack.peek().getDescription();
	}

	/** Description of the next redo target, or null if the stack is empty. */
	public String peekRedoDescription()
	{
		return redoStack.isEmpty() ? null : redoStack.peek().getDescription();
	}

	/** Wipe both stacks. */
	public void clear()
	{
		undoStack.clear();
		redoStack.clear();
	}

	private void trim()
	{
		while (undoStack.size() > CAP)
		{
			undoStack.pollLast();
		}
	}

	int undoSize() { return undoStack.size(); }
	int redoSize() { return redoStack.size(); }
}
