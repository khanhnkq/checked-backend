package com.codegym.locketclone.storage.legacy;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import com.codegym.locketclone.storage.image.ImageProcessingService;
import com.codegym.locketclone.storage.image.ProcessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "storage.type", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;
    private final ImageProcessingService imageProcessingService;
    private final @Qualifier("storageTaskExecutor") Executor storageTaskExecutor;

    @Override
    public UploadedFile uploadPhoto(MultipartFile file) throws IOException {
        ProcessedImage processed = imageProcessingService.processPhoto(file);
        return uploadProcessed(processed, "locket/photos");
    }

    @Override
    public UploadedFile uploadAvatar(MultipartFile file) throws IOException {
        ProcessedImage processed = imageProcessingService.processAvatar(file);
        return uploadProcessed(processed, "locket/avatars");
    }

    @Override
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(key, ObjectUtils.emptyMap());
            cloudinary.uploader().destroy(key + "_thumb", ObjectUtils.emptyMap());
            log.info("Deleted file and thumbnail from Cloudinary: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete file from Cloudinary: {}. Error: {}", key, e.getMessage());
        }
    }

    private UploadedFile uploadProcessed(ProcessedImage processed, String folder) throws IOException {
        String fileUuid = UUID.randomUUID().toString();
        String mainPublicId = folder + "/" + fileUuid;
        String thumbPublicId = folder + "/" + fileUuid + "_thumb";

        CompletableFuture<Map<?, ?>> mainUploadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return cloudinary.uploader().upload(
                        processed.originalBytes(),
                        ObjectUtils.asMap("resource_type", "image", "public_id", mainPublicId)
                );
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, storageTaskExecutor);

        CompletableFuture<Map<?, ?>> thumbUploadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return cloudinary.uploader().upload(
                        processed.thumbnailBytes(),
                        ObjectUtils.asMap("resource_type", "image", "public_id", thumbPublicId)
                );
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, storageTaskExecutor);

        try {
            CompletableFuture.allOf(mainUploadFuture, thumbUploadFuture).join();
            Map<?, ?> mainResult = mainUploadFuture.join();
            Map<?, ?> thumbResult = thumbUploadFuture.join();

            String secureUrl = mainResult.get("secure_url").toString();
            String publicId = mainResult.get("public_id").toString();
            String thumbnailUrl = thumbResult.get("secure_url").toString();

            log.info("Uploaded to Cloudinary in parallel. PublicId: {}, ThumbPublicId: {}", publicId, thumbPublicId);

            return new UploadedFile(
                    secureUrl,
                    thumbnailUrl,
                    publicId,
                    processed.mimeType(),
                    (long) processed.originalBytes().length,
                    processed.width(),
                    processed.height()
            );
        } catch (CompletionException e) {
            log.error("Lỗi khi upload ảnh song song lên Cloudinary: ", e);
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Không thể tải ảnh lên, vui lòng thử lại sau.", cause);
        }
    }
}
