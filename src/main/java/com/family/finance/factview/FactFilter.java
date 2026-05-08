package com.family.finance.factview;

import com.family.finance.domain.period.PeriodType;

import java.time.LocalDate;
import java.util.List;

public record FactFilter(
        long familyId,
        PeriodType periodType,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        boolean includeArchived,
        List<Long> accountIds,
        String viewCurrency
) {
    public FactFilter {
        if (periodType == null) {
            throw new IllegalArgumentException("periodType is required");
        }
        if (rangeStart == null || rangeEnd == null) {
            throw new IllegalArgumentException("rangeStart/rangeEnd are required");
        }
        if (rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("rangeStart must be before rangeEnd");
        }
        accountIds = accountIds == null || accountIds.isEmpty() ? null : List.copyOf(accountIds);
        viewCurrency = viewCurrency == null || viewCurrency.isBlank() ? "CNY" : viewCurrency.toUpperCase();
    }
}
