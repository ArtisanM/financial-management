package com.family.finance.config;

import com.family.finance.auth.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * PRD FR-1:管理员重置密码后 member.must_change_pw=true,用户登录后必须先去 /profile/password 改密。
 * 在改密之前所有动态请求都强制重定向到改密页(白名单除外)。
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED = Set.of(
            "/profile/password",
            "/logout",
            "/login",
            "/health",
            "/error"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MemberPrincipal me)) return true;
        if (!me.isMustChangePw()) return true;

        String path = request.getServletPath();
        if (ALLOWED.contains(path)) return true;
        // 静态资源前缀已经被 WebMvcConfig.excludePathPatterns 拦在外,这里只兜底
        if (path.startsWith("/vendor/") || path.startsWith("/css/")
                || path.startsWith("/img/") || path.startsWith("/uploads/")) return true;

        response.sendRedirect("/profile/password");
        return false;
    }
}
