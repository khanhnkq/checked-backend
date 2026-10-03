package com.codegym.locketclone.user;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.UserMapper;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.user.dto.UpdatePersonalInfoRequest;
import com.codegym.locketclone.user.dto.UpdateProfileRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;

    @Override
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public UserResponse updateCurrentUserProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.username() != null) {
            String username = request.username().trim();
            if (!username.equalsIgnoreCase(user.getUsername()) && userRepository.existsByUsernameIgnoreCase(username)) {
                throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
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

        if (!StringUtils.hasText(user.getUsername())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updatePersonalInfo(UUID userId, UpdatePersonalInfoRequest request) {
        UpdateProfileRequest mappedRequest = new UpdateProfileRequest(
                request.username(),
                request.firstName(),
                request.lastName(),
                request.avatarUrl()
        );
        return updateCurrentUserProfile(userId, mappedRequest);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
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
