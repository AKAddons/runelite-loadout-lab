package com.loadoutlab.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Gear that costs to use: wears down or burns charges per hit/cast
 * (degradable_gear.json; definition Andrew 2026-09-08). */
public final class Degradable
{
	private static final List<String> FAMILIES = new ArrayList<>();
	private static final List<String> EXCEPT = new ArrayList<>();

	static
	{
		JsonObject root = JsonResources.objectOrThrow("degradable_gear.json");
		JsonResources.strings(root, "families", FAMILIES);
		JsonResources.strings(root, "except", EXCEPT);
	}

	public static boolean matches(String nameLower)
	{
		return EXCEPT.stream().noneMatch(nameLower::contains) && FAMILIES.stream().anyMatch(nameLower::contains);
	}

	public static Set<Integer> ids(LoadoutData data)
	{
		Set<Integer> ids = new HashSet<>();
		data.getGearItems().forEach(item ->
		{
			if (matches(item.getNameLower()))
			{
				ids.add(item.getId());
			}
		});
		return ids;
	}
}
