package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.exception.GlobalExceptionHandler;
import com.codegym.locketclone.friendship.dto.*;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FriendshipControllerTest {

    @Mock
    private FriendshipService friendshipService;

    @InjectMocks
    private FriendshipController friendshipController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        currentUserId = UUID.randomUUID();
        userPrincipal = new UserPrincipal(
                currentUserId,
                "khanh",
                "khanh@example.com",
                null,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        );

        mockMvc = MockMvcBuilders.standaloneSetup(friendshipController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void getMyFriends_returnsFriendList() {
        List<FriendProfileResponse> friends = List.of(
                new FriendProfileResponse(UUID.randomUUID(), "friend_1", "Friend", "One", "Friend One", null, false),
                new FriendProfileResponse(UUID.randomUUID(), "friend_2", "Friend", "Two", "Friend Two", null, true)
        );
        when(friendshipService.getAllFriends(currentUserId)).thenReturn(friends);

        var response = friendshipController.getMyFriends(userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() != null && response.getBody().size() == 2);
        verify(friendshipService).getAllFriends(currentUserId);
    }

    @Test
    void getMyFriends_throwsUnauthorizedWhenPrincipalMissing() {
        AppException exception = assertThrows(AppException.class, () -> friendshipController.getMyFriends(null));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void sendFriendRequest_returnsCreated() {
        UUID targetUserId = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        SendFriendRequest request = new SendFriendRequest(targetUserId);

        FriendProfileResponse targetProfile = new FriendProfileResponse(
                targetUserId, "target", "Target", "User", "Target User", null, false);
        SendFriendRequestResponse responseDto = SendFriendRequestResponse.builder()
                .friendshipId(friendshipId)
                .status("PENDING")
                .relationshipStatus("PENDING_SENT")
                .targetUser(targetProfile)
                .createdAt(LocalDateTime.now())
                .build();

        when(friendshipService.sendFriendRequest(eq(currentUserId), any())).thenReturn(responseDto);

        var response = friendshipController.sendFriendRequest(userPrincipal, request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(friendshipId, response.getBody().friendshipId());
        assertEquals("PENDING", response.getBody().status());
    }

    @Test
    void getPendingReceivedRequests_returnsList() {
        UUID friendshipId = UUID.randomUUID();
        FriendProfileResponse requester = new FriendProfileResponse(
                UUID.randomUUID(), "requester", "Req", "User", "Req User", null, false);
        List<FriendRequestResponse> list = List.of(new FriendRequestResponse(friendshipId, requester, LocalDateTime.now()));

        when(friendshipService.getPendingReceivedRequests(currentUserId)).thenReturn(list);

        var response = friendshipController.getPendingReceivedRequests(userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals(friendshipId, response.getBody().get(0).friendshipId());
    }

    @Test
    void getPendingReceivedCount_returnsCount() {
        when(friendshipService.getPendingReceivedCount(currentUserId)).thenReturn(new FriendRequestCountResponse(5L));

        var response = friendshipController.getPendingReceivedCount(userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(5L, response.getBody().pendingCount());
    }

    @Test
    void acceptFriendRequest_returnsOk() {
        UUID friendshipId = UUID.randomUUID();
        FriendProfileResponse friend = new FriendProfileResponse(
                UUID.randomUUID(), "friend", "Fr", "User", "Fr User", null, false);
        AcceptFriendRequestResponse responseDto = AcceptFriendRequestResponse.builder()
                .friendshipId(friendshipId)
                .status("ACCEPTED")
                .friend(friend)
                .acceptedAt(LocalDateTime.now())
                .build();

        when(friendshipService.acceptFriendRequest(currentUserId, friendshipId)).thenReturn(responseDto);

        var response = friendshipController.acceptFriendRequest(userPrincipal, friendshipId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ACCEPTED", response.getBody().status());
    }

    @Test
    void declineFriendRequest_returnsNoContent() {
        UUID friendshipId = UUID.randomUUID();

        var response = friendshipController.declineFriendRequest(userPrincipal, friendshipId);

        assertEquals(204, response.getStatusCode().value());
        verify(friendshipService).declineFriendRequest(currentUserId, friendshipId);
    }

    @Test
    void cancelFriendRequest_returnsNoContent() {
        UUID friendshipId = UUID.randomUUID();

        var response = friendshipController.cancelFriendRequest(userPrincipal, friendshipId);

        assertEquals(204, response.getStatusCode().value());
        verify(friendshipService).cancelFriendRequest(currentUserId, friendshipId);
    }

    @Test
    void unfriend_returnsNoContent() {
        UUID friendId = UUID.randomUUID();

        var response = friendshipController.unfriend(userPrincipal, friendId);

        assertEquals(204, response.getStatusCode().value());
        verify(friendshipService).unfriend(currentUserId, friendId);
    }

    @Test
    void blockUser_returnsNoContent() {
        UUID targetUserId = UUID.randomUUID();

        var response = friendshipController.blockUser(userPrincipal, targetUserId);

        assertEquals(204, response.getStatusCode().value());
        verify(friendshipService).blockUser(currentUserId, targetUserId);
    }

    @Test
    void unblockUser_returnsNoContent() {
        UUID targetUserId = UUID.randomUUID();

        var response = friendshipController.unblockUser(userPrincipal, targetUserId);

        assertEquals(204, response.getStatusCode().value());
        verify(friendshipService).unblockUser(currentUserId, targetUserId);
    }

    @Test
    void getBlockedUsers_returnsOk() {
        FriendProfileResponse blockedProfile = FriendProfileResponse.builder()
                .id(UUID.randomUUID())
                .username("blocked_user")
                .displayName("Blocked User")
                .build();

        when(friendshipService.getBlockedUsers(currentUserId)).thenReturn(List.of(blockedProfile));

        var response = friendshipController.getBlockedUsers(userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("blocked_user", response.getBody().get(0).username());
        verify(friendshipService).getBlockedUsers(currentUserId);
    }
}
