// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;

public final class RangedAmmo
{
	private RangedAmmo()
	{
	}

	public static boolean compatible(GearItem ammo, GearItem weapon)
	{
		if (ammo == null)
		{
			return !weaponUsesProjectileAmmo(weapon);
		}
		if (projectileMatches(ammo, weapon))
		{
			return true;
		}
		return !weaponUsesProjectileAmmo(weapon) && passiveAmmoSlotItem(ammo);
	}

	static boolean strengthApplies(GearItem ammo, GearItem weapon)
	{
		return ammo != null && projectileMatches(ammo, weapon);
	}

	private static boolean weaponUsesProjectileAmmo(GearItem weapon)
	{
		if (weapon == null)
		{
			return false;
		}
		int weaponId = weapon.getId();
		return arrowTierForWeapon(weaponId) > 0
			|| boltTierForWeapon(weaponId) > 0
			|| weaponId == 8880
			|| weaponId == 10156
			|| weaponId == 28869
			|| weaponId == 4734
			|| weaponId == 4934
			|| weaponId == 4935
			|| weaponId == 4936
			|| weaponId == 4937
			|| weaponId == 4938
			|| weaponId == 10146
			|| weaponId == 10147
			|| weaponId == 10148
			|| weaponId == 10149
			|| weaponId == 28834
			|| weaponId == 19478
			|| weaponId == 19481
			|| weaponId == 26712
			|| weaponId == 29000;
	}

	private static boolean projectileMatches(GearItem ammo, GearItem weapon)
	{
		if (ammo == null || weapon == null)
		{
			return false;
		}
		int ammoId = ammo.getId();
		int weaponId = weapon.getId();

		switch (weaponId)
		{
			case 8880:
				return boltTier(ammoId) > 0 && boltTier(ammoId) <= 16
					|| ammoId == 9140
					|| ammoId == 9287
					|| ammoId == 9294
					|| ammoId == 9301
					|| ammoId == 8882;
			case 10156:
				return ammoId == 10158 || ammoId == 10159;
			case 28869:
				return ammoId == 28872 || ammoId == 28878;
			case 4734:
			case 4934:
			case 4935:
			case 4936:
			case 4937:
			case 4938:
				return ammoId == 4740;
			case 10146:
				return ammoId == 10143;
			case 10147:
				return ammoId == 10144;
			case 10148:
				return ammoId == 10145;
			case 10149:
				return ammoId == 10142;
			case 28834:
				return ammoId == 28837;
			case 19478:
			case 19481:
			case 26712:
				return javelinTier(ammoId) > 0;
			case 29000:
				return ammoId == 28991;
			default:
				int arrowTier = arrowTierForWeapon(weaponId);
				if (arrowTier > 0)
				{
					return arrowTier(ammoId) > 0 && arrowTier(ammoId) <= arrowTier;
				}
				int boltTier = boltTierForWeapon(weaponId);
				return boltTier > 0 && boltTier(ammoId) > 0 && boltTier(ammoId) <= boltTier;
		}
	}

	private static int arrowTierForWeapon(int weaponId)
	{
		switch (weaponId)
		{
			case 839:
			case 841:
			case 11708:
			case 23357:
				return 1;
			case 843:
			case 845:
			case 4236:
				return 5;
			case 847:
			case 849:
			case 10280:
				return 20;
			case 851:
			case 853:
				return 30;
			case 6724:
			case 855:
			case 857:
			case 859:
			case 861:
			case 10282:
			case 10284:
			case 12788:
			case 28794:
				return 50;
			case 11235:
			case 12765:
			case 12766:
			case 12767:
			case 12768:
			case 20997:
			case 27610:
			case 27612:
			case 27853:
			case 29591:
			case 29611:
			case 33245:
				return 60;
			default:
				return 0;
		}
	}

