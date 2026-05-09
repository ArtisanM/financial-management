package com.family.finance.web.account;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.account.Account;
import com.family.finance.domain.account.AccountType;
import com.family.finance.repository.MemberMapper;
import com.family.finance.service.AccountService;
import com.family.finance.service.AccountTemplateService;
import com.family.finance.service.NavService;
import com.family.finance.service.ProductCategoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountTemplateService accountTemplateService;
    private final MemberMapper memberMapper;
    private final NavService navService;
    private final ProductCategoryService productCategoryService;

    @GetMapping
    public String index(@AuthenticationPrincipal MemberPrincipal me,
                        @RequestParam(value = "archived", defaultValue = "false") boolean includeArchived,
                        @RequestParam(value = "type", required = false) String typeFilter,
                        Model model) {
        addModel(me, model, includeArchived, false);
        // 用户视角的"按类型筛选"(CASH / STOCK / WEALTH / PROPERTY / LOAN / OTHER / ALL)
        com.family.finance.domain.account.AccountType normalized = null;
        if (typeFilter != null && !typeFilter.isBlank() && !"ALL".equalsIgnoreCase(typeFilter)) {
            try { normalized = com.family.finance.domain.account.AccountType.valueOf(typeFilter.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        @SuppressWarnings("unchecked")
        java.util.List<com.family.finance.service.AccountRow> rows =
                (java.util.List<com.family.finance.service.AccountRow>) model.getAttribute("rows");
        if (normalized != null && rows != null) {
            final var typed = normalized;
            model.addAttribute("rows", rows.stream()
                    .filter(r -> r.account().getType() == typed)
                    .toList());
        }
        model.addAttribute("typeFilter", normalized == null ? "ALL" : normalized.name());
        return "accounts/index";
    }

    @GetMapping("/new")
    public String newWizard(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        addModel(me, model, false, true);
        return "accounts/index";
    }

    @GetMapping("/{id}/edit")
    public String editPage(@AuthenticationPrincipal MemberPrincipal me,
                           @PathVariable("id") long accountId,
                           Model model) {
        Account account = accountService.require(me.getFamilyId(), accountId);
        addEditModel(me, model, account);
        return "accounts/edit";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal MemberPrincipal me,
                         @ModelAttribute AccountForm form) {
        accountService.create(me, form.toAccount());
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal MemberPrincipal me,
                       @PathVariable("id") long accountId,
                       @ModelAttribute AccountForm form) {
        accountService.update(me, accountId, form.toAccount());
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/archive")
    public String archive(@AuthenticationPrincipal MemberPrincipal me,
                          @PathVariable("id") long accountId) {
        accountService.archive(me, accountId);
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/restore")
    public String restore(@AuthenticationPrincipal MemberPrincipal me,
                          @PathVariable("id") long accountId) {
        accountService.restore(me, accountId);
        return "redirect:/accounts?archived=true";
    }

    private void addModel(MemberPrincipal me, Model model, boolean includeArchived, boolean showWizard) {
        model.addAttribute("me", me);
        model.addAttribute("nav", navService.load(me));
        model.addAttribute("rows", accountService.listRows(me.getFamilyId(), includeArchived));
        model.addAttribute("summary", accountService.summarize(me.getFamilyId()));
        model.addAttribute("templates", accountTemplateService.listOrdered());
        model.addAttribute("members", memberMapper.findActiveByFamily(me.getFamilyId()));
        model.addAttribute("types", AccountType.values());
        model.addAttribute("currencies", new String[]{"CNY", "USD", "HKD"});
        model.addAttribute("form", new AccountForm());
        model.addAttribute("allCategories", productCategoryService.listAll());
        model.addAttribute("includeArchived", includeArchived);
        model.addAttribute("showWizard", showWizard);
    }

    private void addEditModel(MemberPrincipal me, Model model, Account account) {
        model.addAttribute("me", me);
        model.addAttribute("nav", navService.load(me));
        model.addAttribute("account", account);
        model.addAttribute("rows", accountService.listRows(me.getFamilyId(), false));
        model.addAttribute("members", memberMapper.findActiveByFamily(me.getFamilyId()));
        model.addAttribute("types", AccountType.values());
        model.addAttribute("currencies", new String[]{"CNY", "USD", "HKD"});
        model.addAttribute("applicableCategories",
                productCategoryService.findApplicableFor(account.getType()));
        model.addAttribute("currentCategory",
                productCategoryService.findByCode(account.getProductCategoryCode()).orElse(null));
    }

    @Data
    public static class AccountForm {
        private Long templateId;
        private String displayName;
        private AccountType type = AccountType.CASH;
        private String currency = "CNY";
        private Long primaryOwnerMemberId;
        private Long defaultPaymentSourceAccountId;
        private Integer displayOrder;
        /** v0.2 · 产品类目 code(FR-40d)*/
        private String productCategoryCode;
        /** v0.2 · 风险等级覆盖(NULL = 沿用类目)· FR-40d */
        private Integer riskLevelOverride;

        Account toAccount() {
            return Account.builder()
                    .templateId(templateId)
                    .displayName(displayName)
                    .type(type)
                    .currency(currency)
                    .primaryOwnerMemberId(primaryOwnerMemberId)
                    .defaultPaymentSourceAccountId(defaultPaymentSourceAccountId)
                    .displayOrder(displayOrder)
                    .productCategoryCode(productCategoryCode)
                    .riskLevelOverride(riskLevelOverride)
                    .build();
        }
    }
}
