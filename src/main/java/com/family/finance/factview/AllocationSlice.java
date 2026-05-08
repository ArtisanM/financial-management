package com.family.finance.factview;

import java.math.BigDecimal;

public record AllocationSlice(String accountType, String label, BigDecimal value, BigDecimal ratio) {
}
