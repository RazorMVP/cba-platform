-- V10: Layer A fixes — standing orders, loan write-off tracking,
--      payment reversal tracking, Spring Batch + Quartz schemas

-- ── Standing Orders (missing from payments) ───────────────────────────────────
CREATE TABLE standing_orders (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    source_account_id       UUID            NOT NULL REFERENCES accounts(id),
    destination_account_id  UUID            NOT NULL REFERENCES accounts(id),
    amount                  NUMERIC(19,4)   NOT NULL,
    currency_code           VARCHAR(3)      NOT NULL DEFAULT 'USD',
    frequency               VARCHAR(20)     NOT NULL
                                CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','QUARTERLY','ANNUALLY')),
    start_date              DATE            NOT NULL,
    end_date                DATE,
    next_execution_date     DATE            NOT NULL,
    description             VARCHAR(500),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE','PAUSED','CANCELLED','COMPLETED')),
    last_executed_at        TIMESTAMPTZ,
    tenant_id               UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_so_source      ON standing_orders(source_account_id);
CREATE INDEX idx_so_next_exec   ON standing_orders(next_execution_date) WHERE status = 'ACTIVE';

-- ── Payment reversals tracking column ─────────────────────────────────────────
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS reversal_of UUID REFERENCES payments(id),
    ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ;

-- ── Loan write-off tracking columns ──────────────────────────────────────────
ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS written_off_on DATE,
    ADD COLUMN IF NOT EXISTS write_off_reason TEXT;

-- ── Loan repayment tracking (link repayment to payment) ───────────────────────
CREATE TABLE loan_repayments (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID            NOT NULL REFERENCES loans(id),
    payment_id          UUID            REFERENCES payments(id),
    amount              NUMERIC(19,4)   NOT NULL,
    principal_portion   NUMERIC(19,4)   NOT NULL DEFAULT 0,
    interest_portion    NUMERIC(19,4)   NOT NULL DEFAULT 0,
    fee_portion         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    payment_date        DATE            NOT NULL,
    payment_method      VARCHAR(50),
    reference_number    VARCHAR(100),
    tenant_id           UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_loan_repayments_loan    ON loan_repayments(loan_id);
CREATE INDEX idx_loan_repayments_payment ON loan_repayments(payment_id);

-- ── Spring Batch Schema ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION         BIGINT,
    JOB_NAME        VARCHAR(100) NOT NULL,
    JOB_KEY         VARCHAR(32)  NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_SEQ START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_EXECUTION_SEQ START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS BATCH_STEP_EXECUTION_SEQ START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID  BIGINT    NOT NULL PRIMARY KEY,
    VERSION           BIGINT,
    JOB_INSTANCE_ID   BIGINT    NOT NULL REFERENCES BATCH_JOB_INSTANCE(JOB_INSTANCE_ID),
    CREATE_TIME       TIMESTAMP NOT NULL,
    START_TIME        TIMESTAMP,
    END_TIME          TIMESTAMP,
    STATUS            VARCHAR(10),
    EXIT_CODE         VARCHAR(2500),
    EXIT_MESSAGE      VARCHAR(2500),
    LAST_UPDATED      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID  BIGINT       NOT NULL REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID),
    PARAMETER_NAME    VARCHAR(100) NOT NULL,
    PARAMETER_TYPE    VARCHAR(100) NOT NULL,
    PARAMETER_VALUE   VARCHAR(2500),
    IDENTIFYING       CHAR(1)      NOT NULL
);

CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID   BIGINT    NOT NULL PRIMARY KEY,
    VERSION             BIGINT    NOT NULL,
    STEP_NAME           VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID    BIGINT    NOT NULL REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID),
    CREATE_TIME         TIMESTAMP NOT NULL,
    START_TIME          TIMESTAMP,
    END_TIME            TIMESTAMP,
    STATUS              VARCHAR(10),
    COMMIT_COUNT        BIGINT,
    READ_COUNT          BIGINT,
    FILTER_COUNT        BIGINT,
    WRITE_COUNT         BIGINT,
    READ_SKIP_COUNT     BIGINT,
    WRITE_SKIP_COUNT    BIGINT,
    PROCESS_SKIP_COUNT  BIGINT,
    ROLLBACK_COUNT      BIGINT,
    EXIT_CODE           VARCHAR(2500),
    EXIT_MESSAGE        VARCHAR(2500),
    LAST_UPDATED        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID   BIGINT        NOT NULL PRIMARY KEY REFERENCES BATCH_STEP_EXECUTION(STEP_EXECUTION_ID),
    SHORT_CONTEXT       VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT  TEXT
);

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID    BIGINT        NOT NULL PRIMARY KEY REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID),
    SHORT_CONTEXT       VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT  TEXT
);

-- ── Quartz Scheduler Schema ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS qrtz_job_details (
    sched_name        VARCHAR(120) NOT NULL,
    job_name          VARCHAR(200) NOT NULL,
    job_group         VARCHAR(200) NOT NULL,
    description       VARCHAR(250),
    job_class_name    VARCHAR(250) NOT NULL,
    is_durable        BOOLEAN      NOT NULL,
    is_nonconcurrent  BOOLEAN      NOT NULL,
    is_update_data    BOOLEAN      NOT NULL,
    requests_recovery BOOLEAN      NOT NULL,
    job_data          BYTEA,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE IF NOT EXISTS qrtz_triggers (
    sched_name     VARCHAR(120) NOT NULL,
    trigger_name   VARCHAR(200) NOT NULL,
    trigger_group  VARCHAR(200) NOT NULL,
    job_name       VARCHAR(200) NOT NULL,
    job_group      VARCHAR(200) NOT NULL,
    description    VARCHAR(250),
    next_fire_time BIGINT,
    prev_fire_time BIGINT,
    priority       INTEGER,
    trigger_state  VARCHAR(16)  NOT NULL,
    trigger_type   VARCHAR(8)   NOT NULL,
    start_time     BIGINT       NOT NULL,
    end_time       BIGINT,
    calendar_name  VARCHAR(200),
    misfire_instr  SMALLINT,
    job_data       BYTEA,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group) REFERENCES qrtz_job_details(sched_name, job_name, job_group)
);

CREATE TABLE IF NOT EXISTS qrtz_cron_triggers (
    sched_name      VARCHAR(120) NOT NULL,
    trigger_name    VARCHAR(200) NOT NULL,
    trigger_group   VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id    VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_fired_triggers (
    sched_name        VARCHAR(120) NOT NULL,
    entry_id          VARCHAR(95)  NOT NULL,
    trigger_name      VARCHAR(200) NOT NULL,
    trigger_group     VARCHAR(200) NOT NULL,
    instance_name     VARCHAR(200) NOT NULL,
    fired_time        BIGINT       NOT NULL,
    sched_time        BIGINT       NOT NULL,
    priority          INTEGER      NOT NULL,
    state             VARCHAR(16)  NOT NULL,
    job_name          VARCHAR(200),
    job_group         VARCHAR(200),
    is_nonconcurrent  BOOLEAN,
    requests_recovery BOOLEAN,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE IF NOT EXISTS qrtz_scheduler_state (
    sched_name        VARCHAR(120) NOT NULL,
    instance_name     VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT       NOT NULL,
    checkin_interval  BIGINT       NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE IF NOT EXISTS qrtz_locks (
    sched_name  VARCHAR(120) NOT NULL,
    lock_name   VARCHAR(40)  NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

-- ── CoB Job Execution History ─────────────────────────────────────────────────
CREATE TABLE cob_job_history (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name        VARCHAR(100)    NOT NULL,
    business_date   DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED','SKIPPED')),
    started_at      TIMESTAMPTZ     NOT NULL,
    completed_at    TIMESTAMPTZ,
    records_processed BIGINT        DEFAULT 0,
    error_message   TEXT,
    triggered_by    VARCHAR(100)    NOT NULL DEFAULT 'SCHEDULER',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_cob_job_date ON cob_job_history(business_date, job_name);
