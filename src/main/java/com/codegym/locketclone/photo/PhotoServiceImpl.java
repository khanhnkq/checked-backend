package com.codegym.locketclone.photo;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.PhotoMapper;
import com.codegym.locketclone.expense.Category;
import com.codegym.locketclone.expense.CategoryRepository;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.photo.dto.PhotoReactionResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest;
import com.codegym.locketclone.photo.dto.UpdatePhotoExpenseRequest;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_REACTION_TYPES = Set.of("LIKE", "LOVE", "HAHA", "WOW", "SAD", "ANGRY");

    private final PhotoRepository photoRepository;
    private final PhotoRecipientRepository photoRecipientRepository;
    private final PhotoReactionRepository photoReactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final FriendshipRepository friendshipRepository;
    private final CloudinaryService cloudinaryService;
    private final PhotoMapper photoMapper;


    @Override
    @Transactional
    public Slice<PhotoResponse> getFeedPhotos(UUID userId, UUID friendId, Pageable pageable) {
        ensureUserExists(userId);
        if (friendId == null) {
            return photoRepository.findFeedPhotos(userId, PhotoStatus.DELETED, pageable).map(photoMapper::toResponse);
        }

        ensureUserExists(friendId);
        return photoRepository.findFeedPhotosBySender(userId, friendId, PhotoStatus.DELETED, pageable)
                .map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public Page<PhotoResponse> getMyPhotos(UUID userId, Pageable pageable) {
        ensureUserExists(userId);
        return photoRepository.findMyPhotos(userId, PhotoStatus.DELETED, pageable).map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public PhotoResponse getPhotoDetail(UUID userId, UUID photoId) {
        ensureUserExists(userId);
        Photo photo = photoRepository.findAccessiblePhotoById(photoId, userId, PhotoStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.PHOTO_NOT_FOUND));
        return photoMapper.toResponse(photo);
    }

    @Override
    @Transactional
    public PhotoResponse uploadPhoto(MultipartFile file,
                                     String caption,
                                     BigDecimal amount,
                                     String note,
                                     UUID categoryId,
                                     RecipientScope recipientScope,
                                     List<UUID> recipientIds,
                                     LocalDateTime takenAt,
                                     UUID senderId) {
        validateFile(file);
        validateAmount(amount);

        User sender = ensureUserExists(senderId);
        Category category = resolveCategory(senderId, categoryId);
        RecipientScope effectiveScope = recipientScope == null ? RecipientScope.ALL_FRIENDS : recipientScope;
        List<User> recipients = resolveRecipients(senderId, effectiveScope, recipientIds);

        try {
            log.info("Bắt đầu upload ảnh lên Cloudinary cho user: {} với scope: {}", senderId, effectiveScope);
            UploadedImage uploadedImage = cloudinaryService.uploadImage(file);

            Photo photo = Photo.builder()
                    .sender(sender)
                    .imageUrl(uploadedImage.secureUrl())
                    .thumbnailUrl(uploadedImage.thumbnailUrl())
                    .publicId(uploadedImage.publicId())
                    .caption(caption)
                    .amount(amount)
                    .note(normalizeNote(note))
                    .category(category)
                    .recipientScope(effectiveScope)
                    .status(PhotoStatus.READY)
                    .mimeType(uploadedImage.mimeType())
                    .fileSize(uploadedImage.bytes())
                    .width(uploadedImage.width())
                    .height(uploadedImage.height())
                    .recipientCount(recipients.size())
                    .takenAt(takenAt)
                    .build();

            Photo savedPhoto = photoRepository.save(photo);
            saveRecipients(savedPhoto, recipients);

            log.info("Lưu Photo vào Database thành công, ID: {}, recipients: {}", savedPhoto.getId(), recipients.size());
            return photoMapper.toResponse(savedPhoto);
        } catch (IOException e) {
            log.error("Lỗi khi xử lý file upload cho user {}: {}", senderId, e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    @Override
    @Transactional
    public PhotoResponse updatePhotoExpense(UUID userId, UUID photoId, UpdatePhotoExpenseRequest request) {
        ensureUserExists(userId);
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new AppException(ErrorCode.PHOTO_NOT_FOUND));

        if (photo.getStatus() == PhotoStatus.DELETED || !photo.getSender().getId().equals(userId)) {
            throw new AppException(ErrorCode.PHOTO_NOT_FOUND);
        }

        if (request.amount() != null) {
            validateAmount(request.amount());
            photo.setAmount(request.amount());
        }

        if (request.note() != null) {
            photo.setNote(normalizeNote(request.note()));
        }

        if (request.categoryId() != null) {
            photo.setCategory(resolveCategory(userId, request.categoryId()));
        }

        Photo saved = photoRepository.save(photo);
        return photoMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PhotoReactionResponse upsertReaction(UUID userId, UUID photoId, UpsertPhotoReactionRequest request) {
        ensureUserExists(userId);
        Photo photo = requireAccessiblePhoto(photoId, userId);
        if (photo.getSender().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_REACT_OWN_PHOTO);
        }

        String normalizedType = normalizeReactionType(request.type());
        PhotoReaction reaction = photoReactionRepository.findByPhotoIdAndUserId(photoId, userId)
                .orElseGet(() -> PhotoReaction.builder().photo(photo).user(findUserById(userId)).build());

        reaction.setReactionType(normalizedType);
        PhotoReaction saved = photoReactionRepository.save(reaction);

        return new PhotoReactionResponse(saved.getPhoto().getId(), saved.getUser().getId(), saved.getReactionType(), saved.getCreatedAt());
    }

    @Override
    @Transactional
    public void removeReaction(UUID userId, UUID photoId) {
        ensureUserExists(userId);
        Photo photo = requireAccessiblePhoto(photoId, userId);
        if (photo.getSender().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_REACT_OWN_PHOTO);
        }

        photoReactionRepository.deleteByPhotoIdAndUserId(photoId, userId);
    }

    @Override
    @Transactional
    public PhotoReactionSummaryResponse getReactionSummary(UUID userId, UUID photoId) {
        ensureUserExists(userId);
        requireAccessiblePhoto(photoId, userId);

        List<Object[]> groupedRows = photoReactionRepository.summarizeByPhotoId(photoId);
        Map<String, Long> countsByType = new LinkedHashMap<>();
        long totalCount = 0L;
        for (Object[] row : groupedRows) {
            String type = (String) row[0];
            Long count = (Long) row[1];
            long safeCount = count == null ? 0L : count;
            countsByType.put(type, safeCount);
            totalCount += safeCount;
        }

        String myReaction = photoReactionRepository.findByPhotoIdAndUserId(photoId, userId)
                .map(PhotoReaction::getReactionType)
                .orElse(null);

        return new PhotoReactionSummaryResponse(photoId, totalCount, myReaction, countsByType);
    }

    private User ensureUserExists(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Photo requireAccessiblePhoto(UUID photoId, UUID userId) {
        return photoRepository.findAccessiblePhotoById(photoId, userId, PhotoStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.PHOTO_NOT_FOUND));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount != null && amount.signum() < 0) {
            throw new AppException(ErrorCode.INVALID_PHOTO_AMOUNT);
        }
    }

    private Category resolveCategory(UUID userId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findActiveVisibleById(categoryId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeReactionType(String type) {
        if (type == null) {
            throw new AppException(ErrorCode.INVALID_REACTION_TYPE);
        }
        String normalized = type.trim().toUpperCase();
        if (!ALLOWED_REACTION_TYPES.contains(normalized)) {
            throw new AppException(ErrorCode.INVALID_REACTION_TYPE);
        }
        return normalized;
    }

    private List<User> resolveRecipients(UUID senderId, RecipientScope scope, List<UUID> recipientIds) {
        Set<UUID> resolvedIds = new LinkedHashSet<>();
        if (scope == RecipientScope.ALL_FRIENDS) {
            // ALL_FRIENDS is evaluated at read-time using current friendships.
            // Persist only sender to avoid snapshot recipient conflicts.
            resolvedIds.add(senderId);
        } else {
            Set<UUID> acceptedFriendIds = new LinkedHashSet<>(friendshipRepository.findAcceptedFriendIds(senderId));
            if (recipientIds == null || recipientIds.isEmpty()) {
                throw new AppException(ErrorCode.RECIPIENTS_REQUIRED);
            }
            resolvedIds.addAll(recipientIds);
            resolvedIds.remove(senderId);
            if (!acceptedFriendIds.containsAll(resolvedIds)) {
                throw new AppException(ErrorCode.INVALID_RECIPIENT_SELECTION);
            }
            resolvedIds.add(senderId);
        }

        List<User> recipients = userRepository.findAllById(resolvedIds);
        if (recipients.size() != resolvedIds.size()) {
            throw new AppException(ErrorCode.INVALID_RECIPIENT_SELECTION);
        }
        return recipients;
    }

    private void saveRecipients(Photo photo, List<User> recipients) {
        List<PhotoRecipient> photoRecipients = recipients.stream()
                .map(recipient -> PhotoRecipient.builder()
                        .photo(photo)
                        .recipient(recipient)
                        .build())
                .toList();
        photoRecipientRepository.saveAll(photoRecipients);
    }
}
