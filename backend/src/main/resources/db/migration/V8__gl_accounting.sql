-- V8: General Ledger — Chart of Accounts, Journal Entries, Period Closures

-- ── Chart of Accounts ────────────────────────────────────────────────────────
CREATE TABLE gl_accounts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    gl_code         VARCHAR(50)     NOT NULL UNIQUE,
    account_type    VARCHAR(20)     NOT NULL
                        CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    usage           VARCHAR(20)     NOT NULL DEFAULT 'DETAIL'
                        CHECK (usage IN ('HEADER','DETAIL')),
    parent_id       UUID            REFERENCES gl_accounts(id),
    description     TEXT,
    is_manual_entries_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    is_disabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    tenant_id       UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_gl_accounts_type   ON gl_accounts(account_type);
CREATE INDEX idx_gl_accounts_parent ON gl_accounts(parent_id);

-- ── Financial Activity Mappings ───────────────────────────────────────────────
-- Maps system activities (ASSET_TRANSFER, LIABILITY_TRANSFER) to GL accounts
CREATE TABLE financial_activity_accounts (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_activity  VARCHAR(50) NOT NULL UNIQUE,
    gl_account_id       UUID        NOT NULL REFERENCES gl_accounts(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Journal Entries ───────────────────────────────────────────────────────────
CREATE TABLE journal_entries (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      VARCHAR(100)    NOT NULL,   -- groups debit+credit pair
    office_id           UUID            REFERENCES offices(id),
    gl_account_id       UUID            NOT NULL REFERENCES gl_accounts(id),
    entry_type          VARCHAR(10)     NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount              NUMERIC(19,4)   NOT NULL,
    currency_code       VARCHAR(3)      NOT NULL DEFAULT 'USD',
    transaction_date    DATE            NOT NULL DEFAULT CURRENT_DATE,
    entity_type         VARCHAR(50),    -- LOAN, ACCOUNT, TELLER_CASH, MANUAL
    entity_id           UUID,
    comments            TEXT,
    submitted_by        VARCHAR(200),
    reversed            BOOLEAN         NOT NULL DEFAULT FALSE,
    reversal_id         UUID            REFERENCES journal_entries(id),
    manual_entry        BOOLEAN         NOT NULL DEFAULT FALSE,
    tenant_id           UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_je_transaction   ON journal_entries(transaction_id);
CREATE INDEX idx_je_gl_account    ON journal_entries(gl_account_id);
CREATE INDEX idx_je_date          ON journal_entries(transaction_date);
CREATE INDEX idx_je_entity        ON journal_entries(entity_type, entity_id);

-- ── Accounting Period Closures ────────────────────────────────────────────────
CREATE TABLE gl_closures (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    office_id       UUID            REFERENCES offices(id),
    closing_date    DATE            NOT NULL,
    comments        TEXT,
    closed_by       VARCHAR(200),
    tenant_id       UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (office_id, closing_date)
);

-- ── Seed Chart of Accounts (standard banking CoA) ────────────────────────────
INSERT INTO gl_accounts (id, name, gl_code, account_type, usage) VALUES
    -- ASSETS
    ('30000000-0000-0000-0000-000000000001', 'Assets',            '1000', 'ASSET',     'HEADER'),
    ('30000000-0000-0000-0000-000000000002', 'Cash on Hand',      '1001', 'ASSET',     'DETAIL'),
    ('30000000-0000-0000-0000-000000000003', 'Cash at Bank',      '1002', 'ASSET',     'DETAIL'),
    ('30000000-0000-0000-0000-000000000004', 'Loans Receivable',  '1100', 'ASSET',     'DETAIL'),
    ('30000000-0000-0000-0000-000000000005', 'Interest Receivable','1101','ASSET',     'DETAIL'),
    -- LIABILITIES
    ('30000000-0000-0000-0000-000000000010', 'Liabilities',       '2000', 'LIABILITY', 'HEADER'),
    ('30000000-0000-0000-0000-000000000011', 'Customer Deposits', '2001', 'LIABILITY', 'DETAIL'),
    ('30000000-0000-0000-0000-000000000012', 'Borrowings',        '2100', 'LIABILITY', 'DETAIL'),
    -- EQUITY
    ('30000000-0000-0000-0000-000000000020', 'Equity',            '3000', 'EQUITY',    'HEADER'),
    ('30000000-0000-0000-0000-000000000021', 'Retained Earnings', '3001', 'EQUITY',    'DETAIL'),
    -- INCOME
    ('30000000-0000-0000-0000-000000000030', 'Income',            '4000', 'INCOME',    'HEADER'),
    ('30000000-0000-0000-0000-000000000031', 'Interest Income',   '4001', 'INCOME',    'DETAIL'),
    ('30000000-0000-0000-0000-000000000032', 'Fee Income',        '4002', 'INCOME',    'DETAIL'),
    -- EXPENSES
    ('30000000-0000-0000-0000-000000000040', 'Expenses',          '5000', 'EXPENSE',   'HEADER'),
    ('30000000-0000-0000-0000-000000000041', 'Bad Debt Expense',  '5001', 'EXPENSE',   'DETAIL'),
    ('30000000-0000-0000-0000-000000000042', 'Interest Expense',  '5002', 'EXPENSE',   'DETAIL');

-- ── Financial Activity Mappings ────────────────────────────────────────────────
INSERT INTO financial_activity_accounts (financial_activity, gl_account_id) VALUES
    ('ASSET_TRANSFER',     '30000000-0000-0000-0000-000000000003'),
    ('LIABILITY_TRANSFER', '30000000-0000-0000-0000-000000000011'),
    ('CASH_AT_TELLER',     '30000000-0000-0000-0000-000000000002'),
    ('LOAN_PORTFOLIO',     '30000000-0000-0000-0000-000000000004'),
    ('INTEREST_RECEIVABLE','30000000-0000-0000-0000-000000000005'),
    ('INTEREST_INCOME',    '30000000-0000-0000-0000-000000000031'),
    ('WRITE_OFF_EXPENSE',  '30000000-0000-0000-0000-000000000041');
