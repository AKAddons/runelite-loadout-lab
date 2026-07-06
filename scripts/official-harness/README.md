# Official calculator verification harness

Compares Loadout Lab's engine against the wiki DPS calculator's own engine
(the dispute resolver). The official calc has no compute API; we run its
open-source engine locally instead.

Setup (once):

    cd ~/Development
    git clone https://github.com/weirdgloop/osrs-dps-calc.git   # GPL-3: stays OUTSIDE this repo
    cd osrs-dps-calc && corepack yarn install
    mkdir -p src/tests/harness
    cp ~/Development/runelite-loadout-lab/scripts/official-harness/LoadoutLabVectors.test.ts src/tests/harness/

Run:

    python3 scripts/verify_official.py

Scenarios live in src/test/java/com/loadoutlab/engine/OfficialVectorExport.java
(SCENARIOS table) - add rows there to test new disputes. Only scenario JSON
crosses the GPL boundary; LoadoutLabVectors.test.ts is our own code (BSD).
