package com.family.finance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 资源缓存策略:
 *   /vendor/** 与 /css/**: 1 年 immutable + cachePublic(模板挂 ?v=${buildVersion} 失效)
 *   /uploads/**: 7 天 cachePublic(logo 等用户上传内容)
 *   动态 HTML: CacheHeaderInterceptor 注入 no-cache,no-store
 * 详见 TDD § 7.3、PRD 性能调优。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final CacheHeaderInterceptor cacheHeaderInterceptor;
    private final MustChangePasswordInterceptor mustChangePasswordInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry r) {
        Path root = Paths.get(props.uploadRoot()).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException ignored) {}
        r.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + root + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
        r.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        r.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        r.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    /** v0.2 FR-34 · .webmanifest 必须以 application/manifest+json 返回,否则浏览器解析失败 */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webmanifestMimeCustomizer() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cacheHeaderInterceptor)
                .excludePathPatterns("/vendor/**", "/css/**", "/img/**", "/uploads/**", "/favicon.ico");
        registry.addInterceptor(mustChangePasswordInterceptor)
                .excludePathPatterns("/vendor/**", "/css/**", "/img/**", "/uploads/**", "/favicon.ico");
    }
}
