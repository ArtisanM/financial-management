package com.family.finance.repository;

import com.family.finance.domain.snapshot.SnapshotTodo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SnapshotTodoMapper {

    @Select("""
            SELECT id, period_id, account_id, assigned_member_id, status, done_at,
                   done_by_member_id, prefilled_balance, prefilled_transfer_id
              FROM snapshot_todo
             WHERE id = #{id}
            """)
    Optional<SnapshotTodo> findById(@Param("id") long id);

    @Select("""
            SELECT id, period_id, account_id, assigned_member_id, status, done_at,
                   done_by_member_id, prefilled_balance, prefilled_transfer_id
              FROM snapshot_todo
             WHERE period_id = #{periodId}
             ORDER BY id
            """)
    List<SnapshotTodo> findByPeriod(@Param("periodId") long periodId);

    @Select("""
            SELECT id, period_id, account_id, assigned_member_id, status, done_at,
                   done_by_member_id, prefilled_balance, prefilled_transfer_id
              FROM snapshot_todo
             WHERE period_id = #{periodId}
               AND account_id = #{accountId}
            """)
    Optional<SnapshotTodo> findByPeriodAndAccount(@Param("periodId") long periodId,
                                                  @Param("accountId") long accountId);

    @Select("""
            SELECT id, period_id, account_id, assigned_member_id, status, done_at,
                   done_by_member_id, prefilled_balance, prefilled_transfer_id
              FROM snapshot_todo
             WHERE period_id = #{periodId}
               AND status = 'PENDING'
               AND (assigned_member_id = #{memberId} OR assigned_member_id IS NULL)
             ORDER BY id
            """)
    List<SnapshotTodo> findPendingForMember(@Param("periodId") long periodId,
                                            @Param("memberId") long memberId);

    @Select("""
            SELECT COUNT(*)
              FROM snapshot_todo
             WHERE period_id = #{periodId}
               AND status = 'PENDING'
            """)
    int countPendingByPeriod(@Param("periodId") long periodId);

    @Insert("""
            INSERT INTO snapshot_todo (
                period_id, account_id, assigned_member_id, status,
                prefilled_balance, prefilled_transfer_id
            ) VALUES (
                #{periodId}, #{accountId}, #{assignedMemberId}, #{status},
                #{prefilledBalance}, #{prefilledTransferId}
            )
            ON DUPLICATE KEY UPDATE
                assigned_member_id = VALUES(assigned_member_id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SnapshotTodo todo);

    @Update("""
            UPDATE snapshot_todo
               SET status = 'DONE',
                   done_at = NOW(3),
                   done_by_member_id = #{memberId}
             WHERE period_id = #{periodId}
               AND account_id = #{accountId}
            """)
    int markDone(@Param("periodId") long periodId,
                 @Param("accountId") long accountId,
                 @Param("memberId") long memberId);

    @Update("""
            UPDATE snapshot_todo
               SET prefilled_balance = #{prefilledBalance},
                   prefilled_transfer_id = #{prefilledTransferId}
             WHERE id = #{id}
            """)
    int updatePrefill(SnapshotTodo todo);
}
