ALTER TABLE patient_care_profiles
  ADD COLUMN IF NOT EXISTS personal_context_json TEXT;
