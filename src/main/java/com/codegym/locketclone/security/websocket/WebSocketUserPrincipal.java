package com.codegym.locketclone.security.websocket;

import com.codegym.locketclone.security.service.UserPrincipal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class WebSocketUserPrincipal implements Principal {

    private final UUID id;
    private final UserPrincipal userPrincipal;

    @Override
    public String getName() {
        return id.toString();
    }
}
