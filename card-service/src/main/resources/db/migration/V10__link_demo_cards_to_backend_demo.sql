-- ─────────────────────────────────────────────────────────────────────────────
-- V10 — Point the demo cards at the backend's demo customer and account
-- ─────────────────────────────────────────────────────────────────────────────
-- V2 seeded the demo cards with placeholder ids for the backend side (customer
-- aaaaaaaa-…, debit account bbbbbbbb-…) that exist nowhere in the backend, so the
-- debit card's balance check always failed and Open Banking never listed the cards.
-- Relink them to the backend's V2 demo data: customer John Doe (30000000-…0001) and
-- his USD savings account (40000000-…0001). Only rows still holding the placeholders
-- change. The credit card's loan link stays: there is no credit line in the backend
-- yet (credit authorizations decline RC 57).

UPDATE cards SET linked_entity_id = '40000000-0000-0000-0000-000000000001'
WHERE id = '22222222-0001-0001-0001-000000000001'
  AND linked_entity_id = 'bbbbbbbb-0001-0001-0001-000000000001';

UPDATE cards SET customer_id = '30000000-0000-0000-0000-000000000001'
WHERE customer_id = 'aaaaaaaa-0001-0001-0001-000000000001';

UPDATE prepaid_wallets SET customer_id = '30000000-0000-0000-0000-000000000001'
WHERE customer_id = 'aaaaaaaa-0001-0001-0001-000000000001';

UPDATE token_vault SET customer_id = '30000000-0000-0000-0000-000000000001'
WHERE customer_id = 'aaaaaaaa-0001-0001-0001-000000000001';
