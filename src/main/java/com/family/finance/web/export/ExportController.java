package com.family.finance.web.export;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.service.AuditLogService;
import com.family.finance.service.export.CsvExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;

/**
 * PRD FR-16:GET /export.zip 一键打包导出。
 */
@Controller
@RequiredArgsConstructor
public class ExportController {

    private final CsvExportService csvExportService;
    private final AuditLogService auditLogService;

    @GetMapping("/export.zip")
    public StreamingResponseBody exportZip(@AuthenticationPrincipal MemberPrincipal me,
                                            HttpServletResponse response) {
        String filename = "family-finance-" + LocalDate.now() + ".zip";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        auditLogService.record(me.getFamilyId(), me.getMemberId(), AuditLogType.EXPORT,
                "family", me.getFamilyId(), "导出 CSV 包: " + filename);
        return out -> csvExportService.writeZip(me.getFamilyId(), out);
    }
}
