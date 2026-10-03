package com.codegym.locketclone.security;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.security.service.UserDetailsServiceImpl;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailsServiceImpl(userRepository);
    }

    @Test
    void loadUserById_returnsUserPrincipalWhenUserExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .username("testuser")
                .password("encoded_pass")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserById(userId);

        assertNotNull(userDetails);
        assertTrue(userDetails instanceof UserPrincipal);
        UserPrincipal principal = (UserPrincipal) userDetails;
        assertEquals(userId, principal.getId());
        assertEquals("testuser", principal.getUsername());
        assertEquals("user@example.com", principal.getEmail());
        verify(userRepository).findById(userId);
    }

    @Test
    void loadUserById_throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> userDetailsService.loadUserById(userId));
    }

    @Test
    void loadUserByUsername_returnsUserPrincipalWhenUserExists() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .username("testuser")
                .password("encoded_pass")
                .build();

        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("testuser", "testuser"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void loadUserByUsername_throwsWhenNotFound() {
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("unknown", "unknown"))
                .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("unknown"));
    }
}
