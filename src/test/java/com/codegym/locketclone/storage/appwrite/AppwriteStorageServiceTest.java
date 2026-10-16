package com.codegym.locketclone.storage.appwrite;

import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.storage.image.ImageProcessingService;
import com.codegym.locketclone.storage.image.ProcessedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class AppwriteStorageServiceTest {

    private MockRestServiceServer mockServer;
    private AppwriteStorageProperties properties;

    @Mock
    private ImageProcessingService imageProcessingService;

    private AppwriteStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new AppwriteStorageProperties();
        properties.setEndpoint("https://fra.cloud.appwrite.io/v1");
        properties.setProjectId("backend-storage-hub");
        properties.setBucketId("checked-storage");
        properties.setApiKey("standard_test_key");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader("X-Appwrite-Project", properties.getProjectId())
                .defaultHeader("X-Appwrite-Key", properties.getApiKey());

        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        storageService = new AppwriteStorageService(restClient, properties, imageProcessingService);
    }

    @Test
    @DisplayName("uploadPhoto: Nén ảnh và upload lên Appwrite bucket thành công")
    void uploadPhoto_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "dummy-content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main-bytes".getBytes(),
                "thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                1080,
                1080
        );
        when(imageProcessingService.processPhoto(file)).thenReturn(processed);

        String jsonResponse = """
                {
                    "$id": "photo-123",
                    "bucketId": "checked-storage",
                    "name": "photo_test.jpg",
                    "mimeType": "image/jpeg",
                    "sizeOriginal": 10240
                }
                """;

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Appwrite-Project", "backend-storage-hub"))
                .andExpect(header("X-Appwrite-Key", "standard_test_key"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(jsonResponse));

        UploadedFile result = storageService.uploadPhoto(file);

        mockServer.verify();
        assertNotNull(result);
        assertEquals("photo-123", result.key());
        assertEquals("photo-123", result.publicId());
        assertEquals("image/jpeg", result.mimeType());
        assertEquals(1080, result.width());
        assertEquals(1080, result.height());
        assertTrue(result.secureUrl().contains("photo-123"));
        assertTrue(result.secureUrl().contains("&width=1080&height=1080&output=webp"));
        assertTrue(result.thumbnailUrl().contains("photo-123"));
        assertTrue(result.thumbnailUrl().contains("&width=320&height=320&output=webp"));
    }

    @Test
    @DisplayName("uploadAvatar: Nén avatar và upload lên Appwrite bucket thành công")
    void uploadAvatar_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "avatar-bytes".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "avatar-bytes".getBytes(),
                "avatar-thumb".getBytes(),
                "jpg",
                "image/jpeg",
                500,
                500
        );
        when(imageProcessingService.processAvatar(file)).thenReturn(processed);

        String jsonResponse = """
                {
                    "$id": "avatar-456",
                    "bucketId": "checked-storage",
                    "name": "avatar_test.jpg",
                    "mimeType": "image/jpeg",
                    "sizeOriginal": 5120
                }
                """;

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(jsonResponse));

        UploadedFile result = storageService.uploadAvatar(file);

        mockServer.verify();
        assertNotNull(result);
        assertEquals("avatar-456", result.key());
        assertTrue(result.secureUrl().contains("&width=500&height=500&output=webp"));
        assertTrue(result.thumbnailUrl().contains("&width=150&height=150&output=webp"));
    }

    @Test
    @DisplayName("uploadTempPhoto: Tải nhanh stream raw phục vụ hàng đợi bất đồng bộ")
    void uploadTempPhoto_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "raw.png", "image/png", "raw-png-bytes".getBytes());

        String jsonResponse = """
                {
                    "$id": "temp-789",
                    "bucketId": "checked-storage",
                    "name": "temp_raw.png",
                    "mimeType": "image/png",
                    "sizeOriginal": 1234
                }
                """;

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(jsonResponse));

        UploadedFile result = storageService.uploadTempPhoto(file);

        mockServer.verify();
        assertNotNull(result);
        assertEquals("temp-789", result.key());
        assertEquals("image/png", result.mimeType());
        assertTrue(result.secureUrl().endsWith("/files/temp-789/view?project=backend-storage-hub"));
    }

    @Test
    @DisplayName("processAndFinalizePhoto: Tải raw stream, nén chuẩn 1080, upload ảnh chính và dọn dẹp tempKey")
    void processAndFinalizePhoto_success() throws IOException {
        byte[] rawBytes = "downloaded-raw-bytes".getBytes();
        ProcessedImage processed = new ProcessedImage(
                "final-main-bytes".getBytes(),
                "final-thumb-bytes".getBytes(),
                "jpg",
                "image/jpeg",
                1080,
                1080
        );
        when(imageProcessingService.processPhoto(rawBytes)).thenReturn(processed);

        // 1. Download raw file
        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/temp-789/download"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(rawBytes, MediaType.APPLICATION_OCTET_STREAM));

        // 2. Upload finalized photo
        String uploadResponse = """
                {
                    "$id": "final-999",
                    "bucketId": "checked-storage",
                    "name": "photo_final.jpg",
                    "mimeType": "image/jpeg",
                    "sizeOriginal": 20480
                }
                """;
        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(uploadResponse));

        // 3. Delete temp file
        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/temp-789"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        UploadedFile result = storageService.processAndFinalizePhoto("temp-789");

        mockServer.verify();
        assertNotNull(result);
        assertEquals("final-999", result.key());
        assertTrue(result.secureUrl().contains("&width=1080&height=1080&output=webp"));
        assertTrue(result.thumbnailUrl().contains("&width=320&height=320&output=webp"));
    }

    @Test
    @DisplayName("deleteFile: Xóa file thành công khi nhận HTTP 204")
    void deleteFile_success() {
        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/file-to-delete"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        storageService.deleteFile("file-to-delete");
        mockServer.verify();
    }

    @Test
    @DisplayName("deleteFile: Idempotent - Nuốt lỗi 404 khi file không tồn tại hoặc đã xóa trước đó")
    void deleteFile_notFound_shouldBeIdempotent() {
        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files/non-existent-file"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertDoesNotThrow(() -> storageService.deleteFile("non-existent-file"));
        mockServer.verify();
    }

    @Test
    @DisplayName("deleteFile: Bỏ qua khi fileId là null hoặc chuỗi khoảng trắng")
    void deleteFile_nullOrBlank_shouldIgnore() {
        storageService.deleteFile(null);
        storageService.deleteFile("   ");
        mockServer.verify(); // Không có request nào được gửi
    }

    @Test
    @DisplayName("uploadPhoto: Ném ra IOException khi Appwrite server bị lỗi 500")
    void uploadPhoto_whenAppwriteFails_shouldThrowIOException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "content".getBytes());
        ProcessedImage processed = new ProcessedImage(
                "main".getBytes(),
                "thumb".getBytes(),
                "jpg",
                "image/jpeg",
                1080,
                1080
        );
        when(imageProcessingService.processPhoto(file)).thenReturn(processed);

        mockServer.expect(requestTo("https://fra.cloud.appwrite.io/v1/storage/buckets/checked-storage/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThrows(IOException.class, () -> storageService.uploadPhoto(file));
        mockServer.verify();
    }
}
