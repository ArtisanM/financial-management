package com.family.finance.web.profile;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.domain.member.Member;
import com.family.finance.repository.MemberMapper;
import com.family.finance.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户主动改密 + 临时密码强制改密入口。
 * PRD FR-1:首次登录或管理员重置后,member.must_change_pw=true 触发强制跳转(由
 * MustChangePasswordInterceptor 处理),用户必须先在此页改密才能访问其它路径。
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @GetMapping("/profile/password")
    public String form(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        model.addAttribute("me", me);
        model.addAttribute("forced", me != null && me.isMustChangePw());
        return "profile/password";
    }

    @PostMapping("/profile/password")
    public String submit(@AuthenticationPrincipal MemberPrincipal me,
                         @RequestParam("oldPassword") String oldPassword,
                         @RequestParam("newPassword") String newPassword,
                         @RequestParam("confirmPassword") String confirmPassword,
                         Model model) {
        Member m = memberMapper.findById(me.getMemberId())
                .orElseThrow(() -> new IllegalStateException("成员不存在"));

        if (!passwordEncoder.matches(oldPassword, m.getPasswordHash())) {
            return back(model, me, "原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 8) {
            return back(model, me, "新密码至少 8 位");
        }
        if (!newPassword.equals(confirmPassword)) {
            return back(model, me, "两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(newPassword, m.getPasswordHash())) {
            return back(model, me, "新密码不能与原密码相同");
        }

        memberMapper.updatePasswordHash(me.getMemberId(), passwordEncoder.encode(newPassword), false);
        auditLogService.record(me.getFamilyId(), me.getMemberId(), AuditLogType.PASSWORD_RESET,
                "member", me.getMemberId(), "成员主动修改密码");
        // 触发重新认证 — 简单做法是清掉 session,跳登录
        SecurityContextHolder.clearContext();
        return "redirect:/login?passwordChanged";
    }

    private String back(Model model, MemberPrincipal me, String error) {
        model.addAttribute("me", me);
        model.addAttribute("forced", me != null && me.isMustChangePw());
        model.addAttribute("error", error);
        return "profile/password";
    }
}
