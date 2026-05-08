package com.family.finance.domain.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    private Long id;
    private Long familyId;
    private Long actorMemberId;
    private AuditLogType type;
    private String targetType;
    private Long targetId;
    private String summary;
    private String payloadJson;
    private LocalDateTime createdAt;
}
