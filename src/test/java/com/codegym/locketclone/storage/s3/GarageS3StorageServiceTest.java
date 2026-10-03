package com.codegym.locketclone.storage.s3;

import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.storage.image.ImageProcessingService;
import com.codegym.locketclone.storage.image.ProcessedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarageS3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageProcessingService imageProcessingService;

    private S3StorageProperties properties;
    private GarageS3StorageService garageS3StorageService;

    @BeforeEach
    void setUp() {
        properties = new S3StorageProperties();
        properties.setBucketName("test-bucket");
        properties.setPublicBaseUrl("http://localhost:3902/test-bucket");
        garageS3StorageService = new GarageS3StorageService(s3Client, properties, imageProcessingService);
    }

    @Test
    void uploadPhoto_putsMainAndThumbnailAndReturnsValidUrls() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main-bytes".getBytes(),
                "thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                1080,
                1920
        );

        when(imageProcessingService.processPhoto(file)).thenReturn(processed);

        UploadedFile result = garageS3StorageService.uploadPhoto(file);

        assertNotNull(result);
        assertTrue(result.secureUrl().startsWith("http://localhost:3902/test-bucket/photos/"));
        assertTrue(result.thumbnailUrl().contains("_thumb.jpg"));
        assertEquals("image/jpeg", result.mimeType());
        assertEquals(1080, result.width());
        assertEquals(1920, result.height());

        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadAvatar_putsMainAndThumbnailAndReturnsValidUrls() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "avatar-content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main-bytes".getBytes(),
                "thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                500,
                500
        );

        when(imageProcessingService.processAvatar(file)).thenReturn(processed);

        UploadedFile result = garageS3StorageService.uploadAvatar(file);

        assertNotNull(result);
        assertTrue(result.secureUrl().startsWith("http://localhost:3902/test-bucket/avatars/"));
        assertTrue(result.thumbnailUrl().contains("_thumb.jpg"));

        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deleteFile_deletesMainAndThumbnailObject() {
        String key = "photos/2026/10/abcd-1234.jpg";

        garageS3StorageService.deleteFile(key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(captor.capture());

        var requests = captor.getAllValues();
        assertEquals(key, requests.get(0).key());
        assertEquals("photos/2026/10/abcd-1234_thumb.jpg", requests.get(1).key());
    }

    @Test
    void deleteFile_doesNothingWhenKeyIsBlank() {
        garageS3StorageService.deleteFile(null);
        garageS3StorageService.deleteFile("   ");

        verifyNoInteractions(s3Client);
    }
}
