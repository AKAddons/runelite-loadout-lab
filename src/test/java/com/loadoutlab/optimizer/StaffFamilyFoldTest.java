package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A weapon and its twin are one weapon to the strip. The battlestaffs
 * (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "just consider that
 * as the same weapon and if the user has the mystic version use that and if
 * they don't sim the other but don't waste both slots") and the tridents
 * (ISS-01M5PTHS4MAVKFB8C1QXF0KTS1: "combine trident of the seas/swamp and
 * their (e) version - those have the same damage just hold a different
 * number of charges"): the worn one stands, else the owned twin, else the
 * base - the twin's sim rung never sits beside the base.
 */
class StaffFamilyFoldTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Gear item(String name) {
		return data.getGearItems(GearSlot.WEAPON).stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static OptimizerService.Rung rung(String name, boolean owned) {
		return new OptimizerService.Rung(item(name), 5.0, 0, owned, null);
	}

	private static List<String> fold(Gear worn, OptimizerService.Rung... rungs) {
		return OptimizerService.foldStaffFamilies(List.of(rungs), worn).stream()
			.map(r -> r.item.getNameLower()).collect(Collectors.toList());
	}

	@Test
	@DisplayName("an owned battlestaff beside an unowned mystic: the battlestaff stands; owned mystic beside an owned battlestaff: the mystic; a lone staff stays")
	void theOwnedTwinStands() {
		assertEquals(List.of("smoke battlestaff", "lava battlestaff", "purging staff"),
			fold(null, rung("smoke battlestaff", true), rung("mystic smoke staff", false), rung("lava battlestaff", true), rung("purging staff", true)));
		assertEquals(List.of("mystic smoke staff", "purging staff"),
			fold(null, rung("smoke battlestaff", true), rung("mystic smoke staff", true), rung("purging staff", true)));
		assertEquals(List.of("mystic fire staff"), fold(null, rung("mystic fire staff", false)));
	}

	@Test
	@DisplayName("the worn weapon stands for its family: a worn battlestaff shows no unowned mystic rung but keeps an owned one; a worn mystic shows no battlestaff rung")
	void theWornOneStands() {
		assertEquals(List.of("purging staff"),
			fold(item("smoke battlestaff"), rung("mystic smoke staff", false), rung("purging staff", true)));
		assertEquals(List.of("mystic smoke staff"),
			fold(item("smoke battlestaff"), rung("mystic smoke staff", true)));
		assertEquals(List.of(), fold(item("mystic smoke staff"), rung("smoke battlestaff", true)));
	}

	@Test
	@DisplayName("the tridents fold the same way: the (e) stands when owned, the plain trident otherwise, for the seas and the swamp")
	void tridentsFold() {
		assertEquals(List.of("trident of the seas", "trident of the swamp (e)"),
			fold(null, rung("trident of the seas", true), rung("trident of the seas (e)", false),
				rung("trident of the swamp", true), rung("trident of the swamp (e)", true)));
		assertEquals(List.of("trident of the seas (e)"),
			fold(item("trident of the swamp"), rung("trident of the seas (e)", true), rung("trident of the swamp (e)", false)));
	}
}
