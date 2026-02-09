CREATE TABLE IF NOT EXISTS parse_job_assets (
  job_id UUID NOT NULL,
  asset_id UUID NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (job_id, asset_id)
);

CREATE INDEX IF NOT EXISTS idx_parse_job_assets_job_id ON parse_job_assets (job_id);
