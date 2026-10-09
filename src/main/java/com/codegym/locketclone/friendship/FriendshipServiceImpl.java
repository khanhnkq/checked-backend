package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FriendProfileResponse> getAllFriends(UUID userId) {
        return friendshipRepository.findAllAcceptedFriends(userId)
                .stream()
                .map(friendship -> resolveFriendUser(friendship, userId))
                .map(userMapper::toFriendProfileResponse)
                .toList();
    }

    private User resolveFriendUser(Friendship friendship, UUID userId) {
        if (friendship.getUser().getId().equals(userId)) {
            return friendship.getFriend();
        }
        return friendship.getUser();
    }
}
