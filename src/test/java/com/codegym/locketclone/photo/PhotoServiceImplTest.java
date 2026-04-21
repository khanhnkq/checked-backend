package com.codegym.locketclone.photo;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.PhotoMapper;
import com.codegym.locketclone.expense.CategoryRepository;
import com.codegym.locketclone.friendship.FriendshipRepository;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceImplTest {

    @Mock
    private PhotoRepository photoRepository;
    @Mock
    private PhotoRecipientRepository photoRecipientRepository;
    @Mock
    private PhotoReactionRepository photoReactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private CloudinaryService cloudinaryService;

    private PhotoServiceImpl photoService;

    @BeforeEach
    void setUp() {
        photoService = new PhotoServiceImpl(
                photoRepository,
                photoRecipientRepository,
                photoReactionRepository,
                userRepository,
                categoryRepository,
                friendshipRepository,
                cloudinaryService,
                new PhotoMapper()
        );
    }

    @Test
    void uploadPhoto_allFriends_savesPhotoAndSenderRecipientOnly() throws Exception {
        UUID senderId = UUID.randomUUID();
        User sender = user(senderId, "sender@example.com", "sender", "Sender User");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());
        LocalDateTime takenAt = LocalDateTime.of(2026, 3, 12, 10, 30);

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findAllById(argThat(ids -> {
            java.util.Set<UUID> collected = new java.util.LinkedHashSet<>();
            ids.forEach(collected::add);
            return collected.equals(java.util.Set.of(senderId));
        }))).thenReturn(List.of(sender));
        when(cloudinaryService.uploadImage(file)).thenReturn(new UploadedImage(
                "https://cdn.example.com/photo.jpg",
                "https://cdn.example.com/photo_thumb.jpg",
                "public-id",
                "image/jpeg",
                12345L,
                1080,
                1920
        ));
        when(photoRepository.save(any(Photo.class))).thenAnswer(invocation -> {
            Photo photo = invocation.getArgument(0);
            photo.setId(UUID.randomUUID());
            photo.setCreatedAt(LocalDateTime.of(2026, 3, 12, 10, 31));
            return photo;
        });

        var response = photoService.uploadPhoto(
                file,
                "Cafe sáng",
                new BigDecimal("45000"),
                "Morning coffee",
                null,
                RecipientScope.ALL_FRIENDS,
                null,
                takenAt,
                senderId
        );

        assertNotNull(response.id());
        assertEquals(RecipientScope.ALL_FRIENDS, response.recipientScope());
        assertEquals(1, response.recipientCount());
        assertEquals(new BigDecimal("45000"), response.amount());
        assertEquals("Sender User", response.senderDisplayName());
        assertEquals("https://cdn.example.com/photo.jpg", response.imageUrl());

        ArgumentCaptor<Photo> photoCaptor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(photoCaptor.capture());
        assertEquals(PhotoStatus.READY, photoCaptor.getValue().getStatus());
        assertEquals(1, photoCaptor.getValue().getRecipientCount());
        assertEquals(takenAt, photoCaptor.getValue().getTakenAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoRecipient>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(photoRecipientRepository).saveAll(recipientsCaptor.capture());
        assertEquals(1, recipientsCaptor.getValue().size());
    }

    @Test
    void uploadPhoto_allFriends_withoutAcceptedFriends_stillSavesPhoto() throws Exception {
        UUID senderId = UUID.randomUUID();
        User sender = user(senderId, "sender@example.com", "sender", "Sender User");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findAllById(argThat(ids -> {
            java.util.Set<UUID> collected = new java.util.LinkedHashSet<>();
            ids.forEach(collected::add);
            return collected.equals(java.util.Set.of(senderId));
        }))).thenReturn(List.of(sender));
        when(cloudinaryService.uploadImage(file)).thenReturn(new UploadedImage(
                "https://cdn.example.com/photo.jpg",
                "https://cdn.example.com/photo_thumb.jpg",
                "public-id",
                "image/jpeg",
                12345L,
                1080,
                1920
        ));
        when(photoRepository.save(any(Photo.class))).thenAnswer(invocation -> {
            Photo photo = invocation.getArgument(0);
            photo.setId(UUID.randomUUID());
            photo.setCreatedAt(LocalDateTime.of(2026, 3, 12, 10, 31));
            return photo;
        });

        var response = photoService.uploadPhoto(
                file,
                null,
                null,
                null,
                null,
                RecipientScope.ALL_FRIENDS,
                null,
                null,
                senderId
        );

        assertNotNull(response.id());
        assertEquals(RecipientScope.ALL_FRIENDS, response.recipientScope());
        assertEquals(1, response.recipientCount());

        ArgumentCaptor<Photo> photoCaptor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(photoCaptor.capture());
        assertEquals(1, photoCaptor.getValue().getRecipientCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoRecipient>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(photoRecipientRepository).saveAll(recipientsCaptor.capture());
        assertEquals(1, recipientsCaptor.getValue().size());
    }

    @Test
    void uploadPhoto_selectedFriends_rejectsUnknownRecipient() {
        UUID senderId = UUID.randomUUID();
        UUID acceptedFriendId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        User sender = user(senderId, "sender@example.com", "sender", "Sender User");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(friendshipRepository.findAcceptedFriendIds(senderId)).thenReturn(List.of(acceptedFriendId));

        AppException exception = assertThrows(AppException.class, () -> photoService.uploadPhoto(
                file,
                null,
                null,
                null,
                null,
                RecipientScope.SELECTED_FRIENDS,
                List.of(strangerId),
                null,
                senderId
        ));

        assertEquals(ErrorCode.INVALID_RECIPIENT_SELECTION, exception.getErrorCode());
    }

    @Test
    void getPhotoDetail_returnsPhotoWhenUserHasAccess() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        User sender = user(userId, "sender@example.com", "sender", "Sender User");
        Photo photo = Photo.builder()
                .id(photoId)
                .sender(sender)
                .imageUrl("https://cdn.example.com/photo.jpg")
                .thumbnailUrl("https://cdn.example.com/photo_thumb.jpg")
                .caption("Cafe sáng")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(1)
                .status(PhotoStatus.READY)
                .mimeType("image/jpeg")
                .fileSize(12345L)
                .width(1080)
                .height(1920)
                .takenAt(LocalDateTime.of(2026, 3, 12, 10, 30))
                .createdAt(LocalDateTime.of(2026, 3, 12, 10, 31))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sender));
        when(photoRepository.findAccessiblePhotoById(photoId, userId, PhotoStatus.DELETED)).thenReturn(Optional.of(photo));

        var response = photoService.getPhotoDetail(userId, photoId);

        assertEquals(photoId, response.id());
        assertEquals(userId, response.senderId());
        assertEquals("Sender User", response.senderDisplayName());
        assertEquals("https://cdn.example.com/photo.jpg", response.imageUrl());
        verify(photoRepository).findAccessiblePhotoById(photoId, userId, PhotoStatus.DELETED);
    }

    @Test
    void getPhotoDetail_throwsPhotoNotFoundWhenUserHasNoAccess() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        User currentUser = user(userId, "viewer@example.com", "viewer", "Viewer User");

        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(photoRepository.findAccessiblePhotoById(eq(photoId), eq(userId), eq(PhotoStatus.DELETED)))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> photoService.getPhotoDetail(userId, photoId));

        assertEquals(ErrorCode.PHOTO_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getFeedPhotos_withoutFriendFilter_returnsRecipientFeed() {
        UUID userId = UUID.randomUUID();
        User currentUser = user(userId, "viewer@example.com", "viewer", "Viewer User");

        Photo photo = Photo.builder()
                .id(UUID.randomUUID())
                .sender(currentUser)
                .imageUrl("https://cdn.example.com/feed.jpg")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(1)
                .status(PhotoStatus.READY)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(photoRepository.findFeedPhotos(userId, PhotoStatus.DELETED, Pageable.unpaged()))
                .thenReturn(new SliceImpl<>(List.of(photo)));

        var result = photoService.getFeedPhotos(userId, null, Pageable.unpaged());

        assertEquals(1, result.getContent().size());
        verify(photoRepository).findFeedPhotos(userId, PhotoStatus.DELETED, Pageable.unpaged());
    }

    @Test
    void getFeedPhotos_withFriendFilter_returnsOnlyFriendPhotos() {
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        User currentUser = user(userId, "viewer@example.com", "viewer", "Viewer User");
        User friend = user(friendId, "friend@example.com", "friend", "Friend User");

        Photo photo = Photo.builder()
                .id(UUID.randomUUID())
                .sender(friend)
                .imageUrl("https://cdn.example.com/friend-feed.jpg")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(2)
                .status(PhotoStatus.READY)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(friendId)).thenReturn(Optional.of(friend));
        when(photoRepository.findFeedPhotosBySender(userId, friendId, PhotoStatus.DELETED, Pageable.unpaged()))
                .thenReturn(new SliceImpl<>(List.of(photo)));

        var result = photoService.getFeedPhotos(userId, friendId, Pageable.unpaged());

        assertEquals(1, result.getContent().size());
        assertTrue(result.getContent().stream().allMatch(p -> friendId.equals(p.senderId())));
        verify(photoRepository).findFeedPhotosBySender(userId, friendId, PhotoStatus.DELETED, Pageable.unpaged());
    }

    @Test
    void upsertReaction_rejectsOwnPhoto() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        User sender = user(userId, "me@example.com", "me", "Me User");
        Photo photo = Photo.builder()
                .id(photoId)
                .sender(sender)
                .status(PhotoStatus.READY)
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sender));
        when(photoRepository.findAccessiblePhotoById(photoId, userId, PhotoStatus.DELETED)).thenReturn(Optional.of(photo));

        AppException exception = assertThrows(AppException.class,
                () -> photoService.upsertReaction(userId, photoId, new com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest("LIKE")));

        assertEquals(ErrorCode.CANNOT_REACT_OWN_PHOTO, exception.getErrorCode());
    }

    @Test
    void upsertReaction_savesAndSummaryReturnsCounts() {
        UUID userId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        User viewer = user(userId, "viewer@example.com", "viewer", "Viewer User");
        User sender = user(senderId, "sender@example.com", "sender", "Sender User");
        Photo photo = Photo.builder()
                .id(photoId)
                .sender(sender)
                .status(PhotoStatus.READY)
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(viewer));
        when(photoRepository.findAccessiblePhotoById(photoId, userId, PhotoStatus.DELETED)).thenReturn(Optional.of(photo));
        when(photoReactionRepository.findByPhotoIdAndUserId(photoId, userId)).thenReturn(Optional.empty());
        when(photoReactionRepository.save(any(PhotoReaction.class))).thenAnswer(invocation -> {
            PhotoReaction reaction = invocation.getArgument(0);
            reaction.setId(UUID.randomUUID());
            reaction.setCreatedAt(LocalDateTime.now());
            return reaction;
        });

        var upsert = photoService.upsertReaction(userId, photoId, new com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest("like"));

        assertEquals("LIKE", upsert.type());
        verify(photoReactionRepository).save(any(PhotoReaction.class));

        when(photoReactionRepository.summarizeByPhotoId(photoId)).thenReturn(List.of(new Object[]{"LIKE", 2L}, new Object[]{"LOVE", 1L}));
        when(photoReactionRepository.findByPhotoIdAndUserId(photoId, userId)).thenReturn(Optional.of(
                PhotoReaction.builder().photo(photo).user(viewer).reactionType("LIKE").build()
        ));

        var summary = photoService.getReactionSummary(userId, photoId);

        assertEquals(3L, summary.totalCount());
        assertEquals("LIKE", summary.myReaction());
        assertEquals(2L, summary.countsByType().get("LIKE"));
    }

    private User user(UUID id, String email, String username, String displayName) {
        String[] nameParts = displayName.split(" ", 2);
        return User.builder()
                .id(id)
                .email(email)
                .username(username)
                .password("secret")
                .firstName(nameParts[0])
                .lastName(nameParts.length > 1 ? nameParts[1] : null)
                .isVerified(true)
                .build();
    }
}

