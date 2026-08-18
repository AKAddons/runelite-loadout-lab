package com.loadoutlab.render;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Monster/group search for the dropdown - async so both the in-core
 * direct call and the Companion's search/search-results round trip
 * fit the same seam. Results: {label, id?} or {label, group?}. */
public interface Searcher
{
	void search(String query, Consumer<List<Map<String, Object>>> onResults);
}
