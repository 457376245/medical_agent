CREATE TABLE IF NOT EXISTS data_rights_requests (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  record_id UUID NOT NULL,
  request_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  download_url TEXT,
  expire_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_data_rights_requests_user_created
  ON data_rights_requests (user_id, created_at DESC);
