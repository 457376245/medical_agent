DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'parse_jobs_idempotency_key_key'
  ) THEN
    ALTER TABLE parse_jobs DROP CONSTRAINT parse_jobs_idempotency_key_key;
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_parse_jobs_tenant_idempotency_key
ON parse_jobs (tenant_id, idempotency_key);
