package com.loadoutlab.command;

import java.util.*;
import lombok.extern.slf4j.Slf4j;

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
public class CommandHistory {
    public static final int CAP = 50;

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    /**
     * Run a command and push it onto the undo stack on success.
     * Forking behavior: any pending redo entries are cleared.
     *
     * @return whatever {@link Command#apply()} returned
     */
    public boolean execute(Command cmd) {
        if (!attempt(cmd, true, "apply")) return false;
        undoStack.push(cmd);
        redoStack.clear();
        while (undoStack.size() > CAP) undoStack.pollLast();
        return true;
    }

    /**
     * Pop the most recent undo entry and revert it. On success the entry
     * moves to the redo stack. On failure the entry is dropped - fail-open.
     *
     * @return true if an entry was reverted, false if the stack was empty
     *         or the revert failed
     */
    public boolean undo() { return move(undoStack, redoStack, false, "revert"); }

    /**
     * Pop the most recent redo entry and re-apply it. On success the entry
     * moves back to the undo stack. On failure the entry is dropped.
     */
    public boolean redo() { return move(redoStack, undoStack, true, "re-apply"); }

    /** Pop from one stack, run the command, push onto the other; a refusal
     * or a throw drops the entry (fail-open). */
    private static boolean move(Deque<Command> from, Deque<Command> to, boolean apply, String verb) {
        if (from.isEmpty()) return false;
        Command cmd = from.pop();
        if (!attempt(cmd, apply, verb)) return false;
        to.push(cmd);
        return true;
    }

    /** Runs apply or revert; false - and a warning - when it refused or threw. */
    private static boolean attempt(Command cmd, boolean apply, String verb) {
        try {
            if (apply ? cmd.apply() : cmd.revert()) return true;
            log.warn("Command {} returned false, dropping: {}", verb, cmd.getDescription());
        } catch (Exception ex) {
            log.warn("Command {} threw, dropping: {}", verb, cmd.getDescription(), ex);
        }
        return false;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    /** Description of the next undo target, or null if the stack is empty. */
    public String peekUndoDescription() { return undoStack.isEmpty() ? null : undoStack.peek().getDescription(); }

    /** Description of the next redo target, or null if the stack is empty. */
    public String peekRedoDescription() { return redoStack.isEmpty() ? null : redoStack.peek().getDescription(); }

    /** Wipe both stacks. */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
