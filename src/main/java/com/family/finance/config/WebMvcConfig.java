package com.family.finance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * /uploads/** 静态资源映射到本地 ${app.upload-root}/。
 * 详见 TDD § 7.3。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties props;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry r) {
        Path root = Paths.get(props.uploadRoot()).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException ignored) {}
        r.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + root + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }
}
