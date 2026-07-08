package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility items a fight REQUIRES that pure dps ranking would never
 * pick: Zulrah's snakelings demand a recoil source (ring of recoil,
 * a recoil-charged Ring of suffering, or Echo boots). The optimizer
 * satisfies the requirement with the least-dps-loss option after the
 * main search; owning the UNCHARGED suffering counts as access to the
 * charged form (recoil rings are pocket change).
 */
public final class RequiredUtility
{
	private static final int RING_OF_RECOIL = 2550;
	private static final int SUFFERING_RECOIL = 20655;
	private static final int SUFFERING_UNCHARGED = 19550;
	private static final int SUFFERING_I_RECOIL = 20657;
	private static final int SUFFERING_I_UNCHARGED = 19710;
	private static final int ECHO_BOOTS = 28945;

	/** Satisfying item id -> the owned id that grants access to it. */
	private static final Map<Integer, Integer> RECOIL_ACCESS = new LinkedHashMap<>();

	static
	{
		RECOIL_ACCESS.put(SUFFERING_I_RECOIL, SUFFERING_I_UNCHARGED);
		RECOIL_ACCESS.put(SUFFERING_RECOIL, SUFFERING_UNCHARGED);
		RECOIL_ACCESS.put(ECHO_BOOTS, ECHO_BOOTS);
		RECOIL_ACCESS.put(RING_OF_RECOIL, RING_OF_RECOIL);
	}

	private RequiredUtility()
	{
	}

	public static boolean requiresRecoil(MonsterStats monster)
	{
		return monster != null && "zulrah".equalsIgnoreCase(monster.getName());
	}

	public static boolean hasRecoil(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && RECOIL_ACCESS.containsKey(item.getId()))
			{
				return true;
			}
		}
		return false;
	}

	/** Recoil items this request may equip, best-first preference. */
	public static List<GearItem> recoilCandidates(LoadoutData data, OptimizationRequest request)
	{
		List<GearItem> result = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : RECOIL_ACCESS.entrySet())
		{
			GearItem item = data.getGear(entry.getKey());
			if (item == null || request.isExcluded(item.getId())
				|| !request.getRequirementProfile().canEquip(item.getRequirements()))
			{
				continue;
			}
			boolean accessible = request.getCandidateMode() == CandidateMode.ALL_STANDARD
				|| request.getOwnedItems().owns(entry.getKey())
				|| request.getOwnedItems().owns(entry.getValue())
				|| request.isDream(entry.getKey());
			if (accessible)
			{
				result.add(item);
			}
		}
		return result;
	}
}
