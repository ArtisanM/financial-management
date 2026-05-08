package com.family.finance.common;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.service.NavService;
import com.family.finance.service.NavState;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 把 me + nav 自动注入到所有 controller 的 model — 模板里 ${me} 和 ${nav} 总是可用。
 * 未登录(/login 等)时这俩为 null。
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NavService navService;

    @ModelAttribute("me")
    public MemberPrincipal me(@AuthenticationPrincipal MemberPrincipal me) {
        return me;
    }

    @ModelAttribute("nav")
    public NavState nav(@AuthenticationPrincipal MemberPrincipal me) {
        return me == null ? null : navService.load(me);
    }
}
