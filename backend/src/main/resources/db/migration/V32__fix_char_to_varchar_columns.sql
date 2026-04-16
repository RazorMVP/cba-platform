-- V27: Convert CHAR columns to VARCHAR for Hibernate schema validation.
-- The old Docker session created currency_code and similar columns as CHAR(n),
-- but JPA entities declare them as VARCHAR(n) (length = 3 etc.).
-- Hibernate strict validation rejects CHAR (bpchar) when expecting varchar.
--
-- PostgreSQL: ALTER COLUMN TYPE is safe and does not require a table rewrite for
-- character/varchar conversions of the same or larger max length.

ALTER TABLE accounts
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE cash_transactions
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE charge_definitions
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE client_charges
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE collaterals
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE deposit_account_transactions
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE deposit_products
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE exchange_rates
    ALTER COLUMN from_currency TYPE VARCHAR(3),
    ALTER COLUMN to_currency   TYPE VARCHAR(3);

ALTER TABLE fixed_deposit_accounts
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE fixed_deposit_products
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE gsim_accounts
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE loan_charges
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE loan_products
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE payments
    ALTER COLUMN currency_code        TYPE VARCHAR(3),
    ALTER COLUMN destination_currency TYPE VARCHAR(3),
    ALTER COLUMN source_currency      TYPE VARCHAR(3);

ALTER TABLE provisioning_entry_details
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE recurring_deposit_accounts
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE recurring_deposit_products
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE share_accounts
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE share_products
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE standing_orders
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE supported_currencies
    ALTER COLUMN code TYPE VARCHAR(3);

ALTER TABLE teller_sessions
    ALTER COLUMN currency_code TYPE VARCHAR(3);

ALTER TABLE tenants
    ALTER COLUMN currency_code TYPE VARCHAR(3),
    ALTER COLUMN country_code  TYPE VARCHAR(2);

ALTER TABLE transactions
    ALTER COLUMN currency_code TYPE VARCHAR(3);
