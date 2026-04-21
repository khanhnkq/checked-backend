package com.codegym.locketclone.friendship.invite;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkResponse;
import com.codegym.locketclone.friendship.invite.dto.CreateFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.FriendInviteLinkResponse;
import com.codegym.locketclone.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/friend-invite-links")
@RequiredArgsConstructor
public class FriendInviteLinkController {

    private final FriendInviteLinkService friendInviteLinkService;

    @PostMapping
    public ResponseEntity<FriendInviteLinkResponse> createOrRotate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody(required = false) CreateFriendInviteLinkRequest request
    ) {
        FriendInviteLinkResponse response = friendInviteLinkService
                .createOrRotate(requireAuthenticatedUser(userPrincipal).getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/current")
    public ResponseEntity<FriendInviteLinkResponse> getCurrent(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(friendInviteLinkService.getCurrent(requireAuthenticatedUser(userPrincipal).getId()));
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> revokeCurrent(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        friendInviteLinkService.revokeCurrent(requireAuthenticatedUser(userPrincipal).getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptFriendInviteLinkResponse> acceptByLink(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody AcceptFriendInviteLinkRequest request
    ) {
        AcceptFriendInviteLinkResponse response = friendInviteLinkService
                .acceptByToken(requireAuthenticatedUser(userPrincipal).getId(), request.token());
        return ResponseEntity.ok(response);
    }

    private UserPrincipal requireAuthenticatedUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userPrincipal;
    }
}


