ALTER TABLE patient_memory_entries ADD COLUMN IF NOT EXISTS valid_from TIMESTAMP;
ALTER TABLE patient_memory_entries ADD COLUMN IF NOT EXISTS valid_to TIMESTAMP;
ALTER TABLE patient_memory_entries ADD COLUMN IF NOT EXISTS is_current BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE patient_memory_entries
SET valid_from = COALESCE(confirmed_at, created_at)
WHERE status = 'CONFIRMED' AND valid_from IS NULL;

CREATE INDEX IF NOT EXISTS idx_patient_memory_entries_current
  ON patient_memory_entries (patient_id, field_path, is_current, updated_at DESC);
