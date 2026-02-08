<!--
Sync Impact Report
- Version change: 0.0.0 -> 1.0.0
- Modified principles:
  - Template Principle 1 -> I. Patient Safety & Non-Diagnostic Boundary
  - Template Principle 2 -> II. Privacy-by-Default & Least Privilege
  - Template Principle 3 -> III. Traceable AI Pipeline & Human Confirmation
  - Template Principle 4 -> IV. Async Reliability & Failure Transparency
  - Template Principle 5 -> V. Versioned Contracts & Measurable Delivery
- Added sections:
  - Medical Safety & Compliance Constraints
  - Delivery Workflow & Quality Gates
- Removed sections:
  - None
- Templates requiring updates:
  - ✅ updated: .specify/templates/plan-template.md
  - ✅ updated: .specify/templates/spec-template.md
  - ✅ updated: .specify/templates/tasks-template.md
  - ✅ checked (no change required): .specify/templates/agent-file-template.md
  - ✅ checked (no change required): .specify/templates/checklist-template.md
  - ⚠ pending: .specify/templates/commands/*.md (directory not present in repository)
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): original ratification date not found in repo history/docs.
-->
# Medical Agent Web MVP Constitution

## Core Principles

### I. Patient Safety & Non-Diagnostic Boundary
The product MUST present AI output as reference information only and MUST NOT claim
diagnostic authority. Any medication dose/frequency content MUST require explicit
user reconfirmation before save. Every summary/plan surface MUST include a visible
medical disclaimer. Rationale: MVP scope excludes diagnosis and must reduce misuse risk.

### II. Privacy-by-Default & Least Privilege
All medical data flows MUST use encrypted transport and encrypted storage. Access control
MUST follow least privilege, and sensitive raw medical text MUST NOT be written to
application logs. Key user actions (upload, parse, edit, generate, delete/export)
MUST be auditable. Rationale: medical records are high-sensitivity data with strict
compliance expectations.

### III. Traceable AI Pipeline & Human Confirmation
Core workflow states MUST be explicit and recoverable: upload -> parsing -> structuring
-> generating -> saved. Low-confidence extracted fields MUST be highlighted with source
evidence and MUST be editable with revision history retained. Generated outputs MUST be
versioned, and regeneration MUST create a new version. Rationale: users need traceability
and correction capability for trustworthy medical record management.

### IV. Async Reliability & Failure Transparency
Long-running OCR/LLM operations MUST execute asynchronously and MUST expose progress,
timeout, and retry paths to users. Error responses MUST include clear reason categories
and actionable recovery steps. Duplicate submission protection MUST be implemented via
fingerprint or idempotency controls. Rationale: medical document processing is variable
and failure-prone; reliable async UX is core MVP value.

### V. Versioned Contracts & Measurable Delivery
APIs, event payloads, and structured-result schemas MUST be versioned and backward
compatible within a release line. Every feature spec MUST define measurable outcomes,
including reliability and latency targets for core flows. Every implementation plan
MUST include constitution gates before design and before delivery. Rationale: MVP must
evolve quickly without breaking existing records, clients, or integrations.

## Medical Safety & Compliance Constraints

- In-scope capabilities are limited to document ingestion, structured extraction,
  summary generation, and medication plan draft generation.
- Out-of-scope capabilities include diagnosis decisions, prescription issuance,
  payment/insurance settlement, and deep HIS/EMR bi-directional sync.
- P95 end-to-end processing target for a single file is <= 90 seconds in MVP.
- Data rights support (export/delete request entry points) MUST be provided.
- Any compliance-region uncertainty MUST be tracked explicitly as a blocker in specs.

## Delivery Workflow & Quality Gates

1. Specification gate: each feature MUST include user stories, edge cases (upload/parse
   failure, low confidence, duplicate submission), and measurable success criteria.
2. Plan gate: each plan MUST document architecture impact, async behavior, security,
   audit events, schema/API version impact, and rollback strategy.
3. Implementation gate: high-risk flows (upload, parse, generation, save, delete/export)
   MUST include automated tests; lower-risk changes MUST include documented manual
   verification steps.
4. Review gate: reviewers MUST verify constitution compliance before approval and record
   any exception in Complexity Tracking with rationale and sunset plan.
5. Release gate: changes affecting contracts, security posture, or user-visible safety
   messaging MUST include release notes and migration/backward-compatibility details.

## Governance

- Authority: This constitution overrides informal practices for product and engineering
  decisions in this repository.
- Amendment process: Changes require (a) a written proposal, (b) impact assessment on
  templates and active specs/plans/tasks, and (c) approval by product + engineering
  maintainers.
- Versioning policy: Semantic versioning is mandatory for this document.
  - MAJOR: Removes or redefines a principle in a backward-incompatible way.
  - MINOR: Adds a principle/section or materially expands a mandatory gate.
  - PATCH: Clarification, wording, typo, or non-semantic edits.
- Compliance review cadence: Constitution compliance MUST be checked at plan creation,
  PR review, and pre-release verification for each feature.
- Exceptions: Any temporary exception MUST include owner, expiry date, and mitigation.
- Runtime guidance: `.specify/templates/plan-template.md`,
  `.specify/templates/spec-template.md`, and `.specify/templates/tasks-template.md`
  MUST remain aligned with this constitution.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): original adoption date not found | **Last Amended**: 2026-02-07
