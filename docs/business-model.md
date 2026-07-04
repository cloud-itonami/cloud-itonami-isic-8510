# Business Model: Pre-primary and primary education

## Classification

- Repository: `cloud-itonami-isic-8510`
- ISIC Rev.5: `8510`
- Activity: pre-primary and primary education -- early-childhood and primary-school instruction for children by licensed educators
- Social impact: education access, data sovereignty, transparent audit

## Customer

- independent preschools/primary schools
- cooperative parent-run schools
- community education programs

## Offer

- student enrollment intake
- curriculum/placement proposal
- progress-report/promotion proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per school
- support: monthly retainer with SLA
- migration: import from an incumbent school-information system
- per-enrollment fee

## Trust Controls

- no promotion decision or safeguarding-relevant record is finalized without human sign-off (a licensed educator)
- a fabricated assessment forces a hold, not an override
- every record path is auditable
- student data (particularly for minors) stays outside Git
- emergency manual override paths remain outside LLM control
