-- V38: Liquidity Management Module
-- Persists reserve requirements and daily snapshots.
-- Live position is computed on-the-fly from accounts + treasury tables.

CREATE TABLE liquidity_reserve_requirements (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency_code            VARCHAR(3)      NOT NULL,
    minimum_balance          NUMERIC(19,4)   NOT NULL DEFAULT 0,
    minimum_ratio_percent    NUMERIC(5,2),
    alert_threshold_percent  NUMERIC(5,2),
    regulatory_reference     VARCHAR(255),
    active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),
    version                  BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_reserve_currency UNIQUE (currency_code)
);

CREATE TABLE liquidity_snapshots (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date               DATE           NOT NULL,
    currency_code               VARCHAR(3)     NOT NULL,
    cash_on_hand                NUMERIC(19,4)  NOT NULL DEFAULT 0,
    placements_deployed         NUMERIC(19,4)  NOT NULL DEFAULT 0,
    interbank_lending           NUMERIC(19,4)  NOT NULL DEFAULT 0,
    interbank_borrowing         NUMERIC(19,4)  NOT NULL DEFAULT 0,
    net_liquidity_position      NUMERIC(19,4)  NOT NULL,
    reserve_requirement         NUMERIC(19,4),
    surplus_deficit             NUMERIC(19,4),
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_snapshot_date_ccy UNIQUE (snapshot_date, currency_code)
);

CREATE INDEX idx_liq_snapshot_date ON liquidity_snapshots (snapshot_date DESC);
CREATE INDEX idx_liq_snapshot_ccy  ON liquidity_snapshots (currency_code);

-- Seed default reserve requirements for demo tenants
INSERT INTO liquidity_reserve_requirements
    (currency_code, minimum_balance, minimum_ratio_percent, alert_threshold_percent, regulatory_reference)
VALUES
    ('USD', 50000.00,   10.00, 15.00, 'Fed Reserve Reg D'),
    ('KES', 5000000.00,  5.50,  8.00, 'CBK Prudential Guidelines 2024'),
    ('GHS', 1000000.00,  8.00, 12.00, 'BoG Capital Adequacy Directive');