	private static int boltTierForWeapon(int weaponId)
	{
		switch (weaponId)
		{
			case 767:
			case 837:
			case 9174:
				return 1;
			case 9176:
				return 16;
			case 9177:
				return 26;
			case 9179:
				return 31;
			case 9181:
				return 36;
			case 9183:
				return 46;
			case 9185:
			case 26486:
				return 61;
			case 11785:
			case 21012:
			case 21902:
			case 25916:
			case 25918:
			case 26374:
			case 28053:
			case 33251:
				return 64;
			default:
				return 0;
		}
	}

	private static int arrowTier(int ammoId)
	{
		switch (ammoId)
		{
			case 78:
			case 598:
			case 882:
			case 883:
			case 884:
			case 885:
			case 2532:
			case 2533:
			case 5616:
			case 5617:
			case 5622:
			case 5623:
			case 22227:
			case 22228:
			case 22229:
			case 22230:
				return 1;
			case 886:
			case 887:
			case 2534:
			case 2535:
			case 5618:
			case 5624:
				return 5;
			case 888:
			case 889:
			case 2536:
			case 2537:
			case 5619:
			case 5625:
				return 20;
			case 890:
			case 891:
			case 2538:
			case 2539:
			case 5620:
			case 5626:
				return 30;
			case 892:
			case 893:
			case 2540:
			case 2541:
			case 5621:
			case 5627:
				return 40;
			case 4160:
			case 21326:
			case 21328:
			case 21330:
			case 21332:
			case 21334:
			case 21336:
				return 50;
			case 11212:
			case 11217:
			case 11222:
			case 11227:
			case 11228:
			case 11229:
				return 60;
			default:
				return 0;
		}
	}

	private static int boltTier(int ammoId)
	{
		switch (ammoId)
		{
			case 877:
			case 878:
			case 879:
			case 6061:
			case 6062:
			case 9236:
				return 1;
			case 9139:
			case 9237:
			case 9286:
			case 9293:
			case 9300:
			case 9335:
				return 16;
			case 880:
			case 9140:
			case 9145:
			case 9238:
			case 9287:
			case 9292:
			case 9294:
			case 9299:
			case 9301:
			case 9306:
				return 26;
			case 9141:
			case 9239:
			case 9288:
			case 9295:
			case 9302:
			case 9336:
				return 31;
			case 9142:
			case 9240:
			case 9241:
			case 9289:
			case 9296:
			case 9303:
			case 9337:
			case 9338:
				return 36;
			case 9143:
			case 9242:
			case 9243:
			case 9290:
			case 9297:
			case 9304:
			case 9339:
			case 9340:
				return 46;
			case 9144:
			case 9244:
			case 9245:
			case 9291:
			case 9298:
			case 9305:
			case 9341:
			case 9342:
			case 11875:
			case 21316:
				return 61;
			case 21905:
			case 21924:
			case 21926:
			case 21928:
			case 21932:
			case 21934:
			case 21936:
			case 21938:
			case 21940:
			case 21942:
			case 21944:
			case 21946:
			case 21948:
			case 21950:
			case 21955:
			case 21957:
			case 21959:
			case 21961:
			case 21963:
			case 21965:
			case 21967:
			case 21969:
			case 21971:
			case 21973:
				return 64;
			default:
				return 0;
		}
	}

	private static int javelinTier(int ammoId)
	{
		switch (ammoId)
		{
			case 825:
			case 826:
			case 827:
			case 828:
			case 829:
			case 830:
			case 831:
			case 832:
			case 833:
			case 834:
			case 835:
			case 836:
			case 5642:
			case 5643:
			case 5644:
			case 5645:
			case 5646:
			case 5647:
			case 5648:
			case 5649:
			case 5650:
			case 5651:
			case 5652:
			case 5653:
			case 19484:
			case 19486:
			case 19488:
			case 19490:
			case 21318:
			case 21320:
			case 21322:
			case 21324:
				return 1;
			default:
				return 0;
		}
	}

	private static boolean passiveAmmoSlotItem(GearItem ammo)
	{
		return ammo.getBonuses().getRangedStrength() <= 0 && ammo.getOffensive().getRanged() <= 0;
	}
}
