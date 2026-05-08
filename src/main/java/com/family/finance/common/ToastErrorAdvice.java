package com.family.finance.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * HTMX 友好的异常处理:把 IllegalStateException / IllegalArgumentException 等业务异常
 * 转换为 200 OK + 空 body + HX-Trigger 头("showToast" 事件,负载含 message + level)。
 * 前端 layout.html 中的全局 toast 监听器据此弹提示,不破坏 HTMX 主流程。
 *
 * 仅作用于 hx-request(由 HX-Request 头判断);非 HTMX 请求保持默认 500 行为。
 */
@ControllerAdvice
@Slf4j
public class ToastErrorAdvice {

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<String> handle(Exception ex, HttpServletRequest request) {
        boolean htmx = "true".equalsIgnoreCase(request.getHeader("HX-Request"));
        boolean writeOp = !"GET".equalsIgnoreCase(request.getMethod());
        if (!htmx || !writeOp) {
            // GET / 非 HTMX 请求让默认 ErrorMvcAutoConfiguration 处理(避免吞掉真实页面渲染异常)
            throw (ex instanceof RuntimeException re ? re : new RuntimeException(ex));
        }
        log.info("[Toast] {} on {} {} -> {}", ex.getClass().getSimpleName(),
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        Map<String, Object> payload = new HashMap<>();
        payload.put("level", "error");
        payload.put("message", ex.getMessage() == null ? "操作失败" : ex.getMessage());
        // HX-Reswap=none 避免 HTMX 用空 body 替换 hx-target 导致行消失
        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":" + jsonValue(payload) + "}")
                .header("HX-Reswap", "none")
                .body("");
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }

    private String jsonValue(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(asciiEscape(String.valueOf(v))).append('"');
            }
        }
        return sb.append("}").toString();
    }

    /** Tomcat 拒绝非 ASCII 的 response header value。中文消息走 \\uXXXX 转义后落进 ASCII 范围。 */
    private String asciiEscape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '"') out.append("\\\"");
            else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
            else if (c < 0x7f) out.append(c);
            else out.append(String.format("\\u%04x", (int) c));
        }
        return out.toString();
    }
}
