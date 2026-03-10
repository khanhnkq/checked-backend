package com.codegym.locketclone.security.service;

import com.codegym.locketclone.user.User;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByPhoneNumber(identifier)
                .orElseGet(() -> userRepository.findByUsername(identifier)
                        .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại: " + identifier)));

        return UserPrincipal.build(user);
    }

    // 2. Dùng cho JwtAuthenticationFilter để xác thực Token từ UUID
    @Transactional
    public UserDetails loadUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return UserPrincipal.build(user);
    }
}
