package com.loadoutlab.collection;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;

public class ProtectOnlyStore extends IdSetStore {
	static final String KEY = "protectOnlyItems";

	public ProtectOnlyStore(ConfigManager configManager, Gson gson) {
		super(configManager, gson);
		load();
	}

	String legacyKey() {
		return KEY;
	}
}
