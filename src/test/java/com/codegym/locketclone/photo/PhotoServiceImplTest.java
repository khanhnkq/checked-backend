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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                userRepository,
                categoryRepository,
                friendshipRepository,
                cloudinaryService,
                new PhotoMapper()
        );
    }

    @Test
    void uploadPhoto_allFriends_savesPhotoAndRecipients() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID friendAId = UUID.randomUUID();
        UUID friendBId = UUID.randomUUID();
        User sender = user(senderId, "sender@example.com", "sender", "Sender User");
        User friendA = user(friendAId, "a@example.com", "friend_a", "Friend A");
        User friendB = user(friendBId, "b@example.com", "friend_b", "Friend B");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());
        LocalDateTime takenAt = LocalDateTime.of(2026, 3, 12, 10, 30);

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(friendshipRepository.findAcceptedFriendIds(senderId)).thenReturn(List.of(friendAId, friendBId));
        when(userRepository.findAllById(argThat(ids -> {
            java.util.Set<UUID> collected = new java.util.LinkedHashSet<>();
            ids.forEach(collected::add);
            return collected.equals(new java.util.LinkedHashSet<>(List.of(friendAId, friendBId, senderId)));
        }))).thenReturn(List.of(friendA, friendB, sender));
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
        assertEquals(3, response.recipientCount());
        assertEquals(new BigDecimal("45000"), response.amount());
        assertEquals("Sender User", response.senderDisplayName());
        assertEquals("https://cdn.example.com/photo.jpg", response.imageUrl());

        ArgumentCaptor<Photo> photoCaptor = ArgumentCaptor.forClass(Photo.class);
        verify(photoRepository).save(photoCaptor.capture());
        assertEquals(PhotoStatus.READY, photoCaptor.getValue().getStatus());
        assertEquals(3, photoCaptor.getValue().getRecipientCount());
        assertEquals(takenAt, photoCaptor.getValue().getTakenAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoRecipient>> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        verify(photoRecipientRepository).saveAll(recipientsCaptor.capture());
        assertEquals(3, recipientsCaptor.getValue().size());
    }

    @Test
    void uploadPhoto_allFriends_withoutAcceptedFriends_stillSavesPhoto() throws Exception {
        UUID senderId = UUID.randomUUID();
        User sender = user(senderId, "sender@example.com", "sender", "Sender User");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(friendshipRepository.findAcceptedFriendIds(senderId)).thenReturn(List.of());
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

