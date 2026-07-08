package com.loadoutlab.profile;

import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

public class PlayerProfileTest
{
	@Test
	public void aProfileSurvivesTheJsonRoundTripExactly()
	{
		PlayerProfile original = new PlayerProfile(
			new PlayerLevels(75, 80, 70, 85, 77, 52, 82),
			new PlayerLevels(80, 85, 70, 89, 77, 52, 82),
			new PrayerUnlocks(true, false, true, false, false),
			new RequirementProfile(Map.of(Skill.ATTACK, 75, Skill.SLAYER, 60),
				Set.of("Dragon Slayer I", "Monkey Madness I")),
			Map.of(4151, 1, 11832, 1, 12934, 5000),
			true);
		PlayerProfile copy = PlayerProfile.fromJson(original.toJson());
		Assert.assertEquals(75, copy.realLevels.getAttack());
		Assert.assertEquals(89, copy.boostedLevels.getRanged());
		Assert.assertTrue(copy.prayerUnlocks.piety());
		Assert.assertFalse(copy.prayerUnlocks.rigour());
		Assert.assertTrue(copy.prayerUnlocks.augury());
		Assert.assertEquals(Integer.valueOf(60), copy.requirements.getLevels().get(Skill.SLAYER));
		Assert.assertTrue(copy.requirements.getCompletedQuests().contains("Monkey Madness I"));
		Assert.assertEquals(Integer.valueOf(5000), copy.owned.get(12934));
		Assert.assertTrue(copy.bankScanned);
	}

	@Test
	public void theHeadlessRunnerAnswersAQueryWithoutAClient() throws Exception
	{
		String out = HeadlessQuery.run(new String[]{"vorkath", "--maxed"});
		Assert.assertTrue(out, out.contains("vs Vorkath"));
		Assert.assertTrue(out, out.contains("game best:"));
		Assert.assertTrue(out, out.contains("[Ranged]"));
	}

	@Test
	public void aMockUserWithABankDrivesOwnedResults() throws Exception
	{
		PlayerProfile mock = new PlayerProfile(PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED,
			Map.of(4151, 1, 1712, 1), true);
		java.nio.file.Path file = java.nio.file.Files.createTempFile("profile", ".json");
		java.nio.file.Files.writeString(file, mock.toJson());
		String out = HeadlessQuery.run(new String[]{"goblin", "--profile", file.toString()});
		Assert.assertTrue(out, out.contains("Abyssal whip"));
		Assert.assertTrue(out, out.contains("Amulet of glory"));
	}
}
