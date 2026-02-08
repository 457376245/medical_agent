# Tasks: Medical Agent Web MVP

**Input**: Design documents from `specs/001-medical-agent-mvp/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: Risk-based testing is required for high-risk flows (upload, parse, generate, save, delete/export, auth/privacy boundaries).

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize the three-module workspace and local runtime baseline.

- [X] T001 Create service skeleton directories in `frontend/src/`, `backend-java/src/main/java/com/medical/agent/`, and `backend-agent/app/`
- [X] T002 Initialize frontend dependencies and scripts in `frontend/package.json`
- [X] T003 [P] Initialize Java dependencies (Spring Web/Security/MyBatis-Plus/AMQP/OTel) in `backend-java/pom.xml`
- [X] T004 [P] Initialize Python dependencies (FastAPI, aio-pika/provider adapters, OTel) in `backend-agent/requirements.txt`
- [X] T005 Add local infrastructure compose stack (PostgreSQL/Redis/RabbitMQ/MinIO) in `docker-compose.yml`
- [X] T006 [P] Create environment templates in `frontend/.env.local.example`, `backend-java/.env.example`, and `backend-agent/.env.example`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build shared capabilities that block all user stories.

**CRITICAL**: Complete this phase before user-story implementation.

- [X] T007 Create baseline SQL migration for core tables in `backend-java/src/main/resources/db/migration/V1__init_core_schema.sql`
- [X] T008 [P] Add migration for data-rights requests (export/delete) in `backend-java/src/main/resources/db/migration/V2__data_rights_requests.sql`
- [X] T009 [P] Implement JWT authentication and RBAC filter chain in `backend-java/src/main/java/com/medical/agent/config/SecurityConfig.java`
- [X] T010 [P] Implement unified API response envelope and global exception mapping in `backend-java/src/main/java/com/medical/agent/api/ApiExceptionHandler.java`
- [X] T011 [P] Implement idempotency middleware/interceptor for create/generate APIs in `backend-java/src/main/java/com/medical/agent/infrastructure/idempotency/IdempotencyInterceptor.java`
- [X] T012 [P] Implement audit event writer (metadata-only, no PHI payload) in `backend-java/src/main/java/com/medical/agent/infrastructure/audit/AuditLogService.java`
- [X] T013 [P] Implement sensitive-log redaction rules in `backend-java/src/main/java/com/medical/agent/config/LoggingRedactionConfig.java`
- [X] T014 [P] Implement OpenTelemetry tracing and requestId propagation in `backend-java/src/main/java/com/medical/agent/config/ObservabilityConfig.java`
- [X] T015 Implement RabbitMQ exchanges/queues/DLQ bindings in `backend-java/src/main/java/com/medical/agent/infrastructure/mq/RabbitTopologyConfig.java`
- [X] T016 [P] Implement provider abstraction interfaces and resilience policy in `backend-agent/app/providers/gateway.py`
- [X] T017 [P] Add TLS enforcement and cert config for API and internal calls in `backend-java/src/main/java/com/medical/agent/config/TlsConfig.java`
- [X] T018 [P] Add contract schema validation test harness in `tests/contract/test_contract_schemas.py`

**Checkpoint**: Foundation complete; user stories can now proceed.

---

## Phase 3: User Story 1 - 上传并生成本次结果 (Priority: P1) 🎯 MVP

**Goal**: 用户在上传时必须选择或新增病症，上传后可异步获得结构化检查结果与摘要，支持失败重试、低置信证据查看和结构化结果修订。

**Independent Test**: 选择病症并上传有效文件后可见 queued/processing/success 或 failed；成功时可查看结构化字段与摘要，低置信字段可查看来源证据并修订生成新版本。

### Tests for User Story 1

- [X] T019 [P] [US1] Add contract test for `POST /api/v1/uploads/presign` and `POST /api/v1/assets/complete` in `tests/contract/test_upload_endpoints.py`
- [X] T020 [P] [US1] Add contract test for `POST /api/v1/parse-jobs` and `GET /api/v1/parse-jobs/{jobId}` in `tests/contract/test_parse_job_endpoints.py`
- [X] T021 [P] [US1] Add contract test for `PATCH /api/v1/records/{recordId}/structured-result` in `tests/contract/test_structured_result_patch.py`
- [X] T022 [P] [US1] Add contract test for `POST /api/v1/records/{recordId}/generate-summary` in `tests/contract/test_summary_endpoint.py`
- [X] T023 [P] [US1] Add integration test for upload-to-parse-success path in `tests/integration/test_us1_upload_parse_success.py`
- [X] T024 [P] [US1] Add integration test for parse failure and retry visibility in `tests/integration/test_us1_parse_failure_retry.py`
- [X] T025 [P] [US1] Add integration test for low-confidence source-evidence and edit revision in `tests/integration/test_us1_low_confidence_revision.py`

### Implementation for User Story 1

- [X] T026 [P] [US1] Implement upload presign API controller in `backend-java/src/main/java/com/medical/agent/api/UploadController.java`
- [X] T027 [P] [US1] Implement asset completion API and persistence in `backend-java/src/main/java/com/medical/agent/api/AssetController.java`
- [X] T028 [US1] Implement parse-job create/status APIs in `backend-java/src/main/java/com/medical/agent/api/ParseJobController.java`
- [X] T029 [P] [US1] Implement Asset/ParseJob/StructuredResult MyBatis mappers in `backend-java/src/main/java/com/medical/agent/infrastructure/persistence/mapper/`
- [X] T030 [US1] Implement parse request publisher for `agent.parse.request.v1` in `backend-java/src/main/java/com/medical/agent/infrastructure/mq/ParseRequestPublisher.java`
- [X] T031 [US1] Implement parse result consumer for `agent.parse.result.v1` in `backend-java/src/main/java/com/medical/agent/infrastructure/mq/ParseResultConsumer.java`
- [X] T032 [P] [US1] Implement agent parse worker with OCR/LLM orchestration in `backend-agent/app/workers/parse_worker.py`
- [X] T033 [P] [US1] Implement structured extraction schema with source-evidence fields in `backend-agent/app/schemas/structured_result_v1.py`
- [X] T034 [US1] Implement structured-result revision endpoint with version control in `backend-java/src/main/java/com/medical/agent/api/StructuredResultController.java`
- [X] T035 [US1] Implement summary-generation endpoint and status tracking in `backend-java/src/main/java/com/medical/agent/api/SummaryController.java`
- [X] T036 [P] [US1] Implement record read API with summary/result payload in `backend-java/src/main/java/com/medical/agent/api/RecordController.java`
- [X] T037 [P] [US1] Implement upload + parse polling UI flow with TanStack Query in `frontend/src/app/records/upload/page.tsx`
- [X] T038 [P] [US1] Implement parse status/progress and source evidence components in `frontend/src/components/parse/ParseJobStatusCard.tsx` and `frontend/src/components/parse/SourceEvidencePanel.tsx`
- [X] T039 [US1] Implement duplicate-upload detection (fingerprint prompt) in `backend-java/src/main/java/com/medical/agent/application/UploadFingerprintService.java`
- [X] T040 [US1] Add manual verification checklist for upload/parse/retry/source-evidence in `tests/manual/us1_upload_parse_checklist.md`

**Checkpoint**: User Story 1 independently delivers upload -> parse -> structured result + summary + revision.

---

## Phase 4: User Story 2 - 生成用药计划草案 (Priority: P2)

**Goal**: 基于已解析记录生成用药计划草案，展示缺失项，并在展示与保存时满足免责声明与二次确认边界。

**Independent Test**: 在已有解析结果的记录上触发生成后，可返回 MED_PLAN 草案；缺失字段有明确提示；页面始终显示免责声明并要求保存前确认。

### Tests for User Story 2

- [X] T041 [P] [US2] Add contract test for `POST /api/v1/records/{recordId}/generate-medication-plan` in `tests/contract/test_med_plan_endpoint.py`
- [X] T042 [P] [US2] Add integration test for med-plan generation success and version increment in `tests/integration/test_us2_med_plan_generation.py`
- [X] T043 [P] [US2] Add integration test for missing-medication-fields fallback messaging in `tests/integration/test_us2_med_plan_missing_fields.py`

### Implementation for User Story 2

- [X] T044 [P] [US2] Implement generation request/result event handlers in `backend-java/src/main/java/com/medical/agent/infrastructure/mq/GenerateRequestPublisher.java` and `backend-java/src/main/java/com/medical/agent/infrastructure/mq/GenerateResultConsumer.java`
- [X] T045 [P] [US2] Implement generated output persistence and versioning service in `backend-java/src/main/java/com/medical/agent/application/GeneratedOutputService.java`
- [X] T046 [P] [US2] Implement med-plan generation worker with provider fallback in `backend-agent/app/workers/generate_worker.py`
- [X] T047 [US2] Implement medication plan API endpoint in `backend-java/src/main/java/com/medical/agent/api/MedicationPlanController.java`
- [X] T048 [P] [US2] Implement medication plan draft UI with disclaimer and missing-field markers in `frontend/src/components/generation/MedicationPlanPanel.tsx`
- [X] T049 [US2] Implement explicit reconfirm-before-save dialog in `frontend/src/components/generation/MedicationPlanConfirmDialog.tsx`
- [X] T050 [US2] Add manual verification for disclaimer visibility and reconfirmation gating in `tests/manual/us2_med_plan_safety_checklist.md`

**Checkpoint**: User Story 2 independently delivers medication-plan draft generation with safety boundaries.

---

## Phase 5: User Story 4 - 数据导出与删除请求 (Priority: P2)

**Goal**: 用户可发起导出/删除请求并查询状态，且全流程满足权限和审计要求。

**Independent Test**: 用户可成功创建导出与删除请求并查询状态；非授权访问被拒绝且有审计记录。

### Tests for User Story 4

- [X] T051 [P] [US4] Add contract test for export request create/status/download endpoints in `tests/contract/test_export_request_endpoints.py`
- [X] T052 [P] [US4] Add contract test for delete request endpoints in `tests/contract/test_delete_request_endpoints.py`
- [X] T053 [P] [US4] Add integration test for RBAC and audit coverage on data-rights requests in `tests/integration/test_us4_data_rights_security.py`

### Implementation for User Story 4

- [X] T054 [P] [US4] Implement export request APIs (create/status/download) in `backend-java/src/main/java/com/medical/agent/api/ExportController.java`
- [X] T055 [P] [US4] Implement delete request APIs in `backend-java/src/main/java/com/medical/agent/api/DeleteController.java`
- [X] T056 [US4] Implement request status and export download URL service in `backend-java/src/main/java/com/medical/agent/application/DataRightsRequestService.java`
- [X] T057 [P] [US4] Implement export/delete request UI actions and export download entry in `frontend/src/components/record/DataRightsActions.tsx`
- [X] T058 [US4] Add manual verification checklist for export/delete request lifecycle in `tests/manual/us4_data_rights_checklist.md`

**Checkpoint**: User Story 4 independently delivers data-rights request entry points and status transparency.

---

## Phase 6: User Story 3 - 时间线回看与按批次管理 (Priority: P3)

**Goal**: 用户可按病症查看时间轴，并按报告日期倒序浏览每次报告的解析后结果与生成内容。

**Independent Test**: 在同一病症下准备多条不同日期报告后，时间轴页可按日期倒序展示；点击节点后可看到该报告的解析后数据、摘要与用药草案。

### Tests for User Story 3

- [X] T059 [P] [US3] Add contract test for timeline list and batch detail endpoints in `tests/contract/test_timeline_endpoints.py`
- [X] T060 [P] [US3] Add integration test for reverse-chronological timeline rendering in `tests/integration/test_us3_timeline_order.py`

### Implementation for User Story 3

- [X] T061 [P] [US3] Add timeline/batch query APIs in `backend-java/src/main/java/com/medical/agent/api/TimelineController.java`
- [X] T062 [P] [US3] Implement timeline query service and mapper in `backend-java/src/main/java/com/medical/agent/application/TimelineService.java`
- [X] T063 [P] [US3] Implement timeline page and batch detail route in `frontend/src/app/timeline/page.tsx` and `frontend/src/app/timeline/[batchId]/page.tsx`
- [X] T064 [US3] Implement timeline grouping components in `frontend/src/components/timeline/TimelineBatchList.tsx`
- [X] T065 [US3] Add manual verification for batch aggregation accuracy in `tests/manual/us3_timeline_batch_checklist.md`

### Follow-up tasks for disease-centric mainline

- [X] T073 [US1] Add disease profile create/select API and bind upload request in `backend-java/src/main/java/com/medical/agent/api/DiseaseProfileController.java` and `backend-java/src/main/java/com/medical/agent/api/AssetController.java`
- [X] T074 [US1] Add disease profile persistence and migration in `backend-java/src/main/resources/db/migration/V3__disease_profile.sql` and `backend-java/src/main/java/com/medical/agent/application/PersistenceService.java`
- [X] T075 [US1] Add disease selector/new-disease UI in upload flow in `frontend/src/app/records/upload/page.tsx`
- [X] T076 [US3] Ensure timeline detail defaults to parsed-result view (not raw file) in `frontend/src/app/timeline/[batchId]/page.tsx` and `backend-java/src/main/java/com/medical/agent/api/RecordController.java`

**Checkpoint**: User Story 3 independently delivers history timeline and batch grouping.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Stabilization and final compliance/performance checks across stories.

- [X] T066 [P] Update API contracts to match implementation in `specs/001-medical-agent-mvp/contracts/openapi.yaml` and `specs/001-medical-agent-mvp/contracts/asyncapi.yaml`
- [X] T067 [P] Add regression tests for idempotency, PHI redaction, and audit completeness in `tests/integration/test_cross_cutting_safety.py`
- [X] T068 [P] Implement object-storage/database encryption configuration and add verification tests in `backend-java/src/main/java/com/medical/agent/config/StorageEncryptionConfig.java` and `tests/integration/test_encryption_controls.py`
- [X] T069 [P] Add KPI instrumentation and dashboard metric checks in `tests/performance/test_kpi_metrics_pipeline.py`
- [X] T070 [P] Add performance smoke test for P95 parse/generate target in `tests/performance/test_p95_pipeline.py`
- [X] T071 Run end-to-end quickstart validation and capture results in `specs/001-medical-agent-mvp/quickstart.md`
- [X] T072 Add release rollback runbook and readiness checklist in `docs/runbook/release-rollback.md` and `tests/manual/release_readiness_checklist.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1) -> Foundational (Phase 2) -> User Story phases (Phase 3/4/5/6) -> Polish (Phase 7)

