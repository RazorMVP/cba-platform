-- V29: Fix all remaining column mismatches found by systematic entity vs DB comparison.
-- These columns exist in JPA entities but were absent from tables created by earlier
-- Docker sessions running an older schema. All statements use IF NOT EXISTS.

-- ── offices ──────────────────────────────────────────────────────────────────
ALTER TABLE offices
    ADD COLUMN IF NOT EXISTS active      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- ── centers ─────────────────────────────────────────────────────────────────
ALTER TABLE centers
    ADD COLUMN IF NOT EXISTS meeting_day_of_week VARCHAR(10);

-- ── client_addresses ─────────────────────────────────────────────────────────
ALTER TABLE client_addresses
    ADD COLUMN IF NOT EXISTS address_line_1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_line_2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_line_3 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_type   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS country_code   VARCHAR(3),
    ADD COLUMN IF NOT EXISTS state_province VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_active      BOOLEAN;

-- ── client_identifiers ────────────────────────────────────────────────────────
ALTER TABLE client_identifiers
    ADD COLUMN IF NOT EXISTS document_type_code_value_id UUID,
    ADD COLUMN IF NOT EXISTS expiry_date                 DATE,
    ADD COLUMN IF NOT EXISTS is_active                   BOOLEAN;

-- ── cob_job_history ───────────────────────────────────────────────────────────
ALTER TABLE cob_job_history
    ADD COLUMN IF NOT EXISTS spring_batch_job_execution_id BIGINT;

-- ── collaterals ───────────────────────────────────────────────────────────────
ALTER TABLE collaterals
    ADD COLUMN IF NOT EXISTS collateral_type_code_value_id UUID;

-- ── collection_sheet_items ────────────────────────────────────────────────────
ALTER TABLE collection_sheet_items
    ADD COLUMN IF NOT EXISTS collected_amount NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS due_amount       NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS is_collected     BOOLEAN;

-- ── datatable_column_definitions ─────────────────────────────────────────────
ALTER TABLE datatable_column_definitions
    ADD COLUMN IF NOT EXISTS column_length INT,
    ADD COLUMN IF NOT EXISTS is_nullable   BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_unique     BOOLEAN;

-- ── fixed_deposit_accounts ────────────────────────────────────────────────────
ALTER TABLE fixed_deposit_accounts
    ADD COLUMN IF NOT EXISTS nominal_annual_interest_rate NUMERIC(19,6);

-- ── fixed_deposit_products ────────────────────────────────────────────────────
ALTER TABLE fixed_deposit_products
    ADD COLUMN IF NOT EXISTS calculation_type    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS compounding_period  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS posting_period      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pre_penalty_applicable BOOLEAN,
    ADD COLUMN IF NOT EXISTS pre_penalty_interest   NUMERIC(19,6);

-- ── floating_rates ────────────────────────────────────────────────────────────
ALTER TABLE floating_rates
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID;

-- ── glim_accounts ─────────────────────────────────────────────────────────────
ALTER TABLE glim_accounts
    ADD COLUMN IF NOT EXISTS currency_code    VARCHAR(3),
    ADD COLUMN IF NOT EXISTS individual_amount NUMERIC(19,4);

-- ── group_members ─────────────────────────────────────────────────────────────
-- Old sessions created group_members as a composite-PK junction table (no id).
-- Add the id column so Hibernate validate passes; full PK promotion requires
-- a manual table rebuild on old volumes (DROP+recreate is safest).
ALTER TABLE group_members
    ADD COLUMN IF NOT EXISTS id          UUID DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS joining_date DATE;

-- ── guarantors ────────────────────────────────────────────────────────────────
ALTER TABLE guarantors
    ADD COLUMN IF NOT EXISTS guarantor_type   VARCHAR(30) NOT NULL DEFAULT 'EXTERNAL',
    ADD COLUMN IF NOT EXISTS first_name       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS email            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mobile_number    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS city             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS country          VARCHAR(100);

-- ── hooks ─────────────────────────────────────────────────────────────────────
ALTER TABLE hooks
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS hook_type    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payload_url  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS secret_key   VARCHAR(255);

-- ── journal_entries ───────────────────────────────────────────────────────────
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS is_reversed      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS posted_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS description      VARCHAR(500);

-- ── loan_reaging_requests ─────────────────────────────────────────────────────
ALTER TABLE loan_reaging_requests
    ADD COLUMN IF NOT EXISTS status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS is_preview        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requested_on_date DATE,
    ADD COLUMN IF NOT EXISTS approved_on_date  DATE,
    ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ;

