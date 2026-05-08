package com.family.finance.factview;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface FactViewService {
    FactSlice loadDefault(Long familyId);

    FactSlice load(FactFilter filter);

    KpiSnapshot kpis(FactSlice slice);

    List<TrendPoint> netWorthTrend(FactSlice slice);

    List<AllocationSlice> allocationByType(FactSlice slice, Long periodId);

    List<WaterfallSegment> incomeExpenseWaterfall(FactSlice slice);

    BigDecimal savingsRate(FactSlice slice);

    Map<Long, BigDecimal> accountXirr(FactSlice slice);

    BigDecimal familyXirr(FactSlice slice);

    BigDecimal familyTwr(FactSlice slice);

    List<DecompositionPoint> principalVsReturnDecomposition(FactSlice slice);

    List<TrendPoint> debtTrend(FactSlice slice);

    List<AccountPerformance> accountPerformance(FactSlice slice);
}
