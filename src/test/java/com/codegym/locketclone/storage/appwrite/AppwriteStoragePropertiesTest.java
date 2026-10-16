package com.codegym.locketclone.storage.appwrite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppwriteStoragePropertiesTest {

    private AppwriteStorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppwriteStorageProperties();
    }

    @Test
    @DisplayName("Giá trị mặc định phải khớp với dự án Appwrite Cloud Pro Frankfurt")
    void defaultValues_shouldMatchAppwriteCloudHub() {
        assertEquals("https://fra.cloud.appwrite.io/v1", properties.getEndpoint());
        assertEquals("backend-storage-hub", properties.getProjectId());
        assertEquals("checked-storage", properties.getBucketId());
        assertEquals("", properties.getApiKey());
    }

    @Test
    @DisplayName("buildViewUrl tạo đúng endpoint xem ảnh gốc")
    void buildViewUrl_shouldGenerateCorrectUrl() {
        String url = properties.buildViewUrl("6ad196af9c12306b8776");
        assertEquals("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/6ad196af9c12306b8776/view?project=backend-storage-hub", url);
    }

    @Test
    @DisplayName("buildPreviewUrl tạo đúng endpoint CDN WebP với kích thước tùy chọn")
    void buildPreviewUrl_shouldGenerateCorrectWebPUrl() {
        String url = properties.buildPreviewUrl("photo-123", 600, 600);
        assertEquals("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/photo-123/preview?project=backend-storage-hub&width=600&height=600&output=webp", url);
    }

    @Test
    @DisplayName("buildPhotoUrl tạo chuẩn ảnh 1080x1080 WebP")
    void buildPhotoUrl_shouldGenerate1080Square() {
        String url = properties.buildPhotoUrl("feed-img");
        assertTrue(url.contains("&width=1080&height=1080&output=webp"));
    }

    @Test
    @DisplayName("buildThumbnailUrl tạo chuẩn thumbnail 320x320 WebP")
    void buildThumbnailUrl_shouldGenerate320Square() {
        String url = properties.buildThumbnailUrl("feed-img");
        assertTrue(url.contains("&width=320&height=320&output=webp"));
    }

    @Test
    @DisplayName("buildAvatarUrl tạo chuẩn avatar 500x500 WebP")
    void buildAvatarUrl_shouldGenerate500Square() {
        String url = properties.buildAvatarUrl("avatar-img");
        assertTrue(url.contains("&width=500&height=500&output=webp"));
    }

    @Test
    @DisplayName("Xử lý an toàn khi fileId rỗng hoặc null")
    void blankOrNullFileId_shouldReturnEmptyString() {
        assertEquals("", properties.buildViewUrl(null));
        assertEquals("", properties.buildViewUrl("   "));
        assertEquals("", properties.buildPreviewUrl(null, 100, 100));
        assertEquals("", properties.buildPhotoUrl(""));
        assertEquals("", properties.buildThumbnailUrl(null));
        assertEquals("", properties.buildAvatarUrl(""));
    }

    @Test
    @DisplayName("Loại bỏ dấu gạch chéo cuối endpoint để tránh lỗi double-slash")
    void trailingSlashInEndpoint_shouldBeTrimmed() {
        properties.setEndpoint("https://fra.cloud.appwrite.io/v1///");
        String url = properties.buildViewUrl("abc");
        assertEquals("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/abc/view?project=backend-storage-hub", url);
    }
}
