package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.dto.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserMapper userMapper;

    @Override
    public void sendFriendRequest(UUID senderId, UUID receiverId) {

    }

    @Override
    public void acceptFriendRequest(UUID userId, UUID friendId) {

    }

    @Override
    @Transactional
    public List<UserResponse> getAllFriends(UUID userId) {
        return friendshipRepository.findAllAcceptedFriends(userId)
                .stream()
                .map(friendship -> resolveFriendUser(friendship, userId))
                .map(userMapper::toResponse)
                .toList();
    }

    private User resolveFriendUser(Friendship friendship, UUID userId) {
        if (friendship.getUser().getId().equals(userId)) {
            return friendship.getFriend();
        }
        return friendship.getUser();
    }
}
