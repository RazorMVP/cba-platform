-- Widen partner_webhooks.secret_hash to TEXT so it can hold the Jasypt AES-256
-- ciphertext of the webhook signing secret (encrypted at rest via EncryptedStringConverter).
-- The column keeps its V49 name (secret_hash) but now stores reversible ciphertext, not a hash —
-- the cleartext is required at dispatch time to compute the X-CBA-Signature HMAC.
ALTER TABLE partner_webhooks ALTER COLUMN secret_hash TYPE TEXT;
