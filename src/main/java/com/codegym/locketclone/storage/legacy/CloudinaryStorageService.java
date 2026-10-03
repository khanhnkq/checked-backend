package com.codegym.locketclone.storage.legacy;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.codegym.locketclone.storage.StorageService;
import com.codegym.locketclone.storage.UploadedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "storage.type", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public UploadedFile uploadPhoto(MultipartFile file) throws IOException {
        return uploadToFolder(file, "locket/photos");
    }

    @Override
    public UploadedFile uploadAvatar(MultipartFile file) throws IOException {
        return uploadToFolder(file, "locket/avatars");
    }

    @Override
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(key, ObjectUtils.emptyMap());
            log.info("Deleted file from Cloudinary: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete file from Cloudinary: {}. Error: {}", key, e.getMessage());
        }
    }

    private UploadedFile uploadToFolder(MultipartFile file, String folder) throws IOException {
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("resource_type", "image", "folder", folder)
            );

            String secureUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();
            Long bytes = uploadResult.get("bytes") instanceof Number number ? number.longValue() : file.getSize();
            Integer width = uploadResult.get("width") instanceof Number number ? number.intValue() : null;
            Integer height = uploadResult.get("height") instanceof Number number ? number.intValue() : null;

            return new UploadedFile(
                    secureUrl,
                    secureUrl,
                    publicId,
                    file.getContentType(),
                    bytes,
                    width,
                    height
            );
        } catch (IOException e) {
            log.error("Lỗi khi upload ảnh lên Cloudinary: ", e);
            throw new IOException("Không thể tải ảnh lên, vui lòng thử lại sau.");
        }
    }
}
