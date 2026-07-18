# Operator Guide

## First Deployment

1. Define the operator's plant roster and operator-certification/registration process.
2. Define consent and purpose categories for logged pressure-log/maintenance-record/progress records.
3. Run synthetic coordination cases (pressure-log/maintenance-record/progress record logging, scheduling, safety-concern flags, supply orders).
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions — every safety-concern flag, no exceptions ever, and every above-threshold supply order.
5. Measure coordination outcomes and audit coverage.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path (pressure-reading, safety-valve-condition and equipment-condition concerns ALWAYS reach a human — no exceptions, ever)
- provenance for all plants and operators before any coordination action
- human review for high-risk cases
- audit export for all gated actions

## Scope Boundary (Mandatory)

This actor coordinates boiler-plant scheduling and logistics ONLY.
Operators must not wire this actor's output into any system that
would let it directly finalize a boiler-operation-execution decision
(authorizing a pressure change/valve operation to proceed), finalize
a plant-safety-clearance decision, or override a plant safety
officer's judgment — the governor's closed op-allowlist and scope-
exclusion rule are the last line of defense, not the only one;
operator-side integrations must not create a path around them. This
actor never operates boiler/steam equipment itself:
`:coordinate-supply-order` covers plant-equipment/administrative/
spare-parts procurement only, never pressure vessels, valves or
steam equipment themselves — boiler/steam equipment handling is
entirely out of scope for this administrative-coordination actor.

## Certification

Certified operators must prove that the governor gates every
safety-critical robot action, that safety-critical risks escalate to
humans unconditionally, and that no integration allows this actor to
finalize a boiler-operation-execution decision, finalize a plant-
safety-clearance decision, or override plant-safety-officer authority.
