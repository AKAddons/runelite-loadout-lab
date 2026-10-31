package com.ll.engine;

import lombok.AllArgsConstructor;

// Unlock-gated prayers: Piety/Chivalry need King's Ransom (Knight Waves),
// Rigour/Augury need CoX prayer scrolls, Deadeye/Mystic Vigour need
// Colosseum-era scrolls, Zeal a Fractured Archive reward. Level
// requirements alone are not enough - the plugin reads quest state and
// the unlock varbits at snapshot time.
@AllArgsConstructor
public class Prayers {
// Everything unlocked - tests, harness, and the game-best ceiling.
public static final Prayers ALL = new Prayers(true, true, true, true, true, true);

// The free-to-play prayer book: everything through Mystic Might is
// F2P; the unlock-gated prayers (Piety/Chivalry/Rigour/Deadeye/
// Augury/Mystic Vigour) are exactly the members-only ones, so denying
// them all IS the F2P ceiling (wiki-verified 2026-07-15).
public static final Prayers F2P = new Prayers(false, false, false, false, false, false);

boolean kingsRansom;
boolean rigour;
boolean augury;
boolean deadeye;
boolean mv;
boolean zeal;

// The pre-Zeal form (older profiles, the harness).
public Prayers(boolean kingsRansom, boolean rigour, boolean augury, boolean deadeye, boolean mv) {
    this(kingsRansom, rigour, augury, deadeye, mv, false);
}

// Zeal: the Fractured Archive's Piety upgrade.
boolean zeal() { return zeal; }

public boolean piety() { return kingsRansom; }

boolean chivalry() { return kingsRansom; }

public boolean rigour() { return rigour; }

public boolean augury() { return augury; }

boolean deadeye() { return deadeye; }

boolean mv() { return mv; }

public String key() {
    return "" + (kingsRansom ? 1 : 0) + (rigour ? 1 : 0) + (augury ? 1 : 0)
        + (deadeye ? 1 : 0) + (mv ? 1 : 0) + (zeal ? 1 : 0);
}
}
