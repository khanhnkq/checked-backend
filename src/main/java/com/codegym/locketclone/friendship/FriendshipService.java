package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

public interface FriendshipService {
    // Lấy danh sách bạn bè đã đồng ý (không lộ email cá nhân)
    @Cacheable(value = CacheConfig.FRIENDS_CACHE, key = "#userId")
    List<FriendProfileResponse> getAllFriends(UUID userId);
}