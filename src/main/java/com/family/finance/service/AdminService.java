package com.family.finance.service;

import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.domain.member.Member;
import com.family.finance.repository.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Admin 操作的横切服务:重置密码、编辑成员资料 等。
 * 操作权限:v0.1 任何成员都可触发,但每次都写 audit_log。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    /** 临时密码字符表 — 去掉易混淆字符(0/O, 1/l, I) */
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    /** 生成一段 12 字符的临时密码,落入 password_hash 并 must_change_pw=1。返回明文(只显示一次)。 */
    public String resetPassword(long familyId, long targetMemberId, Long actorMemberId) {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        String plain = sb.toString();
        String hash = passwordEncoder.encode(plain);
        memberMapper.updatePasswordHash(targetMemberId, hash, true);
        auditLogService.record(familyId, actorMemberId, AuditLogType.PASSWORD_RESET,
                "member", targetMemberId,
                "重置密码 · 临时密码已生成(only-once,管理员当面/微信告诉对方)");
        return plain;
    }

    public void updateMemberProfile(long familyId, long targetMemberId, String displayName, String roleLabel,
                                    Long actorMemberId) {
        memberMapper.updateProfile(targetMemberId, displayName, roleLabel);
        auditLogService.record(familyId, actorMemberId, AuditLogType.FAMILY_UPDATE,
                "member", targetMemberId,
                "成员资料更新:%s · %s".formatted(displayName, roleLabel == null ? "—" : roleLabel));
    }

    /**
     * 添加新成员(同一家庭内)。生成 12 位临时密码 + must_change_pw=1,
     * 返回明文供管理员当面/即时通讯告知对方。
     */
    public String createMember(long familyId, String username, String displayName, String roleLabel,
                                Long actorMemberId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名必填");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("显示名必填");
        }
        if (memberMapper.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已被占用");
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        String plain = sb.toString();
        Member m = Member.builder()
                .familyId(familyId)
                .username(username)
                .passwordHash(passwordEncoder.encode(plain))
                .displayName(displayName)
                .roleLabel(roleLabel)
                .mustChangePw(true)
                .build();
        memberMapper.insert(m);
        auditLogService.record(familyId, actorMemberId, AuditLogType.FAMILY_UPDATE,
                "member", m.getId(),
                "添加成员 · " + displayName + " · 临时密码已生成(only-once)");
        return plain;
    }
}
