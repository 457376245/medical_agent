# Quickstart: Medical Agent Web MVP

## 1. Prerequisites

- Node.js 20+
- Java 21
- Python 3.11+
- Docker + Docker Compose
- PostgreSQL 15, Redis 7, RabbitMQ, S3-compatible storage (or use compose stack)

## 2. Environment setup

Create env files for each module:

- `frontend/.env.local`
  - `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`
- `backend-java/.env`
  - `DB_URL=jdbc:postgresql://localhost:5432/medical_agent`
  - `DB_USER=...`
  - `DB_PASSWORD=...`
  - `REDIS_URL=redis://localhost:6379`
  - `RABBITMQ_ADDRESSES=localhost:5672`
  - `RABBITMQ_USERNAME=...`
  - `RABBITMQ_PASSWORD=...`
  - `RABBITMQ_VHOST=/`
  - `RABBITMQ_CONNECTION_TIMEOUT=5000`
  - `APP_OSS_ENABLED=true`
  - `APP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com`
  - `APP_OSS_BUCKET=...`
  - `APP_OSS_ACCESS_KEY_ID=...`
  - `APP_OSS_ACCESS_KEY_SECRET=...`
  - `JWT_SECRET=...`
- `backend-agent/.env`
  - `RABBITMQ_URL=amqp://localhost:5672`
  - `OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com`
  - `OSS_BUCKET=...`
  - `OSS_ACCESS_KEY_ID=...`
  - `OSS_ACCESS_KEY_SECRET=...`
  - `GOOGLE_API_KEY=...`
  - `GEMINI_MODEL=gemini-2.5-pro`
  - `GEMINI_FALLBACK_MODEL=gemini-2.5-flash`
  - `GEMINI_TEMPERATURE=0`

## 3. Start infrastructure

Run local infra (PostgreSQL/Redis/RabbitMQ/S3-compatible):

```bash
docker compose up -d
```

## 4. Run services

Backend Java:

```bash
cd backend-java
./mvnw spring-boot:run
```

Backend Agent:

```bash
cd backend-agent
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8090
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## 5. Verify MVP flow

1. Select existing disease or create a new disease profile.
2. Request upload URL: `POST /api/v1/uploads/presign`.
3. Upload file directly to object storage using returned URL.
4. Confirm file registration: `POST /api/v1/assets/complete` (bind to disease profile).
5. Create parse job: `POST /api/v1/parse-jobs` with `Idempotency-Key`.
6. Poll status: `GET /api/v1/parse-jobs/{jobId}` until terminal state.
7. Open disease timeline and click a report node.
8. Load parsed report details: `GET /api/v1/records/{recordId}` (parsed result is primary view).
9. Revise structured result if needed, then trigger generation APIs.

## 6. Safety and compliance checks

- Ensure summary/medication plan screens always show disclaimer text.
- Ensure generated medication plan requires user reconfirmation before save.
- Verify logs contain no raw medical text payload.
- Verify audit events exist for upload, parse, edit, generate, delete/export.

## 7. Contract verification

- Validate REST schema against `specs/001-medical-agent-mvp/contracts/openapi.yaml`.
- Validate event payloads against `specs/001-medical-agent-mvp/contracts/asyncapi.yaml`.
- Keep all API/event/schema versions in `v1` for MVP baseline.

## 8. Validation capture

- Setup scaffolding validated: frontend/backend-java/backend-agent baseline files present.
- Core contracts updated for timeline, structured-result revision, summary generation, export/delete with download endpoint.
- Manual checklists created for US1/US2/US3/US4 and release readiness.
