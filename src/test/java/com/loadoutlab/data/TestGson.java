package com.loadoutlab.data;

import com.google.gson.Gson;

/** The test tree's Gson (ServiceLoader, META-INF/services): in the client the
 * plugin hands its injected Gson to JsonResources before any data loads. */
public class TestGson implements JsonResources.GsonSource {
	@Override
	public Gson gson() { return new Gson(); }
}
