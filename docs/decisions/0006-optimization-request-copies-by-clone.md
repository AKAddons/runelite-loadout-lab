---
status: accepted
date: 2026-07-18
decision-makers: ajkatz
---

# OptimizationRequest copies by Object.clone with non-final fields

> Migrated in substance from the pre-MADR `DECISIONS.md` entry "2026-07-18
> (impl): OptimizationRequest copies by clone, fields non-final". See
> [ADR-0001](0001-follow-the-madr-convention.md).

## Context and Problem Statement

The Plugin Hub caps main source at 200k tokens (tiktoken o200k_base,
comments stripped). `OptimizationRequest`'s 14 `with-` helpers each copied
the request through a hand-written mirror class — O(number of fields) of
token cost that grew with every new parameter.

## Considered Options

* Copy via a single `Object.clone()`, dropping `final` from the 22 fields
* Keep the hand-written mirror class
* A builder

## Decision Outcome

Chosen option: "copy via `Object.clone()`" — O(1) in fields instead of
O(n), which is what the token cap rewards.

The cost: dropping `final` forfeits the Java Memory Model's final-field
safe-publication guarantee. Cleared as safe today: requests are built on
the coordinating thread and reach worker threads only through
`ExecutorService.invokeAll` or a single-thread executor, both of which
establish happens-before, and src/main contains no `parallelStream`,
ForkJoin, or `CompletableFuture`.

**This constraint becomes load-bearing the moment the optimizer adopts
parallel streams or any other publication path without a happens-before
edge** — at that point the fields must go back to `final` and the mirror
(or a real builder) comes back.

### Consequences

* Good, because every future request field costs one declaration, not a
  mirror-class echo.
* Bad, because a silent concurrency precondition now guards the class; the
  bold sentence above is the tripwire.

### Confirmation

Review only: any change introducing a new publication path to worker
threads must re-check this ADR. `./gradlew checkTokens` guards the
motivating budget.

## Pros and Cons of the Options

### Hand-written mirror class

* Good, because final fields keep safe publication unconditionally.
* Bad, because O(fields) tokens and a second copy of every field to keep
  in sync.

### Builder

* Bad, because it is the mirror's token cost with extra ceremony; right
  answer only if finality must return.
