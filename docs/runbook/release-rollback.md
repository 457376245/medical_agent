# Release Rollback Runbook

## Trigger Conditions

- Parse/generate error rate exceeds threshold.
- Queue lag breaches SLO.
- Security verification fails.

## Rollback Steps

1. Disable newly deployed feature flags.
2. Revert traffic to previous stable version.
3. Pause non-critical async consumers if backlog risk is high.
4. Validate core APIs and event consumption health.
5. Confirm audit logging and security controls remain active.

## Verification Checklist

- Core upload/parse APIs healthy.
- No contract incompatibility alerts.
- Queue lag returns to baseline.
- Incident report opened and linked to deployment record.
