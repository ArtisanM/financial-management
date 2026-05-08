package com.family.finance.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 给所有动态(非静态)响应注入 no-cache,no-store —— 取代 Spring Security 的全局 cacheControl。
 * 静态资源被 WebMvcConfig.addInterceptors 中的 excludePathPatterns 排除,可继续 long-cache。
 */
@Component
public class CacheHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!response.containsHeader("Cache-Control")) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
        return true;
    }
}
