package com.family.finance.service.checkup.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 阿里 Qwen-Turbo 客户端 (OpenAI 兼容) · v0.2 FR-40c · 决策 6
 *
 * 端点:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 * Bearer:env DASHSCOPE_API_KEY
 *
 * 简单 circuit breaker:连续 3 次失败 → 60 秒拒绝调用。
 */
@Component
@Slf4j
public class QwenLlmClient implements LlmClient {

    private static final String API = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String MODEL = "qwen-plus";
    private static final int FAIL_THRESHOLD = 3;
    private static final long COOLDOWN_MS = 60_000L;

    private final String apiKey;
    private final RestTemplate restTemplate;

    private volatile int consecutiveFailures = 0;
    private volatile Instant breakerOpenedAt = Instant.EPOCH;

    public QwenLlmClient(@Value("${finance.llm.qwen.api-key:}") String apiKey,
                         RestTemplateBuilder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String vendor() { return "qwen"; }

    @Override
    public boolean available() {
        if (apiKey.isBlank()) return false;
        if (consecutiveFailures >= FAIL_THRESHOLD) {
            long since = java.time.Duration.between(breakerOpenedAt, Instant.now()).toMillis();
            return since >= COOLDOWN_MS;  // 冷却结束才尝试半开
        }
        return true;
    }

    @Override
    public String polish(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) throw new IllegalStateException("Qwen API key 未配置");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.15,
                "max_tokens", 320
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(API, new HttpEntity<>(body, h), Map.class);
            if (resp == null) throw new RuntimeException("Qwen 返回 null");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) throw new RuntimeException("Qwen 无 choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) throw new RuntimeException("Qwen 无 message");
            String content = (String) msg.get("content");
            if (content == null || content.isBlank()) throw new RuntimeException("Qwen 空 content");

            consecutiveFailures = 0;
            return content;
        } catch (Exception e) {
            consecutiveFailures++;
            if (consecutiveFailures >= FAIL_THRESHOLD) {
                breakerOpenedAt = Instant.now();
                log.warn("Qwen circuit breaker 触发,冷却 {} 秒", COOLDOWN_MS / 1000);
            }
            throw new RuntimeException("Qwen 调用失败: " + e.getMessage(), e);
        }
    }
}
