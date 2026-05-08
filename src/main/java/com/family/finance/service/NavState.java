package com.family.finance.service;

import com.family.finance.domain.family.Family;
import com.family.finance.domain.period.Period;

public record NavState(
        Family family,
        Period currentPeriod,
        int pendingCount,
        String periodLabel
) {
}
