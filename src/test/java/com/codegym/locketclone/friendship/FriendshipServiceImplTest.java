package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.dto.*;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceImplTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache friendsCache;

    @Mock
    private Cache feedCache;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private FriendshipServiceImpl friendshipService;

    private UUID currentUserId;
    private UUID targetUserId;
    private User currentUser;
    private User targetUser;
    private FriendProfileResponse targetProfile;
    private FriendProfileResponse currentProfile;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        currentUser = User.builder()
                .id(currentUserId)
                .username("me")
                .email("me@example.com")
                .firstName("My")
                .lastName("Name")
                .build();

        targetUser = User.builder()
                .id(targetUserId)
                .username("target")
                .email("target@example.com")
                .firstName("Target")
                .lastName("User")
                .build();

        currentProfile = new FriendProfileResponse(currentUserId, "me", "My", "Name", "My Name", null, false);
        targetProfile = new FriendProfileResponse(targetUserId, "target", "Target", "User", "Target User", null, false);
    }

    @Test
    void getAllFriends_mapsOppositeUserFromAcceptedFriendships() {
        UUID friendAId = UUID.randomUUID();
        UUID friendBId = UUID.randomUUID();

        User friendA = User.builder().id(friendAId).username("friend_a").email("a@example.com").build();
        User friendB = User.builder().id(friendBId).username("friend_b").email("b@example.com").build();

        Friendship row1 = Friendship.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .friend(friendA)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        Friendship row2 = Friendship.builder()
                .id(UUID.randomUUID())
                .user(friendB)
                .friend(currentUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(friendshipRepository.findAllAcceptedFriends(currentUserId)).thenReturn(List.of(row1, row2));
        when(userMapper.toFriendProfileResponse(friendA)).thenReturn(new FriendProfileResponse(friendAId, "friend_a", "A", "User", "A User", null, false));
        when(userMapper.toFriendProfileResponse(friendB)).thenReturn(new FriendProfileResponse(friendBId, "friend_b", "B", "User", "B User", null, false));

        List<FriendProfileResponse> result = friendshipService.getAllFriends(currentUserId);

        assertEquals(2, result.size());
        assertEquals(friendAId, result.get(0).id());
        assertEquals(friendBId, result.get(1).id());
        verify(friendshipRepository).findAllAcceptedFriends(currentUserId);
    }

    @Test
    void sendFriendRequest_whenTargetIsSelf_throwsCannotAddSelf() {
        SendFriendRequest request = new SendFriendRequest(currentUserId);
        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(currentUserId, request));
        assertEquals(ErrorCode.CANNOT_ADD_SELF_AS_FRIEND, ex.getErrorCode());
    }

    @Test
    void sendFriendRequest_whenTargetNotFound_throwsUserNotFound() {
        SendFriendRequest request = new SendFriendRequest(targetUserId);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(currentUserId, request));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void sendFriendRequest_whenAlreadyFriends_throwsFriendAlreadyExists() {
        SendFriendRequest request = new SendFriendRequest(targetUserId);
        Friendship existing = Friendship.builder().status(FriendshipStatus.ACCEPTED).build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(existing));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(currentUserId, request));
        assertEquals(ErrorCode.FRIEND_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void sendFriendRequest_whenBlocked_throwsUserBlocked() {
        SendFriendRequest request = new SendFriendRequest(targetUserId);
        Friendship existing = Friendship.builder().status(FriendshipStatus.BLOCKED).build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(existing));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(currentUserId, request));
        assertEquals(ErrorCode.USER_BLOCKED, ex.getErrorCode());
    }

    @Test
    void sendFriendRequest_whenAlreadyPendingSent_throwsFriendRequestAlreadySent() {
        SendFriendRequest request = new SendFriendRequest(targetUserId);
        Friendship existing = Friendship.builder()
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.PENDING)
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(existing));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.sendFriendRequest(currentUserId, request));
        assertEquals(ErrorCode.FRIEND_REQUEST_ALREADY_SENT, ex.getErrorCode());
    }

    @Test
    void sendFriendRequest_whenMutualPending_autoAcceptsAndEvictsCache() {
        SendFriendRequest request = new SendFriendRequest(targetUserId);
        // target user had sent request to current user
        Friendship existing = Friendship.builder()
                .id(UUID.randomUUID())
                .user(targetUser)
                .friend(currentUser)
                .status(FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(existing));
        when(friendshipRepository.save(existing)).thenReturn(existing);
        when(userMapper.toFriendProfileResponse(targetUser)).thenReturn(targetProfile);
        when(userMapper.toFriendProfileResponse(currentUser)).thenReturn(currentProfile);
        when(cacheManager.getCache(CacheConfig.FRIENDS_CACHE)).thenReturn(friendsCache);
        when(cacheManager.getCache(CacheConfig.FEED_CACHE)).thenReturn(feedCache);

        SendFriendRequestResponse response = friendshipService.sendFriendRequest(currentUserId, request);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.status());
        assertEquals("ACCEPTED", response.relationshipStatus());
        verify(friendsCache).evict(currentUserId);
        verify(friendsCache).evict(targetUserId);
        verify(feedCache).evict(currentUserId);
        verify(feedCache).evict(targetUserId);
        verify(messagingTemplate).convertAndSendToUser(eq(targetUserId.toString()), eq("/queue/friend-requests"), any());
    }

    @Test
    void sendFriendRequest_whenNew_createsPendingAndSendsRealtimeEvent() {
        SendFriendRequest request = new SendFriendRequest(targetUserId);
        UUID friendshipId = UUID.randomUUID();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.empty());
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship f = invocation.getArgument(0);
            f.setId(friendshipId);
            return f;
        });
        when(userMapper.toFriendProfileResponse(currentUser)).thenReturn(currentProfile);
        when(userMapper.toFriendProfileResponse(targetUser)).thenReturn(targetProfile);

        SendFriendRequestResponse response = friendshipService.sendFriendRequest(currentUserId, request);

        assertNotNull(response);
        assertEquals(friendshipId, response.friendshipId());
        assertEquals("PENDING", response.status());
        assertEquals("PENDING_SENT", response.relationshipStatus());
        verify(messagingTemplate).convertAndSendToUser(eq(targetUserId.toString()), eq("/queue/friend-requests"), any());
    }

    @Test
    void getPendingReceivedRequests_returnsListOfRequests() {
        Friendship f1 = Friendship.builder()
                .id(UUID.randomUUID())
                .user(targetUser)
                .friend(currentUser)
                .status(FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(friendshipRepository.findPendingReceivedRequests(currentUserId)).thenReturn(List.of(f1));
        when(userMapper.toFriendProfileResponse(targetUser)).thenReturn(targetProfile);

        List<FriendRequestResponse> result = friendshipService.getPendingReceivedRequests(currentUserId);

        assertEquals(1, result.size());
        assertEquals(f1.getId(), result.get(0).friendshipId());
        assertEquals(targetUserId, result.get(0).requester().id());
    }

    @Test
    void getPendingReceivedCount_returnsCount() {
        when(friendshipRepository.countPendingReceivedRequests(currentUserId)).thenReturn(3L);

        FriendRequestCountResponse result = friendshipService.getPendingReceivedCount(currentUserId);

        assertEquals(3L, result.pendingCount());
    }

    @Test
    void acceptFriendRequest_whenNotFound_throwsNotFound() {
        UUID friendshipId = UUID.randomUUID();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest(currentUserId, friendshipId));
        assertEquals(ErrorCode.FRIEND_REQUEST_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void acceptFriendRequest_whenNotRecipient_throwsNotRequestRecipient() {
        UUID friendshipId = UUID.randomUUID();
        // current user is the requester, NOT the friend/recipient
        Friendship f = Friendship.builder()
                .id(friendshipId)
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.PENDING)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(f));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest(currentUserId, friendshipId));
        assertEquals(ErrorCode.NOT_REQUEST_RECIPIENT, ex.getErrorCode());
    }

    @Test
    void acceptFriendRequest_whenBlocked_throwsUserBlocked() {
        UUID friendshipId = UUID.randomUUID();
        Friendship f = Friendship.builder()
                .id(friendshipId)
                .user(targetUser)
                .friend(currentUser)
                .status(FriendshipStatus.BLOCKED)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(f));

        AppException ex = assertThrows(AppException.class, () -> friendshipService.acceptFriendRequest(currentUserId, friendshipId));
        assertEquals(ErrorCode.USER_BLOCKED, ex.getErrorCode());
    }

    @Test
    void acceptFriendRequest_whenValid_updatesStatusAndEvictsCache() {
        UUID friendshipId = UUID.randomUUID();
        Friendship f = Friendship.builder()
                .id(friendshipId)
                .user(targetUser)
                .friend(currentUser)
                .status(FriendshipStatus.PENDING)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(f));
        when(friendshipRepository.save(f)).thenReturn(f);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userMapper.toFriendProfileResponse(targetUser)).thenReturn(targetProfile);
        when(userMapper.toFriendProfileResponse(currentUser)).thenReturn(currentProfile);
        when(cacheManager.getCache(CacheConfig.FRIENDS_CACHE)).thenReturn(friendsCache);
        when(cacheManager.getCache(CacheConfig.FEED_CACHE)).thenReturn(feedCache);

        AcceptFriendRequestResponse response = friendshipService.acceptFriendRequest(currentUserId, friendshipId);

        assertNotNull(response);
        assertEquals(friendshipId, response.friendshipId());
        assertEquals("ACCEPTED", response.status());
        verify(friendsCache).evict(currentUserId);
        verify(friendsCache).evict(targetUserId);
        verify(feedCache).evict(currentUserId);
        verify(feedCache).evict(targetUserId);
        verify(messagingTemplate).convertAndSendToUser(eq(targetUserId.toString()), eq("/queue/friend-requests"), any());
    }

    @Test
    void declineFriendRequest_whenValid_deletesFriendship() {
        UUID friendshipId = UUID.randomUUID();
        Friendship f = Friendship.builder()
                .id(friendshipId)
                .user(targetUser)
                .friend(currentUser)
                .status(FriendshipStatus.PENDING)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(f));

        friendshipService.declineFriendRequest(currentUserId, friendshipId);

        verify(friendshipRepository).delete(f);
    }

    @Test
    void cancelFriendRequest_whenValid_deletesFriendship() {
        UUID friendshipId = UUID.randomUUID();
        // current user is the requester
        Friendship f = Friendship.builder()
                .id(friendshipId)
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.PENDING)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(f));

        friendshipService.cancelFriendRequest(currentUserId, friendshipId);

        verify(friendshipRepository).delete(f);
    }

    @Test
    void unfriend_whenValid_deletesFriendshipAndEvictsCache() {
        Friendship f = Friendship.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(f));
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userMapper.toFriendProfileResponse(currentUser)).thenReturn(currentProfile);
        when(cacheManager.getCache(CacheConfig.FRIENDS_CACHE)).thenReturn(friendsCache);
        when(cacheManager.getCache(CacheConfig.FEED_CACHE)).thenReturn(feedCache);

        friendshipService.unfriend(currentUserId, targetUserId);

        verify(friendshipRepository).delete(f);
        verify(friendsCache).evict(currentUserId);
        verify(friendsCache).evict(targetUserId);
        verify(feedCache).evict(currentUserId);
        verify(feedCache).evict(targetUserId);
        verify(messagingTemplate).convertAndSendToUser(eq(targetUserId.toString()), eq("/queue/friend-requests"), any());
    }

    @Test
    void blockUser_whenSelf_throwsInvalidRequest() {
        AppException ex = assertThrows(AppException.class,
                () -> friendshipService.blockUser(currentUserId, currentUserId));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void blockUser_whenAlreadyFriends_convertsToBlockedAndEvictsCache() {
        Friendship f = Friendship.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.of(f));
        when(userMapper.toFriendProfileResponse(currentUser)).thenReturn(currentProfile);
        when(cacheManager.getCache(CacheConfig.FRIENDS_CACHE)).thenReturn(friendsCache);
        when(cacheManager.getCache(CacheConfig.FEED_CACHE)).thenReturn(feedCache);

        friendshipService.blockUser(currentUserId, targetUserId);

        assertEquals(FriendshipStatus.BLOCKED, f.getStatus());
        assertEquals(currentUser, f.getUser());
        assertEquals(targetUser, f.getFriend());
        verify(friendshipRepository).save(f);
        verify(friendsCache).evict(currentUserId);
        verify(friendsCache).evict(targetUserId);
        verify(feedCache).evict(currentUserId);
        verify(feedCache).evict(targetUserId);
        verify(messagingTemplate).convertAndSendToUser(eq(targetUserId.toString()), eq("/queue/friend-requests"), any());
    }

    @Test
    void blockUser_whenNew_createsBlocked() {
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)).thenReturn(Optional.empty());
        when(userMapper.toFriendProfileResponse(currentUser)).thenReturn(currentProfile);
        when(cacheManager.getCache(CacheConfig.FRIENDS_CACHE)).thenReturn(friendsCache);
        when(cacheManager.getCache(CacheConfig.FEED_CACHE)).thenReturn(feedCache);

        friendshipService.blockUser(currentUserId, targetUserId);

        verify(friendshipRepository).save(argThat(relation ->
                relation.getUser().equals(currentUser) &&
                        relation.getFriend().equals(targetUser) &&
                        relation.getStatus() == FriendshipStatus.BLOCKED
        ));
    }

    @Test
    void unblockUser_whenBlockedByMe_deletesRelation() {
        Friendship blocked = Friendship.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.BLOCKED)
                .build();

        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId))
                .thenReturn(Optional.of(blocked));

        friendshipService.unblockUser(currentUserId, targetUserId);

        verify(friendshipRepository).delete(blocked);
    }

    @Test
    void unblockUser_whenNotBlockedByMe_throwsForbidden() {
        // Blocked was initiated by targetUser, not currentUserId
        Friendship blocked = Friendship.builder()
                .id(UUID.randomUUID())
                .user(targetUser)
                .friend(currentUser)
                .status(FriendshipStatus.BLOCKED)
                .build();

        when(friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId))
                .thenReturn(Optional.of(blocked));

        AppException ex = assertThrows(AppException.class,
                () -> friendshipService.unblockUser(currentUserId, targetUserId));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void getBlockedUsers_returnsBlockedProfiles() {
        Friendship blocked = Friendship.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.BLOCKED)
                .build();

        when(friendshipRepository.findBlockedUsers(currentUserId)).thenReturn(List.of(blocked));
        when(userMapper.toFriendProfileResponse(targetUser)).thenReturn(targetProfile);

        List<FriendProfileResponse> result = friendshipService.getBlockedUsers(currentUserId);

        assertEquals(1, result.size());
        assertEquals(targetProfile, result.get(0));
        verify(friendshipRepository).findBlockedUsers(currentUserId);
    }
}
