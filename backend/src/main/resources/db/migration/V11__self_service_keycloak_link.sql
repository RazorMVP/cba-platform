-- V11: Link customers to Keycloak IDs for self-service authentication
-- Each customer in self-service has a corresponding Keycloak user; this column
-- stores that Keycloak user UUID (the JWT `sub` claim) for direct lookup.

ALTER TABLE customers ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_keycloak_id ON customers(keycloak_id) WHERE keycloak_id IS NOT NULL;
