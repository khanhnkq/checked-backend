package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.dto.AcceptFriendRequestResponse;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.friendship.dto.FriendRequestCountResponse;
import com.codegym.locketclone.friendship.dto.FriendRequestResponse;
import com.codegym.locketclone.friendship.dto.SendFriendRequest;
import com.codegym.locketclone.friendship.dto.SendFriendRequestResponse;
import com.codegym.locketclone.friendship.dto.SentFriendRequestCountResponse;
import com.codegym.locketclone.friendship.dto.SentFriendRequestResponse;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CacheManager cacheManager;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Cacheable(value = CacheConfig.FRIENDS_CACHE, key = "#userId")
    @Transactional(readOnly = true)
    public List<FriendProfileResponse> getAllFriends(UUID userId) {
        return friendshipRepository.findAllAcceptedFriends(userId)
                .stream()
                .map(friendship -> resolveFriendUser(friendship, userId))
                .map(userMapper::toFriendProfileResponse)
                .toList();
    }

    @Override
    @Transactional
    public SendFriendRequestResponse sendFriendRequest(UUID currentUserId, SendFriendRequest request) {
        if (request == null || request.targetUserId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        UUID targetUserId = request.targetUserId();
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF_AS_FRIEND);
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateFriendLimit(currentUser, false);
        validateFriendLimit(targetUser, true);

        Optional<Friendship> relationOpt = friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId);
        if (relationOpt.isPresent()) {
            Friendship existing = relationOpt.get();
            if (existing.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new AppException(ErrorCode.FRIEND_ALREADY_EXISTS);
            }
            if (existing.getStatus() == FriendshipStatus.BLOCKED) {
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            // Status is PENDING
            if (existing.getUser().getId().equals(currentUserId)) {
                throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
            }

            // Mutual request: Target user already sent request to current user -> Auto-accept!
            existing.setStatus(FriendshipStatus.ACCEPTED);
            Friendship accepted = friendshipRepository.save(existing);
            evictFriendshipAndFeedCache(currentUserId, targetUserId);

            FriendProfileResponse currentUserProfile = userMapper.toFriendProfileResponse(currentUser);
            sendRealtimeEvent(targetUserId, "FRIEND_REQUEST_ACCEPTED", currentUserProfile);

            return SendFriendRequestResponse.builder()
                    .friendshipId(accepted.getId())
                    .status("ACCEPTED")
                    .relationshipStatus("ACCEPTED")
                    .targetUser(userMapper.toFriendProfileResponse(targetUser))
                    .createdAt(accepted.getCreatedAt() != null ? accepted.getCreatedAt() : LocalDateTime.now())
                    .build();
        }

        // Create new PENDING Friendship
        Friendship newFriendship = Friendship.builder()
                .user(currentUser)
                .friend(targetUser)
                .status(FriendshipStatus.PENDING)
                .build();

        Friendship saved;
        try {
            saved = friendshipRepository.save(newFriendship);
        } catch (DataIntegrityViolationException ex) {
            // Concurrency catch: Target user sent request at the exact same microsecond
            Friendship concurrent = friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)
                    .orElseThrow(() -> ex);
            if (concurrent.getStatus() == FriendshipStatus.PENDING && !concurrent.getUser().getId().equals(currentUserId)) {
                concurrent.setStatus(FriendshipStatus.ACCEPTED);
                saved = friendshipRepository.save(concurrent);
                evictFriendshipAndFeedCache(currentUserId, targetUserId);
                sendRealtimeEvent(targetUserId, "FRIEND_REQUEST_ACCEPTED", userMapper.toFriendProfileResponse(currentUser));
                return SendFriendRequestResponse.builder()
                        .friendshipId(saved.getId())
                        .status("ACCEPTED")
                        .relationshipStatus("ACCEPTED")
                        .targetUser(userMapper.toFriendProfileResponse(targetUser))
                        .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                        .build();
            }
            saved = concurrent;
        }

        FriendProfileResponse requesterProfile = userMapper.toFriendProfileResponse(currentUser);
        sendRealtimeEvent(targetUserId, "NEW_FRIEND_REQUEST", requesterProfile);

        return SendFriendRequestResponse.builder()
                .friendshipId(saved.getId())
                .status("PENDING")
                .relationshipStatus("PENDING_SENT")
                .targetUser(userMapper.toFriendProfileResponse(targetUser))
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getPendingReceivedRequests(UUID currentUserId) {
        return friendshipRepository.findPendingReceivedRequests(currentUserId)
                .stream()
                .map(f -> FriendRequestResponse.builder()
                        .friendshipId(f.getId())
                        .requester(userMapper.toFriendProfileResponse(f.getUser()))
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FriendRequestCountResponse getPendingReceivedCount(UUID currentUserId) {
        long count = friendshipRepository.countPendingReceivedRequests(currentUserId);
        return new FriendRequestCountResponse(count);
    }

    @Override
    @Transactional
    public AcceptFriendRequestResponse acceptFriendRequest(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendship.getFriend().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_REQUEST_RECIPIENT);
        }

        if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return AcceptFriendRequestResponse.builder()
                    .friendshipId(friendship.getId())
                    .status("ACCEPTED")
                    .friend(userMapper.toFriendProfileResponse(friendship.getUser()))
                    .acceptedAt(friendship.getCreatedAt() != null ? friendship.getCreatedAt() : LocalDateTime.now())
                    .build();
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        validateFriendLimit(currentUser, false);
        validateFriendLimit(friendship.getUser(), true);

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        Friendship saved = friendshipRepository.save(friendship);

        UUID requesterId = saved.getUser().getId();
        evictFriendshipAndFeedCache(currentUserId, requesterId);

        sendRealtimeEvent(requesterId, "FRIEND_REQUEST_ACCEPTED", userMapper.toFriendProfileResponse(currentUser));

        return AcceptFriendRequestResponse.builder()
                .friendshipId(saved.getId())
                .status("ACCEPTED")
                .friend(userMapper.toFriendProfileResponse(saved.getUser()))
                .acceptedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public void declineFriendRequest(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendship.getFriend().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_REQUEST_RECIPIENT);
        }

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional
    public void cancelFriendRequest(UUID currentUserId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!friendship.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional
    public void unfriend(UUID currentUserId, UUID friendId) {
        if (currentUserId.equals(friendId)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Friendship friendship = friendshipRepository.findRelationBetweenUsers(currentUserId, friendId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        friendshipRepository.delete(friendship);
        evictFriendshipAndFeedCache(currentUserId, friendId);

        User currentUser = userRepository.findById(currentUserId).orElse(null);
        if (currentUser != null) {
            sendRealtimeEvent(friendId, "FRIEND_REMOVED", userMapper.toFriendProfileResponse(currentUser));
        }
    }

    @Override
    @Transactional
    public void blockUser(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Optional<Friendship> relationOpt = friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId);
        if (relationOpt.isPresent()) {
            Friendship relation = relationOpt.get();
            if (relation.getStatus() == FriendshipStatus.BLOCKED && relation.getUser().getId().equals(currentUserId)) {
                return; // Already blocked by me
            }
            relation.setUser(currentUser);
            relation.setFriend(targetUser);
            relation.setStatus(FriendshipStatus.BLOCKED);
            friendshipRepository.save(relation);
        } else {
            Friendship newBlock = Friendship.builder()
                    .user(currentUser)
                    .friend(targetUser)
                    .status(FriendshipStatus.BLOCKED)
                    .build();
            friendshipRepository.save(newBlock);
        }

        evictFriendshipAndFeedCache(currentUserId, targetUserId);
        sendRealtimeEvent(targetUserId, "USER_BLOCKED", userMapper.toFriendProfileResponse(currentUser));
    }

    @Override
    @Transactional
    public void unblockUser(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Friendship relation = friendshipRepository.findRelationBetweenUsers(currentUserId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST));

        if (relation.getStatus() != FriendshipStatus.BLOCKED) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        if (!relation.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        friendshipRepository.delete(relation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendProfileResponse> getBlockedUsers(UUID currentUserId) {
        return friendshipRepository.findBlockedUsers(currentUserId)
                .stream()
                .map(Friendship::getFriend)
                .map(userMapper::toFriendProfileResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentFriendRequestResponse> getPendingSentRequests(UUID currentUserId) {
        return friendshipRepository.findPendingSentRequests(currentUserId)
                .stream()
                .map(f -> SentFriendRequestResponse.builder()
                        .friendshipId(f.getId())
                        .targetUser(userMapper.toFriendProfileResponse(f.getFriend()))
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SentFriendRequestCountResponse getPendingSentCount(UUID currentUserId) {
        long count = friendshipRepository.countPendingSentRequests(currentUserId);
        return new SentFriendRequestCountResponse(count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendProfileResponse> getMutualFriends(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            return List.of();
        }
        return friendshipRepository.findMutualFriends(currentUserId, targetUserId)
                .stream()
                .map(userMapper::toFriendProfileResponse)
                .toList();
    }

    @Override
    @Transactional
    public void acceptAllPendingRequests(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        List<Friendship> pendingRequests = friendshipRepository.findPendingReceivedRequests(currentUserId);
        if (pendingRequests.isEmpty()) {
            return;
        }

        long currentFriendCount = friendshipRepository.countFriends(currentUserId);
        int limit = getMaxFriendsLimit(currentUser);

        List<Friendship> acceptedList = new ArrayList<>();
        List<UUID> acceptedRequesterIds = new ArrayList<>();

        for (Friendship request : pendingRequests) {
            if (currentFriendCount >= limit) {
                break;
            }
            User requester = request.getUser();
            long requesterCount = friendshipRepository.countFriends(requester.getId());
            if (requesterCount >= getMaxFriendsLimit(requester)) {
                continue;
            }
            request.setStatus(FriendshipStatus.ACCEPTED);
            acceptedList.add(request);
            acceptedRequesterIds.add(requester.getId());
            currentFriendCount++;
        }

        if (!acceptedList.isEmpty()) {
            friendshipRepository.saveAll(acceptedList);
            for (UUID requesterId : acceptedRequesterIds) {
                evictFriendshipAndFeedCache(currentUserId, requesterId);
                sendRealtimeEvent(requesterId, "FRIEND_REQUEST_ACCEPTED", userMapper.toFriendProfileResponse(currentUser));
            }
        }
    }

    @Override
    @Transactional
    public void declineAllPendingRequests(UUID currentUserId) {
        List<Friendship> pendingRequests = friendshipRepository.findPendingReceivedRequests(currentUserId);
        if (!pendingRequests.isEmpty()) {
            friendshipRepository.deleteAll(pendingRequests);
        }
    }

    private int getMaxFriendsLimit(User user) {
        return Boolean.TRUE.equals(user.getIsGoldMember()) ? 100 : 20;
    }

    private void validateFriendLimit(User user, boolean isTargetUser) {
        long count = friendshipRepository.countFriends(user.getId());
        int limit = getMaxFriendsLimit(user);
        if (count >= limit) {
            throw new AppException(isTargetUser ? ErrorCode.TARGET_USER_MAX_FRIENDS_REACHED : ErrorCode.MAX_FRIENDS_REACHED);
        }
    }

    private void evictFriendshipAndFeedCache(UUID userA, UUID userB) {
        if (cacheManager == null) return;

        Cache friendsCache = cacheManager.getCache(CacheConfig.FRIENDS_CACHE);
        if (friendsCache != null) {
            friendsCache.evict(userA);
            friendsCache.evict(userB);
        }

        Cache feedCache = cacheManager.getCache(CacheConfig.FEED_CACHE);
        if (feedCache != null) {
            feedCache.evict(userA);
            feedCache.evict(userB);
        }
    }

    private void sendRealtimeEvent(UUID targetUserId, String eventType, Object payload) {
        if (messagingTemplate == null || targetUserId == null) return;
        try {
            messagingTemplate.convertAndSendToUser(
                    targetUserId.toString(),
                    "/queue/friend-requests",
                    Map.of(
                            "type", eventType,
                            "data", payload,
                            "timestamp", LocalDateTime.now().toString()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to send WebSocket friend-requests event: {}", e.getMessage());
        }
    }

    private User resolveFriendUser(Friendship friendship, UUID userId) {
        if (friendship.getUser().getId().equals(userId)) {
            return friendship.getFriend();
        }
        return friendship.getUser();
    }
}
