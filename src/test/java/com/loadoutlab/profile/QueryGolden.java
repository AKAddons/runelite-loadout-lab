package com.loadoutlab.profile;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Golden-output capture: runs HeadlessQuery.run for the benchmark matrix
 * (monsters x optimize modes x risk flags), for both the maxed profile and
 * the FixtureBank account, and prints every rendered result with a header.
 * Redirect to a file and diff across changes - the optimizer contract is
 * that this output is byte-identical unless a behavior change is intended.
 *
 *   ./gradlew -q golden > golden.txt
 */
public final class QueryGolden
{
	private static final String[] MONSTERS = {"general graardor", "zulrah", "callisto"};
	private static final String[] MODES = {"max_dps", "balanced"};
	private static final int[] RISKS = {-1, 3};

	private QueryGolden()
	{
	}

	public static void main(String[] args) throws Exception
	{
		LoadoutData data = new DataService().load();
		PlayerProfile fixture = FixtureBank.profile(data);
		Path fixturePath = Files.createTempFile("loadout-lab-fixture", ".json");
		fixturePath.toFile().deleteOnExit();
		Files.writeString(fixturePath, fixture.toJson());

		System.out.println("fixture bank ids: " + FixtureBank.bank(data).keySet());
		for (String profileKind : new String[]{"maxed", "fixture"})
		{
			for (String monster : MONSTERS)
			{
				for (String mode : MODES)
				{
					for (int risk : RISKS)
					{
						List<String> query = new ArrayList<>();
						for (String word : monster.split(" "))
						{
							query.add(word);
						}
						if ("maxed".equals(profileKind))
						{
							query.add("--maxed");
						}
						else
						{
							query.add("--profile");
							query.add(fixturePath.toString());
						}
						query.add("--mode");
						query.add(mode);
						if (risk >= 0)
						{
							query.add("--low-risk");
							query.add(String.valueOf(risk));
						}
						System.out.println("##### " + profileKind + " | " + monster
							+ " | " + mode + " | risk=" + (risk < 0 ? "off" : risk));
						System.out.println(HeadlessQuery.run(query.toArray(new String[0])));
					}
				}
			}
		}
	}
}
