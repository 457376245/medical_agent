# Implementation Plan: Medical Agent Web MVP

**Branch**: `001-medical-agent-mvp` | **Date**: 2026-02-07 | **Spec**: `specs/001-medical-agent-mvp/spec.md`
**Input**: Technical architecture from `technical_architecture_design.md`

## Summary

Deliver an MVP medical-record assistant with three modules (`frontend`, `backend-java`, `backend-agent`) to support disease-selected upload, async OCR+LLM parsing, structured-result editing, disease-centric timeline tracking, generation of summary/medication-plan drafts, and user-facing data export/delete request entry points. The implementation uses versioned REST/event contracts, queue-driven async processing, strict audit/security controls, and fallback-safe AI provider routing.

## Technical Context

**Language/Version**: TypeScript 5.x + Next.js 14, Java 21 + Spring Boot 3.x, Python 3.11 + FastAPI  
**Primary Dependencies**: Tailwind CSS, TanStack Query, Spring Security, MyBatis-Plus, RabbitMQ client, OpenTelemetry SDK  
**Storage**: PostgreSQL 15, Redis 7, S3-compatible object storage (MinIO dev, OSS/S3 prod)  
**Testing**: Vitest/Playwright (frontend), JUnit5 + Spring Boot Test + Testcontainers (Java), pytest (Agent)  
**Target Platform**: Linux container deployment + modern desktop/mobile browsers  
**Project Type**: Web platform with one frontend and two backend services  
**Performance Goals**: Upload API P95 < 2s; end-to-end parse/generate P95 <= 90s; single task timeout default 120s  
**Constraints**: Async-only long-running jobs, strict PHI log redaction, encrypted transport/storage, idempotent create/generate APIs  
**Scale/Scope**: MVP for up to 10k MAU, 100 concurrent processing jobs, schema-versioned v1 contracts

## Architecture Impact

- Add disease-centric timeline query endpoints and data-rights request endpoints (export/delete) under `/api`.
- Extend structured result payload to include low-confidence source-evidence metadata.
- Enforce disease selection/addition during upload flow and bind each report to one disease profile.
- Keep Java as system-of-record for request/audit status, and Agent as async processing worker.
- Enforce transport/storage encryption and RBAC for new data-rights operations.

## Rollback Strategy

- **API compatibility rollback**: Keep `/api` backward compatible; disable new features via feature flag without removing existing endpoints.
- **Event compatibility rollback**: Preserve `.v1` routing keys; consumers tolerate additive fields and ignore unknown fields.
- **Database rollback**: Use forward-safe `expand -> migrate -> contract` migrations; on incident, disable writer paths and keep readers on previous schema projection.
- **Operational rollback triggers**: rollback when parse/generate error rate exceeds threshold, queue lag breaches SLO, or security checks fail.
- **Runbook ownership**: release owner must maintain rollback checklist and post-rollback verification steps.

## Schema/API Version Impact

- All external APIs remain under `/api`; new capabilities are additive and backward compatible.
- Event contracts remain on `*.v1`; consumers must tolerate additive fields and ignore unknown fields.
- Structured-result schema keeps explicit `schema_version`; new fields are additive and cannot break existing `v1` readers.
- Contract updates require OpenAPI/AsyncAPI diff review and migration notes in release checklist.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase-0 Gate Review

- **I. Patient Safety & Non-Diagnostic Boundary**: PASS - summary/med-plan are drafts only; UI disclaimer and explicit reconfirmation before save are required.
- **II. Privacy-by-Default & Least Privilege**: PASS - TLS + storage encryption, RBAC, no raw medical text in logs, auditable key actions.
- **III. Traceable AI Pipeline & Human Confirmation**: PASS - explicit job states, confidence fields, editable structured results with revision/version support.
- **IV. Async Reliability & Failure Transparency**: PASS - queue-driven async flow, progress/status endpoint, retry + timeout + DLQ strategy, idempotency key.
- **V. Versioned Contracts & Measurable Delivery**: PASS - `/api`, MQ topic `.v1`, structured schema version, measurable latency targets.

### Post-Phase-1 Gate Re-check

- **I**: PASS - contracts and quickstart enforce disclaimer + reconfirmation path.
- **II**: PASS - data model contains audit logs + tenant fields; contracts include security + trace fields.
- **III**: PASS - parse job lifecycle and result versioning reflected in model/contracts.
- **IV**: PASS - async APIs/events define retryable failures and status transparency.
- **V**: PASS - OpenAPI/AsyncAPI and schema versioning documented; no unresolved clarifications.

## Project Structure

### Documentation (this feature)

```text
specs/001-medical-agent-mvp/
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- contracts/
|   |-- openapi.yaml
|   `-- asyncapi.yaml
`-- tasks.md
```

### Source Code (repository root)

```text
frontend/
|-- src/
|   |-- app/
|   |-- components/
|   |-- services/
|   `-- hooks/

backend-java/
|-- src/main/java/.../
|   |-- api/
|   |-- application/
|   |-- domain/
|   |-- infrastructure/
|   `-- config/

backend-agent/
|-- app/
|   |-- api/
|   |-- workers/
|   |-- providers/
|   |-- schemas/
|   `-- core/

tests/
|-- contract/
|-- integration/
|-- performance/
`-- manual/
```

**Structure Decision**: Use the existing three-module architecture from `technical_architecture_design.md`, with Java as system-of-record and FastAPI as async AI worker. Keep service boundaries stable and evolve internally as modular monolith (Java) + worker adapters (Python).

## Complexity Tracking

No constitution violations requiring exceptions.

