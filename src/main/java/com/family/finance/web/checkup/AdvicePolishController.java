package com.family.finance.web.checkup;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.factview.AccountPeriodFact;
import com.family.finance.factview.FactSlice;
import com.family.finance.factview.FactViewService;
import com.family.finance.repository.AccountMapper;
import com.family.finance.service.checkup.AccountDiagnose;
import com.family.finance.service.checkup.AccountDiagnoseService;
import com.family.finance.service.checkup.FamilyDiagnose;
import com.family.finance.service.checkup.FamilyDiagnoseService;
import com.family.finance.service.checkup.llm.LlmAdviceService;
import com.family.finance.service.checkup.rule.Advice;
import com.family.finance.service.checkup.rule.AdviceEngine;
import com.family.finance.service.checkup.rule.RuleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /checkup/advice/{ruleId}/polish · v0.2 FR-40c
 *
 * HTMX POST 触发 LLM 文案润色 → 返回单张卡片 fragment 替换 DOM
 * accountId 参数:有 → 重新评估账户级 ctx;无 → 家庭级 ctx
 */
@Controller
@RequiredArgsConstructor
public class AdvicePolishController {

    private final AdviceEngine adviceEngine;
    private final AccountDiagnoseService accountDiagnoseService;
    private final FamilyDiagnoseService familyDiagnoseService;
    private final LlmAdviceService llmAdviceService;
    private final AccountMapper accountMapper;
    private final FactViewService factViewService;

    @PostMapping("/checkup/advice/{ruleId}/polish")
    public String polish(@AuthenticationPrincipal MemberPrincipal me,
                         @PathVariable String ruleId,
                         @RequestParam(name = "account", required = false) Long accountId,
                         Model model) {
        BigDecimal avgExp = computeAvgExpense(me.getFamilyId());

        // 重新评估,因为 advice 不持久化(状态 stateless)
        RuleContext ctx;
        if (accountId != null) {
            AccountDiagnose ad = accountDiagnoseService.diagnose(me.getFamilyId(), accountId);
            FamilyDiagnose fd = familyDiagnoseService.diagnose(me.getFamilyId());
            ctx = RuleContext.forAccount(ad, fd, List.of(ad), avgExp);
        } else {
            FamilyDiagnose fd = familyDiagnoseService.diagnose(me.getFamilyId());
            ctx = RuleContext.forFamily(fd, collectAccounts(me.getFamilyId()), avgExp);
        }

        Optional<Advice> match = adviceEngine.evaluate(ctx).stream()
                .filter(a -> a.ruleId().equals(ruleId))
                .findFirst();
        if (match.isEmpty()) {
            return "checkup/_advice-card :: empty";
        }

        Advice raw = match.get();
        Advice polished = llmAdviceService.polish(me.getFamilyId(), me.getMemberId(), raw)
                .orElse(raw);
        boolean wasPolished = !polished.body().equals(polished.rawBody());
        model.addAttribute("ad", polished);
        model.addAttribute("polished", wasPolished);
        return "checkup/_advice-card :: htmxCard";
    }

    private List<AccountDiagnose> collectAccounts(long familyId) {
        List<AccountDiagnose> out = new ArrayList<>();
        for (var a : accountMapper.findActiveByFamily(familyId)) {
            try {
                out.add(accountDiagnoseService.diagnose(familyId, a.getId()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private BigDecimal computeAvgExpense(long familyId) {
        FactSlice slice = factViewService.loadDefault(familyId);
        if (slice.periodIds().isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = slice.rows().stream()
                .map(AccountPeriodFact::expenseBase)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(Math.max(1, slice.periodIds().size())), 2, RoundingMode.HALF_EVEN);
    }
}
