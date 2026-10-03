package com.codegym.locketclone.friendship.invite;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.AcceptFriendInviteLinkResponse;
import com.codegym.locketclone.friendship.invite.dto.CreateFriendInviteLinkRequest;
import com.codegym.locketclone.friendship.invite.dto.FriendInviteLinkResponse;
import com.codegym.locketclone.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Friend Invites", description = "Tạo link mời kết bạn, quản lý token lời mời và chấp nhận kết bạn")
@RestController
@RequestMapping("/api/v1/friend-invite-links")
@RequiredArgsConstructor
public class FriendInviteLinkController {

    private final FriendInviteLinkService friendInviteLinkService;

    @Operation(summary = "Tạo hoặc làm mới link mời kết bạn", description = "Sinh link mời kèm token ngẫu nhiên, cấu hình số lượt sử dụng tối đa và thời gian hết hạn (TTL).")
    @PostMapping
    public ResponseEntity<FriendInviteLinkResponse> createOrRotate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody(required = false) CreateFriendInviteLinkRequest request
    ) {
        FriendInviteLinkResponse response = friendInviteLinkService
                .createOrRotate(requireAuthenticatedUser(userPrincipal).getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Lấy link mời kết bạn hiện tại", description = "Lấy link mời đang còn hiệu lực của người dùng hiện tại.")
    @GetMapping("/current")
    public ResponseEntity<FriendInviteLinkResponse> getCurrent(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(friendInviteLinkService.getCurrent(requireAuthenticatedUser(userPrincipal).getId()));
    }

    @Operation(summary = "Hủy link mời kết bạn hiện tại", description = "Vô hiệu hóa link mời hiện tại để không ai có thể sử dụng được nữa.")
    @DeleteMapping("/current")
    public ResponseEntity<Void> revokeCurrent(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        friendInviteLinkService.revokeCurrent(requireAuthenticatedUser(userPrincipal).getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Chấp nhận kết bạn qua link", description = "Chấp nhận kết bạn bằng token trong link mời (có chống race condition bằng pessimistic lock).")
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


