-- ═══════════════════════════════════════════════════════════════════════
-- V21 — Country-Specific Account Number Algorithm Framework
-- ═══════════════════════════════════════════════════════════════════════
-- Adds pluggable account number algorithm support per tenant.
-- First algorithm: NUBAN (Nigerian Uniform Bank Account Number) as mandated
-- by the Central Bank of Nigeria.
--
-- Design:
--   • tenants.country_params JSONB holds algorithm config per account type
--     Shape: { "bankCode":"058", "validationMode":"STRICT",
--              "algorithms":{"SAVINGS":"NUBAN","CHECKING":"NUBAN"} }
--   • nuban_sequences tracks the per-tenant serial counter; isolated from
--     the Mifos-style account_number_sequences table so the two systems
--     cannot interfere with each other.
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1. Extend tenants with algorithm configuration ──────────────────────
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS country_params JSONB NOT NULL DEFAULT '{}';

COMMENT ON COLUMN tenants.country_params IS
    'Per-tenant account number algorithm configuration. '
    'Shape: {"bankCode":"3-digit-CBN-code","validationMode":"STRICT|PARANOID",'
    '"algorithms":{"SAVINGS":"NUBAN","CHECKING":"NUBAN","FIXED_DEPOSIT":"MIFOS"}}';

-- ── 2. NUBAN serial number sequences ───────────────────────────────────
CREATE TABLE nuban_sequences (
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    account_type VARCHAR(50)  NOT NULL,
    last_sequence BIGINT      NOT NULL DEFAULT 0,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, account_type)
);

CREATE INDEX idx_nuban_sequences_tenant ON nuban_sequences(tenant_id);

-- ── 3. Demo data — CBA Nigeria tenant with NUBAN ────────────────────────
-- Insert a Nigeria demo tenant if it doesn't already exist
INSERT INTO tenants (id, code, name, currency_code, country_code, locale_code, active, country_params)
VALUES (
    gen_random_uuid(),
    'CBA_NG',
    'CBA Nigeria',
    'NGN',
    'NG',
    'en-NG',
    true,
    '{
        "bankCode": "058",
        "validationMode": "STRICT",
        "algorithms": {
            "SAVINGS":       "NUBAN",
            "CHECKING":      "NUBAN",
            "FIXED_DEPOSIT": "MIFOS",
            "LOAN":          "MIFOS",
            "SHARE":         "MIFOS"
        }
    }'::jsonb
)
ON CONFLICT (code) DO UPDATE
    SET country_params = EXCLUDED.country_params;

-- Seed NUBAN sequence rows for the Nigeria tenant
INSERT INTO nuban_sequences (tenant_id, account_type, last_sequence)
SELECT t.id, acct_type, 0
FROM tenants t,
     (VALUES ('SAVINGS'), ('CHECKING'), ('FIXED_DEPOSIT')) AS v(acct_type)
WHERE t.code = 'CBA_NG'
ON CONFLICT (tenant_id, account_type) DO NOTHING;
