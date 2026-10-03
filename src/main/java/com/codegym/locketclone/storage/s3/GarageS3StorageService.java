package com.codegym.locketclone.storage.s3;

import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.storage.image.ImageProcessingService;
import com.codegym.locketclone.storage.image.ProcessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "storage.type", havingValue = "garage", matchIfMissing = true)
public class GarageS3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3StorageProperties properties;
    private final ImageProcessingService imageProcessingService;

    @Override
    public UploadedFile uploadPhoto(MultipartFile file) throws IOException {
        ProcessedImage processed = imageProcessingService.processPhoto(file);
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileUuid = UUID.randomUUID().toString();

        String mainKey = "photos/" + yearMonth + "/" + fileUuid + "." + processed.extension();
        String thumbKey = "photos/" + yearMonth + "/" + fileUuid + "_thumb." + processed.extension();

        putObject(mainKey, processed.originalBytes(), processed.mimeType());
        putObject(thumbKey, processed.thumbnailBytes(), processed.mimeType());

        String secureUrl = buildPublicUrl(mainKey);
        String thumbnailUrl = buildPublicUrl(thumbKey);

        log.info("Uploaded photo to Garage S3. Main key: {}, Thumb key: {}", mainKey, thumbKey);

        return new UploadedFile(
                secureUrl,
                thumbnailUrl,
                mainKey,
                processed.mimeType(),
                (long) processed.originalBytes().length,
                processed.width(),
                processed.height()
        );
    }

    @Override
    public UploadedFile uploadAvatar(MultipartFile file) throws IOException {
        ProcessedImage processed = imageProcessingService.processAvatar(file);
        String fileUuid = UUID.randomUUID().toString();

        String mainKey = "avatars/" + fileUuid + "." + processed.extension();
        String thumbKey = "avatars/" + fileUuid + "_thumb." + processed.extension();

        putObject(mainKey, processed.originalBytes(), processed.mimeType());
        putObject(thumbKey, processed.thumbnailBytes(), processed.mimeType());

        String secureUrl = buildPublicUrl(mainKey);
        String thumbnailUrl = buildPublicUrl(thumbKey);

        log.info("Uploaded avatar to Garage S3. Main key: {}, Thumb key: {}", mainKey, thumbKey);

        return new UploadedFile(
                secureUrl,
                thumbnailUrl,
                mainKey,
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
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key)
                    .build());

            // Also delete thumbnail if exists
            int lastDot = key.lastIndexOf('.');
            if (lastDot > 0) {
                String thumbKey = key.substring(0, lastDot) + "_thumb" + key.substring(lastDot);
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucketName())
                        .key(thumbKey)
                        .build());
            }
            log.info("Deleted object and thumbnail from Garage S3: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete file from S3: {}. Error: {}", key, e.getMessage());
        }
    }

    private void putObject(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucketName())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content)
        );
    }

    private String buildPublicUrl(String key) {
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        return base + "/" + key;
    }
}
