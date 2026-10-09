package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipControllerTest {

    @Mock
    private FriendshipService friendshipService;

    @InjectMocks
    private FriendshipController friendshipController;

    @Test
    void getMyFriends_returnsFriendList() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh",
                "khanh@example.com",
                null,
                AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        List<FriendProfileResponse> friends = List.of(
                new FriendProfileResponse(UUID.randomUUID(), "friend_1", "Friend", "One", "Friend One", null, false),
                new FriendProfileResponse(UUID.randomUUID(), "friend_2", "Friend", "Two", "Friend Two", null, true)
        );
        when(friendshipService.getAllFriends(userId)).thenReturn(friends);

        var response = friendshipController.getMyFriends(principal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() != null && response.getBody().size() == 2);
        verify(friendshipService).getAllFriends(userId);
    }

    @Test
    void getMyFriends_throwsUnauthorizedWhenPrincipalMissing() {
        AppException exception = assertThrows(AppException.class, () -> friendshipController.getMyFriends(null));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }
}

