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

    @Operation(summary = "Chặn người dùng (Block)", description = "Chặn người dùng theo userId, hủy kết bạn và hủy các lời mời đang chờ.")
    @PostMapping("/{userId}/block")
    public ResponseEntity<Void> blockUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID userId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.blockUser(currentUser.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bỏ chặn người dùng (Unblock)", description = "Bỏ chặn người dùng theo userId.")
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID userId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.unblockUser(currentUser.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách người dùng bị chặn", description = "Trả về danh sách tất cả những người dùng đang bị tài khoản hiện tại chặn.")
    @GetMapping("/blocked")
    public ResponseEntity<List<FriendProfileResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getBlockedUsers(currentUser.getId()));
    }

    @Operation(summary = "Lấy danh sách lời mời kết bạn đã gửi", description = "Trả về danh sách các lời mời kết bạn mà người dùng hiện tại đã gửi đi đang chờ đối phương phản hồi.")
    @GetMapping("/requests/sent")
    public ResponseEntity<List<SentFriendRequestResponse>> getPendingSentRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getPendingSentRequests(currentUser.getId()));
    }

    @Operation(summary = "Lấy số lượng lời mời đã gửi", description = "Trả về số lượng lời mời kết bạn mà người dùng hiện tại đã gửi đang chờ duyệt.")
    @GetMapping("/requests/sent/count")
    public ResponseEntity<SentFriendRequestCountResponse> getPendingSentCount(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getPendingSentCount(currentUser.getId()));
    }

    @Operation(summary = "Lấy danh sách bạn chung", description = "Trả về danh sách tất cả bạn bè chung giữa tài khoản hiện tại và targetUserId.")
    @GetMapping("/{targetUserId}/mutual")
    public ResponseEntity<List<FriendProfileResponse>> getMutualFriends(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID targetUserId
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        return ResponseEntity.ok(friendshipService.getMutualFriends(currentUser.getId(), targetUserId));
    }

    @Operation(summary = "Chấp nhận tất cả lời mời kết bạn", description = "Chấp nhận đồng loạt tất cả các lời mời kết bạn đang chờ gửi đến người dùng hiện tại.")
    @PutMapping("/requests/accept-all")
    public ResponseEntity<Void> acceptAllPendingRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.acceptAllPendingRequests(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Từ chối tất cả lời mời kết bạn", description = "Từ chối đồng loạt tất cả các lời mời kết bạn đang chờ gửi đến người dùng hiện tại.")
    @PutMapping("/requests/decline-all")
    public ResponseEntity<Void> declineAllPendingRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireAuthenticatedUser(userPrincipal);
        friendshipService.declineAllPendingRequests(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal requireAuthenticatedUser(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userPrincipal;
    }
}
