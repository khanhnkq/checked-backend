package com.codegym.locketclone.photo;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.security.service.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
}

