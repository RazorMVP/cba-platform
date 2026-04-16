-- V25: Add missing version columns for Hibernate optimistic locking
-- These tables were created by earlier Flyway runs (V1–V24) without a version column,
-- but the corresponding JPA entities declare @Version. Hibernate schema-validation
-- rejects startup unless every @Version field maps to a real DB column.
--
-- All statements use ADD COLUMN IF NOT EXISTS so this migration is safe to re-run
-- or to apply against environments where the column was already added manually.

ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE deposit_account_transactions
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE datatables
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE maker_checkers
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE account_number_formats
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_types
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE global_configurations
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE code_values
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE codes
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
