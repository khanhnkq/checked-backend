package com.codegym.locketclone.photo;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionResponse;
import com.codegym.locketclone.photo.dto.PhotoReactionSummaryResponse;
import com.codegym.locketclone.photo.dto.PhotoReactorResponse;
import com.codegym.locketclone.photo.dto.UpsertPhotoReactionRequest;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    @Mock
    private PhotoService photoService;

    @InjectMocks
    private PhotoController photoController;

    @Test
    void uploadPhoto_usesAudienceModeAliasWhenRecipientScopeMissing() {
        UUID userId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-data".getBytes()
        );
        LocalDateTime takenAt = LocalDateTime.of(2026, 3, 12, 11, 0);
        PhotoResponse response = PhotoResponse.builder()
                .id(UUID.randomUUID())
                .senderId(userId)
                .recipientScope(RecipientScope.SELECTED_FRIENDS)
                .recipientCount(1)
                .status(PhotoStatus.READY)
                .build();

        when(photoService.uploadPhoto(
                eq(file),
                eq("Cafe sáng"),
                eq(new BigDecimal("45000")),
                eq((String) null),
                eq("Morning coffee"),
                eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                eq(RecipientScope.SELECTED_FRIENDS),
                eq(List.of(recipientId)),
                eq(takenAt),
                eq(userId)
        )).thenReturn(response);

        var actual = photoController.uploadPhoto(
                file,
                "Cafe sáng",
                new BigDecimal("45000"),
                null,
                "Morning coffee",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                RecipientScope.SELECTED_FRIENDS,
                List.of(recipientId),
                takenAt,
                principal
        );

        assertEquals(201, actual.getStatusCode().value());
        assertEquals(response, actual.getBody());
        verify(photoService).uploadPhoto(
                file,
                "Cafe sáng",
                new BigDecimal("45000"),
                null,
                "Morning coffee",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                RecipientScope.SELECTED_FRIENDS,
                List.of(recipientId),
                takenAt,
                userId
        );
    }

    @Test
    void getMyPhotos_returnsServiceResult() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        var page = new PageImpl<>(List.of(PhotoResponse.builder()
                .id(UUID.randomUUID())
                .senderId(userId)
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(2)
                .status(PhotoStatus.READY)
                .build()));

        when(photoService.getMyPhotos(eq(userId), any(Pageable.class))).thenReturn(page);

        var actual = photoController.getMyPhotos(principal, Pageable.unpaged());

        assertEquals(200, actual.getStatusCode().value());
        assertEquals(page, actual.getBody());
        verify(photoService).getMyPhotos(userId, Pageable.unpaged());
    }

    @Test
    void getPhotoDetail_returnsServiceResult() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        PhotoResponse response = PhotoResponse.builder()
                .id(photoId)
                .senderId(userId)
                .senderDisplayName("Khánh Nguyễn Kim")
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(1)
                .status(PhotoStatus.READY)
                .build();

        when(photoService.getPhotoDetail(userId, photoId)).thenReturn(response);

        var actual = photoController.getPhotoDetail(photoId, principal);

        assertEquals(200, actual.getStatusCode().value());
        assertEquals(response, actual.getBody());
        verify(photoService).getPhotoDetail(userId, photoId);
    }

    @Test
    void getFeedPhotos_passesOptionalFriendIdToService() {
        UUID userId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        var slice = new SliceImpl<>(List.of(PhotoResponse.builder().id(UUID.randomUUID()).senderId(friendId).build()));

        when(photoService.getFeedPhotos(userId, friendId, Pageable.unpaged())).thenReturn(slice);

        var actual = photoController.getFeedPhotos(principal, friendId, Pageable.unpaged());

        assertEquals(200, actual.getStatusCode().value());
        assertEquals(slice, actual.getBody());
        verify(photoService).getFeedPhotos(userId, friendId, Pageable.unpaged());
    }

    @Test
    void upsertMyReaction_returnsServiceResult() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        UpsertPhotoReactionRequest request = new UpsertPhotoReactionRequest("LIKE");
        PhotoReactionResponse response = new PhotoReactionResponse(photoId, userId, "LIKE", LocalDateTime.now());

        when(photoService.upsertReaction(userId, photoId, request)).thenReturn(response);

        var actual = photoController.upsertMyReaction(photoId, principal, request);

        assertEquals(200, actual.getStatusCode().value());
        assertEquals(response, actual.getBody());
        verify(photoService).upsertReaction(userId, photoId, request);
    }

    @Test
    void removeMyReaction_returnsNoContent() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );

        var actual = photoController.removeMyReaction(photoId, principal);

        assertEquals(204, actual.getStatusCode().value());
        verify(photoService).removeReaction(userId, photoId);
    }

    @Test
    void getReactionSummary_returnsServiceResult() {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                "khanh_dev",
                "khanh@example.com",
                "secret",
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER")
        );
        PhotoReactionSummaryResponse response = new PhotoReactionSummaryResponse(
                photoId,
                2,
                "LIKE",
                java.util.Map.of("LIKE", 2L),
                List.of(new PhotoReactorResponse(
                        UUID.randomUUID(),
                        "Friend One",
                        "https://cdn.example.com/avatar-1.jpg",
                        "LIKE",
                        LocalDateTime.now()
                ))
        );
        when(photoService.getReactionSummary(userId, photoId)).thenReturn(response);

        var actual = photoController.getReactionSummary(photoId, principal);

        assertEquals(200, actual.getStatusCode().value());
        assertEquals(response, actual.getBody());
        verify(photoService).getReactionSummary(userId, photoId);
    }
}

