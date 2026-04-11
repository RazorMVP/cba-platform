-- ============================================================
-- CBA Card Service — V2 Demo Data
-- ============================================================
-- Seeds: card products, BIN ranges, fraud rules, demo cards
-- ============================================================

-- ── Card Products ─────────────────────────────────────────────────────────────
INSERT INTO card_products (id, name, card_type, bin_range_start, bin_range_end, default_daily_limit, features) VALUES
  ('11111111-0001-0001-0001-000000000001', 'CBA Classic Debit',   'DEBIT',   '41000000', '41999999', 500000,  '{"contactless": true,  "international": false, "cashback": false}'),
  ('11111111-0001-0001-0001-000000000002', 'CBA Gold Debit',      'DEBIT',   '42000000', '42999999', 1000000, '{"contactless": true,  "international": true,  "cashback": true}'),
  ('11111111-0001-0001-0001-000000000003', 'CBA Prepaid Travel',  'PREPAID', '43000000', '43999999', 300000,  '{"contactless": true,  "international": true,  "cashback": false}'),
  ('11111111-0001-0001-0001-000000000004', 'CBA Credit Standard', 'CREDIT',  '55000000', '55999999', 2000000, '{"contactless": true,  "international": true,  "cashback": true, "interest_free_days": 55}'),
  ('11111111-0001-0001-0001-000000000005', 'CBA Mastercard Debit','DEBIT',   '52000000', '52999999', 500000,  '{"contactless": true,  "international": true,  "cashback": false}');

-- ── BIN Ranges ────────────────────────────────────────────────────────────────
-- Visa BIN ranges (41xxxxxx, 42xxxxxx, 43xxxxxx)
INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code) VALUES
  ('41000000', '41999999', 'VISA',       'Classic',  'DEBIT',   'USA'),
  ('42000000', '42999999', 'VISA',       'Gold',     'DEBIT',   'USA'),
  ('43000000', '43999999', 'VISA',       'Prepaid',  'PREPAID', 'USA'),
  ('40000000', '40999999', 'VISA',       'Standard', 'DEBIT',   'KEN'),
  ('44000000', '44999999', 'VISA',       'Standard', 'DEBIT',   'GHA'),
  ('45000000', '49999999', 'VISA',       'Standard', 'DEBIT',   NULL);

-- Mastercard BIN ranges (51-55xxxxxx)
INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code) VALUES
  ('51000000', '51999999', 'MASTERCARD', 'Standard', 'DEBIT',   NULL),
  ('52000000', '52999999', 'MASTERCARD', 'Standard', 'DEBIT',   NULL),
  ('53000000', '53999999', 'MASTERCARD', 'Standard', 'DEBIT',   NULL),
  ('54000000', '54999999', 'MASTERCARD', 'Gold',     'DEBIT',   NULL),
  ('55000000', '55999999', 'MASTERCARD', 'Standard', 'CREDIT',  NULL);

-- Verve BIN ranges (5061, 6500 series — Nigeria)
INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code) VALUES
  ('50610000', '50619999', 'VERVE',      'Standard', 'DEBIT',   'NGA'),
  ('65000000', '65009999', 'VERVE',      'Standard', 'DEBIT',   'NGA');

-- Afrigo / PAPSS (6200 series — African cross-border)
INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code) VALUES
  ('62000000', '62099999', 'AFRIGO',     'Standard', 'DEBIT',   NULL);

-- UnionPay (62xx series excluding Afrigo range)
INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code) VALUES
  ('62100000', '62999999', 'UNION_PAY',  'Standard', 'DEBIT',   'CHN');

-- Internal token BIN (9999xx) — triggers FEP de-tokenization
INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code) VALUES
  ('99990000', '99999999', 'VISA',       'Token',    'DEBIT',   NULL);

