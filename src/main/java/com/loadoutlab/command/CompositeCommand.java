package com.loadoutlab.command;

import java.util.List;

/**
 * A single Command that wraps an ordered list of sub-commands so they
 * appear as one entry in undo/redo history.
 *
 * <p>Apply runs sub-commands in order; revert runs them in reverse order.
 * If any sub-command fails, the composite returns false but does NOT roll
 * back the partially-applied prefix - the {@link CommandHistory}
 * fail-open contract handles this by dropping the entry from history,
 * leaving the partial state in place.
 *
 * <p>Ported from goal planner's command module - keep in lockstep.
 */
public final class CompositeCommand implements Command
{
	private final List<Command> children;
	private final String description;

	public CompositeCommand(List<Command> children, String description)
	{
		this.children = children;
		this.description = description != null ? description : "Compound action";
	}

	@Override
	public boolean apply()
	{
		boolean any = false;
		for (Command c : children)
		{
			if (c.apply()) any = true;
		}
		return any;
	}

	@Override
	public boolean revert()
	{
		boolean any = false;
		for (int i = children.size() - 1; i >= 0; i--)
		{
			if (children.get(i).revert()) any = true;
		}
		return any;
	}

	@Override
	public String getDescription()
	{
		return description;
	}
}
