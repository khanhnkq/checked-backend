package com.codegym.locketclone.friendship;

import com.codegym.locketclone.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService{

    private final FriendshipRepository friendshipRepository;
    @Override
    public void sendFriendRequest(UUID senderId, UUID receiverId) {

    }

    @Override
    public void acceptFriendRequest(UUID userId, UUID friendId) {

    }

    @Override
    public List<UserResponse> getAllFriends(UUID userId) {
        return List.of();
    }
}
