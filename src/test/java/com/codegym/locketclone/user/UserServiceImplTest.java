package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.user.dto.PublicUserProfileResponse;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
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

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getUserById_returnsPublicUserProfileWithoutEmail() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("secret@example.com")
                .username("public_user")
                .firstName("John")
                .lastName("Doe")
                .avatarUrl("https://cdn.example.com/avatar.jpg")
                .isGoldMember(false)
                .build();

        PublicUserProfileResponse publicResponse = PublicUserProfileResponse.builder()
                .id(userId)
                .username("public_user")
                .firstName("John")
                .lastName("Doe")
                .displayName("John Doe")
                .avatarUrl("https://cdn.example.com/avatar.jpg")
                .isGoldMember(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toPublicResponse(user)).thenReturn(publicResponse);

        PublicUserProfileResponse response = userService.getUserById(userId);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        assertEquals("public_user", response.getUsername());
        assertEquals("John Doe", response.getDisplayName());
        verify(userRepository).findById(userId);
        verify(userMapper).toPublicResponse(user);
    }

    @Test
    void getUserById_throwsWhenNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.getUserById(userId));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verify(userMapper, never()).toPublicResponse(any());
    }

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

    @Test
    void updateAvatar_uploadsAndUpdatesUserAvatar() throws IOException {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .build();

        var file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "avatar-bytes".getBytes()
        );

        UploadedFile uploadedFile = new UploadedFile(
                "https://cdn.example.com/avatar.jpg",
                "https://cdn.example.com/avatar.jpg",
                "public-id",
                MediaType.IMAGE_JPEG_VALUE,
                1000L,
                200,
                200
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storageService.uploadAvatar(file)).thenReturn(uploadedFile);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return UserResponse.builder()
                    .id(u.getId())
                    .avatarUrl(u.getAvatarUrl())
                    .build();
        });

        UserResponse response = userService.updateAvatar(userId, file);

        assertNotNull(response);
        assertEquals("https://cdn.example.com/avatar.jpg", user.getAvatarUrl());
        verify(storageService).uploadAvatar(file);
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatar_throwsWhenFileInvalid() {
        UUID userId = UUID.randomUUID();
        var emptyFile = new MockMultipartFile("file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        AppException ex = assertThrows(AppException.class, () -> userService.updateAvatar(userId, emptyFile));
        assertEquals(ErrorCode.INVALID_PHOTO_FILE, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }
}
