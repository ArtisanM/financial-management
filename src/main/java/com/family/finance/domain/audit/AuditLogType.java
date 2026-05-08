package com.family.finance.domain.audit;

public enum AuditLogType {
    ACCOUNT_CREATE,
    ACCOUNT_UPDATE,
    ACCOUNT_ARCHIVE,
    ACCOUNT_RESTORE,
    FAMILY_UPDATE,
    PERIOD_OPEN,
    PERIOD_CLOSE,
    PERIOD_REOPEN,
    TRANSFER_CREATE,
    /** 现金流登记 / 修改 */
    CASH_FLOW_WRITE,
    /** 余额快照写入 / 覆盖 */
    SNAPSHOT_WRITE,
    /** 汇率拉取 / 手填 */
    FX_FETCH,
    /** 备份 success/fail */
    BACKUP,
    /** 密码重置 */
    PASSWORD_RESET,
    /** Logo 上传 */
    LOGO_UPLOAD,
    /** Metrics 重算 */
    METRICS_RECOMPUTE,
    /** CSV 一键导出 */
    EXPORT,
    SYSTEM
}
