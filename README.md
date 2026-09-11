# cloud-itonami-isco-8182

Open Occupation Blueprint for **ISCO-08 8182**: Steam Engine and Boiler Operators.

This repository designs a forkable OSS business for a boiler-plant scheduling/logistics coordination service: a boiler-plant scheduling/logistics coordination robot manages pressure-log/maintenance-record/progress record logging, crew/shift scheduling, safety-concern flagging and administrative/spare-parts supply order coordination under a governor-gated actor, so the steam-engine/boiler plant operator keeps its own operating records instead of renting a closed plant-scheduling SaaS.

**This actor coordinates BOILER-PLANT SCHEDULING/LOGISTICS ONLY — it never operates boiler/steam equipment itself and never makes a boiler-operation-execution or plant-safety-clearance decision.** Steam Engine and Boiler Operators run high-pressure steam/boiler systems — errors can cause a catastrophic pressure-vessel/boiler explosion, categorically higher-stakes than ordinary workshop trades, on par with chemical-plant and mining operations in this catalog. The actor's closed op-allowlist contains no op that directly finalizes a boiler-operation-execution decision (authorizing a pressure change/valve operation to proceed) or a plant-safety-clearance decision, nor overrides a plant safety officer's judgment. Any proposal that attempts any of these is a hard, permanent block, never overridable by human approval, and NEVER auto-commit-eligible under any confidence level.

**Maturity: `:implemented`.** `src/boilercoord/` implements the
`BoilerCoordActor` as a `langgraph.graph/state-graph`
(`boilercoord.actor`) wired to a `Boiler-Plant Scheduling &
Logistics Coordination Advisor` (`boilercoord.advisor`) and an
independent `BoilerCoordGovernor` (`boilercoord.governor`), following
the itonami actor pattern (ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. See `kbb -M:test` output for
the current test/assertion counts.

HARD invariants (always `:hold`, never overridable): the operator/plant
record must be independently verified/registered before any action;
a referenced operator must be a registered certified plant operator
belonging to that plant; `:effect` must be `:propose` only (no hardware
dispatch, no boiler/steam-equipment operation); the closed op-allowlist
is enforced (no op in the allowlist finalizes a boiler-operation-
execution decision, finalizes a plant-safety-clearance decision, or
overrides plant-safety-officer authority); and any proposal that
attempts to directly finalize a boiler-operation-execution decision
(authorizing a pressure change/valve operation to proceed), finalize a
plant-safety-clearance decision, or override a plant safety officer's
judgment is a hard, **permanent** block — detected as finalization/
execution action phrases (never bare nouns like "boiler"/"steam"/
"pressure"/"valve", which are ordinary vocabulary for this domain and
must not false-trip the guard).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (every surfaced pressure-reading concern,
safety-valve-condition concern or equipment-condition issue, ALWAYS,
no exceptions, ever) and `:coordinate-supply-order` above the
registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a boiler-plant scheduling/logistics coordination robot performs pressure-log/maintenance-record/progress record logging, crew/shift-schedule proposals, safety-concern surfacing and administrative/spare-parts supply order coordination under an actor that proposes
actions and an independent **Boiler-Plant Scheduling & Logistics Coordination Governor** that gates them. The governor never
dispatches hardware itself, never operates boiler/steam equipment, never finalizes a boiler-operation-execution or plant-safety-clearance decision, and never overrides a plant safety officer's judgment; `:high`/`:safety-critical` actions (such as a safety-concern flag or an above-threshold supply order) require human sign-off.

## Core Contract

```text
plant roster + operator roster + plant schedule
        |
        v
Boiler-Plant Scheduling & Logistics Coordination Advisor -> BoilerCoordGovernor -> log record/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a boiler-operation-execution decision (authorizing a
pressure change/valve operation to proceed), finalize a plant-safety-
clearance decision, override a plant safety officer's judgment,
suppress an operating record, or disclose sensitive data without
governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8182`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
