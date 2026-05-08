package com.family.finance.web.admin;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.audit.AuditLog;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.domain.backup.BackupLog;
import com.family.finance.domain.family.Family;
import com.family.finance.domain.fx.FxRate;
import com.family.finance.domain.member.Member;
import com.family.finance.domain.period.Period;
import com.family.finance.domain.period.PeriodType;
import com.family.finance.repository.AccountTemplateMapper;
import com.family.finance.repository.AuditMapper;
import com.family.finance.repository.BackupLogMapper;
import com.family.finance.repository.CashFlowCategoryMapper;
import com.family.finance.repository.FamilyMapper;
import com.family.finance.repository.MemberMapper;
import com.family.finance.service.AdminService;
import com.family.finance.service.AuditLogService;
import com.family.finance.service.FamilyService;
import com.family.finance.service.FxService;
import com.family.finance.service.PeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * /admin · 管理总入口 + 12 个子页(详见 PRD § FR-20)。
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FamilyService familyService;
    private final FamilyMapper familyMapper;
    private final MemberMapper memberMapper;
    private final AccountTemplateMapper accountTemplateMapper;
    private final CashFlowCategoryMapper cashFlowCategoryMapper;
    private final PeriodService periodService;
    private final BackupLogMapper backupLogMapper;
    private final AuditMapper auditMapper;
    private final FxService fxService;
    private final AdminService adminService;
    private final AuditLogService auditLogService;

    // ---------------------------------------------------------------------
    // 1. /admin · 总览
    // ---------------------------------------------------------------------
    @GetMapping
    public String hub(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        Family family = familyService.require(me.getFamilyId());
        Period current = periodService.findCurrentOpen(me.getFamilyId()).orElse(null);
        BackupLog lastBackup = backupLogMapper.latest(me.getFamilyId()).orElse(null);
        List<FxRate> fx = fxService.recent(me.getFamilyId(), 5);
        model.addAttribute("family", family);
        model.addAttribute("currentPeriod", current);
        model.addAttribute("lastBackup", lastBackup);
        model.addAttribute("fxRecent", fx);
        return "admin/index";
    }

    // ---------------------------------------------------------------------
    // 2. /admin/family · 家庭设置(品牌名 + 本位币 + 周期类型 + Logo)
    // ---------------------------------------------------------------------
    @GetMapping("/family")
    public String family(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        model.addAttribute("family", familyService.require(me.getFamilyId()));
        return "admin/family";
    }

    @PostMapping("/family")
    public String updateFamily(@AuthenticationPrincipal MemberPrincipal me,
                               @RequestParam String name,
                               @RequestParam String brandText,
                               @RequestParam String baseCurrency,
                               @RequestParam String periodType,
                               RedirectAttributes ra) {
        Family f = familyService.require(me.getFamilyId());
        PeriodType newType = PeriodType.valueOf(periodType);
        // PRD FR-4:切换周期类型前,必须确认无 OPEN 周期
        if (f.getPeriodType() != newType && periodService.findCurrentOpen(me.getFamilyId()).isPresent()) {
            ra.addFlashAttribute("flash", "切换周期类型失败:存在 OPEN 周期,请先关闭");
            return "redirect:/admin/family";
        }
        f.setName(name);
        f.setBrandText(brandText);
        f.setBaseCurrency(baseCurrency);
        f.setPeriodType(newType);
        familyMapper.update(f);
        auditLogService.record(me.getFamilyId(), me.getMemberId(), AuditLogType.FAMILY_UPDATE,
                "family", me.getFamilyId(), "家庭设置更新:%s · %s · %s".formatted(name, baseCurrency, periodType));
        ra.addFlashAttribute("flash", "已保存");
        return "redirect:/admin/family";
    }

    // ---------------------------------------------------------------------
    // 3. /admin/members · 成员
    // ---------------------------------------------------------------------
    @GetMapping("/members")
    public String members(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        model.addAttribute("me", me);
        model.addAttribute("members", memberMapper.findActiveByFamily(me.getFamilyId()));
        return "admin/members";
    }

    @PostMapping("/members/{memberId}")
    public String updateMember(@AuthenticationPrincipal MemberPrincipal me,
                               @PathVariable long memberId,
                               @RequestParam String displayName,
                               @RequestParam(required = false) String roleLabel,
                               RedirectAttributes ra) {
        adminService.updateMemberProfile(me.getFamilyId(), memberId,
                displayName, blankToNull(roleLabel), me.getMemberId());
        ra.addFlashAttribute("flash", "已保存:" + displayName);
        return "redirect:/admin/members";
    }

    @PostMapping("/members/{memberId}/reset-password")
    public String resetPassword(@AuthenticationPrincipal MemberPrincipal me,
                                @PathVariable long memberId,
                                RedirectAttributes ra) {
        String temp = adminService.resetPassword(me.getFamilyId(), memberId, me.getMemberId());
        Member m = memberMapper.findById(memberId).orElseThrow();
        ra.addFlashAttribute("tempPassword", temp);
        ra.addFlashAttribute("tempPasswordFor", m.getDisplayName());
        ra.addFlashAttribute("tempPasswordMemberId", memberId);
        return "redirect:/admin/members";
    }

    // ---------------------------------------------------------------------
    // 4. /admin/accounts · 直接复用 /accounts(已存在)
    // ---------------------------------------------------------------------
    @GetMapping("/accounts")
    public String adminAccountsRedirect() {
        return "redirect:/accounts";
    }

    // ---------------------------------------------------------------------
    // 5. /admin/account-templates · 只读
    // ---------------------------------------------------------------------
    @GetMapping("/account-templates")
    public String accountTemplates(Model model) {
        model.addAttribute("templates", accountTemplateMapper.listOrdered());
        return "admin/account-templates";
    }

    // ---------------------------------------------------------------------
    // 6. /admin/cash-flow-categories · 只读
    // ---------------------------------------------------------------------
    @GetMapping("/cash-flow-categories")
    public String cashFlowCategories(Model model) {
        model.addAttribute("categories", cashFlowCategoryMapper.listOrdered());
        return "admin/cash-flow-categories";
    }

    // ---------------------------------------------------------------------
    // 7. /admin/periods · 周期管理 + 重新打开
    // ---------------------------------------------------------------------
    @GetMapping("/periods")
    public String periods(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        List<Period> periods = periodService.findLatest(me.getFamilyId(), 24);
        model.addAttribute("periods", periods);
        model.addAttribute("currentPeriod", periodService.findCurrentOpen(me.getFamilyId()).orElse(null));
        return "admin/periods";
    }

    @PostMapping("/periods/{periodId}/reopen")
    public String reopenPeriod(@AuthenticationPrincipal MemberPrincipal me,
                               @PathVariable long periodId,
                               @RequestParam String reason,
                               RedirectAttributes ra) {
        if (reason == null || reason.isBlank()) {
            ra.addFlashAttribute("flash", "重开失败:必须填理由");
            return "redirect:/admin/periods";
        }
        periodService.reopen(periodId, reason);
        ra.addFlashAttribute("flash", "周期已重新打开");
        return "redirect:/admin/periods";
    }

    // ---------------------------------------------------------------------
    // 8. /admin/reminders · 只读(v0.1 仅展示 cron 锚点)
    // ---------------------------------------------------------------------
    @GetMapping("/reminders")
    public String reminders(Model model) {
        model.addAttribute("anchors", List.of(
                "每月 1 日 09:00 · 首轮提醒(站内 banner)",
                "每月 5 日 09:00 · 二轮提醒",
                "每月 10 日 09:00 · 三轮提醒 + 标'迟报'",
                "每月 15 日 +    · XIRR 时间精度受影响标记"
        ));
        return "admin/reminders";
    }

    // ---------------------------------------------------------------------
    // 9. /admin/fx · 汇率维护
    // ---------------------------------------------------------------------
    @GetMapping("/fx")
    public String fx(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        model.addAttribute("family", familyService.require(me.getFamilyId()));
        model.addAttribute("rates", fxService.recent(me.getFamilyId(), 24));
        model.addAttribute("periods", periodService.findLatest(me.getFamilyId(), 12));
        return "admin/fx";
    }

    @PostMapping("/fx/override")
    public String overrideFx(@AuthenticationPrincipal MemberPrincipal me,
                             @RequestParam long periodId,
                             @RequestParam String quoteCurrency,
                             @RequestParam BigDecimal rate,
                             RedirectAttributes ra) {
        fxService.manualOverride(me.getFamilyId(), periodId, quoteCurrency, rate, me.getMemberId());
        ra.addFlashAttribute("flash", "已手填覆盖 %s @ period#%d".formatted(quoteCurrency, periodId));
        return "redirect:/admin/fx";
    }

    @PostMapping("/fx/fetch")
    public String fetchFx(@AuthenticationPrincipal MemberPrincipal me, RedirectAttributes ra) {
        try {
            fxService.fetchForLatestPeriods(me.getFamilyId());
            ra.addFlashAttribute("flash", "拉取完成");
        } catch (Exception e) {
            ra.addFlashAttribute("flash", "拉取失败:" + e.getMessage());
        }
        return "redirect:/admin/fx";
    }

    // ---------------------------------------------------------------------
    // 10. /admin/backup · 备份状态
    // ---------------------------------------------------------------------
    @GetMapping("/backup")
    public String backup(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        model.addAttribute("logs", backupLogMapper.recent(me.getFamilyId(), 8));
        model.addAttribute("now", LocalDateTime.now());
        return "admin/backup";
    }

    // ---------------------------------------------------------------------
    // 11. /admin/audit · 审计日志
    // ---------------------------------------------------------------------
    @GetMapping("/audit")
    public String audit(@AuthenticationPrincipal MemberPrincipal me,
                        @RequestParam(required = false) String type,
                        Model model) {
        List<AuditLog> rows = auditMapper.findByFamily(me.getFamilyId(), blankToNull(type), 100);
        model.addAttribute("rows", rows);
        model.addAttribute("filterType", type);
        model.addAttribute("availableTypes", AuditLogType.values());
        return "admin/audit";
    }

    // ---------------------------------------------------------------------
    // 12. /admin/calc-tweaks · 数值阈值(只读)
    // ---------------------------------------------------------------------
    @GetMapping("/calc-tweaks")
    public String calcTweaks(Model model) {
        model.addAttribute("tweaks", List.of(
                new String[]{"smart_transfer_threshold", "¥3,000", "轧差差额超过此值,提示'看起来像转账?'"},
                new String[]{"loan_abnormal_threshold", "3×", "贷款本期变化超过上期 3 倍,警告'可能提前还款?'"},
                new String[]{"unexplained_epsilon",     "¥0.01", "轧差未解释金额绝对值小于此即视为已分类完毕"}
        ));
        return "admin/calc-tweaks";
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
