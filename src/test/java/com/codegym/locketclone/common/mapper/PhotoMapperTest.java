package com.codegym.locketclone.common.mapper;

import com.codegym.locketclone.expense.Category;
import com.codegym.locketclone.expense.TransactionType;
import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.PhotoStatus;
import com.codegym.locketclone.photo.RecipientScope;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhotoMapperTest {

    private PhotoMapper photoMapper;

    @BeforeEach
    void setUp() {
        photoMapper = Mappers.getMapper(PhotoMapper.class);
    }

    @Test
    void toResponse_withAllFields_mapsCorrectly() {
        UUID photoId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        User sender = User.builder()
                .id(senderId)
                .username("johndoe")
                .firstName("John")
                .lastName("Doe")
                .avatarUrl("http://example.com/avatar.jpg")
                .build();

        Category category = Category.builder()
                .id(categoryId)
                .name("Food & Dining")
                .build();

        Photo photo = Photo.builder()
                .id(photoId)
                .sender(sender)
                .category(category)
                .imageUrl("http://example.com/photo.jpg")
                .thumbnailUrl("http://example.com/thumb.jpg")
                .caption("Lunch with friends")
                .note("Split bill")
                .amount(new BigDecimal("150.00"))
                .transactionType(TransactionType.EXPENSE)
                .recipientScope(RecipientScope.ALL_FRIENDS)
                .recipientCount(5)
                .status(PhotoStatus.READY)
                .mimeType("image/jpeg")
                .fileSize(102400L)
                .width(1920)
                .height(1080)
                .takenAt(now.minusHours(1))
                .createdAt(now)
                .build();

        PhotoResponse response = photoMapper.toResponse(photo);

        assertNotNull(response);
        assertEquals(photoId, response.id());
        assertEquals(senderId, response.senderId());
        assertEquals("John Doe", response.senderDisplayName());
        assertEquals("http://example.com/avatar.jpg", response.senderAvatarUrl());
        assertEquals(categoryId, response.categoryId());
        assertEquals("Food & Dining", response.categoryName());
        assertEquals("http://example.com/photo.jpg", response.imageUrl());
        assertEquals("http://example.com/thumb.jpg", response.thumbnailUrl());
        assertEquals("Lunch with friends", response.caption());
        assertEquals("Split bill", response.note());
        assertEquals(new BigDecimal("150.00"), response.amount());
        assertEquals(TransactionType.EXPENSE, response.transactionType());
        assertEquals(RecipientScope.ALL_FRIENDS, response.recipientScope());
        assertEquals(5, response.recipientCount());
        assertEquals(PhotoStatus.READY, response.status());
        assertEquals("image/jpeg", response.mimeType());
        assertEquals(102400L, response.fileSize());
        assertEquals(1920, response.width());
        assertEquals(1080, response.height());
        assertEquals(now.minusHours(1), response.takenAt());
        assertEquals(now, response.createdAt());
    }

    @Test
    void toResponse_withNullCategory_mapsNullCategoryFieldsGracefully() {
        UUID senderId = UUID.randomUUID();
        User sender = User.builder()
                .id(senderId)
                .username("jane")
                .firstName("Jane")
                .build();

        Photo photo = Photo.builder()
                .id(UUID.randomUUID())
                .sender(sender)
                .category(null)
                .imageUrl("http://example.com/img.jpg")
                .build();

        PhotoResponse response = photoMapper.toResponse(photo);

        assertNotNull(response);
        assertNull(response.categoryId());
        assertNull(response.categoryName());
        assertEquals(senderId, response.senderId());
        assertEquals("Jane", response.senderDisplayName());
    }

    @Test
    void toResponse_nullPhoto_returnsNull() {
        assertNull(photoMapper.toResponse(null));
    }
}
