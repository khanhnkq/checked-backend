package com.codegym.locketclone.friendship.invite;

import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkResponse;
import com.codegym.locketclone.friendship.invite.dto.CreateFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.FriendInviteLinkResponse;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendInviteLinkControllerTest {

    @Mock
    private FriendInviteLinkService friendInviteLinkService;

    private FriendInviteLinkController controller;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new FriendInviteLinkController(friendInviteLinkService);
        principal = new UserPrincipal(
                UUID.randomUUID(),
                "khanh",
                "khanh@example.com",
                null,
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
    }

    @Test
    void createOrRotate_returnsCreated() {
        FriendInviteLinkResponse response = new FriendInviteLinkResponse(
                UUID.randomUUID(),
                "https://example.com/friend/accept?token=abc",
                "abc...",
                50,
                0,
                LocalDateTime.now().plusDays(7),
                null,
                "ACTIVE"
        );
        CreateFriendInviteLinkRequest request = new CreateFriendInviteLinkRequest(120);
        when(friendInviteLinkService.createOrRotate(principal.getId(), request)).thenReturn(response);

        var actual = controller.createOrRotate(principal, request);

        assertEquals(201, actual.getStatusCode().value());
        assertNotNull(actual.getBody());
        assertEquals("ACTIVE", actual.getBody().status());
        verify(friendInviteLinkService).createOrRotate(principal.getId(), request);
    }

    @Test
    void getCurrent_returnsOk() {
        FriendInviteLinkResponse response = new FriendInviteLinkResponse(
                UUID.randomUUID(),
                "https://example.com/friend/accept?token=abc",
                "abc...",
                50,
                0,
                LocalDateTime.now().plusDays(7),
                null,
                "ACTIVE"
        );
        when(friendInviteLinkService.getCurrent(principal.getId())).thenReturn(response);

        var actual = controller.getCurrent(principal);

        assertEquals(200, actual.getStatusCode().value());
        assertNotNull(actual.getBody());
        assertEquals("https://example.com/friend/accept?token=abc", actual.getBody().inviteUrl());
        verify(friendInviteLinkService).getCurrent(principal.getId());
    }

    @Test
    void revokeCurrent_returnsNoContent() {
        var actual = controller.revokeCurrent(principal);

        assertEquals(204, actual.getStatusCode().value());
        verify(friendInviteLinkService).revokeCurrent(principal.getId());
    }

    @Test
    void acceptByLink_returnsOk() {
        AcceptFriendInviteLinkRequest request = new AcceptFriendInviteLinkRequest("invite-token");
        AcceptFriendInviteLinkResponse response = new AcceptFriendInviteLinkResponse(
                UUID.randomUUID(),
                "ACCEPTED",
                new FriendProfileResponse(UUID.randomUUID(), "owner", "Owner", "User", "Owner User", null, false),
                LocalDateTime.now()
        );
        when(friendInviteLinkService.acceptByToken(principal.getId(), "invite-token")).thenReturn(response);

        var actual = controller.acceptByLink(principal, request);

        assertEquals(200, actual.getStatusCode().value());
        assertNotNull(actual.getBody());
        assertEquals("ACCEPTED", actual.getBody().status());
        verify(friendInviteLinkService).acceptByToken(principal.getId(), "invite-token");
    }
}



