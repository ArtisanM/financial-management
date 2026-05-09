package com.family.finance.service;

import com.family.finance.domain.account.Account;
import com.family.finance.domain.category.ProductCategory;
import com.family.finance.domain.snapshot.PeriodSnapshot;

import java.math.BigDecimal;

public record AccountRow(
        Account account,
        String ownerName,
        String paymentSourceName,
        PeriodSnapshot currentSnapshot,
        BigDecimal currentBalance,
        String balanceLabel,
        boolean archived,
        /** v0.2 · 关联的产品类目(可能 null,例如未回填的旧账户)· FR-40d */
        ProductCategory category
) {
    /** 实际风险等级:override 优先,否则用类目 risk_level */
    public Integer effectiveRiskLevel() {
        Integer override = account.getRiskLevelOverride();
        if (override != null && override > 0) return override;
        return category != null ? category.getRiskLevel() : null;
    }

    /** 风险星级字符串(★1-6) */
    public String riskStars() {
        Integer level = effectiveRiskLevel();
        if (level == null || level <= 0) return "—";
        return "★".repeat(Math.min(level, 6));
    }

    /** 用户是否手工覆盖了风险(UI 角标 ✎ 用) */
    public boolean riskOverridden() {
        Integer override = account.getRiskLevelOverride();
        return override != null && override > 0;
    }
}
