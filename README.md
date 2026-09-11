# cloud-itonami-isic-8510

Open Business Blueprint for **ISIC Rev.5 8510**: Pre-primary and
primary education. This repository publishes a school actor --
student intake, jurisdiction assessment, staff background-check
screening, promotion finalization and safeguarding-record
finalization -- as an OSS business that any qualified, licensed school
operator can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610)) --
the FIRST education vertical (ISIC section P) in this fleet. Here it
is **SchoolOps-LLM ⊣ Curriculum Safeguarding Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a student-
> intake summary, normalizing records, and checking whether a
> destination classroom's own recorded capacity has actually been
> exceeded -- but it has **no notion of which jurisdiction's school-
> licensing requirements are official, no license to finalize a real
> promotion or a real safeguarding record, and no way to know on its
> own whether the staff member reporting a safeguarding concern has
> actually had their background check cleared**. Letting it finalize a
> promotion or a safeguarding record directly invites fabricated
> jurisdiction citations, a promotion into an over-capacity classroom,
> and an uncleared staff member's report being quietly relied upon --
> and liability, and child-safety risk, for whoever runs it. This
> project seals the SchoolOps-LLM into a single node and wraps it with
> an independent **Curriculum Safeguarding Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers student intake through jurisdiction assessment,
staff background-check screening, promotion finalization and
safeguarding-record finalization. It does **not**, by itself, hold any
license required to operate a school in a given jurisdiction, and it
does not claim to. It also does **not** model a full curriculum-
design/pedagogical-assessment engine -- no subject-by-subject grading
rubric, no individualized-education-plan workflow, no full student-
information-system feature set (see `school.facts`'s own docstring for
the honest simplification this makes: a starting catalog of licensing
requirements, not a survey of every jurisdiction's curriculum
standards). Whoever deploys and operates a live instance (a licensed
school operator) supplies any jurisdiction-specific license, the real
pedagogical/safeguarding expertise and the real school-information-
system integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution
scaffold so that operator does not have to build the compliance layer
from scratch for every new market.

### Actuation

**Finalizing a real promotion or a real safeguarding record is never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`school.governor`'s `:actuation/finalize-promotion`/
`:actuation/finalize-safeguarding-record` high-stakes gate and
`school.phase`'s phase table, which never puts `:promotion/finalize`/
`:safeguarding/finalize` in any phase's `:auto` set) -- see
`school.phase`'s docstring and `test/school/phase_test.kotoba`'s
`promotion-finalize-never-auto-at-any-phase`/`safeguarding-finalize-
never-auto-at-any-phase`. The actor may draft, check and recommend; a
human licensed educator is always the one who actually finalizes a
promotion or a safeguarding record. Like `6512`/`6622`/`6520`/`6530`/
`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/
`8610`, this actor has TWO actuation events.

## The core contract

```
student intake + jurisdiction facts (school.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ SchoolOps-   │ ─────────────▶ │ Curriculum                   │  (independent system)
   │ LLM (sealed) │  + citations    │ Safeguarding Governor:        │
   └──────────────┘                 │ spec-basis · evidence-       │
                             commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ class-size-exceeds-
                           record + ledger  escalate ─▶ human   maximum (MAXIMUM-
                                             (ALWAYS for         ceiling, non-temporal) ·
                                              :promotion/            background-check-not-
                                              finalize /              cleared (unconditional) ·
                                              :safeguarding/finalize)   already-promoted/-recorded
```

**The SchoolOps-LLM never finalizes a promotion or a safeguarding
record the Curriculum Safeguarding Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
jurisdiction requirements; unsupported evidence; a destination class
size over its own maximum; an uncleared staff background check; a
double promotion or safeguarding-record finalization) force **hold**
and *cannot* be approved past; a clean promotion/safeguarding-record
proposal still always routes to a human.

## Run

```bash
kbb -M:dev:run     # walk one clean lifecycle (promotion finalization + safeguarding-record finalization) + five HARD-hold cases through the actor
kbb -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
kbb -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a classroom-safety
monitoring robot supports physical supervision during activities,
under the actor, gated by the independent **Curriculum Safeguarding
Governor**. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Curriculum Safeguarding Governor, promotion-finalization + safeguarding-record-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8510`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/
`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`, this vertical's
student records are practice-specific rather than a shared cross-
operator data contract, so `school.*` runs on the generic identity/
forms/dmn/bpmn/audit-ledger stack only -- no bespoke domain capability
lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/school/store.kotoba` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate promotion-finalization/safeguarding-record-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded student, and the double-promotion/double-record guards check dedicated `:promoted?`/`:safeguarding-recorded?` booleans rather than a `:status` value |
| `src/school/registry.kotoba` | Promotion-finalization + safeguarding-record-finalization draft records, plus `class-size-exceeds-maximum?` -- the SECOND non-temporal instance of this fleet's MAXIMUM-ceiling family (`facility.registry/occupancy-exceeds-capacity?` established the first) |
| `src/school/facts.kotoba` | Per-jurisdiction school-licensing catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/school/schoolopsllm.kotoba` | **SchoolOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/background-check-screening/promotion-finalization/safeguarding-record-finalization proposals |
| `src/school/governor.kotoba` | **Curriculum Safeguarding Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · class-size-exceeds-maximum, pure ground-truth MAXIMUM-ceiling recompute · background-check-not-cleared, unconditional evaluation, the EIGHTEENTH grounding of this discipline and FIRST specifically for the staff-background-check-clearance concept) + already-promoted/already-recorded guards + 1 soft (confidence/actuation gate) |
| `src/school/phase.kotoba` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (both promotion and safeguarding-record finalization always human; student intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/school/operation.kotoba` | **OperationActor** -- langgraph-clj StateGraph |
| `src/school/sim.kotoba` | demo driver |
| `test/school/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers student intake through jurisdiction assessment,
staff background-check screening, promotion finalization and
safeguarding-record finalization -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Student intake + per-jurisdiction school-licensing checklisting, HARD-gated on an official spec-basis citation (`:student/intake`/`:jurisdiction/assess`) | A full curriculum-design/pedagogical-assessment engine (subject-by-subject grading rubrics, individualized-education-plan workflows -- see `school.facts`'s docstring) |
| Staff background-check screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:background-check/screen`) | Real school-information-system integration, billing/tuition workflows |
| Promotion finalization, HARD-gated on full evidence and destination-class-size sufficiency, plus a double-promotion guard (`:promotion/finalize`) | Ongoing classroom-instruction workflows themselves |
| Safeguarding-record finalization, HARD-gated on the reporting staff member's own background-check clearance and a double-record guard (`:safeguarding/finalize`) | |
| Immutable audit ledger for every intake/assessment/screening/promotion/safeguarding-record decision | |

Extending coverage is additive: add the next gate (e.g. an attendance-
compliance check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`school.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `school.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `school.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `SchoolOps-LLM` + `Curriculum Safeguarding Governor`
run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, modeled closely on
the twenty-seven prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
