package com.family.finance.common;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.service.NavService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * v0.2 · 替换默认 BasicErrorController,显式注入 me + nav 让 fragments/nav 正常渲染。
 * 否则 BasicErrorController 不走 GlobalModelAdvice 的某些路径,nav 顶栏会渲染错。
 *
 * 实现 ErrorController 接口(继承 AbstractErrorController)即被 Spring Boot 识别为
 * error controller,自动替换 BasicErrorController。
 */
@Controller
public class CustomErrorController extends AbstractErrorController {

    private final NavService navService;

    public CustomErrorController(ErrorAttributes errorAttributes, NavService navService) {
        super(errorAttributes);
        this.navService = navService;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request,
                              Model model,
                              @AuthenticationPrincipal MemberPrincipal me) {
        // 从 ErrorAttributes 拿 status / error / message / path
        Map<String, Object> attrs = getErrorAttributes(request, ErrorAttributeOptions.of(
                ErrorAttributeOptions.Include.STATUS,
                ErrorAttributeOptions.Include.ERROR,
                ErrorAttributeOptions.Include.MESSAGE,
                ErrorAttributeOptions.Include.PATH
        ));
        attrs.forEach(model::addAttribute);

        // 显式注入 me + nav,让 templates/error.html 复用 fragments/nav 时不会因 null 渲染失败
        if (me != null) {
            model.addAttribute("me", me);
            model.addAttribute("nav", navService.load(me));
        }
        return "error";
    }
}
