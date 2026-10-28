package com.codegym.locketclone.user.share;

import com.codegym.locketclone.friendship.invite.FriendInviteLink;
import com.codegym.locketclone.friendship.invite.FriendInviteLinkRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendInviteLinkRepository friendInviteLinkRepository;

    @InjectMocks
    private ShareController shareController;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shareController, "profileBaseUrl", "https://checked.app/u/");
        ReflectionTestUtils.setField(shareController, "inviteBaseUrl", "https://checked.app/invite");

        user = User.builder()
                .id(UUID.randomUUID())
                .username("khanhnkq")
                .firstName("Khanh")
                .lastName("Nguyen")
                .avatarUrl("https://storage.checked.app/avatars/khanh.jpg")
                .isGoldMember(true)
                .build();
    }

    // MARK: - Profile Social Share Tests (/u/{username})

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
        verify(userRepository).findByUsernameIgnoreCase("khanhnkq");
    }

    @Test
    void getSocialSharePage_whenUserNotFound_returnsNotFoundHtml() {
        when(userRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());

        ResponseEntity<String> response = shareController.getSocialSharePage("unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        String html = response.getBody();
        assertTrue(html.contains("@unknown"));
        assertTrue(html.contains("Liên kết không khả dụng"));
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
        assertTrue(response.getBody().contains(ShareController.DEFAULT_AVATAR_URL));
    }

    @Test
    void getSocialSharePage_whenUserHasMaliciousAvatarScheme_usesDefaultAvatar() {
        User maliciousUser = User.builder()
                .id(UUID.randomUUID())
                .username("badactor")
                .firstName("Bad")
                .lastName("Actor")
                .avatarUrl("javascript:alert(1)")
                .build();

        when(userRepository.findByUsernameIgnoreCase("badactor")).thenReturn(Optional.of(maliciousUser));

        ResponseEntity<String> response = shareController.getSocialSharePage("badactor");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(ShareController.DEFAULT_AVATAR_URL));
        assertFalse(response.getBody().contains("javascript:alert(1)"));
    }

    // MARK: - Invite Link Share Tests (/invite & /friend/accept)

    @Test
    void getInviteSharePage_whenTokenNullOrEmpty_returnsBadRequestHtml() {
        ResponseEntity<String> responseNull = shareController.getInviteSharePage(null);
        assertEquals(HttpStatus.BAD_REQUEST, responseNull.getStatusCode());
        assertNotNull(responseNull.getBody());
        assertTrue(responseNull.getBody().contains("Liên kết không khả dụng"));
        assertTrue(responseNull.getBody().contains(org.springframework.web.util.HtmlUtils.htmlEscape("Vui lòng cung cấp mã mời (token) hợp lệ.")));

        ResponseEntity<String> responseEmpty = shareController.getInviteSharePage("   ");
        assertEquals(HttpStatus.BAD_REQUEST, responseEmpty.getStatusCode());
        assertNotNull(responseEmpty.getBody());
        assertTrue(responseEmpty.getBody().contains(org.springframework.web.util.HtmlUtils.htmlEscape("Vui lòng cung cấp mã mời (token) hợp lệ.")));
    }

    @Test
    void getInviteSharePage_whenTokenNotFound_returnsNotFoundHtml() {
        when(friendInviteLinkRepository.findByTokenWithOwner("nonexistent-token")).thenReturn(Optional.empty());

        ResponseEntity<String> response = shareController.getInviteSharePage("nonexistent-token");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Liên kết không khả dụng"));
        assertTrue(response.getBody().contains(org.springframework.web.util.HtmlUtils.htmlEscape("Liên kết mời kết bạn không tồn tại hoặc đã bị xóa.")));
    }

    @Test
    void getInviteSharePage_whenTokenRevoked_returnsGoneHtml() {
        FriendInviteLink revokedLink = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .token("revoked-token")
                .owner(user)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .revokedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(friendInviteLinkRepository.findByTokenWithOwner("revoked-token")).thenReturn(Optional.of(revokedLink));

        ResponseEntity<String> response = shareController.getInviteSharePage("revoked-token");

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Liên kết không khả dụng"));
        assertTrue(response.getBody().contains(org.springframework.web.util.HtmlUtils.htmlEscape("Liên kết mời kết bạn này đã bị chủ sở hữu hủy.")));
    }

    @Test
    void getInviteSharePage_whenTokenExpired_returnsGoneHtml() {
        FriendInviteLink expiredLink = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .token("expired-token")
                .owner(user)
                .expiresAt(LocalDateTime.now().minusHours(2))
                .revokedAt(null)
                .build();

        when(friendInviteLinkRepository.findByTokenWithOwner("expired-token")).thenReturn(Optional.of(expiredLink));

        ResponseEntity<String> response = shareController.getInviteSharePage("expired-token");

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Liên kết không khả dụng"));
        assertTrue(response.getBody().contains(org.springframework.web.util.HtmlUtils.htmlEscape("Liên kết mời kết bạn này đã hết hạn.")));
    }

    @Test
    void getInviteSharePage_whenTokenValid_returnsOkWithCardAndCopyButton() {
        String token = "valid-secret-token-xyz";
        FriendInviteLink validLink = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .token(token)
                .owner(user)
                .expiresAt(LocalDateTime.now().plusDays(5))
                .revokedAt(null)
                .maxUses(50)
                .usedCount(2)
                .build();

        when(friendInviteLinkRepository.findByTokenWithOwner(token)).thenReturn(Optional.of(validLink));

        ResponseEntity<String> response = shareController.getInviteSharePage(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String html = response.getBody();

        // Kiểm tra OpenGraph metadata
        assertTrue(html.contains("og:title"));
        assertTrue(html.contains("Khanh Nguyen"));
        assertTrue(html.contains("og:image"));
        assertTrue(html.contains(user.getAvatarUrl()));

        // Kiểm tra DeepLink & Web invite URL
        assertTrue(html.contains("checked://invite?token=" + token));
        assertTrue(html.contains("https://checked.app/invite?token=" + token));

        // Kiểm tra UI Component Card & Nút Copy Token
        assertTrue(html.contains("MÃ MỜI"));
        assertTrue(html.contains(token));
        assertTrue(html.contains("copyToken('" + token + "'"));
        assertTrue(html.contains("Sao chép"));
        assertTrue(html.contains("Chấp nhận kết bạn trong Checked"));
    }
}
