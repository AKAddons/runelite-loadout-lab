# Loadout Lab

A RuneLite plugin that answers one question: **"what's the best gear I
actually own for THIS fight?"** Pick a monster and Loadout Lab computes your
strongest owned set per combat style - exact DPS included - from live
knowledge of your bank, inventory, and equipment.

> Early development. See [docs/ROADMAP.md](docs/ROADMAP.md) for the plan.

## Develop

```bash
./gradlew run        # dev client with the plugin
./gradlew preSubmit  # test + hub token gate + glyph gate
```

## Attribution

- The DPS engine (`com.loadoutlab.engine`, `com.loadoutlab.data`) derives from
  [guccifurs/best-dps](https://github.com/guccifurs/best-dps) (BSD-2-Clause,
  Copyright (c) 2026, Noid). The original license text is kept at
  [licenses/best-dps-LICENSE](licenses/best-dps-LICENSE).
- The bundled monster/equipment data resources derive from the OSRS Wiki via
  the [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc)
  data pipeline (wiki data, CC BY-NC-SA).
