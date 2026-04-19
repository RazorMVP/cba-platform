-- External / SWIFT / SEPA payment fields on the payments table
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS external_network          VARCHAR(10),   -- SWIFT | SEPA | ACH
    ADD COLUMN IF NOT EXISTS beneficiary_name          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS beneficiary_iban          VARCHAR(34),
    ADD COLUMN IF NOT EXISTS beneficiary_bic           VARCHAR(11),
    ADD COLUMN IF NOT EXISTS beneficiary_bank_name     VARCHAR(200),
    ADD COLUMN IF NOT EXISTS beneficiary_country_code  VARCHAR(3),
    ADD COLUMN IF NOT EXISTS external_reference        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS charge_type               VARCHAR(5);    -- SHA | OUR | BEN (SWIFT charge bearing)

CREATE INDEX IF NOT EXISTS idx_payments_external_network ON payments(external_network) WHERE external_network IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_beneficiary_iban ON payments(beneficiary_iban) WHERE beneficiary_iban IS NOT NULL;
