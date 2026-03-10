package com.codegym.locketclone.security.jwt;

import com.codegym.locketclone.security.service.UserDetailsServiceImpl;
import com.codegym.locketclone.security.service.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtUtils.validateToken(jwt)) {
                // Sửa thành UUID để khớp với Entity User
                UUID userId = jwtUtils.getUserIdFromJWT(jwt);

                var userDetails = userDetailsService.loadUserById(userId);

                if (userDetails instanceof UserPrincipal userPrincipal) {
                    if (!userPrincipal.isAccountNonLocked() || !userPrincipal.isEnabled()) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Không thể thiết lập xác thực người dùng trong security context", ex);
        }

        filterChain.doFilter(request, response);
    }
}
