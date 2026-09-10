# Operator Quickstart

## Prerequisites

- **Clojure CLI** (clojure) 1.11+
- **Java** 11+
- **git**

For working inside the full monorepo (optional), you also need:
- **nbb** (Node.js babashka) for any ClojureScript/Node.js tasks in sibling verticals
- Access to workspace siblings under `../../kotoba-lang/` (langgraph-clj, langchain-clj)

For a **standalone fork** outside the monorepo, override the `:local/root` entries in `deps.edn` with git coordinates (see comments in `deps.edn`).

## Run Tests

The actor's governor contract, phase invariants, store parity, registry conformance, and facts coverage are all tested:

```bash
clojure -M:dev:test
```

Test files cover:
- `school.governor` — Curriculum Safeguarding Governor contract (hard gates, evidence checks, background-check clearance)
- `school.phase` — Phase table invariants (no auto-eligible high-stakes ops)
- `school.store` — Store protocol compliance (in-memory and Datomic)
- `school.registry` — Draft record and class-size-exceeds-maximum checks
- `school.facts` — Jurisdiction catalog coverage and spec-basis citations

All tests must pass before any deployment.

## Run the Demo

Walk one clean lifecycle (promotion finalization + safeguarding-record finalization) plus five HARD-hold cases through the actor:

```bash
clojure -M:dev:run
```

This drives `school.sim`, the demo driver. The output shows:
1. A student intake and jurisdiction assessment
2. A promotion proposal with governor approval
3. A safeguarding-record proposal with governor approval
4. Five HARD-hold scenarios (fabricated citation, incomplete evidence, class-size exceeded, uncleared background check, double promotion)

## Demo Page & Publishing

To publish a read-only demo page:

```bash
# Generate the demo output to stdout
clojure -M:dev:run > demo-output.txt

# Create a simple HTML wrapper (optional)
# Then host on GitHub Pages, Netlify, or your platform
```

The demo output shows real actor behavior and governor decisions — use it to validate the system before deploying to production.

## Core Modules

| Module | Location | Role |
|--------|----------|------|
| **Curriculum Safeguarding Governor** | `src/school/governor.kotoba` | Independent verification of promotion and safeguarding-record decisions; enforces hard gates |
| **SchoolOps-LLM Advisor** | `src/school/schoolopsllm.kotoba` | Drafts proposals; mock or real LLM mode |
| **Phase Table** | `src/school/phase.kotoba` | Governs which operations can be auto-executed (only student intake); forces human sign-off for high-stakes ops |
| **OperationActor** | `src/school/operation.kotoba` | langgraph-clj StateGraph orchestrating the full lifecycle |
| **Store** | `src/school/store.kotoba` | In-memory or Datomic persistence with append-only audit ledger |
| **Facts & Jurisdiction Catalog** | `src/school/facts.kotoba` | Per-jurisdiction school-licensing requirements with spec-basis citations |

## Before Going Live

1. **Review the governor contract** — see `test/school/governor_contract_test.kotoba`
2. **Configure trust controls** — set hold/escalation policy in your `Governor` instance
3. **Test with real jurisdiction facts** — add your jurisdiction to `school.facts/catalog` with an official spec-basis citation
4. **Run the full test suite** — `clojure -M:dev:test`
5. **Set up audit export** — ensure `school.store`'s audit ledger path is backed up and monitored
6. **Establish a manual override process** — for when the system itself needs override (human, not LLM)

## First Deployment

See `operator-guide.md` for the minimum production controls:
- Licensed-educator sign-off before any determination
- Finalizing a promotion or safeguarding-record always requires human sign-off
- Audit export for every hold, approval and record action
- Backup manual process for governor/system outage

## Support & Issues

- **Architecture & design**: see `docs/adr/0001-architecture.md`
- **Business model & customers**: see `docs/business-model.md`
- **Governance & operator playbook**: see `docs/operator-guide.md`
- **Source code**: browse `src/school/` with its docstrings

For forks, keep AGPL-3.0-or-later headers and license unchanged.