### User Story Dependency Graph

- US1 (P1) -> US2 (P2)
- US1 (P1) -> US3 (P3)
- US1 (P1) -> US4 (P2)
- US2, US3, US4 can execute in parallel after US1 baseline data flow is stable.

### Within Each User Story

- Tests first (contract/integration), then backend APIs/events/services, then frontend integration, then manual verification.

---

## Parallel Execution Examples

### User Story 1

```bash
# Parallel test authoring
T019, T020, T021, T022, T023, T024, T025

# Parallel backend/agent/frontend implementation
T026, T027, T029, T032, T033, T036, T037, T038
```

### User Story 2

```bash
# Parallel tests
T041, T042, T043

# Parallel implementation
T044, T045, T046, T048
```

### User Story 4

```bash
# Parallel tests
T051, T052, T053

# Parallel implementation
T054, T055, T057
```

### User Story 3

```bash
# Parallel tests
T059, T060

# Parallel implementation
T061, T062, T063
```

---

## Implementation Strategy

### MVP First (US1)

1. Complete Phase 1 and Phase 2.
2. Complete US1 (Phase 3) and validate independently.
3. Demo/deploy MVP upload -> parse -> structured result flow.

### Incremental Delivery

1. Add US2 for medication plan generation and safety confirmation.
2. Add US4 for data-rights request entry points.
3. Add US3 for timeline and batch management.
4. Finish Phase 7 polish before release.

### Parallel Team Strategy

1. Team aligns on foundation tasks (Phase 1-2).
2. After US1 stability checkpoint, split lanes:
   - Engineer A: US2 backend/agent.
   - Engineer B: US2 frontend and safety UX.
   - Engineer C: US4 data-rights APIs and RBAC.
   - Engineer D: US3 timeline backend/frontend.
