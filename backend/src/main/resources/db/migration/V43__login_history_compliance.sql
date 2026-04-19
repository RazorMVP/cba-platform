-- ── Login History ────────────────────────────────────────────────────────────
CREATE TABLE login_history (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        VARCHAR(120),
    username       VARCHAR(120),
    ip_address     VARCHAR(64),
    user_agent     TEXT,
    status         VARCHAR(20)  NOT NULL,   -- SUCCESS | FAILURE | LOCKED | LOGOUT
    failure_reason VARCHAR(200),
    session_ref    VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_history_user_id   ON login_history(user_id);
CREATE INDEX idx_login_history_username  ON login_history(username);
CREATE INDEX idx_login_history_status    ON login_history(status);
CREATE INDEX idx_login_history_created   ON login_history(created_at DESC);

-- seed a few demo rows so the UI isn't empty on first load
INSERT INTO login_history (user_id, username, ip_address, user_agent, status, created_at)
VALUES
  ('admin-uuid',    'admin@cba.com',    '127.0.0.1', 'Mozilla/5.0 (Macintosh)', 'SUCCESS', now() - INTERVAL '5 minutes'),
  ('teller-uuid',   'teller@cba.com',   '127.0.0.1', 'Mozilla/5.0 (Macintosh)', 'SUCCESS', now() - INTERVAL '1 hour'),
  ('unknown-user',  'eve@attacker.com', '10.0.0.1',  'curl/7.68.0',             'FAILURE',  now() - INTERVAL '2 hours'),
  ('unknown-user',  'eve@attacker.com', '10.0.0.1',  'curl/7.68.0',             'FAILURE',  now() - INTERVAL '2 hours' + INTERVAL '5 seconds'),
  ('unknown-user',  'eve@attacker.com', '10.0.0.1',  'curl/7.68.0',             'LOCKED',   now() - INTERVAL '2 hours' + INTERVAL '10 seconds'),
  ('admin-uuid',    'admin@cba.com',    '127.0.0.1', 'Mozilla/5.0 (Windows)',   'SUCCESS', now() - INTERVAL '1 day'),
  ('teller-uuid',   'teller@cba.com',   '192.168.1.5','Mozilla/5.0 (Linux)',    'LOGOUT',   now() - INTERVAL '3 hours');
