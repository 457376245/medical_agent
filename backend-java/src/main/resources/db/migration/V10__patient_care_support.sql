CREATE TABLE IF NOT EXISTS patient_care_profiles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  patient_id UUID NOT NULL,
  diagnosed_conditions_json TEXT,
  current_medications_json TEXT,
  allergies_json TEXT,
  abnormal_baseline_json TEXT,
  doctor_instructions TEXT,
  care_goals_json TEXT,
  red_flag_notes_json TEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE (tenant_id, patient_id)
);

CREATE INDEX IF NOT EXISTS idx_patient_care_profiles_patient ON patient_care_profiles (patient_id);

CREATE TABLE IF NOT EXISTS follow_up_tasks (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  patient_id UUID NOT NULL,
  disease_profile_id UUID,
  record_id UUID,
  title VARCHAR(255) NOT NULL,
  due_date DATE,
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  notes TEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_follow_up_tasks_patient_status
  ON follow_up_tasks (patient_id, status, due_date);

CREATE TABLE IF NOT EXISTS symptom_logs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  patient_id UUID NOT NULL,
  disease_profile_id UUID,
  label VARCHAR(128) NOT NULL,
  value VARCHAR(128),
  unit VARCHAR(32),
  alert_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  notes TEXT,
  recorded_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_symptom_logs_patient_recorded_at
  ON symptom_logs (patient_id, recorded_at DESC);
