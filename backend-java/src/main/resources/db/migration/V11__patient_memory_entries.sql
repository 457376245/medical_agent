CREATE TABLE IF NOT EXISTS patient_memory_entries (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  patient_id UUID NOT NULL,
  disease_profile_id UUID,
  record_id UUID,
  conversation_thread_id VARCHAR(128),
  turn_id VARCHAR(128),
  memory_type VARCHAR(64) NOT NULL,
  field_path VARCHAR(128) NOT NULL,
  value_text TEXT,
  value_json TEXT,
  evidence_text TEXT,
  source_type VARCHAR(32) NOT NULL DEFAULT 'CONVERSATION',
  source_ref VARCHAR(255),
  confidence DOUBLE PRECISION,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
  rejection_reason TEXT,
  confirmed_at TIMESTAMP,
  supersedes_memory_id UUID,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_patient_memory_entries_patient_status
  ON patient_memory_entries (patient_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_patient_memory_entries_profile_status
  ON patient_memory_entries (disease_profile_id, status, updated_at DESC);
