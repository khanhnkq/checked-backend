package com.codegym.locketclone.storage.legacy;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.storage.image.ImageProcessingService;
import com.codegym.locketclone.storage.image.ProcessedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryStorageServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private ImageProcessingService imageProcessingService;

    private CloudinaryStorageService cloudinaryStorageService;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        cloudinaryStorageService = new CloudinaryStorageService(cloudinary, imageProcessingService);
    }

    @Test
    void uploadPhoto_success_uploadsOriginalAndThumbnail() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "raw-content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main-bytes".getBytes(),
                "thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                1920,
                1080
        );

        when(imageProcessingService.processPhoto(file)).thenReturn(processed);

        Map<String, Object> mainResult = Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/locket/photos/sample.jpg",
                "public_id", "locket/photos/sample"
        );
        Map<String, Object> thumbResult = Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/locket/photos/sample_thumb.jpg",
                "public_id", "locket/photos/sample_thumb"
        );

        when(uploader.upload(eq(processed.originalBytes()), any(Map.class))).thenReturn(mainResult);
        when(uploader.upload(eq(processed.thumbnailBytes()), any(Map.class))).thenReturn(thumbResult);

        UploadedFile result = cloudinaryStorageService.uploadPhoto(file);

        assertNotNull(result);
        assertEquals("https://res.cloudinary.com/demo/image/upload/locket/photos/sample.jpg", result.secureUrl());
        assertEquals("https://res.cloudinary.com/demo/image/upload/locket/photos/sample_thumb.jpg", result.thumbnailUrl());
        assertEquals("locket/photos/sample", result.key());
        assertEquals("image/jpeg", result.mimeType());
        assertEquals(1920, result.width());
        assertEquals(1080, result.height());

        verify(uploader, times(2)).upload(any(byte[].class), any(Map.class));
    }

    @Test
    void uploadAvatar_success_uploadsOriginalAndThumbnail() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "raw-content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main-bytes".getBytes(),
                "thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                500,
                500
        );

        when(imageProcessingService.processAvatar(file)).thenReturn(processed);

        Map<String, Object> mainResult = Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/locket/avatars/user1.jpg",
                "public_id", "locket/avatars/user1"
        );
        Map<String, Object> thumbResult = Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/locket/avatars/user1_thumb.jpg",
                "public_id", "locket/avatars/user1_thumb"
        );

        when(uploader.upload(eq(processed.originalBytes()), any(Map.class))).thenReturn(mainResult);
        when(uploader.upload(eq(processed.thumbnailBytes()), any(Map.class))).thenReturn(thumbResult);

        UploadedFile result = cloudinaryStorageService.uploadAvatar(file);

        assertNotNull(result);
        assertEquals("https://res.cloudinary.com/demo/image/upload/locket/avatars/user1.jpg", result.secureUrl());
        assertEquals("https://res.cloudinary.com/demo/image/upload/locket/avatars/user1_thumb.jpg", result.thumbnailUrl());
        assertEquals("locket/avatars/user1", result.key());

        verify(uploader, times(2)).upload(any(byte[].class), any(Map.class));
    }

    @Test
    void uploadPhoto_whenCloudinaryFails_throwsIOException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "raw-content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main-bytes".getBytes(),
                "thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                100,
                100
        );

        when(imageProcessingService.processPhoto(file)).thenReturn(processed);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("Cloudinary timeout"));

        assertThrows(IOException.class, () -> cloudinaryStorageService.uploadPhoto(file));
    }

    @Test
    void deleteFile_destroysMainAndThumbnail() throws Exception {
        cloudinaryStorageService.deleteFile("locket/photos/sample");

        verify(uploader, times(1)).destroy(eq("locket/photos/sample"), any(Map.class));
        verify(uploader, times(1)).destroy(eq("locket/photos/sample_thumb"), any(Map.class));
    }

    @Test
    void deleteFile_blankKey_doesNothing() {
        cloudinaryStorageService.deleteFile(null);
        cloudinaryStorageService.deleteFile("   ");

        verifyNoInteractions(uploader);
    }
}
