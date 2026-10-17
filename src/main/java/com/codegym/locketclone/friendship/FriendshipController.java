package com.codegym.locketclone.friendship;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.friendship.dto.*;
import com.codegym.locketclone.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Friendships", description = "Quản lý mối quan hệ bạn bè, danh sách bạn bè và lời mời kết bạn")
@RestController
@RequestMapping("/api/v1/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @Operation(summary = "Lấy danh sách tất cả bạn bè", description = "Trả về danh sách tất cả người dùng đang là bạn bè của tài khoản hiện tại (ẩn email cá nhân).")
    @GetMapping
    public ResponseEntity<List<FriendProfileResponse>> getMyFriends(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getAllFriends(currentUser.getId()));
    }

    @Operation(summary = "Gửi lời mời kết bạn", description = "Gửi lời mời kết bạn tới người dùng khác qua targetUserId. Tự động chuyển thành ACCEPTED nếu đối phương đã từng gửi cho mình trước đó.")
    @PostMapping("/requests")
    public ResponseEntity<SendFriendRequestResponse> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SendFriendRequest request
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        SendFriendRequestResponse response = friendshipService.sendFriendRequest(currentUser.getId(), request);
        HttpStatus status = "ACCEPTED".equals(response.status()) ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "Lấy danh sách lời mời kết bạn đã nhận", description = "Trả về danh sách các lời mời kết bạn người khác gửi tới mình đang chờ phản hồi.")
    @GetMapping("/requests/received")
    public ResponseEntity<List<FriendRequestResponse>> getPendingReceivedRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getPendingReceivedRequests(currentUser.getId()));
    }

    @Operation(summary = "Đếm số lượng lời mời kết bạn chưa duyệt", description = "Trả về số lượng lời mời kết bạn đang chờ duyệt phục vụ hiển thị Badge/chấm đỏ.")
    @GetMapping("/requests/count")
    public ResponseEntity<FriendRequestCountResponse> getPendingReceivedCount(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getPendingReceivedCount(currentUser.getId()));
    }

    @Operation(summary = "Chấp nhận lời mời kết bạn", description = "Chấp nhận lời mời kết bạn theo friendshipId. Chỉ người nhận lời mời mới có quyền duyệt.")
    @PutMapping("/requests/{friendshipId}/accept")
    public ResponseEntity<AcceptFriendRequestResponse> acceptFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID friendshipId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.acceptFriendRequest(currentUser.getId(), friendshipId));
    }

    @Operation(summary = "Từ chối lời mời kết bạn", description = "Từ chối lời mời kết bạn theo friendshipId. Bản ghi lời mời sẽ bị xóa.")
    @PutMapping("/requests/{friendshipId}/decline")
    public ResponseEntity<Void> declineFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID friendshipId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.declineFriendRequest(currentUser.getId(), friendshipId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Hủy lời mời kết bạn đã gửi", description = "Người gửi rút lại lời mời kết bạn khi đối phương chưa kịp duyệt.")
    @DeleteMapping("/requests/{friendshipId}/cancel")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID friendshipId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.cancelFriendRequest(currentUser.getId(), friendshipId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Hủy kết bạn (Unfriend)", description = "Xóa quan hệ bạn bè với một tài khoản theo friendId.")
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> unfriend(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID friendId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.unfriend(currentUser.getId(), friendId);
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal requireAuthenticatedUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userPrincipal;
    }
}
