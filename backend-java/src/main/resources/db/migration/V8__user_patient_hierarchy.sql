-- ============================================================
-- V8: User-Patient hierarchy
-- A user (login account) can manage multiple patients (family members).
-- Each patient owns their own disease profiles, records, etc.
-- ============================================================

-- 1. Extend users table with auth columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(256);
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(128);

-- 2. Create patients table
CREATE TABLE IF NOT EXISTS patients (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(128) NOT NULL,
  relationship VARCHAR(32) NOT NULL DEFAULT '本人',
  gender VARCHAR(16),
  birth_date DATE,
  notes TEXT,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE (tenant_id, user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_patients_user ON patients (user_id);

-- 3. Add patient_id column to existing tables
ALTER TABLE disease_profiles ADD COLUMN IF NOT EXISTS patient_id UUID;
ALTER TABLE records ADD COLUMN IF NOT EXISTS patient_id UUID;
ALTER TABLE report_categories ADD COLUMN IF NOT EXISTS patient_id UUID;
ALTER TABLE data_rights_requests ADD COLUMN IF NOT EXISTS patient_id UUID;

-- 4. Seed default user (matches ScopeConstants.DEFAULT_USER_ID) if not present
INSERT INTO users (id, tenant_id, account, status, role, created_at, updated_at, password_hash, display_name)
VALUES (
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  'default@local',
  'ACTIVE',
  'OWNER',
  now(), now(),
  NULL,
  '默认用户'
) ON CONFLICT (tenant_id, account) DO NOTHING;

-- 5. Create default "self" patient for the default user
INSERT INTO patients (id, tenant_id, user_id, name, relationship, is_default, created_at, updated_at)
VALUES (
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002',
  '本人',
  '本人',
  true,
  now(), now()
) ON CONFLICT (tenant_id, user_id, name) DO NOTHING;

-- 6. Backfill patient_id on existing rows
UPDATE disease_profiles SET patient_id = '00000000-0000-0000-0000-000000000003' WHERE patient_id IS NULL;
UPDATE records SET patient_id = '00000000-0000-0000-0000-000000000003' WHERE patient_id IS NULL;
UPDATE report_categories SET patient_id = '00000000-0000-0000-0000-000000000003' WHERE patient_id IS NULL;
UPDATE data_rights_requests SET patient_id = '00000000-0000-0000-0000-000000000003' WHERE patient_id IS NULL;

-- 7. Update uniqueness constraints to use patient_id instead of user_id
ALTER TABLE disease_profiles DROP CONSTRAINT IF EXISTS disease_profiles_tenant_id_user_id_name_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_disease_profiles_tenant_patient_name
  ON disease_profiles (tenant_id, patient_id, name);

ALTER TABLE report_categories DROP CONSTRAINT IF EXISTS report_categories_tenant_id_user_id_name_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_report_categories_tenant_patient_name
  ON report_categories (tenant_id, patient_id, name);

-- 8. Add indexes on patient_id
CREATE INDEX IF NOT EXISTS idx_disease_profiles_patient ON disease_profiles (patient_id);
CREATE INDEX IF NOT EXISTS idx_records_patient ON records (patient_id);
