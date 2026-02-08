CREATE TABLE IF NOT EXISTS disease_profiles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  name VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE (tenant_id, user_id, name)
);

ALTER TABLE records
  ADD COLUMN IF NOT EXISTS disease_profile_id UUID;

CREATE INDEX IF NOT EXISTS idx_records_disease_profile_date
  ON records (disease_profile_id, record_date DESC);
