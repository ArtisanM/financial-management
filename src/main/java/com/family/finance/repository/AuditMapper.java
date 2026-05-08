package com.family.finance.repository;

import com.family.finance.domain.audit.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditMapper {

    @Insert("""
            INSERT INTO audit_log (
                family_id, actor_member_id, type, target_type, target_id, summary, payload_json
            ) VALUES (
                #{familyId}, #{actorMemberId}, #{type}, #{targetType}, #{targetId}, #{summary}, #{payloadJson}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog auditLog);

    @Select("""
            <script>
            SELECT id, family_id, actor_member_id, type, target_type, target_id, summary,
                   payload_json, created_at
              FROM audit_log
             WHERE family_id = #{familyId}
            <if test='type != null and type != ""'>
                AND type = #{type}
            </if>
             ORDER BY created_at DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    List<AuditLog> findByFamily(@Param("familyId") long familyId,
                                @Param("type") String type,
                                @Param("limit") int limit);
}
