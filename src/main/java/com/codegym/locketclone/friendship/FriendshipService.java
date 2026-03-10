package com.codegym.locketclone.friendship;

import com.codegym.locketclone.user.dto.UserResponse;
import java.util.List;
import java.util.UUID;

public interface FriendshipService {
    // Gửi lời mời kết bạn
    void sendFriendRequest(UUID senderId, UUID receiverId);

    // Chấp nhận lời mời kết bạn
    void acceptFriendRequest(UUID userId, UUID friendId);

    // Lấy danh sách bạn bè đã đồng ý
    List<UserResponse> getAllFriends(UUID userId);
}