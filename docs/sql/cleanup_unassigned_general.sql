-- Cleanup script for historical placeholder disease profiles:
-- - "Unassigned"
-- - "General"
--
-- Safe behavior:
-- 1) For records linked to placeholder profiles, try to rebind by title prefix
--    to an existing real disease profile under same tenant/user.
-- 2) If no match is found, set disease_profile_id = NULL (unclassified).
-- 3) Fix dangling disease_profile_id references (profile row missing).
-- 4) Delete placeholder profiles only when no records reference them.
--
-- IMPORTANT:
-- - Default tenant/user IDs below match current app defaults.
-- - If your environment differs, change both UUID constants first.
-- - Run in PostgreSQL.

-- ====== PREVIEW ======
WITH params AS (
  SELECT
    '00000000-0000-0000-0000-000000000001'::uuid AS tenant_id,
    '00000000-0000-0000-0000-000000000002'::uuid AS user_id
)
SELECT dp.id, dp.name, COUNT(r.id) AS linked_records
FROM disease_profiles dp
LEFT JOIN records r ON r.disease_profile_id = dp.id
JOIN params p ON dp.tenant_id = p.tenant_id AND dp.user_id = p.user_id
WHERE lower(dp.name) IN ('unassigned', 'general')
GROUP BY dp.id, dp.name
ORDER BY dp.name;

BEGIN;

-- Step 1: Rebind placeholder-linked records to real profiles by title prefix.
WITH params AS (
  SELECT
    '00000000-0000-0000-0000-000000000001'::uuid AS tenant_id,
    '00000000-0000-0000-0000-000000000002'::uuid AS user_id
),
placeholder_profiles AS (
  SELECT dp.id, dp.tenant_id, dp.user_id
  FROM disease_profiles dp
  JOIN params p ON dp.tenant_id = p.tenant_id AND dp.user_id = p.user_id
  WHERE lower(dp.name) IN ('unassigned', 'general')
),
matched_targets AS (
  SELECT
    r.id AS record_id,
    dp_target.id AS target_profile_id
  FROM records r
  JOIN placeholder_profiles pp ON r.disease_profile_id = pp.id
  JOIN disease_profiles dp_target
    ON dp_target.tenant_id = pp.tenant_id
   AND dp_target.user_id = pp.user_id
   AND lower(dp_target.name) = lower(split_part(coalesce(r.title, ''), '-', 1))
   AND lower(dp_target.name) NOT IN ('unassigned', 'general')
)
UPDATE records r
SET disease_profile_id = mt.target_profile_id
FROM matched_targets mt
WHERE r.id = mt.record_id;

-- Step 2: Remaining placeholder-linked records become unclassified.
WITH params AS (
  SELECT
    '00000000-0000-0000-0000-000000000001'::uuid AS tenant_id,
    '00000000-0000-0000-0000-000000000002'::uuid AS user_id
)
UPDATE records r
SET disease_profile_id = NULL
WHERE r.tenant_id = (SELECT tenant_id FROM params)
  AND r.user_id = (SELECT user_id FROM params)
  AND EXISTS (
    SELECT 1
    FROM disease_profiles dp
    WHERE dp.id = r.disease_profile_id
      AND dp.tenant_id = (SELECT tenant_id FROM params)
      AND dp.user_id = (SELECT user_id FROM params)
      AND lower(dp.name) IN ('unassigned', 'general')
  );

-- Step 3: Fix dangling profile references.
WITH params AS (
  SELECT
    '00000000-0000-0000-0000-000000000001'::uuid AS tenant_id,
    '00000000-0000-0000-0000-000000000002'::uuid AS user_id
)
UPDATE records r
SET disease_profile_id = NULL
WHERE r.tenant_id = (SELECT tenant_id FROM params)
  AND r.user_id = (SELECT user_id FROM params)
  AND r.disease_profile_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM disease_profiles dp WHERE dp.id = r.disease_profile_id
  );

-- Step 4: Delete empty placeholder profiles.
WITH params AS (
  SELECT
    '00000000-0000-0000-0000-000000000001'::uuid AS tenant_id,
    '00000000-0000-0000-0000-000000000002'::uuid AS user_id
)
DELETE FROM disease_profiles dp
WHERE dp.tenant_id = (SELECT tenant_id FROM params)
  AND dp.user_id = (SELECT user_id FROM params)
  AND lower(dp.name) IN ('unassigned', 'general')
  AND NOT EXISTS (
    SELECT 1 FROM records r WHERE r.disease_profile_id = dp.id
  );

COMMIT;

-- ====== POST CHECK ======
WITH params AS (
  SELECT
    '00000000-0000-0000-0000-000000000001'::uuid AS tenant_id,
    '00000000-0000-0000-0000-000000000002'::uuid AS user_id
)
SELECT
  SUM(CASE WHEN r.disease_profile_id IS NULL THEN 1 ELSE 0 END) AS unclassified_records,
  SUM(CASE WHEN r.disease_profile_id IS NOT NULL THEN 1 ELSE 0 END) AS classified_records
FROM records r
JOIN params p ON r.tenant_id = p.tenant_id AND r.user_id = p.user_id;

