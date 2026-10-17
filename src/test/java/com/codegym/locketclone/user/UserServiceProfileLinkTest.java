package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.user.dto.UserProfileWithRelationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileLinkTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User targetUser;
    private UUID targetUserId;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        targetUserId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        targetUser = User.builder()
                .id(targetUserId)
                .email("target@example.com")
                .username("khanhnkq")
                .firstName("Khanh")
                .lastName("Nguyen")
                .avatarUrl("https://cdn.example.com/khanh.jpg")
                .isGoldMember(true)
                .build();
    }

    @Test
    void getUserProfileByUsername_nullOrEmptyUsername_throwsAppException() {
        AppException ex1 = assertThrows(AppException.class, () -> userService.getUserProfileByUsername(null, currentUserId));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () -> userService.getUserProfileByUsername("   ", currentUserId));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex2.getErrorCode());
    }

    @Test
    void getUserProfileByUsername_userNotFound_throwsAppException() {
        when(userRepository.findByUsernameIgnoreCase("nonexistent")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> userService.getUserProfileByUsername("nonexistent", currentUserId));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getUserProfileByUsername_whenUnauthenticated_returnsUnauthenticated() {
        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", null);

        assertNotNull(response);
        assertEquals(targetUserId, response.id());
        assertEquals("khanhnkq", response.username());
        assertEquals("Khanh Nguyen", response.displayName());
        assertEquals("UNAUTHENTICATED", response.relationshipStatus());
        assertNull(response.friendshipId());
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void getUserProfileByUsername_whenSelf_returnsSelf() {
        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", targetUserId);

        assertNotNull(response);
        assertEquals(targetUserId, response.id());
        assertEquals("SELF", response.relationshipStatus());
        assertNull(response.friendshipId());
        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void getUserProfileByUsername_whenNoRelation_returnsNone() {
        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.empty());

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", currentUserId);

        assertNotNull(response);
        assertEquals(targetUserId, response.id());
        assertEquals("NONE", response.relationshipStatus());
        assertNull(response.friendshipId());
    }

    @Test
    void getUserProfileByUsername_whenAccepted_returnsAccepted() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .user(targetUser)
                .friend(User.builder().id(currentUserId).build())
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(friendship));

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", currentUserId);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.relationshipStatus());
        assertEquals(friendshipId, response.friendshipId());
    }

    @Test
    void getUserProfileByUsername_whenBlocked_returnsBlocked() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .user(targetUser)
                .friend(User.builder().id(currentUserId).build())
                .status(FriendshipStatus.BLOCKED)
                .build();

        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(friendship));

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", currentUserId);

        assertNotNull(response);
        assertEquals("BLOCKED", response.relationshipStatus());
        assertEquals(friendshipId, response.friendshipId());
    }

    @Test
    void getUserProfileByUsername_whenPendingSent_returnsPendingSent() {
        UUID friendshipId = UUID.randomUUID();
        // current user is the requester (user)
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .user(User.builder().id(currentUserId).build())
                .friend(targetUser)
                .status(FriendshipStatus.PENDING)
                .build();

        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(friendship));

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", currentUserId);

        assertNotNull(response);
        assertEquals("PENDING_SENT", response.relationshipStatus());
        assertEquals(friendshipId, response.friendshipId());
    }

    @Test
    void getUserProfileByUsername_whenPendingReceived_returnsPendingReceived() {
        UUID friendshipId = UUID.randomUUID();
        // target user is the requester (user), current user is the addressee (friend)
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .user(targetUser)
                .friend(User.builder().id(currentUserId).build())
                .status(FriendshipStatus.PENDING)
                .build();

        when(userRepository.findByUsernameIgnoreCase("khanhnkq")).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(friendship));

        UserProfileWithRelationResponse response = userService.getUserProfileByUsername("khanhnkq", currentUserId);

        assertNotNull(response);
        assertEquals("PENDING_RECEIVED", response.relationshipStatus());
        assertEquals(friendshipId, response.friendshipId());
    }
}
