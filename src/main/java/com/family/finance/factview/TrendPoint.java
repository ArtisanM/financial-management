package com.family.finance.factview;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendPoint(Long periodId, LocalDate periodStart, String label, BigDecimal value) {
}
