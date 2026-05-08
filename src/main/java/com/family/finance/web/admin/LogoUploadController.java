package com.family.finance.web.admin;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.config.AppProperties;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.service.AuditLogService;
import com.family.finance.service.FamilyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Logo 上传 · v0.1 简化版。
 *
 * 前端 Canvas 已把图压缩为 WebP(≤ 50KB 常态),后端只做 4 件事:
 *  1. Content-Type == image/webp
 *  2. 大小 1..200KB(留 4× 余量给非典型用户)
 *  3. 前 4 字节魔数 == "RIFF"
 *  4. 写到 ${app.upload-root}/family-{id}/logo.webp,落盘前 normalize 校验路径不逃出根目录
 *
 * 详见 PRD § FR-1 Logo 上传规则;TDD § 7。
 */
@Controller
@RequestMapping("/admin/family/logo")
@RequiredArgsConstructor
@Slf4j
public class LogoUploadController {

    private static final long MIN_BYTES = 1;
    private static final long MAX_BYTES = 200L * 1024;     // 200KB
    private static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };

    private final AppProperties props;
    private final FamilyService familyService;
    private final AuditLogService auditLogService;

    @PostMapping
    public ResponseEntity<String> upload(@RequestParam("logo") MultipartFile file,
                                         @AuthenticationPrincipal MemberPrincipal me) throws IOException {
        if (file == null || file.isEmpty())
            return ResponseEntity.badRequest().body("file empty");

        long size = file.getSize();
        if (size < MIN_BYTES || size > MAX_BYTES)
            return ResponseEntity.badRequest().body("size out of range (1B..200KB)");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/webp"))
            return ResponseEntity.badRequest().body("must be image/webp");

        // RIFF 魔数校验(防伪)
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            if (head.length < 4 || !java.util.Arrays.equals(head, RIFF_MAGIC))
                return ResponseEntity.badRequest().body("not a valid webp (RIFF magic missing)");
        }

        Path uploadRoot = Paths.get(props.uploadRoot()).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
        Path familyDir = uploadRoot.resolve("family-" + me.getFamilyId());
        Files.createDirectories(familyDir);
        Path target = familyDir.resolve("logo.webp").normalize();

        // path traversal 防御:目标路径必须严格在 uploadRoot 之下
        if (!target.startsWith(uploadRoot)) {
            log.warn("[LogoUpload] refused path traversal: {}", target);
            return ResponseEntity.badRequest().body("path escape");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String relativePath = "family-" + me.getFamilyId() + "/logo.webp";
        familyService.updateBrandLogo(me.getFamilyId(), relativePath);
        auditLogService.write(me.getFamilyId(), me.getMemberId(), AuditLogType.LOGO_UPLOAD,
                "family", me.getFamilyId(),
                "Logo 上传 · %d bytes".formatted(size),
                java.util.Map.of("path", relativePath, "size", size));

        return ResponseEntity.ok("ok");
    }

    @PostMapping("/remove")
    public String remove(@AuthenticationPrincipal MemberPrincipal me) throws IOException {
        Path uploadRoot = Paths.get(props.uploadRoot()).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve("family-" + me.getFamilyId()).resolve("logo.webp").normalize();
        if (target.startsWith(uploadRoot)) {
            Files.deleteIfExists(target);
        }
        familyService.updateBrandLogo(me.getFamilyId(), null);
        auditLogService.record(me.getFamilyId(), me.getMemberId(), AuditLogType.LOGO_UPLOAD,
                "family", me.getFamilyId(), "Logo 已移除");
        return "redirect:/admin/family";
    }
}
