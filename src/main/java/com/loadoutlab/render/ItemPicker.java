package com.loadoutlab.render;

import java.util.function.BiConsumer;

/** Native chatbox item search, supplied by the plugin (client access
 * stays core-side; renderers only ask and receive the pick). In a
 * future split this becomes the item-search/item-picked message
 * round trip from the contract. */
public interface ItemPicker
{
	void search(String prompt, BiConsumer<Integer, String> onPicked);
}
