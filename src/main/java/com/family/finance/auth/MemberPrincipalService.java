package com.family.finance.auth;

import com.family.finance.repository.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberPrincipalService implements UserDetailsService {

    private final MemberMapper memberMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberMapper.findByUsername(username)
                .filter(m -> !m.isArchived())
                .map(MemberPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("成员不存在或已归档: " + username));
    }
}
