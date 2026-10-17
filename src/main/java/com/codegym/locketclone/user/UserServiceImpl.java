package com.codegym.locketclone.user;

import com.codegym.locketclone.common.config.CacheConfig;
import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.friendship.Friendship;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.friendship.FriendshipStatus;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.user.dto.PublicUserProfileResponse;
import com.codegym.locketclone.user.dto.UpdatePersonalInfoRequest;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserProfileWithRelationResponse;
import com.codegym.locketclone.user.dto.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final FriendshipRepository friendshipRepository;

    @Override
    public PublicUserProfileResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toPublicResponse(user);
    }

    @Override
    public UserProfileWithRelationResponse getUserProfileByUsername(String username, UUID currentUserId) {
        if (username == null || username.trim().isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        String normalizedUsername = username.trim();
        User targetUser = userRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Integer mutualFriendsCount = null;
        List<com.codegym.locketclone.friendship.dto.FriendProfileResponse> mutualFriendsPreview = List.of();

        if (currentUserId == null) {
            return buildProfileWithRelationResponse(targetUser, "UNAUTHENTICATED", null, null, List.of());
        }

        if (currentUserId.equals(targetUser.getId())) {
            return buildProfileWithRelationResponse(targetUser, "SELF", null, null, List.of());
        }

        long count = friendshipRepository.countMutualFriends(currentUserId, targetUser.getId());
        mutualFriendsCount = (int) count;
        List<User> mutualUsers = friendshipRepository.findMutualFriends(currentUserId, targetUser.getId());
        if (mutualUsers != null && userMapper != null) {
            mutualFriendsPreview = mutualUsers.stream()
                    .limit(3)
                    .map(userMapper::toFriendProfileResponse)
                    .toList();
        }

        Optional<Friendship> relationOpt = friendshipRepository.findRelationBetweenUsers(currentUserId, targetUser.getId());
        if (relationOpt.isEmpty()) {
            return buildProfileWithRelationResponse(targetUser, "NONE", null, mutualFriendsCount, mutualFriendsPreview);
        }

        Friendship friendship = relationOpt.get();
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return buildProfileWithRelationResponse(targetUser, "ACCEPTED", friendship.getId(), mutualFriendsCount, mutualFriendsPreview);
        }
        if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
            return buildProfileWithRelationResponse(targetUser, "BLOCKED", friendship.getId(), mutualFriendsCount, mutualFriendsPreview);
        }

        // friendship.getStatus() == FriendshipStatus.PENDING
        if (friendship.getUser().getId().equals(currentUserId)) {
            return buildProfileWithRelationResponse(targetUser, "PENDING_SENT", friendship.getId(), mutualFriendsCount, mutualFriendsPreview);
        } else {
            return buildProfileWithRelationResponse(targetUser, "PENDING_RECEIVED", friendship.getId(), mutualFriendsCount, mutualFriendsPreview);
        }
    }

    private UserProfileWithRelationResponse buildProfileWithRelationResponse(
            User user,
            String relationStatus,
            UUID friendshipId,
            Integer mutualFriendsCount,
            List<com.codegym.locketclone.friendship.dto.FriendProfileResponse> mutualFriendsPreview
    ) {
        return UserProfileWithRelationResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .isGoldMember(user.getIsGoldMember())
                .relationshipStatus(relationStatus)
                .friendshipId(friendshipId)
                .mutualFriendsCount(mutualFriendsCount)
                .mutualFriendsPreview(mutualFriendsPreview)
                .build();
    }


    @Override
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    @CacheEvict(value = CacheConfig.USERS_CACHE, key = "#userId")
    @Transactional
    public UserResponse updateCurrentUserProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.username() != null) {
            String username = request.username().trim();
            if (!username.equalsIgnoreCase(user.getUsername()) && userRepository.existsByUsernameIgnoreCase(username)) {
                throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
            user.setUsername(username);
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }

        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }

        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }

        if (request.dob() != null) {
            user.setDob(request.dob());
        }

        if (!StringUtils.hasText(user.getUsername())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    @CacheEvict(value = CacheConfig.USERS_CACHE, key = "#userId")
    @Transactional
    public UserResponse updatePersonalInfo(UUID userId, UpdatePersonalInfoRequest request) {
        UpdateProfileRequest mappedRequest = new UpdateProfileRequest(
                request.username(),
                request.firstName(),
                request.lastName(),
                request.avatarUrl(),
                request.dob()
        );
        return updateCurrentUserProfile(userId, mappedRequest);
    }

    @Override
    @CacheEvict(value = CacheConfig.USERS_CACHE, key = "#userId")
    @Transactional
    public UserResponse updateAvatar(UUID userId, MultipartFile file) {
        validateAvatarFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UploadedFile uploadedFile;
        try {
            uploadedFile = storageService.uploadAvatar(file);
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        user.setAvatarUrl(uploadedFile.secureUrl());
        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
        }
    }
}
