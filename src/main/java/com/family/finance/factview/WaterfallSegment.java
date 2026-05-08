package com.family.finance.factview;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WaterfallSegment(
        Long periodId,
        LocalDate periodStart,
        String label,
        BigDecimal baseline,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal pnl,
        BigDecimal ending
) {
}
