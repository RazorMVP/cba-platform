-- V7: Groups, Centers, GLIM, and Collection Sheets

-- ── Centers ──────────────────────────────────────────────────────────────────
CREATE TABLE centers (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    external_id     VARCHAR(100)    UNIQUE,
    office_id       UUID            NOT NULL REFERENCES offices(id),
    staff_id        UUID            REFERENCES staff(id),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','ACTIVE','CLOSED')),
    activation_date DATE,
    meeting_day     VARCHAR(10),    -- MONDAY, TUESDAY, etc.
    meeting_time    TIME,
    tenant_id       UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_centers_office ON centers(office_id);
CREATE INDEX idx_centers_staff  ON centers(staff_id);

-- ── Groups ───────────────────────────────────────────────────────────────────
CREATE TABLE groups (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    external_id     VARCHAR(100)    UNIQUE,
    office_id       UUID            NOT NULL REFERENCES offices(id),
    center_id       UUID            REFERENCES centers(id),
    staff_id        UUID            REFERENCES staff(id),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','ACTIVE','CLOSED')),
    submitted_on    DATE            NOT NULL DEFAULT CURRENT_DATE,
    activation_date DATE,
    tenant_id       UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_groups_office ON groups(office_id);
CREATE INDEX idx_groups_center ON groups(center_id);
CREATE INDEX idx_groups_staff  ON groups(staff_id);

-- ── Group Members (customers in groups) ──────────────────────────────────────
CREATE TABLE group_members (
    group_id    UUID    NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    customer_id UUID    NOT NULL REFERENCES customers(id),
    joined_date DATE    NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (group_id, customer_id)
);

-- ── GLIM — Group Loan (one loan account shared by group members) ──────────────
-- Each member has a share of the group loan tracked individually
CREATE TABLE glim_accounts (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID            NOT NULL REFERENCES groups(id),
    loan_id             UUID            NOT NULL REFERENCES loans(id),   -- parent group loan
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    principal_share     NUMERIC(19,4)   NOT NULL,
    outstanding_share   NUMERIC(19,4)   NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    tenant_id           UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (loan_id, customer_id)
);

CREATE INDEX idx_glim_group    ON glim_accounts(group_id);
CREATE INDEX idx_glim_loan     ON glim_accounts(loan_id);
CREATE INDEX idx_glim_customer ON glim_accounts(customer_id);

-- ── Collection Sheets ─────────────────────────────────────────────────────────
CREATE TABLE collection_sheets (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    center_id           UUID            REFERENCES centers(id),
    group_id            UUID            REFERENCES groups(id),
    meeting_date        DATE            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','SUBMITTED','PROCESSED')),
    staff_id            UUID            REFERENCES staff(id),
    total_due           NUMERIC(19,4)   NOT NULL DEFAULT 0,
    total_collected     NUMERIC(19,4)   NOT NULL DEFAULT 0,
    processed_at        TIMESTAMPTZ,
    tenant_id           UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (group_id, meeting_date)
);

-- ── Collection Sheet Line Items ────────────────────────────────────────────────
CREATE TABLE collection_sheet_items (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_sheet_id UUID            NOT NULL REFERENCES collection_sheets(id) ON DELETE CASCADE,
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    loan_id             UUID            REFERENCES loans(id),
    account_id          UUID            REFERENCES accounts(id),
    amount_due          NUMERIC(19,4)   NOT NULL DEFAULT 0,
    amount_paid         NUMERIC(19,4),
    charge_amount       NUMERIC(19,4)   NOT NULL DEFAULT 0,
    currency_code       VARCHAR(3)      NOT NULL DEFAULT 'USD',
    item_type           VARCHAR(20)     NOT NULL CHECK (item_type IN ('LOAN_REPAYMENT','SAVINGS_DEPOSIT')),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_cs_items_sheet ON collection_sheet_items(collection_sheet_id);
