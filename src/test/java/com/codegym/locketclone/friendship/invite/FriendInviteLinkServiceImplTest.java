package com.codegym.locketclone.friendship.invite;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.friendship.invite.dto.CreateFriendInviteLinkRequest;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import com.codegym.locketclone.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendInviteLinkServiceImplTest {

    @Mock
    private FriendInviteLinkRepository friendInviteLinkRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FriendInviteLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "inviteBaseUrl", "https://example.com/friend/accept");
    }

    @Test
    void createOrRotate_revokesCurrentAndCreatesNewLink() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("khanh@example.com").username("khanh").password("x").build();

        FriendInviteLink active = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .token("active-token")
                .maxUses(50)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(friendInviteLinkRepository.findByOwner_IdAndRevokedAtIsNullAndExpiresAtAfter(eq(ownerId), any(LocalDateTime.class)))
                .thenReturn(List.of(active));
        when(friendInviteLinkRepository.existsByToken(anyString())).thenReturn(false);
        when(friendInviteLinkRepository.save(any(FriendInviteLink.class))).thenAnswer(invocation -> {
            FriendInviteLink link = invocation.getArgument(0);
            link.setId(UUID.randomUUID());
            return link;
        });

        var response = service.createOrRotate(ownerId, new CreateFriendInviteLinkRequest(60));

        assertNotNull(response.id());
        assertTrue(response.inviteUrl().startsWith("https://example.com/friend/accept?token="));
        assertEquals("ACTIVE", response.status());
        assertEquals(50, response.maxUses());
        assertEquals(0, response.usedCount());
        verify(friendInviteLinkRepository).saveAll(anyList());
    }

    @Test
    void acceptByToken_createsAcceptedFriendshipAndIncrementsUsage() {
        UUID ownerId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("owner@example.com").username("owner").password("x").build();
        User currentUser = User.builder().id(currentUserId).email("me@example.com").username("me").password("x").build();

        FriendInviteLink link = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .token("invite-token")
                .maxUses(50)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        Friendship createdFriendship = Friendship.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .friend(currentUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(friendInviteLinkRepository.findByTokenAndRevokedAtIsNull("invite-token")).thenReturn(Optional.of(link));
        when(friendshipRepository.findAcceptedBetweenUsers(ownerId, currentUserId)).thenReturn(Optional.empty());
        when(friendshipRepository.findBetweenUsers(ownerId, currentUserId)).thenReturn(List.of());
        when(friendshipRepository.save(any(Friendship.class))).thenReturn(createdFriendship);
        when(userMapper.toResponse(owner)).thenReturn(UserResponse.builder().id(ownerId).username("owner").build());

        var response = service.acceptByToken(currentUserId, "invite-token");

        assertEquals(createdFriendship.getId(), response.friendshipId());
        assertEquals("ACCEPTED", response.status());
        assertNotNull(response.friend());
        assertEquals(1, link.getUsedCount());
        verify(friendInviteLinkRepository).save(link);
    }

    @Test
    void acceptByToken_throwsWhenExpired() {
        UUID ownerId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("owner@example.com").username("owner").password("x").build();
        User currentUser = User.builder().id(currentUserId).email("me@example.com").username("me").password("x").build();

        FriendInviteLink link = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .token("invite-token")
                .maxUses(50)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(friendInviteLinkRepository.findByTokenAndRevokedAtIsNull("invite-token")).thenReturn(Optional.of(link));

        AppException exception = assertThrows(AppException.class, () -> service.acceptByToken(currentUserId, "invite-token"));

        assertEquals(ErrorCode.FRIEND_INVITE_LINK_EXPIRED, exception.getErrorCode());
    }

    @Test
    void acceptByToken_throwsWhenRevoked() {
        UUID ownerId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("owner@example.com").username("owner").password("x").build();
        User currentUser = User.builder().id(currentUserId).email("me@example.com").username("me").password("x").build();

        FriendInviteLink revokedLink = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .token("invite-token")
                .maxUses(50)
                .usedCount(1)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(friendInviteLinkRepository.findByTokenAndRevokedAtIsNull("invite-token")).thenReturn(Optional.empty());
        when(friendInviteLinkRepository.findByToken("invite-token")).thenReturn(Optional.of(revokedLink));

        AppException exception = assertThrows(AppException.class, () -> service.acceptByToken(currentUserId, "invite-token"));

        assertEquals(ErrorCode.FRIEND_INVITE_LINK_REVOKED, exception.getErrorCode());
    }

    @Test
    void acceptByToken_throwsWhenMaxUsesReached() {
        UUID ownerId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("owner@example.com").username("owner").password("x").build();
        User currentUser = User.builder().id(currentUserId).email("me@example.com").username("me").password("x").build();

        FriendInviteLink exhaustedLink = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .token("invite-token")
                .maxUses(1)
                .usedCount(1)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(friendInviteLinkRepository.findByTokenAndRevokedAtIsNull("invite-token")).thenReturn(Optional.of(exhaustedLink));

        AppException exception = assertThrows(AppException.class, () -> service.acceptByToken(currentUserId, "invite-token"));

        assertEquals(ErrorCode.FRIEND_INVITE_LINK_MAX_USES_REACHED, exception.getErrorCode());
    }

    @Test
    void getCurrent_throwsNotFoundWhenNoActiveLink() {
        UUID ownerId = UUID.randomUUID();
        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(friendInviteLinkRepository.findFirstByOwner_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(ownerId), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> service.getCurrent(ownerId));

        assertEquals(ErrorCode.FRIEND_INVITE_LINK_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void revokeCurrent_marksLinkAsRevoked() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("khanh@example.com").username("khanh").password("x").build();
        FriendInviteLink current = FriendInviteLink.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .token("token")
                .maxUses(50)
                .usedCount(0)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(friendInviteLinkRepository.findFirstByOwner_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                eq(ownerId), any(LocalDateTime.class)
        )).thenReturn(Optional.of(current));

        service.revokeCurrent(ownerId);

        assertNotNull(current.getRevokedAt());
        verify(friendInviteLinkRepository).save(current);
    }

    @Test
    void createOrRotate_throwsWhenTtlOutOfRange() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("khanh@example.com").username("khanh").password("x").build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(friendInviteLinkRepository.findByOwner_IdAndRevokedAtIsNullAndExpiresAtAfter(eq(ownerId), any(LocalDateTime.class)))
                .thenReturn(List.of());

        AppException exception = assertThrows(AppException.class,
                () -> service.createOrRotate(ownerId, new CreateFriendInviteLinkRequest(1)));

        assertEquals(ErrorCode.INVALID_FRIEND_INVITE_TTL, exception.getErrorCode());
    }
}



