package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceImplTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FriendshipServiceImpl friendshipService;

    @Test
    void getAllFriends_mapsOppositeUserFromAcceptedFriendships() {
        UUID currentUserId = UUID.randomUUID();
        UUID friendAId = UUID.randomUUID();
        UUID friendBId = UUID.randomUUID();

        User currentUser = User.builder().id(currentUserId).username("me").email("me@example.com").password("x").build();
        User friendA = User.builder().id(friendAId).username("friend_a").email("a@example.com").password("x").build();
        User friendB = User.builder().id(friendBId).username("friend_b").email("b@example.com").password("x").build();

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
}

