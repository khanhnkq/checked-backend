package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.security.service.UserPrincipal;
import com.codegym.locketclone.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Friendships", description = "Quản lý mối quan hệ bạn bè, danh sách bạn bè")
@RestController
@RequestMapping("/api/v1/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @Operation(summary = "Lấy danh sách tất cả bạn bè", description = "Trả về danh sách tất cả người dùng đang là bạn bè của tài khoản hiện tại.")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getMyFriends(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getAllFriends(currentUser.getId()));
    }

    private UserPrincipal requireAuthenticatedUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userPrincipal;
    }
}

