package com.loadoutlab.command;

import com.loadoutlab.collection.ExclusionStore;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Command factories that only the test suite ever builds. They live here -
 * in the matching package, so they can reach Commands' package-private
 * {@code of}/{@code setFlag} helpers - to keep main source out of the
 * Plugin Hub bot's 200k token budget. Behaviour is byte-for-byte the
 * production version that used to sit in {@link Commands}.
 */
final class TestCommands
{
	private TestCommands()
	{
	}

	/** Wipe the global exclusion list as ONE undo entry; undo restores every id. */
	static Command clearExclusions(ExclusionStore store)
	{
		Set<Integer> before = new LinkedHashSet<>(store.snapshot());
		return Commands.of("Clear exclusions (" + before.size() + ")",
			() ->
			{
				if (store.snapshot().isEmpty())
				{
					return false; // nothing to clear - stay off the stack
				}
				store.clear();
				return true;
			},
			() ->
			{
				for (int id : before)
				{
					Commands.setFlag(store::isExcluded, store::toggle, id, true);
				}
				return true;
			});
	}
}
