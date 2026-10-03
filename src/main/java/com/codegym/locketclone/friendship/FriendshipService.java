package com.codegym.locketclone.friendship;

import com.codegym.locketclone.user.dto.UserResponse;
import java.util.List;
import java.util.UUID;

public interface FriendshipService {
    // Lấy danh sách bạn bè đã đồng ý
    List<UserResponse> getAllFriends(UUID userId);
}