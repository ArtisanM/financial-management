-- =========================================================
-- 家庭账房 v0.1 · 初始 schema
-- 对应 TDD § 3.1 / § 3.2
-- =========================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------
-- family — 顶层租户(v0.1 仅 1 行)
-- ---------------------------------------------------------
CREATE TABLE family (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(60)  NOT NULL,
    brand_text      VARCHAR(60)  NOT NULL DEFAULT '账房',
    logo_path       VARCHAR(255) NULL,
    base_currency   CHAR(3)      NOT NULL DEFAULT 'CNY',
    period_type     VARCHAR(8)   NOT NULL DEFAULT 'MONTHLY',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_family            PRIMARY KEY (id),
    CONSTRAINT ck_family_currency   CHECK (base_currency IN ('CNY','USD','HKD')),
    CONSTRAINT ck_family_period     CHECK (period_type   IN ('MONTHLY','WEEKLY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- member — 成员
-- ---------------------------------------------------------
CREATE TABLE member (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    family_id       BIGINT       NOT NULL,
    username        VARCHAR(40)  NOT NULL,
    password_hash   CHAR(60)     NOT NULL,
    display_name    VARCHAR(40)  NOT NULL,
    role_label      VARCHAR(20)  NULL,
    must_change_pw  TINYINT(1)   NOT NULL DEFAULT 0,
    archived_at     DATETIME(3)  NULL,
    last_login_at   DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_member          PRIMARY KEY (id),
    CONSTRAINT uk_member_username UNIQUE (username),
    CONSTRAINT fk_member_family   FOREIGN KEY (family_id) REFERENCES family(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- account_template — 内置账户模板
-- ---------------------------------------------------------
CREATE TABLE account_template (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    code                VARCHAR(40) NOT NULL,
    display_name        VARCHAR(60) NOT NULL,
    type                VARCHAR(10) NOT NULL,
    default_currency    CHAR(3)     NOT NULL DEFAULT 'CNY',
    icon                VARCHAR(40) NULL,
    sort_order          INT         NOT NULL DEFAULT 0,
    is_custom_slot      TINYINT(1)  NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_template      PRIMARY KEY (id),
    CONSTRAINT uk_account_template      UNIQUE (code),
    CONSTRAINT ck_account_template_type CHECK (type IN ('STOCK','CASH','WEALTH','PROPERTY','LOAN','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- account — 账户
-- ---------------------------------------------------------
CREATE TABLE account (
    id                                  BIGINT       NOT NULL AUTO_INCREMENT,
    family_id                           BIGINT       NOT NULL,
    template_id                         BIGINT       NULL,
    display_name                        VARCHAR(80)  NOT NULL,
    type                                VARCHAR(10)  NOT NULL,
    currency                            CHAR(3)      NOT NULL,
    primary_owner_member_id             BIGINT       NULL,
    default_payment_source_account_id   BIGINT       NULL,
    display_order                       INT          NOT NULL DEFAULT 0,
    archived_at                         DATETIME(3)  NULL,
    created_at                          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_account                  PRIMARY KEY (id),
    CONSTRAINT fk_account_family           FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_account_template         FOREIGN KEY (template_id) REFERENCES account_template(id),
    CONSTRAINT fk_account_owner            FOREIGN KEY (primary_owner_member_id) REFERENCES member(id),
    CONSTRAINT fk_account_payment_source   FOREIGN KEY (default_payment_source_account_id) REFERENCES account(id),
    CONSTRAINT ck_account_type             CHECK (type IN ('STOCK','CASH','WEALTH','PROPERTY','LOAN','OTHER')),
    CONSTRAINT ck_account_currency         CHECK (currency IN ('CNY','USD','HKD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- period — 周期
-- ---------------------------------------------------------
CREATE TABLE period (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    family_id       BIGINT       NOT NULL,
    period_type     VARCHAR(8)   NOT NULL,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    status          VARCHAR(8)   NOT NULL DEFAULT 'OPEN',
    closed_at       DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_period            PRIMARY KEY (id),
    CONSTRAINT uk_period_natural    UNIQUE (family_id, period_type, period_start),
    CONSTRAINT fk_period_family     FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT ck_period_status     CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_period_type       CHECK (period_type IN ('MONTHLY','WEEKLY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- period_snapshot — 期末余额
-- ---------------------------------------------------------
CREATE TABLE period_snapshot (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    period_id        BIGINT         NOT NULL,
    account_id       BIGINT         NOT NULL,
    end_balance      DECIMAL(18,2)  NOT NULL,
    submitted_by     BIGINT         NOT NULL,
    submitted_at     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    note             VARCHAR(255)   NULL,
    CONSTRAINT pk_period_snapshot       PRIMARY KEY (id),
    CONSTRAINT uk_period_snapshot       UNIQUE (period_id, account_id),
    CONSTRAINT fk_period_snapshot_p     FOREIGN KEY (period_id)  REFERENCES period(id),
    CONSTRAINT fk_period_snapshot_a     FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_period_snapshot_by    FOREIGN KEY (submitted_by) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- cash_flow — 现金流
-- ---------------------------------------------------------
CREATE TABLE cash_flow (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    period_id       BIGINT         NOT NULL,
    account_id      BIGINT         NOT NULL,
    kind            VARCHAR(8)     NOT NULL,
    category_code   VARCHAR(40)    NOT NULL,
    amount          DECIMAL(18,2)  NOT NULL,
    occurred_at     DATE           NULL,
    note            VARCHAR(255)   NULL,
    submitted_by    BIGINT         NOT NULL,
    submitted_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_cash_flow         PRIMARY KEY (id),
    CONSTRAINT fk_cash_flow_p       FOREIGN KEY (period_id)  REFERENCES period(id),
    CONSTRAINT fk_cash_flow_a       FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_cash_flow_by      FOREIGN KEY (submitted_by) REFERENCES member(id),
    CONSTRAINT ck_cash_flow_kind    CHECK (kind IN ('INCOME','EXPENSE')),
    CONSTRAINT ck_cash_flow_amount  CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- cash_flow_category — 类别字典
-- ---------------------------------------------------------
CREATE TABLE cash_flow_category (
    code            VARCHAR(40) NOT NULL,
    display_name    VARCHAR(40) NOT NULL,
    kind            VARCHAR(8)  NOT NULL,
    sort_order      INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_cash_flow_category    PRIMARY KEY (code),
    CONSTRAINT ck_cash_flow_cat_kind    CHECK (kind IN ('INCOME','EXPENSE','BOTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- transfer — 跨账户转账
-- ---------------------------------------------------------
CREATE TABLE transfer (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    period_id       BIGINT         NOT NULL,
    from_account_id BIGINT         NOT NULL,
    to_account_id   BIGINT         NOT NULL,
    amount          DECIMAL(18,2)  NOT NULL,
    occurred_at     DATE           NULL,
    note            VARCHAR(255)   NULL,
    submitted_by    BIGINT         NOT NULL,
    submitted_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_draft        TINYINT(1)     NOT NULL DEFAULT 0,
    CONSTRAINT pk_transfer            PRIMARY KEY (id),
    CONSTRAINT fk_transfer_p          FOREIGN KEY (period_id)       REFERENCES period(id),
    CONSTRAINT fk_transfer_from       FOREIGN KEY (from_account_id) REFERENCES account(id),
    CONSTRAINT fk_transfer_to         FOREIGN KEY (to_account_id)   REFERENCES account(id),
    CONSTRAINT fk_transfer_by         FOREIGN KEY (submitted_by)    REFERENCES member(id),
    CONSTRAINT ck_transfer_distinct   CHECK (from_account_id <> to_account_id),
    CONSTRAINT ck_transfer_amount     CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- fx_rate — 期末汇率
-- ---------------------------------------------------------
CREATE TABLE fx_rate (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    family_id       BIGINT         NOT NULL,
    base_currency   CHAR(3)        NOT NULL,
    quote_currency  CHAR(3)        NOT NULL,
    period_id       BIGINT         NOT NULL,
    rate            DECIMAL(18,6)  NOT NULL,
    source          VARCHAR(40)    NOT NULL,
    fetched_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_fx_rate           PRIMARY KEY (id),
    CONSTRAINT uk_fx_rate_natural   UNIQUE (family_id, base_currency, quote_currency, period_id),
    CONSTRAINT fk_fx_rate_family    FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_fx_rate_period    FOREIGN KEY (period_id) REFERENCES period(id),
    CONSTRAINT ck_fx_rate_positive  CHECK (rate > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- snapshot_todo — 待办
-- ---------------------------------------------------------
CREATE TABLE snapshot_todo (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    period_id               BIGINT       NOT NULL,
    account_id              BIGINT       NOT NULL,
    assigned_member_id      BIGINT       NULL,
    status                  VARCHAR(8)   NOT NULL DEFAULT 'PENDING',
    done_at                 DATETIME(3)  NULL,
    done_by_member_id       BIGINT       NULL,
    prefilled_balance       DECIMAL(18,2) NULL,
    prefilled_transfer_id   BIGINT       NULL,
    CONSTRAINT pk_snapshot_todo            PRIMARY KEY (id),
    CONSTRAINT uk_snapshot_todo_natural    UNIQUE (period_id, account_id),
    CONSTRAINT fk_snapshot_todo_p          FOREIGN KEY (period_id) REFERENCES period(id),
    CONSTRAINT fk_snapshot_todo_a          FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_snapshot_todo_assignee   FOREIGN KEY (assigned_member_id) REFERENCES member(id),
    CONSTRAINT fk_snapshot_todo_done_by    FOREIGN KEY (done_by_member_id)  REFERENCES member(id),
    CONSTRAINT fk_snapshot_todo_pre_xfer   FOREIGN KEY (prefilled_transfer_id) REFERENCES transfer(id),
    CONSTRAINT ck_snapshot_todo_status     CHECK (status IN ('PENDING','DONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- period_member_completion
-- ---------------------------------------------------------
CREATE TABLE period_member_completion (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    period_id       BIGINT       NOT NULL,
    member_id       BIGINT       NOT NULL,
    completed_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_pmc       PRIMARY KEY (id),
    CONSTRAINT uk_pmc       UNIQUE (period_id, member_id),
    CONSTRAINT fk_pmc_p     FOREIGN KEY (period_id) REFERENCES period(id),
    CONSTRAINT fk_pmc_m     FOREIGN KEY (member_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- audit_log
-- ---------------------------------------------------------
CREATE TABLE audit_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    family_id       BIGINT        NOT NULL,
    actor_member_id BIGINT        NULL,
    type            VARCHAR(40)   NOT NULL,
    target_type     VARCHAR(40)   NULL,
    target_id       BIGINT        NULL,
    summary         VARCHAR(255)  NOT NULL,
    payload_json    JSON          NULL,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_audit_log     PRIMARY KEY (id),
    CONSTRAINT fk_audit_family  FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_audit_actor   FOREIGN KEY (actor_member_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------
-- persistent_logins — Spring Security RememberMe
-- ---------------------------------------------------------
CREATE TABLE persistent_logins (
    series      VARCHAR(64)  NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    token       VARCHAR(64)  NOT NULL,
    last_used   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (series)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =========================================================
-- 索引(高频查询路径)· 详见 TDD § 3.2
-- =========================================================
CREATE INDEX ix_period_snapshot_period      ON period_snapshot(period_id);
CREATE INDEX ix_cash_flow_period_account    ON cash_flow(period_id, account_id);
CREATE INDEX ix_transfer_period_from        ON transfer(period_id, from_account_id);
CREATE INDEX ix_transfer_period_to          ON transfer(period_id, to_account_id);
CREATE INDEX ix_period_family_start         ON period(family_id, period_start);
CREATE INDEX ix_todo_period_assignee_status ON snapshot_todo(period_id, assigned_member_id, status);
CREATE INDEX ix_audit_family_created        ON audit_log(family_id, created_at);
CREATE INDEX ix_transfer_dup_check          ON transfer(period_id, from_account_id, to_account_id, amount);
CREATE INDEX ix_fx_rate_period              ON fx_rate(period_id, quote_currency);
