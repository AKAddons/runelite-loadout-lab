package com.loadoutlab.command;

import com.loadoutlab.collection.DreamStore;
import com.loadoutlab.collection.ExclusionStore;
import com.loadoutlab.collection.ManualOwnedStore;
import com.loadoutlab.collection.MonsterProfileStore;
import com.loadoutlab.collection.ProtectOnlyStore;
import com.loadoutlab.data.GearSlot;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Factories building a reversible {@link Command} for every deliberate user
 * mutation of Loadout Lab's persistent stores. Each command captures the
 * before-state it needs to revert AT CONSTRUCTION (per the Command
 * contract), so build the command at the moment of the user's action and
 * hand it straight to {@link CommandHistory#execute}.
 *
 * <p>Toggles use set-to-target semantics rather than blind re-toggling:
 * apply drives the flag to the captured target, revert drives it back -
 * idempotent even if something else moved the flag in between.
 *
 * <p>Item labels are passed in by the caller (the plugin adapters resolve
 * them from the gear corpus) so this layer stays free of data-service
 * dependencies and trivially testable.
 */
public final class Commands
{
	private Commands()
	{
	}

	/** A Command from two lambdas plus a label - the shape every factory returns. */
	static Command of(String description, BooleanSupplier apply, BooleanSupplier revert)
	{
		return new Command()
		{
			@Override public boolean apply() { return apply.getAsBoolean(); }
			@Override public boolean revert() { return revert.getAsBoolean(); }
			@Override public String getDescription() { return description; }
		};
	}

	// ---- global item-flag toggles ------------------------------------

	public static Command toggleExclusion(ExclusionStore store, int itemId, String label)
	{
		return toggleFlag(store::isExcluded, store::toggle, itemId, "Exclude ", "Include ", label);
	}

	public static Command toggleDream(DreamStore store, int itemId, String label)
	{
		return toggleFlag(store::isDreamed, store::toggle, itemId, "Sim item ", "Stop simming ", label);
	}

	public static Command toggleStored(ManualOwnedStore store, int itemId, String label)
	{
		return toggleFlag(store::isStored, store::toggle, itemId,
			"Mark stored elsewhere: ", "Unmark stored elsewhere: ", label);
	}

	public static Command toggleProtectOnly(ProtectOnlyStore store, int itemId, String label)
	{
		return toggleFlag(store::isProtectOnly, store::toggle, itemId,
			"Only bring protected: ", "Bring even unprotected: ", label);
	}

	/**
	 * Shared body for the four global item-flag toggles. The stores each name
	 * their state predicate differently (isExcluded/isDreamed/...), so the
	 * caller hands it in as a method reference alongside the store's flip
	 * method; the public factories keep their concrete store types so call
	 * sites are unaffected. {@code toggle} returns a boolean that is
	 * deliberately discarded - set-to-target semantics only care about the
	 * resulting state.
	 */
	private static Command toggleFlag(IntPredicate isOn, IntConsumer toggle, int itemId,
		String onPrefix, String offPrefix, String label)
	{
		boolean turnOn = !isOn.test(itemId);
		return of((turnOn ? onPrefix : offPrefix) + label,
			() -> setFlag(isOn, toggle, itemId, turnOn),
			() -> setFlag(isOn, toggle, itemId, !turnOn));
	}

	/** Drives the flag to {@code want}, flipping only when it is not already there. */
	static boolean setFlag(IntPredicate isOn, IntConsumer toggle, int itemId, boolean want)
	{
		if (isOn.test(itemId) != want)
		{
			toggle.accept(itemId);
		}
		return true;
	}

	// ---- per-monster profile mutations --------------------------------

	public static Command pin(MonsterProfileStore store, int monsterId, String scope,
		GearSlot slot, int itemId, String label)
	{
		Integer prior = priorPin(store, monsterId, scope, slot);
		return of("Pin " + label,
			() -> { store.pin(monsterId, scope, slot, itemId); return true; },
			() ->
			{
				if (prior == null)
				{
					store.unpin(monsterId, scope, slot);
				}
				else
				{
					store.pin(monsterId, scope, slot, prior);
				}
				return true;
			});
	}

	public static Command unpin(MonsterProfileStore store, int monsterId, String scope,
		GearSlot slot, String label)
	{
		Integer prior = priorPin(store, monsterId, scope, slot);
		return of("Unpin " + label,
			() ->
			{
				if (prior == null)
				{
					return false; // nothing pinned - no-op
				}
				store.unpin(monsterId, scope, slot);
				return true;
			},
			() ->
			{
				if (prior != null)
				{
					store.pin(monsterId, scope, slot, prior);
				}
				return true;
			});
	}

	private static Integer priorPin(MonsterProfileStore store, int monsterId, String scope, GearSlot slot)
	{
		return store.allPins(monsterId).getOrDefault(scope, Map.of()).get(slot);
	}

	public static Command setNote(MonsterProfileStore store, int monsterId, String note)
	{
		String before = store.noteFor(monsterId);
		String after = note == null ? "" : note;
		return of("Edit note",
			() ->
			{
				if (after.equals(before == null ? "" : before))
				{
					return false; // unchanged text - no-op
				}
				store.setNote(monsterId, after);
				return true;
			},
			() -> { store.setNote(monsterId, before); return true; });
	}

	public static Command setPinnedSpell(MonsterProfileStore store, int monsterId, String spellName)
	{
		String before = store.pinnedSpellFor(monsterId);
		String after = spellName == null ? "" : spellName;
		return of(after.isEmpty() ? "Clear pinned spell" : "Pin spell " + after,
			() ->
			{
				if (after.equals(before == null ? "" : before))
				{
					return false;
				}
				store.setPinnedSpell(monsterId, after);
				return true;
			},
			() -> { store.setPinnedSpell(monsterId, before); return true; });
	}

	public static Command excludeForMob(MonsterProfileStore store, int monsterId, String scope,
		int itemId, String label)
	{
		return of("Exclude here: " + label,
			() -> { store.exclude(monsterId, scope, itemId); return true; },
			() -> { store.removeExclusion(monsterId, scope, itemId); return true; });
	}

	public static Command removeMobExclusion(MonsterProfileStore store, int monsterId, String scope,
		int itemId, String label)
	{
		return of("Allow here: " + label,
			() -> { store.removeExclusion(monsterId, scope, itemId); return true; },
			() -> { store.exclude(monsterId, scope, itemId); return true; });
	}

	public static Command simForMob(MonsterProfileStore store, int monsterId,
		int itemId, String label)
	{
		return of("Sim here: " + label,
			() -> { store.addSim(monsterId, itemId, label); return true; },
			() -> { store.removeSim(monsterId, itemId); return true; });
	}

	public static Command removeMobSim(MonsterProfileStore store, int monsterId,
		int itemId, String label)
	{
		return of("Unsim here: " + label,
			() -> { store.removeSim(monsterId, itemId); return true; },
			() -> { store.addSim(monsterId, itemId, label); return true; });
	}

	public static Command addFilterItem(MonsterProfileStore store, int monsterId, String scope,
		int itemId, String name)
	{
		return of("Add trip supply " + name,
			() -> { store.addFilterItem(monsterId, scope, itemId, name); return true; },
			() -> { store.removeFilterItem(monsterId, scope, itemId); return true; });
	}

	public static Command removeFilterItem(MonsterProfileStore store, int monsterId, String scope,
		int itemId)
	{
		// Capture the persisted display name so undo can restore it intact.
		String name = store.allFilterItems(monsterId)
			.getOrDefault(scope, Map.of())
			.getOrDefault(itemId, "item " + itemId);
		return of("Remove trip supply " + name,
			() -> { store.removeFilterItem(monsterId, scope, itemId); return true; },
			() -> { store.addFilterItem(monsterId, scope, itemId, name); return true; });
	}
}
