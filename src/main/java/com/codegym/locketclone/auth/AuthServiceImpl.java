package com.codegym.locketclone.auth;

import com.codegym.locketclone.auth.dto.LoginRequest;
import com.codegym.locketclone.auth.dto.RegisterRequest;
import com.codegym.locketclone.auth.dto.JwtResponse;
import com.codegym.locketclone.user.dto.UserResponse;
import com.codegym.locketclone.security.jwt.JwtUtils;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    @Override
    public JwtResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.phoneNumber(),
                        loginRequest.password()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Tạo Token từ thông tin đã xác thực
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String jwt = jwtUtils.generateToken(userPrincipal);

        // 4. Trả về Response chứa Token và thông tin cơ bản
        return new JwtResponse(
                jwt,
                "Bearer",
                userPrincipal.getId(),
                userPrincipal.getPhoneNumber()
        );
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest registerRequest) {
        return userService.createUser(registerRequest);
    }
}
