package com.family.finance.service.checkup;

import com.family.finance.domain.account.AccountClass;
import com.family.finance.domain.account.AccountLiquidity;
import com.family.finance.domain.category.ProductCategory;
import com.family.finance.domain.family.Family;
import com.family.finance.domain.period.Period;
import com.family.finance.factview.AccountPeriodFact;
import com.family.finance.factview.AllocationSlice;
import com.family.finance.factview.FactFilter;
import com.family.finance.factview.FactSlice;
import com.family.finance.factview.FactViewService;
import com.family.finance.factview.KpiSnapshot;
import com.family.finance.repository.FamilyMapper;
import com.family.finance.repository.PeriodMapper;
import com.family.finance.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 全家体检主聚合 · v0.2 · FR-40a
 *
 * 用 {@link FactViewService} 已有方法取 KPI / Allocation,补充风险分布与家庭加权年化。
 */
@Service
@RequiredArgsConstructor
public class FamilyDiagnoseService {

    private final FactViewService factViewService;
    private final ProductCategoryService productCategoryService;
    private final FamilyMapper familyMapper;
    private final PeriodMapper periodMapper;

    public FamilyDiagnose diagnose(long familyId) {
        // v0.2 bug 修(2026-05-10): 旧实现 factViewService.loadDefault 用 LocalDate.now() 作 end,
        // 当用户测试期间生成了未来期(>当前日期),那些期会被排除,与 /dashboard(用 latest period 作 anchor)不一致。
        // 改为与 dashboard 同口径:取 latest period(无论 OPEN/CLOSED)作 end,前推 11 个月。
        Family family = familyMapper.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("家庭不存在: " + familyId));
        Period latest = periodMapper.findLatest(familyId, 1).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("尚未创建周期"));
        java.time.LocalDate end = latest.getPeriodStart();
        java.time.LocalDate start = end.minusMonths(11);
        FactSlice slice = factViewService.load(new FactFilter(
                familyId, family.getPeriodType(), start, end, false, null, family.getBaseCurrency()));
        KpiSnapshot kpi = factViewService.kpis(slice);
        List<AllocationSlice> allocation = factViewService.allocationByType(slice, slice.lastPeriodId());
        BigDecimal familyXirr = factViewService.familyXirr(slice);
        BigDecimal familyTwr = factViewService.familyTwr(slice);

        // 风险分布:用最后一期账户余额 × fxToBase + product_category.risk_level
        Map<String, ProductCategory> categoriesByCode = productCategoryService.listAll().stream()
                .collect(java.util.stream.Collectors.toMap(ProductCategory::getCode, java.util.function.Function.identity()));

        List<AccountPeriodFact> lastRows = slice.rows().stream()
                .filter(r -> Objects.equals(r.periodId(), slice.lastPeriodId()))
                .filter(r -> r.accountClass() == AccountClass.ASSET)
                .filter(r -> r.endBalanceBase() != null)
                .toList();

        // 取每个账户的 product_category_code(查 account 字段)— 这里通过 displayOrder + name 不够,直接 SQL 查也可以
        // 简化:用 FactBaseRow 没有 productCategoryCode,我们重新用 AccountService 拉账户列表
        // 但为了纯函数 + 性能,这里 map 通过 accountId 查 account 表
        Map<Integer, BigDecimal> riskAmountByLevel = new java.util.TreeMap<>();
        BigDecimal totalAssetBase = BigDecimal.ZERO;
        for (AccountPeriodFact row : lastRows) {
            BigDecimal v = row.endBalanceBase();
            if (v == null) continue;
            totalAssetBase = totalAssetBase.add(v);
            // category 暂时拿不到(FactBaseRow 没带过来),用账户类型映射:
            // STOCK→3, WEALTH→2, CASH→1, PROPERTY→2, OTHER→0
            // 这是 fallback;Stage 3.2 会引入完整 RuleContext 时再升级到真正的 product_category_code
            int level = fallbackRisk(row.accountType().name());
            riskAmountByLevel.merge(level, v, BigDecimal::add);
        }

        List<FamilyDiagnose.RiskBucket> riskDist = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : riskAmountByLevel.entrySet()) {
            BigDecimal amt = e.getValue().setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal ratio = totalAssetBase.signum() == 0 ? BigDecimal.ZERO :
                    amt.divide(totalAssetBase, 6, RoundingMode.HALF_EVEN);
            riskDist.add(new FamilyDiagnose.RiskBucket(e.getKey(), riskLabel(e.getKey()), amt, ratio));
        }

        BigDecimal liquidAssets = slice.rows().stream()
                .filter(r -> Objects.equals(r.periodId(), slice.lastPeriodId()))
                .filter(r -> r.accountLiquidity() == AccountLiquidity.LIQUID)
                .map(AccountPeriodFact::endBalanceBase)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal cumulativeYtdPnl = slice.rows().stream()
                .filter(r -> r.periodStart() != null
                        && r.periodStart().getYear() == java.time.LocalDate.now().getYear())
                .map(AccountPeriodFact::periodPnlBase)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);

        int accountCount = (int) slice.rows().stream()
                .filter(r -> Objects.equals(r.periodId(), slice.lastPeriodId()))
                .map(AccountPeriodFact::accountId)
                .distinct()
                .count();

        return new FamilyDiagnose(
                kpi,
                allocation,
                riskDist,
                liquidAssets,
                kpi.emergencyFundMonths(),
                familyXirr,
                familyTwr,
                cumulativeYtdPnl,
                accountCount,
                0  // pending TODO 接入 SnapshotTodoMapper(此值仅 banner 用,现阶段不阻塞)
        );
    }

    private static int fallbackRisk(String type) {
        return switch (type) {
            case "STOCK" -> 4;       // 中
            case "WEALTH" -> 2;      // 低
            case "CASH" -> 1;        // 极低
            case "PROPERTY" -> 2;    // 低
            case "OTHER" -> 0;
            case "LOAN" -> 0;
            default -> 0;
        };
    }

    private static String riskLabel(int level) {
        return switch (level) {
            case 0 -> "无风险";
            case 1 -> "极低";
            case 2 -> "低";
            case 3 -> "中低";
            case 4 -> "中";
            case 5 -> "中高";
            case 6 -> "极高";
            default -> "未知";
        };
    }
}
