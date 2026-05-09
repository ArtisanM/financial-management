package com.family.finance.web.checkup;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.account.Account;
import com.family.finance.repository.AccountMapper;
import com.family.finance.service.NavService;
import com.family.finance.service.ProductCategoryService;
import com.family.finance.service.checkup.AccountDiagnose;
import com.family.finance.service.checkup.AccountDiagnoseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * v0.2 资产体检模块 · 入口 controller(FR-40)
 *
 * <p>当前是 <strong>阶段 1 占位</strong>:
 * <ul>
 *   <li>{@code GET /checkup} → 全家维度占位页</li>
 *   <li>{@code GET /checkup?account=X} → 账户维度占位页</li>
 * </ul>
 *
 * 后续阶段:
 * <ul>
 *   <li>阶段 2:account 维度 + FR-40b 类型差异化诊断</li>
 *   <li>阶段 3:family 维度 + FR-40a 全家诊断 + FR-40c 智能建议 + LLM 文案润色</li>
 * </ul>
 *
 * 详见 PRD § FR-40 / TDD § 决策 1
 */
@Controller
@RequiredArgsConstructor
public class CheckupController {

    private final NavService navService;
    private final AccountMapper accountMapper;
    private final ProductCategoryService categoryService;
    private final AccountDiagnoseService accountDiagnoseService;

    @GetMapping("/checkup")
    public String checkup(@AuthenticationPrincipal MemberPrincipal me,
                          @RequestParam(name = "account", required = false) Long accountId,
                          Model model) {
        model.addAttribute("me", me);
        model.addAttribute("nav", navService.load(me));
        model.addAttribute("active", "checkup");

        if (accountId == null) {
            // 全家维度 · 阶段 1 占位
            model.addAttribute("scope", "FAMILY");
            return "checkup/placeholder-family";
        }

        // 账户维度 · 阶段 2 实页 · FR-40b
        Optional<Account> account = accountMapper.findById(accountId)
                .filter(a -> a.getFamilyId().equals(me.getFamilyId()));
        if (account.isEmpty()) {
            return "redirect:/checkup";
        }

        AccountDiagnose diagnose = accountDiagnoseService.diagnose(me.getFamilyId(), accountId);
        model.addAttribute("scope", "ACCOUNT");
        model.addAttribute("account", account.get());
        model.addAttribute("category",
                categoryService.findByCode(account.get().getProductCategoryCode()).orElse(null));
        model.addAttribute("diagnose", diagnose);
        return "checkup/account";
    }
}
