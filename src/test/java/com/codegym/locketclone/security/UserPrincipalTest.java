package com.codegym.locketclone.security;

import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserPrincipalTest {

    @Test
    void build_whenUserIsVerified_isEnabledReturnsTrue() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .username("testuser")
                .password("password123")
                .isVerified(true)
                .build();

        UserPrincipal principal = UserPrincipal.build(user);

        assertNotNull(principal);
        assertEquals(userId, principal.getId());
        assertEquals("test@example.com", principal.getEmail());
        assertEquals("testuser", principal.getUsername());
        assertEquals("password123", principal.getPassword());
        assertTrue(principal.isEnabled());
        assertTrue(principal.isAccountNonExpired());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.isCredentialsNonExpired());
    }

    @Test
    void build_whenUserIsNotVerified_isEnabledReturnsFalse() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("unverified@example.com")
                .username("unverified")
                .password("password123")
                .isVerified(false)
                .build();

        UserPrincipal principal = UserPrincipal.build(user);

        assertNotNull(principal);
        assertFalse(principal.isEnabled(), "Unverified user must not be enabled");
    }

    @Test
    void build_whenUserIsVerifiedIsNull_isEnabledReturnsFalse() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("nullverified@example.com")
                .username("nullverified")
                .password("password123")
                .isVerified(null)
                .build();

        UserPrincipal principal = UserPrincipal.build(user);

        assertNotNull(principal);
        assertFalse(principal.isEnabled(), "Null verification status should default to disabled");
    }

    @Test
    void equalsAndHashCode_basedOnId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        User user1 = User.builder().id(id1).email("u1@a.com").username("u1").password("p").isVerified(true).build();
        User user2 = User.builder().id(id1).email("u2@a.com").username("u2").password("p").isVerified(false).build();
        User user3 = User.builder().id(id2).email("u3@a.com").username("u3").password("p").isVerified(true).build();

        UserPrincipal p1 = UserPrincipal.build(user1);
        UserPrincipal p2 = UserPrincipal.build(user2);
        UserPrincipal p3 = UserPrincipal.build(user3);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
    }
}
