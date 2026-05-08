package com.family.finance.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 极简 liveness 检查,Nginx/外部监控可探活。
 */
@RestController
public class HealthController {

    @GetMapping(value = "/health", produces = "application/json")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
