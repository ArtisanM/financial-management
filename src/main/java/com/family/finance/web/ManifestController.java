package com.family.finance.web;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.family.Family;
import com.family.finance.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.2 FR-1/FR-34:动态 PWA manifest — 根据当前会话所在 family.logoPreset 拼 icon URL。
 * 未登录(无 cookie / 安装 PWA 前的 manifest 探测)时回退到默认 icon2,与 SecurityConfig
 * permitAll 配合(/manifest.webmanifest 已在 permitAll 列表里)。
 *
 * 取代了 src/main/resources/static/manifest.webmanifest(已删)。Spring 静态资源命中
 * 优先于 controller,所以必须确保静态文件不存在,否则切换预设无效。
 */
@RestController
@RequiredArgsConstructor
public class ManifestController {

    private final FamilyService familyService;

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public Map<String, Object> manifest(@AuthenticationPrincipal MemberPrincipal me) {
        String preset = FamilyService.DEFAULT_LOGO_PRESET;
        if (me != null) {
            try {
                Family f = familyService.require(me.getFamilyId());
                if (f.getLogoPreset() != null && !f.getLogoPreset().isBlank()) {
                    preset = f.getLogoPreset();
                }
            } catch (Exception ignore) {
                // 任何异常都安全回退到默认,manifest 不能挂
            }
        }
        String base = "/img/presets/" + preset;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "家庭账房");
        body.put("short_name", "账房");
        body.put("description", "家庭资产 · 月度账册 · 仅限家庭内部使用");
        body.put("lang", "zh-CN");
        body.put("start_url", "/dashboard");
        body.put("scope", "/");
        body.put("display", "standalone");
        body.put("orientation", "portrait");
        body.put("theme_color", "#1a1714");
        body.put("background_color", "#f6f1ea");
        body.put("icons", List.of(
                Map.of("src", base + "-192.png", "sizes", "192x192", "type", MediaType.IMAGE_PNG_VALUE, "purpose", "any"),
                Map.of("src", base + "-512.png", "sizes", "512x512", "type", MediaType.IMAGE_PNG_VALUE, "purpose", "any"),
                Map.of("src", base + "-512.png", "sizes", "512x512", "type", MediaType.IMAGE_PNG_VALUE, "purpose", "maskable")
        ));
        return body;
    }
}
