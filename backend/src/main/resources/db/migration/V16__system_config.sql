-- ═══════════════════════════════════════════════════════════════════
-- V16__system_config.sql — System Configuration Resources
-- Covers: Codes, GlobalConfig, Funds, PaymentTypes, FloatingRates,
--         Taxes, Rates, AccountingRules, Provisioning,
--         AccountNumberFormats, Roles, Permissions
-- ═══════════════════════════════════════════════════════════════════

-- ── codes & code_values ───────────────────────────────────────────────
-- Mifos-style configurable lookup lists
CREATE TABLE codes (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    code_name       VARCHAR(100)    UNIQUE NOT NULL,
    system_defined  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE code_values (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    code_id         UUID            NOT NULL REFERENCES codes(id),
    code_value      VARCHAR(200)    NOT NULL,
    code_description TEXT,
    code_score      INT             NOT NULL DEFAULT 0,
    order_position  INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    mandatory       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (code_id, code_value)
);

CREATE INDEX idx_code_values_code ON code_values(code_id);

-- Seed system-defined codes
INSERT INTO codes (code_name, system_defined) VALUES
    ('Gender', TRUE),
    ('ClientClassification', TRUE),
    ('ClientType', TRUE),
    ('LoanCollateralType', TRUE),
    ('LoanPurpose', TRUE),
    ('PaymentType', TRUE),
    ('Country', TRUE),
    ('State', TRUE),
    ('YesNo', TRUE);

-- ── global_configurations ─────────────────────────────────────────────
CREATE TABLE global_configurations (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)    UNIQUE NOT NULL,
    value           BIGINT          NOT NULL DEFAULT 0,
    string_value    VARCHAR(500),
    enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    trap_door       BOOLEAN         NOT NULL DEFAULT FALSE,
    description     TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

INSERT INTO global_configurations (name, value, enabled, description) VALUES
    ('maker-checker', 0, FALSE, 'Enable maker-checker for state-changing operations'),
    ('amazon-S3', 0, FALSE, 'Use Amazon S3 for document storage'),
    ('rounding-mode', 6, TRUE, 'Rounding mode for financial calculations'),
    ('backdate-penalties-enabled', 0, FALSE, 'Allow backdated penalty charges'),
    ('organisation-start-date', 0, FALSE, 'Organisation configured start date'),
    ('paymentTypeApplicableForDisbursement', 0, FALSE, 'Payment types applicable for disbursement'),
    ('interestChargedFromDateSameAsDisbursementDate', 0, FALSE, 'Charge interest from disbursement date'),
    ('days-in-year-for-interest', 360, TRUE, 'Days in year used for interest calculation'),
    ('days-in-month-type-for-interest', 30, TRUE, 'Days in month type for interest calculation'),
    ('reschedule-repayments-on-holidays', 0, FALSE, 'Reschedule repayments falling on holidays'),
    ('allow-transactions-on-non-working-days', 0, FALSE, 'Allow transactions on non-working days'),
    ('constraint_approach_for_datatables', 0, FALSE, 'Use constraint approach for datatables'),
    ('penalty-wait-period', 2, TRUE, 'Days to wait before applying penalty'),
    ('grace-on-penalty-period', 0, FALSE, 'Enable grace period on penalties'),
    ('loan-reschedule-is-available', 1, TRUE, 'Loan reschedule functionality is available'),
    ('is-interest-to-be-recovered-first-when-greater-than-emi', 0, FALSE, 'Recover interest first when > EMI'),
    ('is-principal-compounding-disabled-for-overdue-loans', 0, FALSE, 'Disable principal compounding on overdue loans');

-- ── funds ─────────────────────────────────────────────────────────────
CREATE TABLE funds (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    UNIQUE NOT NULL,
    external_id VARCHAR(100),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version     BIGINT          NOT NULL DEFAULT 0
);

-- ── payment_types ─────────────────────────────────────────────────────
CREATE TABLE payment_types (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)    UNIQUE NOT NULL,
    description     VARCHAR(500),
    is_cash_payment BOOLEAN         NOT NULL DEFAULT FALSE,
    order_position  INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

INSERT INTO payment_types (name, description, is_cash_payment, order_position) VALUES
    ('Cash', 'Physical cash payment', TRUE, 1),
    ('Mobile Money', 'Mobile money transfer (M-Pesa, Airtel Money, etc.)', FALSE, 2),
    ('Bank Transfer', 'Direct bank transfer / SWIFT / SEPA', FALSE, 3),
    ('Cheque', 'Paper cheque payment', FALSE, 4),
    ('Card', 'Debit or credit card payment', FALSE, 5),
    ('Direct Debit', 'Automated direct debit / standing order', FALSE, 6);

-- ── floating_rates ────────────────────────────────────────────────────
CREATE TABLE floating_rates (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    UNIQUE NOT NULL,
    is_base_lending_rate BOOLEAN    NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE floating_rate_periods (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    floating_rate_id        UUID            NOT NULL REFERENCES floating_rates(id),
    from_date               DATE            NOT NULL,
    interest_rate           NUMERIC(8,4)    NOT NULL,
    is_differential_to_base_lending_rate BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_floating_rate_periods_rate ON floating_rate_periods(floating_rate_id);

-- ── tax_components ────────────────────────────────────────────────────
CREATE TABLE tax_components (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    NOT NULL,
    percentage          NUMERIC(8,4)    NOT NULL,
    debit_account_type  VARCHAR(20),
    debit_account_id    UUID            REFERENCES gl_accounts(id),
    credit_account_type VARCHAR(20),
    credit_account_id   UUID            REFERENCES gl_accounts(id),
    start_date          DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

-- ── tax_groups ────────────────────────────────────────────────────────
CREATE TABLE tax_groups (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version     BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE tax_group_mappings (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_group_id    UUID            NOT NULL REFERENCES tax_groups(id),
    tax_component_id UUID           NOT NULL REFERENCES tax_components(id),
    start_date      DATE,
    end_date        DATE,
    UNIQUE (tax_group_id, tax_component_id)
);

-- ── rates ─────────────────────────────────────────────────────────────
-- Fineract rates — interest rate overrides for specific loan conditions
CREATE TABLE rates (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    UNIQUE NOT NULL,
    percentage      NUMERIC(8,4)    NOT NULL,
    product_apply   VARCHAR(20)     NOT NULL DEFAULT 'LOAN',
    approve         BOOLEAN         NOT NULL DEFAULT FALSE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    note            TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

-- ── accounting_rules ──────────────────────────────────────────────────
CREATE TABLE accounting_rules (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            REFERENCES tenants(id),
    office_id               UUID            REFERENCES offices(id),
    account_to_debit        UUID            REFERENCES gl_accounts(id),
    account_to_credit       UUID            REFERENCES gl_accounts(id),
    name                    VARCHAR(200)    NOT NULL,
    description             TEXT,
    system_defined          BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_multiple_debits   BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_multiple_credits  BOOLEAN         NOT NULL DEFAULT FALSE,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounting_rules_office ON accounting_rules(office_id);
CREATE INDEX idx_accounting_rules_tenant ON accounting_rules(tenant_id);

-- ── provisioning_criteria ─────────────────────────────────────────────
CREATE TABLE provisioning_criteria (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    criteria_name   VARCHAR(200)    UNIQUE NOT NULL,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE provisioning_criteria_definitions (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    criteria_id             UUID            NOT NULL REFERENCES provisioning_criteria(id),
    category_name           VARCHAR(100)    NOT NULL,
    min_age                 INT             NOT NULL DEFAULT 0,
    max_age                 INT             NOT NULL,
    provision_percentage    NUMERIC(8,4)    NOT NULL,
    liability_account_id    UUID            REFERENCES gl_accounts(id),
    expense_account_id      UUID            REFERENCES gl_accounts(id),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_prov_criteria_def ON provisioning_criteria_definitions(criteria_id);

-- ── provisioning_entries ──────────────────────────────────────────────
CREATE TABLE provisioning_entries (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    criteria_id     UUID            REFERENCES provisioning_criteria(id),
    created_by      VARCHAR(100),
    created_date    DATE            NOT NULL DEFAULT CURRENT_DATE,
    journal_entry_created BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE provisioning_entry_details (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id            UUID            NOT NULL REFERENCES provisioning_entries(id),
    criteria_id         UUID            REFERENCES provisioning_criteria(id),
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    outstanding_amount  NUMERIC(19,4)   NOT NULL,
    overdue_in_days     INT             NOT NULL DEFAULT 0,
    category_name       VARCHAR(100),
    provision_amount    NUMERIC(19,4)   NOT NULL,
    liability_account_id UUID           REFERENCES gl_accounts(id),
    expense_account_id  UUID            REFERENCES gl_accounts(id),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_prov_entry_details ON provisioning_entry_details(entry_id);

-- ── account_number_formats ────────────────────────────────────────────
CREATE TABLE account_number_formats (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_type        VARCHAR(30)     UNIQUE NOT NULL,
    -- LOAN | SAVINGS | CLIENT | SHARE
    prefix_type         VARCHAR(30)     NOT NULL DEFAULT 'ID',
    -- ID | OFFICE_NAME | LOAN_PRODUCT_SHORT_NAME | SAVINGS_PRODUCT_SHORT_NAME | SHARE_PRODUCT_SHORT_NAME
    prefix_character    VARCHAR(10),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

INSERT INTO account_number_formats (account_type, prefix_type) VALUES
    ('LOAN', 'ID'),
    ('SAVINGS', 'ID'),
    ('CLIENT', 'ID'),
    ('SHARE', 'ID');

-- ── roles & permissions ───────────────────────────────────────────────
-- REST-managed roles/permissions (mirrors Keycloak roles but also stored locally)
CREATE TABLE roles (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)    UNIQUE NOT NULL,
    description VARCHAR(500),
    disabled    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    grouping        VARCHAR(100)    NOT NULL,
    code            VARCHAR(200)    UNIQUE NOT NULL,
    entity_name     VARCHAR(100),
    action_name     VARCHAR(100),
    can_maker_checker BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id         UUID    NOT NULL REFERENCES roles(id),
    permission_id   UUID    NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO roles (name, description) VALUES
    ('SUPER_USER', 'Super user with all permissions'),
    ('ADMIN', 'Platform administrator'),
    ('TELLER', 'Branch teller / cashier'),
    ('CUSTOMER', 'End customer — self-service only'),
    ('API_CLIENT', 'Third-party API client (Open Banking TPP)');
