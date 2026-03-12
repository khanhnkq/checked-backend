package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.exception.GlobalExceptionHandler;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                .andExpect(jsonPath("$.message").value("{firstName=First name không được để trống nếu được cung cấp}"));
    }
}
