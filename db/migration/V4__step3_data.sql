-- =========================================================
-- 家庭账房 v0.1 · Step 3 demo fact data
-- 12 closed periods through 2026-04 with snapshots, cash flows, transfers, FX
-- =========================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS step3_period_seed;
CREATE TABLE step3_period_seed (
    period_start DATE NOT NULL PRIMARY KEY,
    period_end DATE NOT NULL,
    a1 DECIMAL(18,2), a2 DECIMAL(18,2), a3 DECIMAL(18,2), a4 DECIMAL(18,2), a5 DECIMAL(18,2),
    a6 DECIMAL(18,2), a7 DECIMAL(18,2), a8 DECIMAL(18,2), a9 DECIMAL(18,2), a10 DECIMAL(18,2), a11 DECIMAL(18,2),
    salary DECIMAL(18,2), partner_salary DECIMAL(18,2), consumption DECIMAL(18,2), household DECIMAL(18,2),
    loan_principal DECIMAL(18,2), loan_interest DECIMAL(18,2), usd_rate DECIMAL(18,6), hkd_rate DECIMAL(18,6)
) ENGINE=InnoDB;

INSERT INTO step3_period_seed VALUES
('2025-05-01','2025-05-31', 36000,158000, 960000,42500,-9800,27000,5800,188000,117000,1800000,-1242820,52000,22000,29500,10500,4820,1580,7.190000,0.920000),
('2025-06-01','2025-06-30', 38000,160000, 980000,43000,-9200,28000,6000,190000,118000,1800000,-1238000,52000,22000,30000,11000,4820,1560,7.200000,0.921000),
('2025-07-01','2025-07-31', 40500,162500, 995000,43800,-8800,28600,6300,193500,119000,1800000,-1233180,52000,22000,28500,11200,4820,1540,7.205000,0.922000),
('2025-08-01','2025-08-31', 42100,164000,1012000,44200,-8100,29400,6500,196000,120000,1800000,-1228360,52000,22000,31000,10800,4820,1520,7.210000,0.923000),
('2025-09-01','2025-09-30', 44800,166500,1038000,45100,-7600,30100,6800,199500,121000,1800000,-1223540,52000,22000,29800,11100,4820,1500,7.215000,0.924000),
('2025-10-01','2025-10-31', 46200,168000,1026000,44600,-7200,30800,7100,202000,121500,1800000,-1218720,52000,22000,33500,11600,4820,1480,7.220000,0.925000),
('2025-11-01','2025-11-30', 48500,170500,1054000,45900,-6900,31600,7400,205000,122000,1800000,-1213900,52000,22000,29200,10900,4820,1460,7.215000,0.924000),
('2025-12-01','2025-12-31', 51800,173000,1078000,46600,-7100,32400,7600,208000,123000,1800000,-1209080,62000,26000,36000,12800,4820,1440,7.205000,0.923000),
('2026-01-01','2026-01-31', 50200,174000,1065000,46200,-7600,31800,7800,207000,123500,1800000,-1204260,52000,22000,42000,13000,4820,1420,7.210000,0.923000),
('2026-02-01','2026-02-28', 54000,176000,1088000,46800,-6500,33000,8200,211000,124000,1800000,-1199440,52000,22000,28600,10700,4820,1400,7.218000,0.924000),
('2026-03-01','2026-03-31', 45000,180000,1102400,47500,-6200,33600,8400,214600,125000,1800000,-1200000,52000,22000,30000,11000,4820,1380,7.211000,0.923500),
('2026-04-01','2026-04-30', 73200,186400,1124800,48200,-4820,35400,9200,218400,127800,1800000,-1195180,52000,22000,29600,11200,4820,1360,7.211000,0.923500);

INSERT INTO period (family_id, period_type, period_start, period_end, status, closed_at)
SELECT 1, 'MONTHLY', period_start, period_end, 'CLOSED', DATE_ADD(period_end, INTERVAL 2 DAY)
  FROM step3_period_seed
ON DUPLICATE KEY UPDATE
    period_end = VALUES(period_end),
    status = VALUES(status),
    closed_at = VALUES(closed_at);

INSERT INTO period_snapshot (period_id, account_id, end_balance, submitted_by, submitted_at, note)
SELECT p.id, 1, s.a1, 1, TIMESTAMP(s.period_end, '20:00:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 2, s.a2, 1, TIMESTAMP(s.period_end, '20:01:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 3, s.a3, 1, TIMESTAMP(s.period_end, '20:02:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 4, s.a4, 1, TIMESTAMP(s.period_end, '20:03:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 5, s.a5, 1, TIMESTAMP(s.period_end, '20:04:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 6, s.a6, 2, TIMESTAMP(s.period_end, '20:05:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 7, s.a7, 2, TIMESTAMP(s.period_end, '20:06:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 8, s.a8, 2, TIMESTAMP(s.period_end, '20:07:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 9, s.a9, 2, TIMESTAMP(s.period_end, '20:08:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id,10, s.a10,1, TIMESTAMP(s.period_end, '20:09:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id,11, s.a11,1, TIMESTAMP(s.period_end, '20:10:00'), 'Step 3 趋势种子' FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
ON DUPLICATE KEY UPDATE
    end_balance = VALUES(end_balance),
    submitted_by = VALUES(submitted_by),
    submitted_at = VALUES(submitted_at),
    note = VALUES(note);

INSERT INTO cash_flow (period_id, account_id, kind, category_code, amount, occurred_at, note, submitted_by)
SELECT p.id, 1, 'INCOME',  'salary',        s.salary,         s.period_end, '工资收入', 1 FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 6, 'INCOME',  'salary',        s.partner_salary, s.period_end, '工资收入', 2 FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 1, 'EXPENSE', 'consumption',   s.consumption,    s.period_end, '家庭消费', 1 FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 6, 'EXPENSE', 'consumption',   s.household,      s.period_end, '家庭消费', 2 FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL SELECT p.id, 1, 'EXPENSE', 'interest_paid', s.loan_interest,  s.period_end, '房贷利息', 1 FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start;

INSERT INTO transfer (period_id, from_account_id, to_account_id, amount, occurred_at, note, submitted_by, is_draft)
SELECT p.id, 1, 11, s.loan_principal, s.period_end, '房贷本金还款', 1, 0
  FROM step3_period_seed s
  JOIN period p ON p.family_id=1 AND p.period_start=s.period_start;

INSERT INTO fx_rate (family_id, base_currency, quote_currency, period_id, rate, source)
SELECT 1, 'CNY', 'USD', p.id, s.usd_rate, 'manual-seed'
  FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
UNION ALL
SELECT 1, 'CNY', 'HKD', p.id, s.hkd_rate, 'manual-seed'
  FROM step3_period_seed s JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
ON DUPLICATE KEY UPDATE
    rate = VALUES(rate),
    source = VALUES(source),
    fetched_at = NOW(3);

INSERT IGNORE INTO period_member_completion (period_id, member_id, completed_at)
SELECT p.id, m.id, DATE_ADD(s.period_end, INTERVAL 2 DAY)
  FROM step3_period_seed s
  JOIN period p ON p.family_id=1 AND p.period_start=s.period_start
  JOIN member m ON m.family_id=1 AND m.archived_at IS NULL;

INSERT INTO audit_log (family_id, actor_member_id, type, target_type, target_id, summary)
VALUES (1, NULL, 'SYSTEM', 'migration', 4, 'Step 3 fact demo data loaded');

DROP TABLE step3_period_seed;