-- ── Fraud Rules ───────────────────────────────────────────────────────────────
INSERT INTO fraud_rules (rule_id, weight, enabled, params) VALUES
  ('VELOCITY_LIMIT',           40,  true,  '{"max_transactions": 5, "window_minutes": 10}'),
  ('SINGLE_AMOUNT_LIMIT',      35,  true,  '{"thresholds":{"840":100000,"404":13000000,"288":500000,"566":7500000},"default_threshold_minor_units":100000}'),
  ('BLOCKED_COUNTRY',          60,  true,  '{"blocked_country_codes": ["PRK", "IRN", "CUB"]}'),
  ('BLOCKED_MCC',              45,  true,  '{"blocked_mccs": ["7995", "9754"]}'),
  ('DUPLICATE_TRANSACTION',    50,  true,  '{"window_minutes": 2}'),
  ('CNP_DEBIT',                25,  true,  '{}'),
  ('OUTSIDE_HOURS',            20,  false, '{"allowed_start": "06:00", "allowed_end": "23:00"}'),
  ('CARD_EXPIRED',            100,  true,  '{}'),
  ('CARD_BLOCKED',            100,  true,  '{}'),
  ('PIN_RETRY_EXCEEDED',      100,  true,  '{"max_retries": 3}');

-- ── Demo Cards ────────────────────────────────────────────────────────────────
-- These use the demo customer UUIDs from the monolith's V2__demo_data.sql.
-- PAN values are test PANs (Luhn-valid) — encrypted here with placeholder
-- (real encryption happens at runtime via CardService.issueCard)
-- For demo purposes we store static test values.
INSERT INTO cards (id, pan_encrypted, pan_hash, pan_prefix, pan_suffix, expiry_date,
                   cvv_encrypted, card_type, status, virtual_flag, customer_id,
                   linked_entity_id, product_id, pin_set)
VALUES
  -- Customer demo debit card (Visa Classic)
  ('22222222-0001-0001-0001-000000000001',
   'ENC(4111111111111111)',  -- placeholder — real Jasypt value at runtime
   'demo-pan-hash-001',
   '41111111', '1111',
   '2812',                   -- exp Dec 2028
   'ENC(123)',
   'DEBIT', 'ACTIVE', false,
   'aaaaaaaa-0001-0001-0001-000000000001',  -- demo customer UUID from monolith
   'bbbbbbbb-0001-0001-0001-000000000001',  -- demo savings account UUID
   '11111111-0001-0001-0001-000000000001',
   true),

  -- Customer demo credit card (Mastercard)
  ('22222222-0001-0001-0001-000000000002',
   'ENC(5500005555555559)',
   'demo-pan-hash-002',
   '55000055', '5559',
   '2812',
   'ENC(456)',
   'CREDIT', 'ACTIVE', false,
   'aaaaaaaa-0001-0001-0001-000000000001',
   'cccccccc-0001-0001-0001-000000000001',  -- demo loan/credit UUID
   '11111111-0001-0001-0001-000000000004',
   true),

  -- Customer demo prepaid virtual card
  ('22222222-0001-0001-0001-000000000003',
   'ENC(4301234567891234)',
   'demo-pan-hash-003',
   '43012345', '1234',
   '2712',
   'ENC(789)',
   'PREPAID', 'ACTIVE', true,
   'aaaaaaaa-0001-0001-0001-000000000001',
   '22222222-0001-0001-0001-000000000003',  -- self-referencing wallet (set below)
   '11111111-0001-0001-0001-000000000003',
   false);

-- ── Card Limits (one per card) ────────────────────────────────────────────────
INSERT INTO card_limits (card_id, daily_purchase_limit, daily_withdrawal_limit, per_txn_limit, monthly_limit, currency_code)
VALUES
  ('22222222-0001-0001-0001-000000000001', 500000,  200000,  100000,  2000000, 'USD'),
  ('22222222-0001-0001-0001-000000000002', 2000000, 500000,  500000,  5000000, 'USD'),
  ('22222222-0001-0001-0001-000000000003', 300000,  0,       100000,  1000000, 'USD');

-- ── Prepaid Wallet (for the virtual prepaid card) ─────────────────────────────
INSERT INTO prepaid_wallets (id, card_id, customer_id, balance, currency_code, status)
VALUES
  ('22222222-0001-0001-0001-000000000003',
   '22222222-0001-0001-0001-000000000003',
   'aaaaaaaa-0001-0001-0001-000000000001',
   50000.00, 'USD', 'ACTIVE');
