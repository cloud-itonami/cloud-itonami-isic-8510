# ADR-0001: cloud-itonami-isic-8510 -- SchoolOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311` ADR-0001s (the pattern this ADR ports); ADR-2607071250/
  ADR-2607071320/ADR-2607071351/ADR-2607071618/ADR-2607071640/
  ADR-2607071654/ADR-2607071717/ADR-2607071732/ADR-2607071752/
  ADR-2607071819/ADR-2607071849/ADR-2607071922/ADR-2607072715/
  ADR-2607072730/ADR-2607072745/ADR-2607072800/ADR-2607072815/
  ADR-2607072830/ADR-2607072845/ADR-2607072900 (`6612`/`6492`/`6920`/
  `6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
  `8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`, the twenty
  verticals built outside ADR-2607032000's original insurance/real-
  estate batch -- this is the twenty-first)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `9311`, this ADR deepens `cloud-itonami-
  isic-8510` (pre-primary and primary education) from `:blueprint` to
  `:implemented`, the thirty-fifth actor in this fleet -- the FIRST
  education vertical (ISIC section P) built in this fleet.

## Problem

A school's promotion-finalization/safeguarding-record-finalization
workflow bundles several distinct concerns under one governed
workflow:

1. **Jurisdiction school-licensing correctness** -- an official spec-
   basis citation from a real regulator (文部科学省/state Departments
   of Education under ESSA/the DfE and Ofsted jointly/the
   Kultusministerien der Länder), never fabricated.
2. **Destination-classroom sufficiency** -- does a student's own
   destination classroom's current size exceed that classroom's own
   recorded maximum? The SECOND non-temporal instance of this fleet's
   MAXIMUM-ceiling family (`facility.registry/occupancy-exceeds-
   capacity?` established the first, comparing a sports facility's own
   occupancy against its own capacity), reusing the same two-field-
   on-one-entity comparison shape for a genuinely different ground
   truth.
3. **Staff background-check clearance verification** -- has the
   reporting staff member's own background check actually been
   cleared before a safeguarding-relevant record is finalized? The
   school-specific application of the unconditional-evaluation
   screening discipline this fleet's `casualty.governor/sanctions-
   violations` originally established -- an EIGHTEENTH distinct
   grounding overall, and the FIRST specifically for the staff-
   background-check-clearance concept (distinct from the credential-
   CURRENCY concept `clinic`/`veterinary`/`hospital` established -- a
   background check is a one-time clearance gate, not a renewable
   license).
4. **Real, high-stakes actuation, twice** -- finalizing a real
   promotion and finalizing a real safeguarding record are two
   independently-gated real-world acts on the SAME entity (a student).

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a school with an LLM" but "seal the LLM
inside a trust boundary and layer evidence-sufficiency, classroom-
capacity verification, background-check-clearance verification, audit
and human-approval on top of it, while structurally fixing both real
actuation events as human-only."

## Decision

### 1. SchoolOps-LLM is sealed into the bottom node; it never finalizes a promotion or safeguarding record directly

`school.schoolopsllm` returns exactly five kinds of proposal: intake
normalization, jurisdiction school-licensing checklist, background-
check screening, promotion-finalization draft, and safeguarding-
record-finalization draft. No proposal writes the SSoT or commits a
real promotion/safeguarding-record finalization directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 school operation

`school.operation/build` is the SAME StateGraph shape as every sibling
actor's operation namespace, copied verbatim.

### 3. `class-size-exceeds-maximum?` is the SECOND non-temporal instance of the MAXIMUM-ceiling family

`facility.registry/occupancy-exceeds-capacity?` established the FIRST
non-temporal check in this fleet's MAXIMUM-ceiling family, comparing a
sports facility's own current occupancy against its own recorded
capacity. `class-size-exceeds-maximum?` is the SECOND instance,
reusing the same two-field-on-one-entity comparison shape for a
genuinely different ground truth: a student's own destination
classroom's current size against that classroom's own recorded
maximum, before a promotion can be finalized into it.

### 4. Background-check-not-cleared screening reuses the unconditional-evaluation discipline for an eighteenth distinct grounding, and a first specifically for this concept

`background-check-not-cleared-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for `:background-check/screen`, `:promotion/finalize` AND
`:safeguarding/finalize` -- the EIGHTEENTH distinct application of this
exact discipline in this fleet overall, and the FIRST specifically for
the staff-background-check-clearance concept. This is a genuinely new
sub-concept, not a fourth reuse of the credential-CURRENCY concept
`clinic`/`veterinary`/`hospital` established: a background check is a
one-time clearance gate that either happened or didn't, not a license
that can lapse and later be renewed.

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety` and eight later siblings

`background-check-not-cleared-is-held-and-unoverridable` calls
`:background-check/screen` directly against `student-4` (an uncleared
check), NOT `:promotion/finalize`/`:safeguarding/finalize` against an
unscreened student -- because a failing screen is itself a HARD hold
whose payload never persists to the store, so the actuation ops alone
could never discover the bad ground-truth flag through this check
family without the screening op having actually been run first. This
build applied that lesson PROACTIVELY for a ninth consecutive vertical
(after `eldercare`, `museum`, `conservation`, `salon`, `entertainment`,
`casework`, `hospital` and `facility`), further reinforcing that
lessons recorded in this fleet's ADRs transfer forward reliably.

### 6. Dual actuation, matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`'s shape

`school.governor`'s `high-stakes` set has exactly two members
(`:actuation/finalize-promotion`, `:actuation/finalize-safeguarding-
record`), each acting on the SAME student entity, each with its OWN
history collection (`promotion-history`/`safeguarding-history`),
sequence counter and dedicated double-actuation-guard boolean.

### 7. Double-promotion/double-safeguarding-record guards check dedicated booleans, not `:status`

`already-promoted-violations`/`already-recorded-violations` check
`:promoted?`/`:safeguarding-recorded?`, dedicated booleans set once
and never cleared, rather than a `:status` value that could
legitimately advance past a checked state (the exact trap `cloud-
itonami-isic-6492`'s ADR-0001 documents in detail, explicitly avoided
BY DESIGN in every sibling actor's equivalent guard since). This
actor's `:status` never needs to encode "has this actuation already
happened" at all -- a deliberate architectural choice applied here for
a nineteenth consecutive time.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`, and unlike
most other actors in this fleet, this vertical's student records are
practice-specific rather than a shared cross-operator data contract --
`school.*` runs on the generic identity/forms/dmn/bpmn/audit-ledger
stack only, per the blueprint's own explicit statement.

## Consequences

- (+) Pre-primary/primary education gets the same governed, auditable-
  actor treatment as the twenty-eight prior actors, and this fleet now
  has a TWENTY-FIRST concrete precedent for extending past
  ADR-2607032000's original scope, and its FIRST education vertical
  (ISIC section P).
- (+) `class-size-exceeds-maximum?` is a genuine structural
  contribution: the second non-temporal instance of the MAXIMUM-
  ceiling family, further validating that family's generality beyond
  the assembly-venue-occupancy concept `facility` established it for.
- (+) `background-check-not-cleared-violations` is a genuine domain-
  modeling contribution: the first time this fleet's unconditional-
  evaluation discipline has been applied to a one-time clearance gate
  rather than a renewable-license-currency concept.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/school/phase_test.clj`'s `promotion-
  finalize-never-auto-at-any-phase`/`safeguarding-finalize-never-auto-
  at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/school/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) The background-check-not-cleared test/demo correctly applied the
  established SCREENING-op-directly pattern for a ninth consecutive
  vertical -- further evidence that lessons recorded in this fleet's
  ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `school.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `class-size-exceeds-maximum?` models only a single classroom-
  size-vs-maximum comparison, not a full curriculum-design/
  pedagogical-assessment engine (subject-by-subject grading rubrics,
  individualized-education-plan workflows are out of scope -- see
  `school.facts`'s own docstring); real school-information-system
  integration and ongoing classroom-instruction workflows are all out
  of scope for this OSS actor -- each operator's responsibility (see
  README's coverage table).
- 36 tests / 173 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All twenty of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`; this is also this fleet's first ISIC section P (education) vertical, with no prior overlapping-division sibling to fold into at all |
| Keep `cloud-itonami-isic-8510` at `:blueprint` only | ❌ | The standing direction continues past `9311`; pre-primary/primary education is a natural next domain, opening this fleet's first education-sector coverage |
| Model `background-check-not-cleared?` as a fourth reuse of the credential-not-current concept (`clinic`/`veterinary`/`hospital`) | ❌ | A background check is a one-time clearance gate, not a renewable license that can lapse and later be re-verified as current -- collapsing the two concepts into one grounding would misrepresent a genuine domain distinction; honestly framing this as a FIRST for a distinct sub-concept keeps the fleet's check-family taxonomy accurate |
| Test `background-check-not-cleared-violations` via an actuation op against an unscreened student (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s own ADR-2607071922 Decision 5 and reconfirmed by eight later siblings' ADR-0001s -- a failing screen never persists its payload to the store, so the actuation ops alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/school`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning established by every "no bespoke capability lib" sibling's ADR |

## References

- ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900
  (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/
  `9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`, first twenty post-batch verticals)
- ADR-2607032000 (original insurance/real-estate batch, Addenda 1-7)
- `cloud-itonami-isic-8510/docs/adr/0001-architecture.md` (this ADR)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)
