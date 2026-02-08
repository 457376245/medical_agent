CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  account VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  role VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE (tenant_id, account)
);

CREATE TABLE IF NOT EXISTS records (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  record_date DATE NOT NULL,
  title VARCHAR(255),
  source_type VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS assets (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  record_id UUID NOT NULL,
  object_key VARCHAR(512) NOT NULL UNIQUE,
  file_type VARCHAR(16) NOT NULL,
  file_size BIGINT NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS parse_jobs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  record_id UUID NOT NULL,
  status VARCHAR(32) NOT NULL,
  progress SMALLINT NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_code VARCHAR(64),
  trace_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS structured_results (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  job_id UUID NOT NULL,
  record_id UUID NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  payload_json JSONB NOT NULL,
  confidence_score NUMERIC(5,4),
  revision INT NOT NULL DEFAULT 1,
  is_user_edited BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS generated_outputs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  record_id UUID NOT NULL,
  type VARCHAR(16) NOT NULL,
  version INT NOT NULL,
  content TEXT NOT NULL,
  model_meta JSONB,
  requires_confirmation BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL,
  UNIQUE (record_id, type, version)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  error_code VARCHAR(64),
  request_id VARCHAR(64) NOT NULL,
  trace_id VARCHAR(64),
  ip VARCHAR(64),
  created_at TIMESTAMP NOT NULL
);
