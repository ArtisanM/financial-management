package com.family.finance.factview;

import java.math.BigDecimal;

public record KpiSnapshot(
        BigDecimal netWorth,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal emergencyFundMonths,
        BigDecimal debtToAssetRatio,
        BigDecimal netWorthDelta,
        BigDecimal netWorthDeltaPct
) {
}
