package com.loadoutlab.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REQ-SC-9 (Andrew's v2 review): operators are per cannon, at most one
 * cannon is Yours, and picking a material auto-raises a crew operator to
 * the minimum Privateering that can man it - "the system should
 * automatically assume you are going to pick a crew member at minimum that
 * can man the cannon". Explicit higher picks are never lowered.
 */
class ShipOperatorTest
{
	@Test
	@DisplayName("picking a dragon cannon raises its crew to P4 automatically")
	void materialRaisesTheCrew()
	{
		PageState state = new PageState();
		state.setParam("cannon1Operator", "crew1");
		state.setParam("cannon1Material", "dragon");
		assertEquals("crew4", state.paramsNode().get("cannon1Operator"));
		// Stepping DOWN to steel keeps the explicit crew4 - never lowered.
		state.setParam("cannon1Material", "steel");
		assertEquals("crew4", state.paramsNode().get("cannon1Operator"));
	}

	@Test
	@DisplayName("a crew pick below the material's gate is raised on the spot")
	void operatorPickIsGated()
	{
		PageState state = new PageState();
		state.setParam("cannon1Material", "rune");
		state.setParam("cannon1Operator", "crew2");
		assertEquals("crew4", state.paramsNode().get("cannon1Operator"),
			"rune demands P4; a crew2 pick rises to it");
	}

	@Test
	@DisplayName("only one cannon can be Yours - picking it flips the other to crew")
	void oneYouAcrossTheShip()
	{
		PageState state = new PageState();
		state.setParam("cannon1Material", "dragon");
		state.setParam("cannon2Material", "bronze");
		state.setParam("cannon1Operator", "you");
		state.setParam("cannon2Operator", "you");
		assertEquals("you", state.paramsNode().get("cannon2Operator"));
		assertEquals("crew4", state.paramsNode().get("cannon1Operator"),
			"cannon 1 falls back to crew AT ITS GATE (dragon -> P4)");
	}
}
