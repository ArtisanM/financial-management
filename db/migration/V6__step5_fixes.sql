-- =========================================================
-- v0.1 · Step 5 收尾修补
-- 1) period_reopen_log — PRD FR-12 验收要求的专表
-- 2) metrics_recompute_log — TDD § 4.3 / FR-11 提及的专表
-- =========================================================
SET NAMES utf8mb4;

CREATE TABLE period_reopen_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    period_id       BIGINT       NOT NULL,
    reopened_by     BIGINT       NULL,                  -- member.id;系统操作时为 NULL
    reopened_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reason          VARCHAR(500) NOT NULL,
    CONSTRAINT pk_period_reopen_log     PRIMARY KEY (id),
    CONSTRAINT fk_prl_period            FOREIGN KEY (period_id)   REFERENCES period(id),
    CONSTRAINT fk_prl_member            FOREIGN KEY (reopened_by) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_prl_period_time ON period_reopen_log(period_id, reopened_at);

CREATE TABLE metrics_recompute_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    family_id       BIGINT       NOT NULL,
    period_id       BIGINT       NOT NULL,
    started_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at    DATETIME(3)  NULL,
    duration_ms     INT          NULL,
    identity_ok     TINYINT(1)   NULL,                 -- 1=PRD § 5.16 主恒等式通过
    deviation       DECIMAL(18,2) NULL,                -- 不通过时的偏差金额
    error_message   VARCHAR(500) NULL,
    CONSTRAINT pk_mrl                 PRIMARY KEY (id),
    CONSTRAINT fk_mrl_family          FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_mrl_period          FOREIGN KEY (period_id) REFERENCES period(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_mrl_family_started ON metrics_recompute_log(family_id, started_at);
