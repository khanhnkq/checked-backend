package com.codegym.locketclone.storage.appwrite;

import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.storage.appwrite.dto.AppwriteFileResponse;
import com.codegym.locketclone.storage.image.ImageProcessingService;
import com.codegym.locketclone.storage.image.ProcessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "storage.type", havingValue = "appwrite")
public class AppwriteStorageService implements StorageService {

    private final RestClient appwriteRestClient;
    private final AppwriteStorageProperties properties;
    private final ImageProcessingService imageProcessingService;

    @Override
    public UploadedFile uploadPhoto(MultipartFile file) throws IOException {
        ProcessedImage processed = imageProcessingService.processPhoto(file);
        String filename = "photo_" + UUID.randomUUID() + "." + processed.extension();
        String fileId = uploadToAppwrite(processed.originalBytes(), filename, processed.mimeType());

        String secureUrl = properties.buildPhotoUrl(fileId);
        String thumbnailUrl = properties.buildThumbnailUrl(fileId);

        log.info("Uploaded photo to Appwrite. FileId: {}", fileId);

        return new UploadedFile(
                secureUrl,
                thumbnailUrl,
                fileId,
                processed.mimeType(),
                (long) processed.originalBytes().length,
                processed.width(),
                processed.height()
        );
    }

    @Override
    public UploadedFile uploadAvatar(MultipartFile file) throws IOException {
        ProcessedImage processed = imageProcessingService.processAvatar(file);
        String filename = "avatar_" + UUID.randomUUID() + "." + processed.extension();
        String fileId = uploadToAppwrite(processed.originalBytes(), filename, processed.mimeType());

        String secureUrl = properties.buildAvatarUrl(fileId);
        String thumbnailUrl = properties.buildPreviewUrl(fileId, 150, 150);

        log.info("Uploaded avatar to Appwrite. FileId: {}", fileId);

        return new UploadedFile(
                secureUrl,
                thumbnailUrl,
                fileId,
                processed.mimeType(),
                (long) processed.originalBytes().length,
                processed.width(),
                processed.height()
        );
    }

    @Override
    public UploadedFile uploadTempPhoto(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = "jpg";
        if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        String filename = "temp_" + UUID.randomUUID() + "." + ext;
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        String fileId = uploadToAppwrite(file.getBytes(), filename, contentType);

        String viewUrl = properties.buildViewUrl(fileId);
        log.info("Uploaded raw temp photo to Appwrite. TempKey: {}", fileId);

        return new UploadedFile(
                viewUrl,
                viewUrl,
                fileId,
                contentType,
                file.getSize(),
                null,
                null
        );
    }

    @Override
    public UploadedFile processAndFinalizePhoto(String tempKey) throws IOException {
        log.info("Finalizing photo from Appwrite tempKey: {}", tempKey);

        byte[] rawBytes = downloadFromAppwrite(tempKey);
        ProcessedImage processed = imageProcessingService.processPhoto(rawBytes);

        String filename = "photo_" + UUID.randomUUID() + "." + processed.extension();
        String finalFileId = uploadToAppwrite(processed.originalBytes(), filename, processed.mimeType());

        // Dọn dẹp file tạm thô trên Appwrite
        deleteFile(tempKey);

        String secureUrl = properties.buildPhotoUrl(finalFileId);
        String thumbnailUrl = properties.buildThumbnailUrl(finalFileId);

        log.info("Finalized photo to Appwrite. FinalId: {}, Deleted TempId: {}", finalFileId, tempKey);

        return new UploadedFile(
                secureUrl,
                thumbnailUrl,
                finalFileId,
                processed.mimeType(),
                (long) processed.originalBytes().length,
                processed.width(),
                processed.height()
        );
    }

    @Override
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            appwriteRestClient.delete()
                    .uri("/storage/buckets/{bucketId}/files/{fileId}", properties.getBucketId(), key.trim())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted file from Appwrite Storage: {}", key);
        } catch (HttpClientErrorException.NotFound e) {
            log.info("File not found on Appwrite (already deleted or non-existent): {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete file from Appwrite: {}. Error: {}", key, e.getMessage());
        }
    }

    private byte[] downloadFromAppwrite(String fileId) throws IOException {
        try {
            byte[] bytes = appwriteRestClient.get()
                    .uri("/storage/buckets/{bucketId}/files/{fileId}/download", properties.getBucketId(), fileId)
                    .retrieve()
                    .body(byte[].class);

            if (bytes == null || bytes.length == 0) {
                throw new IOException("Không tải được dữ liệu file từ Appwrite: " + fileId);
            }
            return bytes;
        } catch (Exception e) {
            throw new IOException("Lỗi khi tải file từ Appwrite Storage: " + fileId, e);
        }
    }

    private String uploadToAppwrite(byte[] bytes, String filename, String contentType) throws IOException {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("fileId", "unique()");
            builder.part("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }, MediaType.parseMediaType(contentType));
            builder.part("permissions[]", "read(\"any\")");

            AppwriteFileResponse response = appwriteRestClient.post()
                    .uri("/storage/buckets/{bucketId}/files", properties.getBucketId())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(AppwriteFileResponse.class);

            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new IOException("Phản hồi từ Appwrite Storage không hợp lệ hoặc thiếu file ID");
            }
            return response.id();
        } catch (Exception e) {
            log.error("Lỗi khi upload file lên Appwrite Storage: {}", e.getMessage(), e);
            throw new IOException("Không thể tải file lên Appwrite Storage", e);
        }
    }
}
