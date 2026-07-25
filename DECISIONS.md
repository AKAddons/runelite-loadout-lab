# Decisions — Loadout Lab

Architecture decisions are recorded as [MADR](https://adr.github.io/madr/)
files in **[`docs/decisions/`](docs/decisions/)** — one file per decision.

Start from [`0000-adr-template.md`](docs/decisions/0000-adr-template.md).
Everything except Context, Considered Options, and Decision Outcome is
optional. Numbers are identifiers, not chronology — `date:` in the frontmatter
carries the real ordering.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](docs/decisions/0001-follow-the-madr-convention.md) | This repo records architecture decisions as MADR files | accepted |
| [0002](docs/decisions/0002-fork-the-best-dps-engine-and-vendor-wiki-data.md) | Fork best-dps's BSD engine and vendor wiki monster data; all computation local | accepted |
| [0003](docs/decisions/0003-results-area-is-a-multi-mob-canvas.md) | The results area is a multi-mob canvas: a page of result cards, each holding 1..N mobs | accepted |
| [0004](docs/decisions/0004-style-tabs-and-per-result-parameters.md) | Style cards become a tab strip; search parameters move into each result | accepted |
| [0005](docs/decisions/0005-m4-group-answers-are-kit-based.md) | M-4 group answers: kit-based sets under a swap budget, greedy-merged, with BiS under the same budget | accepted |
| [0006](docs/decisions/0006-optimization-request-copies-by-clone.md) | OptimizationRequest copies by Object.clone with non-final fields | accepted |
| [0007](docs/decisions/0007-pareto-frontier-dp-optimizer-with-war-semantics.md) | Replace the beam-search core with a Pareto-frontier DP carrying WAR semantics | accepted |

The pre-MADR log was migrated by decision, not by entry: same-day
addendum/clarification/correction chains were consolidated into single ADRs
whose rejected intermediate designs appear as considered options.

Numbering restarts per repo. Decisions in other repos are cited in full —
`runelite-goal-planner ADR-0001` — never as a bare number, since a bare number
means something different in each repo.
