-- Account lifecycle state machine (Session 72)
-- Adds SUBMITTED, APPROVED, REJECTED states to account lifecycle.
-- Existing ACTIVE accounts remain ACTIVE (no data change needed).
-- Demo data accounts are already ACTIVE and stay that way.
-- New accounts created after this migration start as SUBMITTED.

-- Extend the status column to accommodate the longest new value (SUBMITTED = 9 chars).
-- Current VARCHAR(20) already fits; this comment documents the intent.
-- No DDL change needed — VARCHAR(20) is sufficient for all status values.

-- Ensure any lingering test/demo accounts that may have been created with
-- the old default (ACTIVE) remain valid.
SELECT 1; -- no-op migration; logic enforced at application layer
