package com.codegym.locketclone.user;

import com.codegym.locketclone.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;



    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse userResponse = userService.getUserById(id);
        return new ResponseEntity<>(userResponse, HttpStatus.OK);
    }
    @PatchMapping("/{id}/fcm-token")
    public ResponseEntity<UserResponse> updateFcmToken(@PathVariable UUID id, @RequestParam String fcmToken) {
        userService.updateFcmToken(id, fcmToken);
        return ResponseEntity.noContent().build();
    }
}
