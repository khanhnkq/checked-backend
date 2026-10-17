package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.friendship.dto.AcceptFriendRequestResponse;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.friendship.dto.FriendRequestCountResponse;
import com.codegym.locketclone.friendship.dto.FriendRequestResponse;
import com.codegym.locketclone.friendship.dto.SendFriendRequest;
import com.codegym.locketclone.friendship.dto.SendFriendRequestResponse;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

public interface FriendshipService {
    // Lấy danh sách bạn bè đã đồng ý (không lộ email cá nhân)
    @Cacheable(value = CacheConfig.FRIENDS_CACHE, key = "#userId")
    List<FriendProfileResponse> getAllFriends(UUID userId);

    SendFriendRequestResponse sendFriendRequest(UUID currentUserId, SendFriendRequest request);

    List<FriendRequestResponse> getPendingReceivedRequests(UUID currentUserId);

    FriendRequestCountResponse getPendingReceivedCount(UUID currentUserId);

    AcceptFriendRequestResponse acceptFriendRequest(UUID currentUserId, UUID friendshipId);

    void declineFriendRequest(UUID currentUserId, UUID friendshipId);

    void cancelFriendRequest(UUID currentUserId, UUID friendshipId);

    void unfriend(UUID currentUserId, UUID friendId);

    void blockUser(UUID currentUserId, UUID targetUserId);

    void unblockUser(UUID currentUserId, UUID targetUserId);

    List<FriendProfileResponse> getBlockedUsers(UUID currentUserId);
}