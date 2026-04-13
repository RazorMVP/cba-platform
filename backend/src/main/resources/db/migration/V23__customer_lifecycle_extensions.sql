-- V23: Customer lifecycle extensions (Session 49)
-- Adds lifecycle dates, lifecycle reasons, staff/office assignment,
-- and inter-branch transfer fields to the customers table.
-- Matches new fields on Customer.java entity.

ALTER TABLE customers
    -- Lifecycle dates
    ADD COLUMN IF NOT EXISTS activation_date   DATE,
    ADD COLUMN IF NOT EXISTS closure_date      DATE,
    ADD COLUMN IF NOT EXISTS rejection_date    DATE,
    ADD COLUMN IF NOT EXISTS withdrawal_date   DATE,

    -- Lifecycle reasons
    ADD COLUMN IF NOT EXISTS closure_reason    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS rejection_reason  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS withdrawal_reason VARCHAR(500),

    -- Staff / office assignment
    ADD COLUMN IF NOT EXISTS staff_id          UUID,
    ADD COLUMN IF NOT EXISTS office_id         UUID,

    -- Inter-branch transfer
    ADD COLUMN IF NOT EXISTS transfer_to_office_id UUID,
    ADD COLUMN IF NOT EXISTS transfer_date         DATE,
    ADD COLUMN IF NOT EXISTS transfer_note         VARCHAR(500);

-- Index to speed up "find all customers pending transfer" queries
CREATE INDEX IF NOT EXISTS idx_customers_transfer_to_office
    ON customers (transfer_to_office_id)
    WHERE transfer_to_office_id IS NOT NULL;

-- Index to speed up staff workload queries
CREATE INDEX IF NOT EXISTS idx_customers_staff_id
    ON customers (staff_id)
    WHERE staff_id IS NOT NULL;

-- Update the KYC status column to allow the new enum values.
-- PostgreSQL CHECK constraints (if any) need to be updated.
-- The existing column is VARCHAR(20) without a check constraint,
-- so the new values (REJECTED, WITHDRAWN, TRANSFER_IN_PROGRESS)
-- are already accepted — no DDL change required for the column itself.

-- Backfill activation_date for existing ACTIVE / SUSPENDED / CLOSED customers
-- where we don't have the original date — use created_at as a safe fallback.
UPDATE customers
SET activation_date = created_at::DATE
WHERE kyc_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
  AND activation_date IS NULL;
