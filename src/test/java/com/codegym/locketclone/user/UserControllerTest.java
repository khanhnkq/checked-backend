package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.exception.GlobalExceptionHandler;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.dto.UpdatePersonalInfoRequest;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void getCurrentUser_returnsUnauthorizedWhenNoAuthenticatedPrincipal() {
        AppException exception = assertThrows(AppException.class, () -> userController.getCurrentUser(null));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void updateCurrentUserProfile_returnsUpdatedUserAndCompletedState() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                null,
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        UpdateProfileRequest request = new UpdateProfileRequest("khanh_dev", "Khanh", "Nguyen", "https://example.com/avatar.jpg");
        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .firstName("Khanh")
                .lastName("Nguyen")
                .displayName("Khanh Nguyen")
                .avatarUrl("https://example.com/avatar.jpg")
                .isVerified(true)
                .isGoldMember(false)
                .profileCompleted(true)
                .build();

        when(userService.updateCurrentUserProfile(userId, request)).thenReturn(response);

        var actual = userController.updateCurrentUserProfile(principal, request);

        assertEquals(200, actual.getStatusCode().value());
        assertNotNull(actual.getBody());
        assertEquals("Khanh Nguyen", actual.getBody().getDisplayName());
        assertEquals(true, actual.getBody().getProfileCompleted());
        verify(userService).updateCurrentUserProfile(userId, request);
    }

    @Test
    void updateCurrentUserProfile_rejectsBlankFirstName() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu yêu cầu không hợp lệ"))
                .andExpect(jsonPath("$.errors.firstName").value("First name không được để trống nếu được cung cấp"));
    }

    @Test
    void updateCurrentUserPersonalInfo_returnsUpdatedUser() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                null,
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        UpdatePersonalInfoRequest request = new UpdatePersonalInfoRequest(
                "khanh_dev",
                "Khanh",
                "Nguyen Kim",
                "https://example.com/avatar-2.jpg"
        );
        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .firstName("Khanh")
                .lastName("Nguyen Kim")
                .displayName("Khanh Nguyen Kim")
                .avatarUrl("https://example.com/avatar-2.jpg")
                .isVerified(true)
                .isGoldMember(false)
                .profileCompleted(true)
                .build();

        when(userService.updatePersonalInfo(userId, request)).thenReturn(response);

        var actual = userController.updateCurrentUserPersonalInfo(principal, request);

        assertEquals(200, actual.getStatusCode().value());
        assertNotNull(actual.getBody());
        assertEquals("Khanh Nguyen Kim", actual.getBody().getDisplayName());
        verify(userService).updatePersonalInfo(userId, request);
    }

    @Test
    void updateCurrentUserPersonalInfo_rejectsBlankLastName() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/settings/personal-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu yêu cầu không hợp lệ"))
                .andExpect(jsonPath("$.errors.lastName").value("Last name khong duoc de trong neu duoc cung cap"));
    }

    @Test
    void updateCurrentUserAvatar_uploadsFileAndUpdatesAvatarUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                null,
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );

        var file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "avatar-data".getBytes()
        );

        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("khanh@example.com")
                .username("khanh_dev")
                .avatarUrl("https://cdn.example.com/avatar.jpg")
                .profileCompleted(true)
                .build();

        when(userService.updateAvatar(userId, file)).thenReturn(response);

        var actual = userController.updateCurrentUserAvatar(principal, file);

        assertEquals(200, actual.getStatusCode().value());
        assertNotNull(actual.getBody());
        assertEquals("https://cdn.example.com/avatar.jpg", actual.getBody().getAvatarUrl());
        verify(userService).updateAvatar(userId, file);
    }

    @Test
    void getUserById_returnsPublicUserProfileWithoutEmail() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        com.codegym.locketclone.user.dto.PublicUserProfileResponse publicResponse =
                com.codegym.locketclone.user.dto.PublicUserProfileResponse.builder()
                        .id(targetUserId)
                        .username("target_user")
                        .firstName("Target")
                        .lastName("User")
                        .displayName("Target User")
                        .avatarUrl("https://example.com/target.jpg")
                        .isGoldMember(true)
                        .build();

        when(userService.getUserById(targetUserId)).thenReturn(publicResponse);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/" + targetUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUserId.toString()))
                .andExpect(jsonPath("$.username").value("target_user"))
                .andExpect(jsonPath("$.displayName").value("Target User"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.isGoldMember").value(true));

        verify(userService).getUserById(targetUserId);
    }
}

