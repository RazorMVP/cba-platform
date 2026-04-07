-- ═══════════════════════════════════════════════════════════════════
-- V17__hooks_holidays_campaigns.sql
-- Covers: Hooks/Webhooks, Holidays, SMS Campaigns, Report Mailing Jobs,
--         Standing Instructions, Document upload, Notes (polymorphic)
-- ═══════════════════════════════════════════════════════════════════

-- ── hooks ─────────────────────────────────────────────────────────────
-- Outbound webhook callbacks triggered by business events
CREATE TABLE hooks (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)    UNIQUE NOT NULL,
    template_name   VARCHAR(100)    NOT NULL DEFAULT 'Web',
    -- Web | SMS
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    events          JSONB           NOT NULL DEFAULT '[]',
    -- Array of event names: ["LOAN_APPROVED","LOAN_DISBURSED",...]
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE hook_configurations (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    hook_id     UUID            NOT NULL REFERENCES hooks(id),
    field_name  VARCHAR(100)    NOT NULL,
    field_value VARCHAR(500)    NOT NULL,
    UNIQUE (hook_id, field_name)
);

-- ── holidays ──────────────────────────────────────────────────────────
CREATE TABLE holidays (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            REFERENCES tenants(id),
    name                    VARCHAR(100)    NOT NULL,
    from_date               DATE            NOT NULL,
    to_date                 DATE            NOT NULL,
    repayment_scheduling_type VARCHAR(20)   NOT NULL DEFAULT 'NEXT_REPAYMENT_MEETING_DATE',
    -- SAME_DAY | NEXT_REPAYMENT_MEETING_DATE | NEXT_WORKING_DAY | MOVE_TO_SPECIFIC_DATE
    rescheduled_repayment_date DATE,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | ACTIVE | DELETED
    description             TEXT,
    processed               BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_holidays_dates  ON holidays(from_date, to_date);
CREATE INDEX idx_holidays_tenant ON holidays(tenant_id);

CREATE TABLE holiday_office_mappings (
    holiday_id  UUID    NOT NULL REFERENCES holidays(id),
    office_id   UUID    NOT NULL REFERENCES offices(id),
    PRIMARY KEY (holiday_id, office_id)
);

-- ── sms_campaigns ─────────────────────────────────────────────────────
CREATE TABLE sms_campaigns (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    campaign_name       VARCHAR(200)    NOT NULL,
    campaign_type       VARCHAR(30)     NOT NULL,
    -- INDIVIDUAL | ALL | QUERY
    trigger_type        VARCHAR(30)     NOT NULL DEFAULT 'SCHEDULED',
    -- DIRECT | SCHEDULED | TRIGGERED
    run_report_id       UUID,
    param_value         TEXT,
    report_param_name   VARCHAR(100),
    message             TEXT            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | WAITING_FOR_ACTIVATION | ACTIVE | CLOSED | DELETED
    recurrence          VARCHAR(100),
    -- iCal RRULE format
    run_date            DATE,
    next_trigger_date   TIMESTAMPTZ,
    last_trigger_date   TIMESTAMPTZ,
    submitted_on_date   DATE            NOT NULL DEFAULT CURRENT_DATE,
    closed_on_date      DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_sms_campaigns_status ON sms_campaigns(status);
CREATE INDEX idx_sms_campaigns_tenant ON sms_campaigns(tenant_id);

CREATE TABLE sms_messages (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID            REFERENCES sms_campaigns(id),
    customer_id     UUID            REFERENCES customers(id),
    mobile_no       VARCHAR(30),
    message         TEXT            NOT NULL,
    delivery_status VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | SENT | FAILED | INVALID
    submitted_on    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    delivered_on    TIMESTAMPTZ
);

CREATE INDEX idx_sms_messages_campaign  ON sms_messages(campaign_id);
CREATE INDEX idx_sms_messages_status    ON sms_messages(delivery_status);

-- ── report_mailing_jobs ───────────────────────────────────────────────
CREATE TABLE report_mailing_jobs (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200)    UNIQUE NOT NULL,
    description         TEXT,
    recurrence          VARCHAR(100)    NOT NULL,
    -- iCal RRULE format
    next_run_date_time  TIMESTAMPTZ,
    email_recipients    TEXT            NOT NULL,
    -- Comma-separated email addresses
    report_name         VARCHAR(200)    NOT NULL,
    report_params       JSONB,
    output_type         VARCHAR(20)     NOT NULL DEFAULT 'CSV',
    -- CSV | PDF | XLS
    email_subject       VARCHAR(300),
    email_message       TEXT,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    run_count           BIGINT          NOT NULL DEFAULT 0,
    previous_run_start_time TIMESTAMPTZ,
    previous_run_end_time   TIMESTAMPTZ,
    previous_run_status VARCHAR(20),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

-- ── standing_instructions ─────────────────────────────────────────────
-- Mifos standinginstructions — different from payment standing orders (recurring transfers)
-- This is the Mifos model: periodic account-to-account transfer instructions
CREATE TABLE standing_instructions (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            REFERENCES tenants(id),
    name                    VARCHAR(200)    NOT NULL,
    client_id               UUID            REFERENCES customers(id),
    from_account_id         UUID            NOT NULL REFERENCES accounts(id),
    from_account_type       VARCHAR(20)     NOT NULL DEFAULT 'SAVINGS',
    to_client_id            UUID            REFERENCES customers(id),
    to_account_id           UUID            REFERENCES accounts(id),
    to_account_type         VARCHAR(20)     NOT NULL DEFAULT 'SAVINGS',
    instruction_type        VARCHAR(20)     NOT NULL DEFAULT 'FIXED',
    -- FIXED | OUTSTANDING_BALANCE
    priority                VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    -- HIGH | MEDIUM | LOW | URGENT
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | DISABLED | DELETED
    amount                  NUMERIC(19,4),
    validity_from_date      DATE,
    validity_till_date      DATE,
    recurrence_type         VARCHAR(20)     NOT NULL DEFAULT 'PERIODIC_RECURRENCE',
    -- PERIODIC_RECURRENCE | AS_PER_DUES
    recurrence_frequency    INT             NOT NULL DEFAULT 1,
    recurrence_interval     INT             NOT NULL DEFAULT 1,
    recurrence_on_day       INT,
    recurrence_on_nth_day   VARCHAR(10),
    recurrence_on_day_of_month INT,
    next_run_for_date       DATE,
    last_run_history        TIMESTAMPTZ,
    transfer_type           VARCHAR(30)     NOT NULL DEFAULT 'ACCOUNT_TRANSFER',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_standing_inst_client  ON standing_instructions(client_id);
CREATE INDEX idx_standing_inst_status  ON standing_instructions(status);
CREATE INDEX idx_standing_inst_tenant  ON standing_instructions(tenant_id);

-- ── notes ─────────────────────────────────────────────────────────────
-- Polymorphic notes on any entity (client, loan, account, group, etc.)
CREATE TABLE notes (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            REFERENCES tenants(id),
    entity_type     VARCHAR(30)     NOT NULL,
    -- CLIENT | LOAN | SAVINGS | GROUP | SHARE | CENTER | LOAN_TRANSACTION | SAVINGS_TRANSACTION
    entity_id       UUID            NOT NULL,
    note            TEXT            NOT NULL,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_notes_entity ON notes(entity_type, entity_id);
CREATE INDEX idx_notes_tenant ON notes(tenant_id);

-- ── documents ─────────────────────────────────────────────────────────
-- Polymorphic document attachments
CREATE TABLE documents (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            REFERENCES tenants(id),
    parent_entity_type  VARCHAR(30) NOT NULL,
    -- CLIENT | LOAN | SAVINGS | GROUP | CENTER | STAFF | SHARE
    parent_entity_id    UUID        NOT NULL,
    name            VARCHAR(250)    NOT NULL,
    file_name       VARCHAR(250),
    size            BIGINT,
    type            VARCHAR(500),
    -- MIME type
    description     VARCHAR(1000),
    location        VARCHAR(500),
    -- S3 key or local path
    storage_type    VARCHAR(20)     NOT NULL DEFAULT 'FILE_SYSTEM',
    -- FILE_SYSTEM | S3 | DATABASE
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_entity ON documents(parent_entity_type, parent_entity_id);
CREATE INDEX idx_documents_tenant ON documents(tenant_id);
