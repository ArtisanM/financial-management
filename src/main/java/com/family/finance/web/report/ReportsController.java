package com.family.finance.web.report;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.account.Account;
import com.family.finance.domain.family.Family;
import com.family.finance.domain.fx.FxRate;
import com.family.finance.domain.period.Period;
import com.family.finance.domain.period.PeriodStatus;
import com.family.finance.factview.AccountPeriodFact;
import com.family.finance.factview.AccountPerformance;
import com.family.finance.factview.DecompositionPoint;
import com.family.finance.factview.FactFilter;
import com.family.finance.factview.FactSlice;
import com.family.finance.factview.FactViewService;
import com.family.finance.factview.TrendPoint;
import com.family.finance.factview.WaterfallSegment;
import com.family.finance.repository.AccountMapper;
import com.family.finance.repository.FxMapper;
import com.family.finance.repository.PeriodMapper;
import com.family.finance.service.FamilyService;
import com.family.finance.service.NavService;
import com.family.finance.service.checkup.FamilyDiagnose;
import com.family.finance.service.checkup.FamilyDiagnoseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ReportsController {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0");

    private final FactViewService factViewService;
    private final FamilyService familyService;
    private final PeriodMapper periodMapper;
    private final AccountMapper accountMapper;
    private final FxMapper fxMapper;
    private final NavService navService;
    private final FamilyDiagnoseService familyDiagnoseService;

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal MemberPrincipal me,
                          @RequestParam(defaultValue = "1Y") String range,
                          @RequestParam(name = "accounts", required = false) List<Long> accounts,
                          @RequestParam(required = false) String currency,
                          @RequestHeader(value = "HX-Request", required = false) String htmx,
                          Model model) {
        String accountsCsv = accounts == null || accounts.isEmpty()
                ? null
                : accounts.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        populateModel(me, range, accountsCsv, currency, model);
        if ("true".equalsIgnoreCase(htmx)) {
            return "reports/_region :: region";
        }
        return "reports/index";
    }

    @GetMapping("/reports/period/{periodId}")
    public String periodDrilldown(@AuthenticationPrincipal MemberPrincipal me,
                                  @PathVariable long periodId,
                                  Model model) {
        Family family = familyService.require(me.getFamilyId());
        Period period = periodMapper.findById(periodId)
                .filter(p -> p.getFamilyId() == me.getFamilyId())
                .orElseThrow(() -> new IllegalArgumentException("周期不存在: " + periodId));
        FactSlice slice = factViewService.load(new FactFilter(
                me.getFamilyId(), period.getPeriodType(), period.getPeriodStart(), period.getPeriodStart(),
                false, null, family.getBaseCurrency()));
        List<AccountPeriodFact> rows = slice.rows().stream()
                .filter(row -> row.periodId().equals(periodId))
                .toList();
        model.addAttribute("period", period);
        model.addAttribute("rows", rows);
        model.addAttribute("currency", family.getBaseCurrency());
        return "reports/_drilldown :: modal";
    }

    private void populateModel(MemberPrincipal me, String range, String accountsCsv, String currency, Model model) {
        Family family = familyService.require(me.getFamilyId());
        Period anchor = anchorPeriod(me.getFamilyId());
        List<Long> accountIds = parseAccountIds(accountsCsv);
        String viewCurrency = parseCurrency(currency, family.getBaseCurrency());
        FactSlice slice = factViewService.load(new FactFilter(
                me.getFamilyId(),
                family.getPeriodType(),
                rangeStart(range, anchor.getPeriodStart()),
                anchor.getPeriodStart(),
                false,
                accountIds,
                viewCurrency
        ));
        List<WaterfallSegment> waterfall = factViewService.incomeExpenseWaterfall(slice);
        List<DecompositionPoint> decomposition = factViewService.principalVsReturnDecomposition(slice);
        List<TrendPoint> debtTrend = factViewService.debtTrend(slice);
        List<AccountPerformance> accountRows = factViewService.accountPerformance(slice);
        DecompositionPoint lastDecomposition = decomposition.isEmpty() ? null : decomposition.getLast();
        List<Account> allAccounts = accountMapper.findActiveByFamily(me.getFamilyId());
        List<FxRate> fxRates = fxMapper.findLatestByFamily(me.getFamilyId(), 36);

        model.addAttribute("me", me);
        model.addAttribute("nav", navService.load(me));
        model.addAttribute("range", normalizeRange(range));
        model.addAttribute("currency", viewCurrency);
        model.addAttribute("ranges", List.of("1M", "3M", "6M", "YTD", "1Y", "ALL"));
        model.addAttribute("currencies", List.of("CNY", "USD", "HKD"));
        model.addAttribute("accountsCsv", accountsCsv == null ? "" : accountsCsv);
        model.addAttribute("selectedAccountCount", accountIds == null ? allAccounts.size() : accountIds.size());
        model.addAttribute("allAccounts", allAccounts);
        model.addAttribute("anchorPeriod", anchor);

        model.addAttribute("familyXirr", percent(factViewService.familyXirr(slice)));
        model.addAttribute("familyTwr", percent(factViewService.familyTwr(slice)));
        model.addAttribute("cumulativeNetInflow", money(viewCurrency, lastDecomposition == null ? BigDecimal.ZERO : lastDecomposition.cumulativeNetInflow()));
        model.addAttribute("cumulativePnl", money(viewCurrency, lastDecomposition == null ? BigDecimal.ZERO : lastDecomposition.cumulativePnl()));
        model.addAttribute("waterfall", waterfall);
        model.addAttribute("decomposition", decomposition);
        model.addAttribute("debtTrend", debtTrend);
        model.addAttribute("accountRows", accountRows);
        model.addAttribute("fxRates", fxRates);
        model.addAttribute("sankeyNodes", sankeyNodes(slice));
        model.addAttribute("sankeyLinks", sankeyLinks(slice));

        model.addAttribute("labels", waterfall.stream().map(WaterfallSegment::label).toList());
        model.addAttribute("periodIds", waterfall.stream().map(WaterfallSegment::periodId).toList());
        model.addAttribute("incomeValues", waterfall.stream().map(WaterfallSegment::income).toList());
        model.addAttribute("expenseValues", waterfall.stream().map(WaterfallSegment::expense).toList());
        model.addAttribute("pnlValues", waterfall.stream().map(WaterfallSegment::pnl).toList());
        model.addAttribute("decompPrincipal", decomposition.stream().map(DecompositionPoint::cumulativeNetInflow).toList());
        model.addAttribute("decompPnl", decomposition.stream().map(DecompositionPoint::cumulativePnl).toList());
        model.addAttribute("debtValues", debtTrend.stream().map(TrendPoint::value).toList());

        // v0.2 FR-40e · 风险等级分布(用于 reports 风险敞口环形图)
        FamilyDiagnose familyDiagnose = familyDiagnoseService.diagnose(me.getFamilyId());
        model.addAttribute("riskDistribution", familyDiagnose.riskDistribution());
        model.addAttribute("riskLabels", familyDiagnose.riskDistribution().stream()
                .map(FamilyDiagnose.RiskBucket::label).toList());
        model.addAttribute("riskValues", familyDiagnose.riskDistribution().stream()
                .map(FamilyDiagnose.RiskBucket::amount).toList());
        model.addAttribute("riskRatios", familyDiagnose.riskDistribution().stream()
                .map(b -> b.ratio() == null ? BigDecimal.ZERO
                        : b.ratio().multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_EVEN))
                .toList());
    }

    private List<Map<String, Object>> sankeyNodes(FactSlice slice) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        nodes.put("外部收入", Map.of("name", "外部收入"));
        slice.rows().stream()
                .filter(row -> row.incomeBase().signum() > 0)
                .forEach(row -> nodes.putIfAbsent(row.accountName(), Map.of("name", row.accountName())));
        return List.copyOf(nodes.values());
    }

    private List<Map<String, Object>> sankeyLinks(FactSlice slice) {
        return slice.rows().stream()
                .filter(row -> row.incomeBase().signum() > 0)
                .collect(java.util.stream.Collectors.groupingBy(AccountPeriodFact::accountName, LinkedHashMap::new,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, AccountPeriodFact::incomeBase, BigDecimal::add)))
                .entrySet().stream()
                .map(entry -> Map.<String, Object>of("source", "外部收入", "target", entry.getKey(), "value", entry.getValue()))
                .toList();
    }

    private Period anchorPeriod(long familyId) {
        return periodMapper.findLatest(familyId, 120).stream()
                .filter(period -> period.getStatus() == PeriodStatus.CLOSED)
                .findFirst()
                .or(() -> periodMapper.findLatest(familyId, 1).stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("尚未创建周期"));
    }

    private LocalDate rangeStart(String range, LocalDate anchor) {
        return switch (normalizeRange(range)) {
            case "1M" -> anchor;
            case "3M" -> anchor.minusMonths(2);
            case "6M" -> anchor.minusMonths(5);
            case "YTD" -> anchor.withDayOfYear(1);
            case "ALL" -> LocalDate.of(1970, 1, 1);
            default -> anchor.minusMonths(11);
        };
    }

    private String normalizeRange(String range) {
        if (range == null) {
            return "1Y";
        }
        return switch (range.toUpperCase()) {
            case "1M", "3M", "6M", "YTD", "ALL" -> range.toUpperCase();
            default -> "1Y";
        };
    }

    private List<Long> parseAccountIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        List<Long> ids = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .distinct()
                .toList();
        return ids.isEmpty() ? null : ids;
    }

    private String parseCurrency(String currency, String fallback) {
        if (currency == null || currency.isBlank()) {
            return fallback;
        }
        return switch (currency.toUpperCase()) {
            case "CNY", "USD", "HKD" -> currency.toUpperCase();
            default -> fallback;
        };
    }

    private String money(String currency, BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        String symbol = switch (currency) {
            case "USD" -> "$";
            case "HKD" -> "HK$";
            default -> "¥";
        };
        return symbol + MONEY.format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    private String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "—";
        }
        return ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
