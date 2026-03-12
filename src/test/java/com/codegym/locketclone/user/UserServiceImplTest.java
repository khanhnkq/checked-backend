package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void updateCurrentUserProfile_marksProfileCompletedWhenNamesAreProvided() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .build();
        UpdateProfileRequest request = new UpdateProfileRequest(null, "  Khanh  ", "  Nguyen  ", " https://img.example/avatar.png ");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            return UserResponse.builder()
                    .id(savedUser.getId())
                    .email(savedUser.getEmail())
                    .username(savedUser.getUsername())
                    .firstName(savedUser.getFirstName())
                    .lastName(savedUser.getLastName())
                    .displayName(savedUser.getDisplayName())
                    .avatarUrl(savedUser.getAvatarUrl())
                    .isVerified(savedUser.getIsVerified())
                    .isGoldMember(savedUser.getIsGoldMember())
                    .profileCompleted(savedUser.getProfileCompleted())
                    .build();
        });

        UserResponse response = userService.updateCurrentUserProfile(userId, request);

        assertEquals("Khanh", user.getFirstName());
        assertEquals("Nguyen", user.getLastName());
        assertEquals("https://img.example/avatar.png", user.getAvatarUrl());
        assertTrue(user.getProfileCompleted());
        assertEquals("Khanh Nguyen", response.getDisplayName());
        assertTrue(response.getProfileCompleted());
        verify(userRepository).save(user);
    }

    @Test
    void updateCurrentUserProfile_keepsProfileIncompleteWhenLastNameIsStillMissing() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .build();
        UpdateProfileRequest request = new UpdateProfileRequest(null, "Khanh", null, "https://img.example/avatar.png");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            return UserResponse.builder()
                    .id(savedUser.getId())
                    .email(savedUser.getEmail())
                    .username(savedUser.getUsername())
                    .firstName(savedUser.getFirstName())
                    .lastName(savedUser.getLastName())
                    .avatarUrl(savedUser.getAvatarUrl())
                    .profileCompleted(savedUser.getProfileCompleted())
                    .build();
        });

        UserResponse response = userService.updateCurrentUserProfile(userId, request);

        assertEquals("Khanh", user.getFirstName());
        assertNull(user.getLastName());
        assertEquals("https://img.example/avatar.png", user.getAvatarUrl());
        assertFalse(user.getProfileCompleted());
        assertFalse(response.getProfileCompleted());
    }

    @Test
    void updateCurrentUserProfile_throwsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> userService.updateCurrentUserProfile(
                userId,
                new UpdateProfileRequest("khanh_dev", "Khanh", null, null)
        ));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateCurrentUserProfile_throwsWhenUsernameAlreadyExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCase("another_user")).thenReturn(true);

        assertThrows(AppException.class, () -> userService.updateCurrentUserProfile(
                userId,
                new UpdateProfileRequest("another_user", null, null, null)
        ));

        verify(userRepository, never()).save(any(User.class));
    }
}
