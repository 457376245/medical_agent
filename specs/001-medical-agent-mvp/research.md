# Phase 0 Research: Medical Agent Web MVP

## Decision 1: Java ORM selection

- Decision: Use MyBatis-Plus as MVP ORM in `backend-java`.
- Rationale: Keeps SQL explicit for auditability, avoids implicit ORM behavior, and supports deterministic migration-first PostgreSQL delivery.
- Alternatives considered: JPA/Hibernate (faster CRUD scaffolding but less predictable SQL/perf), pure MyBatis (higher boilerplate).

## Decision 2: Async status delivery to frontend

- Decision: Keep parse fully asynchronous and surface completion via timeline/record queries; no dedicated polling endpoint is exposed.
- Rationale: Lowest operational complexity behind common gateways, resilient reconnect behavior, and sufficient UX for <= 90s tasks.
- Alternatives considered: SSE (better real-time but higher infra complexity), WebSocket (overkill for one-way progress updates).

## Decision 3: OCR/LLM provider abstraction and fallback

- Decision: Implement provider adapter layer in `backend-agent` with primary + fallback providers, per-provider circuit breaker, and retry wrapper.
- Rationale: Prevents vendor lock-in, enables controlled failover, and centralizes timeout/retry/error classification.
- Alternatives considered: Direct SDK integration (simple now, hard to evolve), active-active dual provider (too costly for MVP).

## Decision 4: Retry, timeout, and idempotency policy

- Decision: Use request deadline budget (45-60s external call budget, 120s job timeout), retry at most 2 times for retryable errors (429/5xx/timeout), and idempotency key for create/generate calls.
- Rationale: Balances resilience and queue stability, avoids duplicate processing costs, and supports at-least-once MQ delivery semantics.
- Alternatives considered: Queue-only retries (slow failure recovery), unlimited retries (queue pileup and cost risk).

## Decision 5: Authentication strategy

- Decision: MVP uses Spring Security JWT (access + refresh token) with RBAC; OAuth2 social/enterprise login deferred.
- Rationale: Meets least-privilege and API security requirements with minimal external dependency complexity.
- Alternatives considered: OAuth2-only identity federation (more integration effort), session-based auth (less suitable for service boundaries).

## Decision 6: Object storage and deployment baseline

- Decision: Use S3-compatible API abstraction; MinIO for local/dev and cloud S3-compatible managed storage in production.
- Rationale: Same API across environments, simple local testing, and provider portability.
- Alternatives considered: Local filesystem (not safe/scalable), vendor-specific SDK-only path (reduced portability).

## Decision 7: Observability/logging stack choice

- Decision: Standardize on OpenTelemetry + Prometheus + Grafana + Loki for MVP logs/metrics/traces.
- Rationale: Lower operational overhead than full ELK in MVP while keeping correlation via `traceId` and request IDs.
- Alternatives considered: ELK stack (richer search but heavier operations and cost early stage).

## Decision 8: Audit log scope and PHI-safe logging

- Decision: Audit mandatory actions (upload, parse, edit, generate, delete/export, auth/access denial, role changes) with metadata-only logs and PHI redaction-by-default middleware.
- Rationale: Satisfies compliance traceability while minimizing sensitive-data leakage risk.
- Alternatives considered: Full payload logging (high compliance risk), security-only logging (insufficient accountability).

## Clarification Status

All items that could be marked as NEEDS CLARIFICATION in technical context are resolved by the decisions above.

