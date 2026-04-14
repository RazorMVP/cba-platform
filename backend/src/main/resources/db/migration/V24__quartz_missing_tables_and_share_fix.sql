-- V24: Runtime fixes — complete missing Quartz tables + share_accounts column
--
-- Context: V10 created 6 of 11 required Quartz PostgreSQL tables.
-- Spring Boot's LocalDataSourceJobStore requires all 11 at startup.
-- The 5 missing tables were applied directly to the dev DB during Session 50
-- debugging. This migration ensures fresh deployments also have them.
--
-- Also adds: share_accounts.total_shares_held (missing from V14, causing
-- Hibernate schema validation failure on first startup).

-- ── Missing Quartz Tables ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS qrtz_simple_triggers (
    sched_name      VARCHAR(120) NOT NULL,
    trigger_name    VARCHAR(200) NOT NULL,
    trigger_group   VARCHAR(200) NOT NULL,
    repeat_count    BIGINT       NOT NULL,
    repeat_interval BIGINT       NOT NULL,
    times_triggered BIGINT       NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_simprop_triggers (
    sched_name    VARCHAR(120)   NOT NULL,
    trigger_name  VARCHAR(200)   NOT NULL,
    trigger_group VARCHAR(200)   NOT NULL,
    str_prop_1    VARCHAR(512),
    str_prop_2    VARCHAR(512),
    str_prop_3    VARCHAR(512),
    int_prop_1    INT,
    int_prop_2    INT,
    long_prop_1   BIGINT,
    long_prop_2   BIGINT,
    dec_prop_1    NUMERIC(13,4),
    dec_prop_2    NUMERIC(13,4),
    bool_prop_1   BOOLEAN,
    bool_prop_2   BOOLEAN,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_blob_triggers (
    sched_name    VARCHAR(120) NOT NULL,
    trigger_name  VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data     BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
        REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_calendars (
    sched_name    VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar      BYTEA        NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE IF NOT EXISTS qrtz_paused_trigger_grps (
    sched_name    VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

-- ── share_accounts: total_shares_held column ──────────────────────────────────
-- Entity field was present in ShareAccount.java but missing from V14 DDL,
-- causing Hibernate ddl-auto: validate failure at startup.

ALTER TABLE share_accounts
    ADD COLUMN IF NOT EXISTS total_shares_held BIGINT NOT NULL DEFAULT 0;
