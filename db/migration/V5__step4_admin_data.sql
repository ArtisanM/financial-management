-- =========================================================
-- 家庭账房 v0.1 · Step 4 admin 数据
-- 1) backup_log 表 + 几条样例
-- 2) 把 V4 已有的若干 fx_rate 行补全到所有 CLOSED 周期
-- =========================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------
-- backup_log — 每周备份的状态历史
-- ---------------------------------------------------------
CREATE TABLE backup_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    family_id       BIGINT       NOT NULL,
    kind            VARCHAR(8)   NOT NULL DEFAULT 'WEEKLY',
    status          VARCHAR(8)   NOT NULL,
    size_bytes      BIGINT       NULL,
    location_local  VARCHAR(255) NULL,
    location_remote VARCHAR(255) NULL,
    error_message   VARCHAR(500) NULL,
    started_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at    DATETIME(3)  NULL,
    CONSTRAINT pk_backup_log    PRIMARY KEY (id),
    CONSTRAINT fk_backup_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT ck_backup_kind   CHECK (kind   IN ('WEEKLY','MANUAL')),
    CONSTRAINT ck_backup_status CHECK (status IN ('SUCCESS','FAILED','RUNNING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_backup_family_started ON backup_log(family_id, started_at);

-- 样例:近 8 周备份,其中 1 次失败
INSERT INTO backup_log (family_id, kind, status, size_bytes, location_local, location_remote, started_at, completed_at)
VALUES
  (1, 'WEEKLY', 'SUCCESS', 2412034, '/var/backup/finance/2026-05-04.sql.gz', 'oss://family-finance/2026-05-04.sql.gz',
   '2026-05-04 03:00:00', '2026-05-04 03:00:18'),
  (1, 'WEEKLY', 'SUCCESS', 2390122, '/var/backup/finance/2026-04-27.sql.gz', 'oss://family-finance/2026-04-27.sql.gz',
   '2026-04-27 03:00:00', '2026-04-27 03:00:17'),
  (1, 'WEEKLY', 'SUCCESS', 2356477, '/var/backup/finance/2026-04-20.sql.gz', 'oss://family-finance/2026-04-20.sql.gz',
   '2026-04-20 03:00:00', '2026-04-20 03:00:15'),
  (1, 'WEEKLY', 'FAILED', NULL, '/var/backup/finance/2026-04-13.sql.gz', NULL,
   '2026-04-13 03:00:00', '2026-04-13 03:00:32'),
  (1, 'WEEKLY', 'SUCCESS', 2305988, '/var/backup/finance/2026-04-06.sql.gz', 'oss://family-finance/2026-04-06.sql.gz',
   '2026-04-06 03:00:00', '2026-04-06 03:00:14'),
  (1, 'WEEKLY', 'SUCCESS', 2293119, '/var/backup/finance/2026-03-30.sql.gz', 'oss://family-finance/2026-03-30.sql.gz',
   '2026-03-30 03:00:00', '2026-03-30 03:00:15'),
  (1, 'WEEKLY', 'SUCCESS', 2271100, '/var/backup/finance/2026-03-23.sql.gz', 'oss://family-finance/2026-03-23.sql.gz',
   '2026-03-23 03:00:00', '2026-03-23 03:00:13'),
  (1, 'WEEKLY', 'SUCCESS', 2245533, '/var/backup/finance/2026-03-16.sql.gz', 'oss://family-finance/2026-03-16.sql.gz',
   '2026-03-16 03:00:00', '2026-03-16 03:00:13');

-- 失败行的错误信息
UPDATE backup_log SET error_message = '远端 OSS 网络超时(已重试 3 次)' WHERE status = 'FAILED';
