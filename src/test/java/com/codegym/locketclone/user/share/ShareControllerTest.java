package com.codegym.locketclone.user.share;

import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ShareController shareController;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .username("khanhnkq")
                .firstName("Khanh")
                .lastName("Nguyen")
                .avatarUrl("https://storage.checked.app/avatars/khanh.jpg")
                .isGoldMember(true)
                .build();
    }

    @Test
    void getSocialSharePage_whenUserExists_returnsOkWithOpenGraphHtml() {
        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(user));

        ResponseEntity<String> response = shareController.getSocialSharePage("khanhnkq");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String html = response.getBody();
        assertTrue(html.contains("og:title"));
        assertTrue(html.contains("Khanh Nguyen"));
        assertTrue(html.contains("og:image"));
        assertTrue(html.contains("https://storage.checked.app/avatars/khanh.jpg"));
        assertTrue(html.contains("checked://u/khanhnkq"));
        assertTrue(html.contains("https://checked.app/download"));
        verify(userRepository).findByUsernameIgnoreCase("khanhnkq");
    }

    @Test
    void getSocialSharePage_whenUserNotFound_returnsNotFoundHtml() {
        when(userRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());

        ResponseEntity<String> response = shareController.getSocialSharePage("unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        String html = response.getBody();
        assertTrue(html.contains("Không tìm thấy người dùng"));
        assertTrue(html.contains("@unknown"));
        verify(userRepository).findByUsernameIgnoreCase("unknown");
    }

    @Test
    void getSocialSharePage_whenUserHasNoAvatar_usesDefaultAvatar() {
        User noAvatarUser = User.builder()
                .id(UUID.randomUUID())
                .username("nam")
                .firstName("Nam")
                .lastName("Tran")
                .avatarUrl(null)
                .build();

        when(userRepository.findByUsernameIgnoreCase("nam")).thenReturn(Optional.of(noAvatarUser));

        ResponseEntity<String> response = shareController.getSocialSharePage("nam");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("https://storage.checked.app/defaults/default-avatar.png"));
    }
}
