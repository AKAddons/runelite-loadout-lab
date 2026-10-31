package com.ll.data;

import com.google.gson.Gson;

/** The test tree's Gson (ServiceLoader, META-INF/services): in the client the
 * plugin hands its injected Gson to Json before any data loads. */
public class TestGson implements Json.GsonSource {
	@Override
	public Gson gson() { return new Gson(); }
}
