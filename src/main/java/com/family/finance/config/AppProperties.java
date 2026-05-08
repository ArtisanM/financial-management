package com.family.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 应用级配置属性。前缀 `app.*` 见 application.yml。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("/tmp/finance-uploads") String uploadRoot,
        @DefaultValue("dev-only-key-change-in-prod") String rememberMeKey,
        @DefaultValue("2592000") int rememberMeValiditySeconds,
        @DefaultValue("https://api.exchangerate.host") String fxApiBase,
        @DefaultValue("3000") long fxFetchTimeoutMs
) {}
