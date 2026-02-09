CREATE TABLE IF NOT EXISTS report_categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE (tenant_id, user_id, name)
);

ALTER TABLE records
  ALTER COLUMN source_type TYPE VARCHAR(64);

INSERT INTO report_categories (id, tenant_id, user_id, name, created_at, updated_at)
SELECT
  (
    substr(md5(r.tenant_id::text || ':' || r.user_id::text || ':' || r.source_type), 1, 8) || '-' ||
    substr(md5(r.tenant_id::text || ':' || r.user_id::text || ':' || r.source_type), 9, 4) || '-' ||
    substr(md5(r.tenant_id::text || ':' || r.user_id::text || ':' || r.source_type), 13, 4) || '-' ||
    substr(md5(r.tenant_id::text || ':' || r.user_id::text || ':' || r.source_type), 17, 4) || '-' ||
    substr(md5(r.tenant_id::text || ':' || r.user_id::text || ':' || r.source_type), 21, 12)
  )::uuid AS id,
  r.tenant_id,
  r.user_id,
  r.source_type,
  now(),
  now()
FROM records r
WHERE r.source_type IS NOT NULL
  AND btrim(r.source_type) <> ''
GROUP BY r.tenant_id, r.user_id, r.source_type
ON CONFLICT (tenant_id, user_id, name) DO NOTHING;
