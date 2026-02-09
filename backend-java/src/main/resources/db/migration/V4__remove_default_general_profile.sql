-- Stop using a synthetic "General" disease profile as fallback.
-- Rebind historical records linked to General:
-- 1) try to map by report title prefix to an existing disease profile
-- 2) otherwise mark as unclassified (disease_profile_id = null)

WITH general_profiles AS (
  SELECT id, tenant_id, user_id
  FROM disease_profiles
  WHERE lower(name) = 'general'
),
target_map AS (
  SELECT r.id AS record_id, dp_target.id AS target_profile_id
  FROM records r
  JOIN general_profiles gp
    ON r.disease_profile_id = gp.id
   AND r.tenant_id = gp.tenant_id
  JOIN disease_profiles dp_target
    ON dp_target.tenant_id = gp.tenant_id
   AND dp_target.user_id = gp.user_id
   AND dp_target.id <> gp.id
   AND lower(dp_target.name) = lower(split_part(coalesce(r.title, ''), '-', 1))
)
UPDATE records r
SET disease_profile_id = tm.target_profile_id
FROM target_map tm
WHERE r.id = tm.record_id;

UPDATE records r
SET disease_profile_id = null
FROM disease_profiles gp
WHERE r.disease_profile_id = gp.id
  AND lower(gp.name) = 'general';

DELETE FROM disease_profiles gp
WHERE lower(gp.name) = 'general'
  AND NOT EXISTS (
    SELECT 1 FROM records r WHERE r.disease_profile_id = gp.id
  );
