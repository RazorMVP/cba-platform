-- ── Rate Limiting Configuration ────────────────────────────────────────────
-- Seed per-tier request-per-minute limits into global_configurations.
-- Numeric_value holds the req/min cap; value (BIGINT) mirrors it for
-- compatibility with existing GlobalConfiguration readers.

INSERT INTO global_configurations (name, value, numeric_value, is_enabled, description) VALUES
    ('rate_limit_sandbox',    30,   30,   TRUE, 'API requests per minute — sandbox tier (sk_test_ keys)'),
    ('rate_limit_basic',      100,  100,  TRUE, 'API requests per minute — basic tier (default)'),
    ('rate_limit_pro',        500,  500,  TRUE, 'API requests per minute — pro tier'),
    ('rate_limit_enterprise', 2000, 2000, TRUE, 'API requests per minute — enterprise tier')
ON CONFLICT (name) DO NOTHING;
