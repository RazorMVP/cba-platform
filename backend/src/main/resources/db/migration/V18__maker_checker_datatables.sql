-- ═══════════════════════════════════════════════════════════════════
-- V18__maker_checker_datatables.sql
-- Covers: Maker-Checker workflow, DataTables (dynamic schema),
--         Search index (view), Two-Factor Auth, Beneficiaries,
--         Credit Bureau Config, Surveys
-- ═══════════════════════════════════════════════════════════════════

-- ── maker_checkers ────────────────────────────────────────────────────
-- Command-source pattern — every write can require 2-person approval
CREATE TABLE maker_checkers (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    action_name         VARCHAR(100)    NOT NULL,
    entity_name         VARCHAR(100)    NOT NULL,
    maker_id            VARCHAR(100)    NOT NULL,
    made_on_date        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    checker_id          VARCHAR(100),
    checked_on_date     TIMESTAMPTZ,
    processing_result   VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | APPROVED | REJECTED
    command_as_json     TEXT            NOT NULL,
    -- Full request payload stored for re-execution after approval
    url                 VARCHAR(500),
    api_get_url         VARCHAR(500),
    resource_id         UUID,
    sub_resource_id     UUID,
    error               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_maker_checkers_status ON maker_checkers(processing_result);
CREATE INDEX idx_maker_checkers_maker  ON maker_checkers(maker_id);
CREATE INDEX idx_maker_checkers_tenant ON maker_checkers(tenant_id);

-- ── datatables ────────────────────────────────────────────────────────
-- Dynamic schema — admins register extra columns against core entities
CREATE TABLE datatables (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    registered_table_name VARCHAR(100)  UNIQUE NOT NULL,
    application_table_name VARCHAR(100) NOT NULL,
    -- m_client | m_loan | m_savings_account | m_group etc. (Mifos naming)
    system_defined      BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_multiple_rows BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE datatable_column_definitions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    datatable_id    UUID            NOT NULL REFERENCES datatables(id),
    column_name     VARCHAR(100)    NOT NULL,
    column_type     VARCHAR(30)     NOT NULL,
    -- STRING | NUMBER | DECIMAL | DATE | DATETIME | TEXT | DROPDOWN | CHECKBOX | CODELOOKUP
    nullable        BOOLEAN         NOT NULL DEFAULT TRUE,
    unique_column   BOOLEAN         NOT NULL DEFAULT FALSE,
    indexed         BOOLEAN         NOT NULL DEFAULT FALSE,
    code_id         UUID            REFERENCES codes(id),
    -- Only for DROPDOWN/CODELOOKUP types
    column_display_type VARCHAR(30),
    order_position  INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_datatable_cols_table ON datatable_column_definitions(datatable_id);

-- ── two_factor_auth ───────────────────────────────────────────────────
CREATE TABLE two_factor_auth_tokens (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            REFERENCES platform_users(id),
    token           VARCHAR(20)     NOT NULL,
    delivery_method VARCHAR(20)     NOT NULL DEFAULT 'EMAIL',
    -- EMAIL | SMS
    expires_at      TIMESTAMPTZ     NOT NULL,
    verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_2fa_user    ON two_factor_auth_tokens(user_id);
CREATE INDEX idx_2fa_token   ON two_factor_auth_tokens(token);

-- ── beneficiaries ─────────────────────────────────────────────────────
-- Third-party transfer beneficiaries (self-service)
CREATE TABLE beneficiaries (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    name                VARCHAR(200)    NOT NULL,
    account_number      VARCHAR(100)    NOT NULL,
    bank_number         VARCHAR(100),
    -- Routing/BIC/sort code
    transfer_limit      NUMERIC(19,4),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_beneficiaries_customer ON beneficiaries(customer_id);

-- ── credit_bureau_configuration ───────────────────────────────────────
CREATE TABLE credit_bureau_integrations (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    NOT NULL,
    impl_class          VARCHAR(300)    NOT NULL,
    credit_bureau_id    VARCHAR(100),
    country             VARCHAR(10),
    active              BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE credit_bureau_product_mappings (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_product_id         UUID            NOT NULL REFERENCES loan_products(id),
    credit_bureau_id        UUID            NOT NULL REFERENCES credit_bureau_integrations(id),
    is_creditcheck_mandatory BOOLEAN        NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    UNIQUE (loan_product_id, credit_bureau_id)
);

-- ── surveys ───────────────────────────────────────────────────────────
CREATE TABLE surveys (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    key             VARCHAR(100)    UNIQUE NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    country_code    VARCHAR(10),
    description     TEXT,
    valid_from      DATE,
    valid_to        DATE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE survey_questions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id       UUID            NOT NULL REFERENCES surveys(id),
    key             VARCHAR(100)    NOT NULL,
    text            TEXT            NOT NULL,
    description     TEXT,
    sequence_no     INT             NOT NULL DEFAULT 0
);

CREATE TABLE survey_responses (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID            NOT NULL REFERENCES survey_questions(id),
    text            TEXT            NOT NULL,
    value           INT             NOT NULL DEFAULT 0,
    sequence_no     INT             NOT NULL DEFAULT 0
);

CREATE TABLE survey_scorecards (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id       UUID            NOT NULL REFERENCES surveys(id),
    customer_id     UUID            NOT NULL REFERENCES customers(id),
    user_id         UUID,
    created_on      DATE            NOT NULL DEFAULT CURRENT_DATE,
    start_date      DATE,
    end_date        DATE,
    country_code    VARCHAR(10),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE survey_scorecard_scores (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    scorecard_id    UUID            NOT NULL REFERENCES survey_scorecards(id),
    question_id     UUID            NOT NULL REFERENCES survey_questions(id),
    response_id     UUID            NOT NULL REFERENCES survey_responses(id),
    value           INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_scorecards_survey   ON survey_scorecards(survey_id);
CREATE INDEX idx_scorecards_customer ON survey_scorecards(customer_id);

-- ── client_extensions ────────────────────────────────────────────────
-- Client identifiers, addresses, images (previously on customer entity only)
CREATE TABLE client_identifiers (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    document_type_id    UUID            REFERENCES code_values(id),
    -- Code 'IdentifierDocumentType'
    document_key        VARCHAR(200)    NOT NULL,
    description         VARCHAR(500),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_client_ids_customer ON client_identifiers(customer_id);

CREATE TABLE client_addresses (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    address_type_id     UUID            REFERENCES code_values(id),
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    line_1              VARCHAR(200),
    line_2              VARCHAR(200),
    line_3              VARCHAR(200),
    city                VARCHAR(100),
    county_district     VARCHAR(100),
    state_province_id   UUID            REFERENCES code_values(id),
    country_id          UUID            REFERENCES code_values(id),
    postal_code         VARCHAR(20),
    latitude            NUMERIC(10,6),
    longitude           NUMERIC(10,6),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_client_addresses_customer ON client_addresses(customer_id);

CREATE TABLE client_images (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID            NOT NULL UNIQUE REFERENCES customers(id),
    location        VARCHAR(500),
    -- S3 key or local path
    storage_type    VARCHAR(20)     NOT NULL DEFAULT 'FILE_SYSTEM',
    content_type    VARCHAR(100),
    size            BIGINT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
