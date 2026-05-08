package com.family.finance.factview;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record FactSlice(
        FactFilter filter,
        List<AccountPeriodFact> rows,
        List<Long> periodIds,
        Long lastPeriodId
) {
    public Map<Long, List<AccountPeriodFact>> byAccount() {
        return rows.stream()
                .collect(Collectors.groupingBy(AccountPeriodFact::accountId, LinkedHashMap::new, Collectors.toList()));
    }

    public Map<Long, List<AccountPeriodFact>> byPeriod() {
        return rows.stream()
                .sorted(Comparator.comparing(AccountPeriodFact::periodStart).thenComparing(AccountPeriodFact::displayOrder))
                .collect(Collectors.groupingBy(AccountPeriodFact::periodId, LinkedHashMap::new, Collectors.toList()));
    }
}
