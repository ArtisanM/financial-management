package com.family.finance.web.entry;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.account.Account;
import com.family.finance.domain.flow.CashFlowKind;
import com.family.finance.domain.period.Period;
import com.family.finance.repository.AccountMapper;
import com.family.finance.repository.PeriodMapper;
import com.family.finance.service.EntryService;
import com.family.finance.service.EntryRow;
import com.family.finance.service.NavService;
import com.family.finance.service.PeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;
    private final PeriodMapper periodMapper;
    private final PeriodService periodService;
    private final AccountMapper accountMapper;
    private final NavService navService;

    @GetMapping("/entry")
    public String entry(@AuthenticationPrincipal MemberPrincipal me,
                        @RequestParam(value = "period", required = false) String periodParam,
                        @RequestParam(value = "mine", defaultValue = "false") boolean mineOnly,
                        @RequestParam(value = "account", required = false) Long accountFilter,
                        Model model) {
        Period period = entryService.findSelectedPeriod(me.getFamilyId(), periodParam)
                .orElseThrow(() -> new IllegalStateException("找不到周期: " + periodParam));
        model.addAttribute("me", me);
        model.addAttribute("nav", navService.load(me));
        List<EntryRow> rows = entryService.listRows(me.getFamilyId(), me.getMemberId(), period, mineOnly);
        if (accountFilter != null) {
            rows = rows.stream().filter(r -> r.account().getId().equals(accountFilter)).toList();
        }
        model.addAttribute("period", period);
        model.addAttribute("periods", periodMapper.findLatest(me.getFamilyId(), 12));
        model.addAttribute("accounts", accountMapper.findActiveByFamily(me.getFamilyId()));
        model.addAttribute("rows", rows);
        model.addAttribute("doneCount", rows.stream().filter(EntryRow::done).count());
        model.addAttribute("mineOnly", mineOnly);
        model.addAttribute("accountFilter", accountFilter);
        return "entry/index";
    }

    @PostMapping("/entry/{accountId}/balance")
    public String submitBalance(@AuthenticationPrincipal MemberPrincipal me,
                                @PathVariable long accountId,
                                @RequestParam MultiValueMap<String, String> params,
                                Model model) {
        long periodId = longParam(params, "periodId", () -> periodService.requireCurrentOpen(me.getFamilyId()).getId());
        EntryRow row = entryService.submitBalance(
                me.getFamilyId(),
                me.getMemberId(),
                periodId,
                accountId,
                decimalParam(params, "newBalance"),
                cashFlowLines(params),
                transferLines(params),
                params.getFirst("note")
        );
        return rowFragment(me, row, periodId, model);
    }

    @PostMapping("/entry/{accountId}/cash-flow")
    public String addCashFlow(@AuthenticationPrincipal MemberPrincipal me,
                              @PathVariable long accountId,
                              @RequestParam long periodId,
                              @RequestParam CashFlowKind kind,
                              @RequestParam(defaultValue = "other_income") String categoryCode,
                              @RequestParam BigDecimal amount,
                              @RequestParam(required = false) String note,
                              Model model) {
        EntryRow row = entryService.addCashFlow(me.getFamilyId(), me.getMemberId(), periodId, accountId,
                kind, categoryCode, amount, note);
        return rowFragment(me, row, periodId, model);
    }

    @PostMapping("/entry/{accountId}/transfer")
    public String addTransfer(@AuthenticationPrincipal MemberPrincipal me,
                              @PathVariable long accountId,
                              @RequestParam long periodId,
                              @RequestParam long toAccountId,
                              @RequestParam BigDecimal amount,
                              @RequestParam(required = false) String note,
                              @RequestParam(defaultValue = "false") boolean confirmDuplicate,
                              Model model) {
        EntryRow row = entryService.addTransfer(me.getFamilyId(), me.getMemberId(), periodId,
                accountId, toAccountId, amount, note, confirmDuplicate);
        return rowFragment(me, row, periodId, model);
    }

    @PostMapping("/entry/transfer/quick")
    public String quickTransfer(@AuthenticationPrincipal MemberPrincipal me,
                                @RequestParam long fromAccountId,
                                @RequestParam(required = false) Long periodId,
                                @RequestParam long toAccountId,
                                @RequestParam BigDecimal amount,
                                @RequestParam(required = false) String note,
                                @RequestParam(defaultValue = "false") boolean confirmDuplicate,
                                Model model) {
        EntryRow row = entryService.quickTransfer(me.getFamilyId(), me.getMemberId(), fromAccountId,
                periodId, toAccountId, amount, note, confirmDuplicate);
        long effectivePeriodId = periodId == null ? periodService.requireCurrentOpen(me.getFamilyId()).getId() : periodId;
        return rowFragment(me, row, effectivePeriodId, model);
    }

    @PostMapping("/entry/{periodId}/complete")
    public String completePeriod(@AuthenticationPrincipal MemberPrincipal me,
                                 @PathVariable long periodId) {
        periodService.markCompletedByMember(periodId, me.getMemberId());
        return "redirect:/entry?period=" + periodId;
    }

    private String rowFragment(MemberPrincipal me, EntryRow row, long periodId, Model model) {
        model.addAttribute("me", me);
        model.addAttribute("row", row);
        model.addAttribute("period", periodMapper.findById(periodId).orElse(null));
        model.addAttribute("accounts", accountMapper.findActiveByFamily(me.getFamilyId()));
        return "entry/_row :: row";
    }

    private List<EntryService.CashFlowLine> cashFlowLines(MultiValueMap<String, String> params) {
        List<String> amounts = values(params, "cashFlowAmount");
        List<EntryService.CashFlowLine> lines = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            if (amounts.get(i) == null || amounts.get(i).isBlank()) {
                continue;
            }
            CashFlowKind kind = CashFlowKind.valueOf(valueAt(values(params, "cashFlowKind"), i, "INCOME"));
            String category = valueAt(values(params, "cashFlowCategory"), i, kind == CashFlowKind.INCOME ? "other_income" : "consumption");
            String note = valueAt(values(params, "cashFlowNote"), i, null);
            lines.add(new EntryService.CashFlowLine(kind, category, new BigDecimal(amounts.get(i)), note));
        }
        return lines;
    }

    private List<EntryService.TransferLine> transferLines(MultiValueMap<String, String> params) {
        List<String> amounts = values(params, "transferAmount");
        List<String> targets = values(params, "transferToAccountId");
        List<EntryService.TransferLine> lines = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            if (amounts.get(i) == null || amounts.get(i).isBlank() || valueAt(targets, i, null) == null) {
                continue;
            }
            String note = valueAt(values(params, "transferNote"), i, null);
            lines.add(new EntryService.TransferLine(Long.parseLong(targets.get(i)), new BigDecimal(amounts.get(i)), note));
        }
        return lines;
    }

    private List<String> values(MultiValueMap<String, String> params, String key) {
        return params.get(key) == null ? List.of() : params.get(key);
    }

    private String valueAt(List<String> values, int index, String fallback) {
        return index < values.size() ? values.get(index) : fallback;
    }

    private long longParam(MultiValueMap<String, String> params, String key, java.util.function.LongSupplier fallback) {
        String value = params.getFirst(key);
        return value == null || value.isBlank() ? fallback.getAsLong() : Long.parseLong(value);
    }

    private BigDecimal decimalParam(MultiValueMap<String, String> params, String key) {
        String value = params.getFirst(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return new BigDecimal(value);
    }
}
