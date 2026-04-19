-- ── Fraud Module — V45 ────────────────────────────────────────────────────────

-- Configurable fraud rules (velocity, amount thresholds, AML patterns, blacklist)
CREATE TABLE fraud_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    rule_type   VARCHAR(50)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    blocking    BOOLEAN      NOT NULL DEFAULT false,
    severity    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    params      JSONB        NOT NULL DEFAULT '{}',
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

-- Fraud alerts raised when a rule fires
CREATE TABLE fraud_alerts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id        UUID        REFERENCES fraud_rules(id),
    rule_name      VARCHAR(100),
    customer_id    UUID,
    account_id     UUID,
    transaction_id UUID,
    severity       VARCHAR(20)  NOT NULL,
    status         VARCHAR(40)  NOT NULL DEFAULT 'OPEN',
    alert_type     VARCHAR(50)  NOT NULL,
    details        JSONB        NOT NULL DEFAULT '{}',
    case_id        UUID,
    reviewed_by    VARCHAR(100),
    resolved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_fraud_alerts_customer ON fraud_alerts(customer_id);
CREATE INDEX idx_fraud_alerts_status   ON fraud_alerts(status);
CREATE INDEX idx_fraud_alerts_created  ON fraud_alerts(created_at DESC);
CREATE INDEX idx_fraud_alerts_case     ON fraud_alerts(case_id);

-- Fraud cases grouping related alerts for investigation
CREATE TABLE fraud_cases (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_number      VARCHAR(30)  UNIQUE NOT NULL,
    title            VARCHAR(200) NOT NULL,
    customer_id      UUID,
    status           VARCHAR(40)  NOT NULL DEFAULT 'OPEN',
    risk_level       VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    assigned_to      VARCHAR(100),
    resolution_notes TEXT,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_fraud_cases_status   ON fraud_cases(status);
CREATE INDEX idx_fraud_cases_customer ON fraud_cases(customer_id);

-- Sanctions / blacklist
CREATE TABLE blacklist_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(30)  NOT NULL,
    entity_value VARCHAR(500) NOT NULL,
    reason       TEXT,
    source       VARCHAR(30)  NOT NULL DEFAULT 'INTERNAL',
    active       BOOLEAN      NOT NULL DEFAULT true,
    added_by     VARCHAR(100),
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_blacklist_entity ON blacklist_entries(entity_type, active);

-- Per-customer risk scores
CREATE TABLE customer_risk_scores (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id           UUID UNIQUE NOT NULL,
    score                 INTEGER     NOT NULL DEFAULT 0,
    risk_level            VARCHAR(20) NOT NULL DEFAULT 'LOW',
    factors               JSONB       NOT NULL DEFAULT '{}',
    open_alerts_count     INTEGER     NOT NULL DEFAULT 0,
    confirmed_cases_count INTEGER     NOT NULL DEFAULT 0,
    blacklist_hits        INTEGER     NOT NULL DEFAULT 0,
    calculated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT      NOT NULL DEFAULT 0
);

-- ── Seed default rules ─────────────────────────────────────────────────────────

INSERT INTO fraud_rules (name, rule_type, enabled, blocking, severity, params, description) VALUES
(
    'Transaction Velocity Limit',
    'VELOCITY', true, true, 'HIGH',
    '{"maxTransactions": 10, "windowMinutes": 60}',
    'Blocks more than 10 debit/transfer transactions within a 60-minute rolling window on a single account'
),
(
    'Large Cash Transaction (CTR)',
    'LARGE_AMOUNT', true, false, 'HIGH',
    '{"thresholds": {"840": 1000000, "404": 130000000, "288": 5000000, "566": 1500000000, "default": 1000000}}',
    'Flags single cash transactions exceeding regulatory reporting threshold. Amounts in minor units (cents/kobo).'
),
(
    'Structuring Detection',
    'STRUCTURING', true, false, 'CRITICAL',
    '{"windowHours": 24, "minTransactions": 3, "thresholds": {"840": 900000, "404": 117000000, "288": 4500000, "566": 1350000000, "default": 900000}}',
    'Detects multiple transactions just below the CTR threshold within 24 hours — potential smurfing/structuring'
),
(
    'Blacklist / Sanctions Hit',
    'BLACKLIST_HIT', true, true, 'CRITICAL',
    '{}',
    'Blocks transactions where the account owner or counterparty appears in the sanctions/blacklist'
),
(
    'Rapid Fund Movement (Layering)',
    'RAPID_MOVEMENT', true, false, 'HIGH',
    '{"windowHours": 24, "minBalanceRatioDrained": 0.80}',
    'Flags accounts where 80% or more of the opening balance is transferred out within 24 hours — potential layering'
);
