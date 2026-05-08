package com.family.finance.auth;

import com.family.finance.domain.member.Member;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 视角的成员主体。包含登录态需要的家庭/成员上下文。
 * <p>
 * 任意 Controller 可通过 @AuthenticationPrincipal MemberPrincipal me 注入。
 */
@Getter
public class MemberPrincipal implements UserDetails {

    private final long memberId;
    private final long familyId;
    private final String username;
    private final String displayName;
    private final String roleLabel;
    private final transient String passwordHash; // 登录后不应再使用
    private final boolean mustChangePw;
    private final boolean archived;

    public MemberPrincipal(Member m) {
        this.memberId      = m.getId();
        this.familyId      = m.getFamilyId();
        this.username      = m.getUsername();
        this.displayName   = m.getDisplayName();
        this.roleLabel     = m.getRoleLabel();
        this.passwordHash  = m.getPasswordHash();
        this.mustChangePw  = m.isMustChangePw();
        this.archived      = m.isArchived();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // v0.1 单家庭内无权限分级 — 全员 ROLE_MEMBER
        return List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public boolean isAccountNonExpired()     { return !archived; }

    @Override
    public boolean isAccountNonLocked()      { return !archived; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()               { return !archived; }
}
