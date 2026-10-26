package com.loadoutlab.collection;

/** A per-character store: re-keyed when the logged-in character changes. */
public interface ScopedStore {
    void loadScope(String scope);

    /** Re-read from config - the active RuneLite profile may have changed. */
    default void reload() {
    }

    /** The reset-customisations control: a store that holds customisations
     * wipes this scope's; bank memory (the ledger) is no customisation and stays. */
    default void clear() {
    }
}
