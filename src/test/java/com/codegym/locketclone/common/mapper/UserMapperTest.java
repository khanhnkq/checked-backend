package com.codegym.locketclone.common.mapper;

import com.codegym.locketclone.friendship.dto.FriendProfileResponse;
import com.codegym.locketclone.user.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMapperTest {

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toFriendProfileResponse_mapsFieldsCorrectlyWithoutExposingEmail() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("secret-friend-email@example.com")
                .username("friend_bob")
                .firstName("Bob")
                .lastName("Smith")
                .avatarUrl("https://example.com/bob.jpg")
                .isGoldMember(false)
                .build();

        FriendProfileResponse response = userMapper.toFriendProfileResponse(user);

        assertNotNull(response);
        assertEquals(userId, response.id());
        assertEquals("friend_bob", response.username());
        assertEquals("Bob", response.firstName());
        assertEquals("Smith", response.lastName());
        assertEquals("Bob Smith", response.displayName());
        assertEquals("https://example.com/bob.jpg", response.avatarUrl());
        assertFalse(response.isGoldMember());
    }
}
