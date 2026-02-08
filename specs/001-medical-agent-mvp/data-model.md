# Phase 1 Data Model: Medical Agent Web MVP

## Entities

### 1) User

- Purpose: Authenticated account operating medical record workflows.
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `account` (varchar(128), unique per tenant)
  - `status` (enum: ACTIVE, DISABLED)
  - `role` (enum: PATIENT, CLINICIAN, ADMIN)
  - `created_at`, `updated_at` (timestamp)
- Validation:
  - `account` required and unique under (`tenant_id`, `account`).
  - `status` required.

### 2) Record

- Purpose: Logical medical record container.
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `user_id` (UUID, FK -> users.id)
  - `record_date` (date)
  - `title` (varchar(255))
  - `source_type` (enum: UPLOAD, MANUAL)
  - `created_at`, `updated_at` (timestamp)
- Validation:
  - `user_id`, `record_date`, `source_type` required.
  - title length <= 255.

### 3) Asset

- Purpose: Uploaded file metadata mapped to object storage.
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `record_id` (UUID, FK -> records.id)
  - `object_key` (varchar(512), unique)
  - `file_type` (enum: IMAGE, PDF)
  - `file_size` (bigint)
  - `checksum` (varchar(128))
  - `created_at` (timestamp)
- Validation:
  - `object_key`, `file_type`, `file_size`, `checksum` required.
  - `file_size` > 0 and <= max upload policy.

### 4) ParseJob

- Purpose: Async OCR/LLM processing job state machine.
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `record_id` (UUID, FK -> records.id)
  - `status` (enum: QUEUED, PROCESSING, SUCCESS, FAILED, RETRYING, DEAD_LETTER)
  - `progress` (smallint, 0-100)
  - `retry_count` (int, default 0)
  - `error_code` (varchar(64), nullable)
  - `trace_id` (varchar(64))
  - `idempotency_key` (varchar(128), unique)
  - `created_at`, `updated_at` (timestamp)
- Validation:
  - `status`, `progress`, `trace_id`, `idempotency_key` required.
  - `progress` between 0 and 100.

### 5) StructuredResult

- Purpose: Versioned structured extraction output linked to parse job.
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `job_id` (UUID, FK -> parse_jobs.id)
  - `record_id` (UUID, FK -> records.id)
  - `schema_version` (varchar(32))
  - `payload_json` (jsonb)
  - `confidence_score` (numeric(5,4))
  - `revision` (int, default 1)
  - `is_user_edited` (boolean, default false)
  - `created_at`, `updated_at` (timestamp)
- Validation:
  - `schema_version`, `payload_json` required.
  - `confidence_score` in [0, 1].
  - On edit, `revision` increments and history is retained.

### 6) GeneratedOutput

- Purpose: Generated summary or medication-plan drafts (versioned).
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `record_id` (UUID, FK -> records.id)
  - `type` (enum: SUMMARY, MED_PLAN)
  - `version` (int, starts at 1)
  - `content` (text)
  - `model_meta` (jsonb)
  - `requires_confirmation` (boolean, default true)
  - `created_at` (timestamp)
- Validation:
  - `type`, `version`, `content` required.
  - Unique constraint on (`record_id`, `type`, `version`).

### 7) AuditLog

- Purpose: Immutable, PHI-safe traceability log.
- Fields:
  - `id` (UUID, PK)
  - `tenant_id` (UUID, indexed)
  - `user_id` (UUID, nullable for system actions)
  - `action` (varchar(64))
  - `resource_type` (varchar(64))
  - `resource_id` (varchar(64))
  - `outcome` (enum: SUCCESS, FAILURE)
  - `error_code` (varchar(64), nullable)
  - `request_id` (varchar(64))
  - `trace_id` (varchar(64))
  - `ip` (varchar(64))
  - `created_at` (timestamp)
- Validation:
  - `action`, `resource_type`, `outcome`, `request_id` required.
  - Must not store raw medical text in event payload.

## Relationships

- `users 1 - N records`
- `records 1 - N assets`
- `records 1 - N parse_jobs`
- `parse_jobs 1 - N structured_results` (revision history)
- `records 1 - N generated_outputs`
- `users 1 - N audit_logs`

## State Transitions

### ParseJob

- `QUEUED -> PROCESSING`
- `PROCESSING -> SUCCESS`
- `PROCESSING -> FAILED`
- `FAILED -> RETRYING -> PROCESSING`
- `FAILED -> DEAD_LETTER` (after retry limit)

Transition guards:
- Retry allowed only when `retry_count < max_retry`.
- `SUCCESS` and `DEAD_LETTER` are terminal states.

### GeneratedOutput

- New generation always inserts `version + 1` per (`record_id`, `type`).
- Existing versions are immutable.

## Indexing Strategy

- `records(user_id, record_date desc)`
- `assets(record_id)`
- `parse_jobs(record_id, status, created_at desc)`
- `parse_jobs(idempotency_key)` unique
- `structured_results(record_id, revision desc)`
- `generated_outputs(record_id, type, version desc)`
- `audit_logs(user_id, created_at desc)`
