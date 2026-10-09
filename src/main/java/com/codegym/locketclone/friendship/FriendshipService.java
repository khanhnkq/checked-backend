package com.codegym.locketclone.friendship;

import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import java.util.List;
import java.util.UUID;

public interface FriendshipService {
    // Lấy danh sách bạn bè đã đồng ý (không lộ email cá nhân)
    List<FriendProfileResponse> getAllFriends(UUID userId);
}