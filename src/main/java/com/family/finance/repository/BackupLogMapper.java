package com.family.finance.repository;

import com.family.finance.domain.backup.BackupLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface BackupLogMapper {

    @Select("""
            SELECT id, family_id, kind, status, size_bytes,
                   location_local, location_remote, error_message,
                   started_at, completed_at
              FROM backup_log
             WHERE family_id = #{familyId}
             ORDER BY started_at DESC
             LIMIT #{limit}
            """)
    List<BackupLog> recent(@Param("familyId") long familyId, @Param("limit") int limit);

    @Select("""
            SELECT id, family_id, kind, status, size_bytes,
                   location_local, location_remote, error_message,
                   started_at, completed_at
              FROM backup_log
             WHERE family_id = #{familyId}
             ORDER BY started_at DESC
             LIMIT 1
            """)
    Optional<BackupLog> latest(@Param("familyId") long familyId);
}
