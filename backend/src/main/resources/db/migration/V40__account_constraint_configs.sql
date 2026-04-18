-- ═══════════════════════════════════════════════════════════════════
-- V40__account_constraint_configs.sql
-- Seeds two runtime-configurable GlobalConfiguration flags that control
-- account constraint enforcement.  Admins toggle them via
-- PUT /api/v1/configurations without any code change or redeployment.
-- ═══════════════════════════════════════════════════════════════════

INSERT INTO global_configurations (name, boolean_value, is_enabled, description)
VALUES
    ('enforce-min-required-opening-balance',
     TRUE,
     TRUE,
     'When enabled, an account cannot be activated (APPROVED → ACTIVE) unless its balance meets the deposit product''s minimum required opening balance.'),

    ('enforce-lockin-period-withdrawal',
     TRUE,
     TRUE,
     'When enabled, withdrawals are blocked for accounts that are still within the deposit product''s configured lock-in period.')

ON CONFLICT (name) DO NOTHING;
