package com.loadoutlab.collection;

/** A per-character store: re-keyed when the logged-in character changes. */
public interface ScopedStore
{
	void loadScope(String scope);

	/** Re-read from config - the active RuneLite profile may have changed. */
	default void reload()
	{
	}
}
