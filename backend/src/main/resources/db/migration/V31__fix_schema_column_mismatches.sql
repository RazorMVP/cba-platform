-- V26: Fix column name mismatches between JPA entities and tables created by earlier Docker sessions.
-- The Docker backend ran Flyway V1–V9 creating some tables with column names that differ
-- from what V19 (accounting_rules_provisioning) and other later migrations expected.
-- All statements use ADD COLUMN IF NOT EXISTS for idempotency.

-- ── accounting_rules ────────────────────────────────────────────────────────
-- Entity uses debit_account_id / credit_account_id; old table used account_to_debit / account_to_credit.
ALTER TABLE accounting_rules
    ADD COLUMN IF NOT EXISTS debit_account_id  UUID,
    ADD COLUMN IF NOT EXISTS credit_account_id UUID;

-- ── maker_checkers ──────────────────────────────────────────────────────────
-- Entity uses made_by_user_id / checked_by_user_id / entity_id;
-- old table used maker_id / checker_id / resource_id.
ALTER TABLE maker_checkers
    ADD COLUMN IF NOT EXISTS made_by_user_id    UUID,
    ADD COLUMN IF NOT EXISTS checked_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS entity_id          UUID;

-- ── global_configurations ───────────────────────────────────────────────────
-- Old table stored a single value column; entity expects typed boolean/numeric/string columns
-- plus is_enabled instead of enabled.
ALTER TABLE global_configurations
    ADD COLUMN IF NOT EXISTS boolean_value BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_enabled    BOOLEAN,
    ADD COLUMN IF NOT EXISTS numeric_value NUMERIC(19,4);

-- ── codes ───────────────────────────────────────────────────────────────────
-- Old table used system_defined; entity expects is_system_defined.
ALTER TABLE codes
    ADD COLUMN IF NOT EXISTS is_system_defined BOOLEAN;

-- ── code_values ─────────────────────────────────────────────────────────────
-- Old table used order_position / active; entity expects code_value_order / is_active.
ALTER TABLE code_values
    ADD COLUMN IF NOT EXISTS code_value_order INT,
    ADD COLUMN IF NOT EXISTS is_active        BOOLEAN;

-- ── payment_types ────────────────────────────────────────────────────────────
-- Old table used order_position; entity expects code_value_position / is_system_defined.
ALTER TABLE payment_types
    ADD COLUMN IF NOT EXISTS code_value_position INT,
    ADD COLUMN IF NOT EXISTS is_system_defined   BOOLEAN;

-- ── datatables ───────────────────────────────────────────────────────────────
-- Entity has updatedAt mapped to updated_at; old table missing that column.
ALTER TABLE datatables
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- ── documents ────────────────────────────────────────────────────────────────
-- Old table used parent_entity_type / parent_entity_id / size / type / location;
-- entity expects entity_type / entity_id / file_size / content_type / storage_path.
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS entity_type  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS entity_id    UUID,
    ADD COLUMN IF NOT EXISTS file_size    BIGINT,
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS storage_path VARCHAR(512);

-- ── roles ─────────────────────────────────────────────────────────────────────
-- Old table used disabled; entity expects is_disabled.
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS is_disabled BOOLEAN;

-- ── deposit_account_transactions ──────────────────────────────────────────────
-- Old table missing reference_number, note, and updated_at columns.
ALTER TABLE deposit_account_transactions
    ADD COLUMN IF NOT EXISTS reference_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS note             TEXT,
    ADD COLUMN IF NOT EXISTS updated_at       TIMESTAMPTZ;
