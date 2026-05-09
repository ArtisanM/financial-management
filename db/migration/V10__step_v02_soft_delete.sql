-- =========================================================
-- V10 · v0.2 软删字段(FR-32)
-- =========================================================
-- 给 cash_flow / transfer 加 deleted_at TIMESTAMP(3) NULL
-- 所有 mapper SELECT 应加 WHERE deleted_at IS NULL(在代码侧改)
-- snapshot.end_balance 不动(权威值);软删后未解释金额会反映差额
-- 对应 PRD § FR-32 / TDD § 决策 2
-- =========================================================

ALTER TABLE cash_flow
    ADD COLUMN deleted_at TIMESTAMP(3) NULL DEFAULT NULL;

ALTER TABLE transfer
    ADD COLUMN deleted_at TIMESTAMP(3) NULL DEFAULT NULL;

-- 索引:加快"过滤未删除"的查询
CREATE INDEX idx_cf_deleted ON cash_flow(deleted_at);
CREATE INDEX idx_tr_deleted ON transfer(deleted_at);
