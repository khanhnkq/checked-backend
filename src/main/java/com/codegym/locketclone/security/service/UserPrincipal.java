package com.codegym.locketclone.security.service;

import com.codegym.locketclone.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


@Getter
public class UserPrincipal implements UserDetails {
    private final UUID id;
    private final String phoneNumber;
    @JsonIgnore
    private final   String password; // Locket dùng OTP/Phone nhưng Security vẫn cần field này
    private Collection<? extends GrantedAuthority> authorities;
    public UserPrincipal(UUID id,  String phoneNumber, String password, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.authorities = authorities;
    }
    public static UserPrincipal build(User user) {
        // Hiện tại app Locket chưa phân quyền phức tạp, mặc định là ROLE_USER
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new UserPrincipal(
                user.getId(),
                user.getPhoneNumber(), // Map chuẩn từ entity
                user.getPassword(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return phoneNumber;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }


}
