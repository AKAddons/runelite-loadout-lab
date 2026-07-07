package com.loadoutlab.engine;

/**
 * Unlock-gated prayers: Piety/Chivalry need King's Ransom (Knight Waves),
 * Rigour/Augury need CoX prayer scrolls, Deadeye/Mystic Vigour need
 * Colosseum-era scrolls. Level requirements alone are not enough - the
 * plugin reads quest state and the unlock varbits at snapshot time.
 */
public final class PrayerUnlocks
{
	/** Everything unlocked - tests, harness, and the game-best ceiling. */
	public static final PrayerUnlocks ALL = new PrayerUnlocks(true, true, true, true, true);

	private final boolean kingsRansom;
	private final boolean rigour;
	private final boolean augury;
	private final boolean deadeye;
	private final boolean mysticVigour;

	public PrayerUnlocks(boolean kingsRansom, boolean rigour, boolean augury,
		boolean deadeye, boolean mysticVigour)
	{
		this.kingsRansom = kingsRansom;
		this.rigour = rigour;
		this.augury = augury;
		this.deadeye = deadeye;
		this.mysticVigour = mysticVigour;
	}

	public boolean piety()
	{
		return kingsRansom;
	}

	public boolean chivalry()
	{
		return kingsRansom;
	}

	public boolean rigour()
	{
		return rigour;
	}

	public boolean augury()
	{
		return augury;
	}

	public boolean deadeye()
	{
		return deadeye;
	}

	public boolean mysticVigour()
	{
		return mysticVigour;
	}

	public String key()
	{
		return "" + (kingsRansom ? 1 : 0) + (rigour ? 1 : 0) + (augury ? 1 : 0)
			+ (deadeye ? 1 : 0) + (mysticVigour ? 1 : 0);
	}
}
