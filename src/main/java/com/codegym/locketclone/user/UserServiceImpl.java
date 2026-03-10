package com.codegym.locketclone.user;

import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;


    @Override
    public UserResponse createUser(RegisterRequest request) {

        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new AppException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
        }
        if (userRepository.existsByUsername(request.username())){
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        User user = User.builder().phoneNumber(request.phoneNumber())
                .username(request.username())
                .password(passwordEncoder.encode(request.password())) // Băm mật khẩu
                .displayName(request.displayName())
                // .fcmToken(null) // Tạm thời để trống, update sau khi đăng nhập trên mobile
                .build();
        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserByPhoneNumber(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void updateFcmToken(UUID userId, String fcmToken) {
        User user = userRepository.findById(userId).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }
}