-- ── loan_reamortization_requests ──────────────────────────────────────────────
ALTER TABLE loan_reamortization_requests
    ADD COLUMN IF NOT EXISTS status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS requested_on_date DATE,
    ADD COLUMN IF NOT EXISTS approved_on_date  DATE,
    ADD COLUMN IF NOT EXISTS comment           TEXT,
    ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ;

-- ── loan_reschedule_requests ──────────────────────────────────────────────────
ALTER TABLE loan_reschedule_requests
    ADD COLUMN IF NOT EXISTS adjust_repayment_date DATE,
    ADD COLUMN IF NOT EXISTS extra_terms           INT,
    ADD COLUMN IF NOT EXISTS grace_on_interest     INT,
    ADD COLUMN IF NOT EXISTS grace_on_principal    INT,
    ADD COLUMN IF NOT EXISTS recalculate_interest  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS requested_on_date     DATE,
    ADD COLUMN IF NOT EXISTS comment               TEXT;

-- ── notes ─────────────────────────────────────────────────────────────────────
ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID;

-- ── open_banking_consents ─────────────────────────────────────────────────────
ALTER TABLE open_banking_consents
    ADD COLUMN IF NOT EXISTS scope TEXT;

-- ── platform_users ────────────────────────────────────────────────────────────
ALTER TABLE platform_users
    ADD COLUMN IF NOT EXISTS first_name   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS role_name    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- ── provisioning_criteria ─────────────────────────────────────────────────────
ALTER TABLE provisioning_criteria
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- ── recurring_deposit_accounts ────────────────────────────────────────────────
ALTER TABLE recurring_deposit_accounts
    ADD COLUMN IF NOT EXISTS deposit_period                      INT,
    ADD COLUMN IF NOT EXISTS deposit_period_type                 VARCHAR(30),
    ADD COLUMN IF NOT EXISTS mandatory_recommended_deposit_amount NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS maturity_instruction                VARCHAR(50),
    ADD COLUMN IF NOT EXISTS nominal_annual_interest_rate        NUMERIC(19,6);

-- ── recurring_deposit_products ────────────────────────────────────────────────
ALTER TABLE recurring_deposit_products
    ADD COLUMN IF NOT EXISTS calculation_type                     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS compounding_period                   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS deposit_frequency                    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS mandatory_recommended_deposit_amount NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS max_deposit_term_type                VARCHAR(30),
    ADD COLUMN IF NOT EXISTS min_deposit_term_type                VARCHAR(30),
    ADD COLUMN IF NOT EXISTS posting_period                       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pre_penalty_applicable               BOOLEAN,
    ADD COLUMN IF NOT EXISTS pre_penalty_interest                 NUMERIC(19,6);

-- ── reports ────────────────────────────────────────────────────────────────────
-- 'category' and 'enabled' are entity field names; old sessions have 'report_category' / 'use_report'
ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS category VARCHAR(100) NOT NULL DEFAULT 'General',
    ADD COLUMN IF NOT EXISTS enabled  BOOLEAN      NOT NULL DEFAULT TRUE;

-- ── report_parameters ─────────────────────────────────────────────────────────
ALTER TABLE report_parameters
    ADD COLUMN IF NOT EXISTS required   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sort_order INT     NOT NULL DEFAULT 0;

-- ── share_account_transactions ────────────────────────────────────────────────
ALTER TABLE share_account_transactions
    ADD COLUMN IF NOT EXISTS total_amount NUMERIC(19,4);

-- ── share_products ────────────────────────────────────────────────────────────
ALTER TABLE share_products
    ADD COLUMN IF NOT EXISTS allow_dividends_for_inactive      BOOLEAN,
    ADD COLUMN IF NOT EXISTS lock_in_period_frequency          INT,
    ADD COLUMN IF NOT EXISTS lock_in_period_frequency_type     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS maximum_shares                    BIGINT,
    ADD COLUMN IF NOT EXISTS minimum_active_period_frequency   INT,
    ADD COLUMN IF NOT EXISTS minimum_active_period_frequency_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS minimum_shares                    BIGINT;

-- ── platform_users ────────────────────────────────────────────────────────────
-- 'enabled' is the entity field name; old sessions have 'is_active' instead
ALTER TABLE platform_users
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- ── staff ─────────────────────────────────────────────────────────────────────
-- 'active' is the entity field name; old sessions have 'is_active' instead
ALTER TABLE staff
    ADD COLUMN IF NOT EXISTS active     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name  VARCHAR(100);

-- ── tax_groups ────────────────────────────────────────────────────────────────
ALTER TABLE tax_groups
    ADD COLUMN IF NOT EXISTS start_date DATE;
