# Governance

`cloud-itonami-isic-8510` is an OSS open-business blueprint for pre-primary and primary education -- early-childhood and primary-school instruction for children by licensed educators.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Curriculum Safeguarding Governor remains independent of the advisor.
- hard policy violations (fabricated assessment, incomplete records) cannot be
  overridden by human approval.
- finalizing a promotion decision or a safeguarding-relevant record always escalates to a human -- never automated.
- every hold, approval and record-action path is auditable.
- student and participant data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Curriculum Safeguarding Governor's policy checks
- mishandling student/participant data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
