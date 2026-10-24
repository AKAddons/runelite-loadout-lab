package com.loadoutlab.command;

import com.loadoutlab.collection.ExclusionStore;
import com.loadoutlab.collection.MonsterProfileStore;
import com.loadoutlab.data.GearSlot;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.*;

/**
 * Command factories that only the test suite ever builds. They live here -
 * in the matching package, next to {@link Commands#of} - to keep main
 * source out of the Plugin Hub bot's 200k token budget. Behaviour is
 * byte-for-byte the production version that used to sit in
 * {@link Commands}: the plugin's StoreOps built these only to call
 * apply() and discard the revert (the engine's history is the one live
 * stack), so they moved here whole in the 2026-10 slim.
 *
 * <p>Toggles use set-to-target semantics rather than blind re-toggling:
 * apply drives the flag to the captured target, revert drives it back -
 * idempotent even if something else moved the flag in between.
 *
 * <p>Item labels are passed in by the caller (the plugin adapters resolve
 * them from the gear corpus) so this layer stays free of data-service
 * dependencies and trivially testable.
 */
final class TestCommands {
	private TestCommands() {
	}

	/** Wipe the global exclusion list as ONE undo entry; undo restores every id. */
	static Command clearExclusions(ExclusionStore store) {
		Set<Integer> before = new LinkedHashSet<>(store.snapshot());
		return Commands.of("Clear exclusions (" + before.size() + ")",
			() -> {
				if (store.snapshot().isEmpty()) {
					return false; // nothing to clear - stay off the stack
				}
				store.clear();
				return true;
			},
			() -> {
				for (int id : before) {
					setFlag(store::isExcluded, store::toggle, id, true);
				}
				return true;
			});
	}

	// ---- global item-flag toggles ------------------------------------

	static Command toggleExclusion(ExclusionStore store, int itemId, String label) {
		return toggleFlag(store::isExcluded, store::toggle, itemId, "Exclude ", "Include ", label);
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
		String onPrefix, String offPrefix, String label) {
		boolean turnOn = !isOn.test(itemId);
		return Commands.of((turnOn ? onPrefix : offPrefix) + label,
			() -> setFlag(isOn, toggle, itemId, turnOn),
			() -> setFlag(isOn, toggle, itemId, !turnOn));
	}

	/** Drives the flag to {@code want}, flipping only when it is not already there. */
	static boolean setFlag(IntPredicate isOn, IntConsumer toggle, int itemId, boolean want) {
		if (isOn.test(itemId) != want) {
			toggle.accept(itemId);
		}
		return true;
	}

	// ---- per-monster profile mutations --------------------------------

	static Command pin(MonsterProfileStore store, int monsterId, String scope,
		GearSlot slot, int itemId, String label) {
		Integer prior = priorPin(store, monsterId, scope, slot);
		return Commands.of("Pin " + label,
			() -> { store.pin(monsterId, scope, slot, itemId); return true; },
			() -> {
				if (prior == null) {
					store.unpin(monsterId, scope, slot);
				} else {
					store.pin(monsterId, scope, slot, prior);
				}
				return true;
			});
	}

	static Command unpin(MonsterProfileStore store, int monsterId, String scope,
		GearSlot slot, String label) {
		Integer prior = priorPin(store, monsterId, scope, slot);
		return Commands.of("Unpin " + label,
			() -> {
				if (prior == null) {
					return false; // nothing pinned - no-op
				}
				store.unpin(monsterId, scope, slot);
				return true;
			},
			() -> {
				if (prior != null) {
					store.pin(monsterId, scope, slot, prior);
				}
				return true;
			});
	}

	private static Integer priorPin(MonsterProfileStore store, int monsterId, String scope, GearSlot slot) {
		return store.allPins(monsterId).getOrDefault(scope, Map.of()).get(slot);
	}

	static Command setNote(MonsterProfileStore store, int monsterId, String note) {
		String before = store.note(monsterId);
		String after = note == null ? "" : note;
		return Commands.of("Edit note",
			() -> {
				if (after.equals(before == null ? "" : before)) {
					return false; // unchanged text - no-op
				}
				store.setNote(monsterId, after);
				return true;
			},
			() -> { store.setNote(monsterId, before); return true; });
	}

	static Command setPinnedSpell(MonsterProfileStore store, int monsterId, String spellName) {
		String before = store.pinnedSpell(monsterId);
		String after = spellName == null ? "" : spellName;
		return Commands.of(after.isEmpty() ? "Clear pinned spell" : "Pin spell " + after,
			() -> {
				if (after.equals(before == null ? "" : before)) {
					return false;
				}
				store.setPinnedSpell(monsterId, after);
				return true;
			},
			() -> { store.setPinnedSpell(monsterId, before); return true; });
	}

	static Command setPinnedSpec(MonsterProfileStore store, int monsterId,
		int itemId, String label) {
		int before = store.pinnedSpec(monsterId);
		return Commands.of(itemId <= 0 ? "Unpin spec weapon" : "Pin spec " + label,
			() -> {
				if (itemId == before) {
					return false;
				}
				store.setPinnedSpec(monsterId, itemId);
				return true;
			},
			() -> { store.setPinnedSpec(monsterId, before); return true; });
	}

	static Command excludeForMob(MonsterProfileStore store, int monsterId, String scope,
		int itemId, String label) {
		return Commands.of("Exclude here: " + label,
			() -> { store.exclude(monsterId, scope, itemId); return true; },
			() -> { store.removeMobExclusion(monsterId, scope, itemId); return true; });
	}

	static Command removeMobExclusion(MonsterProfileStore store, int monsterId, String scope,
		int itemId, String label) {
		return Commands.of("Allow here: " + label,
			() -> { store.removeMobExclusion(monsterId, scope, itemId); return true; },
			() -> { store.exclude(monsterId, scope, itemId); return true; });
	}

	static Command simForMob(MonsterProfileStore store, int monsterId,
		int itemId, String label) {
		return Commands.of("Sim here: " + label,
			() -> { store.addSim(monsterId, itemId, label); return true; },
			() -> { store.removeMobSim(monsterId, itemId); return true; });
	}

	static Command removeMobSim(MonsterProfileStore store, int monsterId,
		int itemId, String label) {
		return Commands.of("Unsim here: " + label,
			() -> { store.removeMobSim(monsterId, itemId); return true; },
			() -> { store.addSim(monsterId, itemId, label); return true; });
	}

	static Command addFilterItem(MonsterProfileStore store, int monsterId, String scope,
		int itemId, String name) {
		return Commands.of("Add trip supply " + name,
			() -> { store.addFilterItem(monsterId, scope, itemId, name); return true; },
			() -> { store.removeMobFilter(monsterId, scope, itemId); return true; });
	}
}
