---
status: accepted
date: 2026-08-08
decision-makers: ajkatz
---

# Spec energy is priced at sustained pace, not one kill from a full bar

## Context and Problem Statement

The spec value model priced every kill as if it opened on a full 100%
special bar (`energy = 100 + regen x ttk`). That is the burst limit -
true only when downtime between kills refills the bar for free. Field
report 2026-08-08: the Lightbearer kept losing ring arbitration ("it has
an aversion to recommending the lightbearer") because the free opening
bar swamps the regen difference the ring exists for. Real outings kill
50-200 mobs back-to-back: the bar amortises to nothing and the budget is
regen-bound - doubled regen is doubled spec THROUGHPUT, every kill.

## Considered Options

* Sustained pricing everywhere: `energy = regen x ttk`, fractional specs
  per kill
* A Burst|Sustained chip, defaulting burst (no existing numbers move)
* A kills-per-trip horizon control (`100/N + regen x ttk`)

## Decision Outcome

Chosen option: "sustained everywhere" - user call: "sustained is just
more realistic for like 99% of outings." Fractional uses are the point:
0.5 specs per kill = a spec every other kill, a throughput rate rather
than a count (the old `floor()` was a burst-ism). The horizon control
converges to pure sustained at any realistic N (the bar contributes
under 2 energy per kill at N >= 50), so it would add a knob without
information. A "burst" mode for always-POH-pool play (ornate
rejuvenation pool between kills - every kill genuinely opens at 100%)
is roadmapped rather than shipped.

### Consequences

* All spec values shrink on short fights - honest: nobody DDS-specs
  every trash kill forever - and rankings shift toward regen-aware
  picks; the Lightbearer finally prices its real value.
* Defence-drain fishing scales below one attempt per kill (fractional
  exponent in P(landed), cost = expected specs spent).
* Shared-spec argmax flips at the roster level are absorbed by the
  ranked seat fallback (the strongest option that pays for its carried
  slot wins) rather than dropping the spec.
* DeathChargeTest re-derived; the Death Charge refund term was already
  per-kill and survives unchanged.
