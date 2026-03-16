package com.codegym.locketclone.photo;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {
    private final Cloudinary cloudinary;

    public UploadedImage uploadImage(MultipartFile file) throws IOException {
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("resource_type", "image", "folder", "locket/photos")
            );

            String secureUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();
            Long bytes = uploadResult.get("bytes") instanceof Number number ? number.longValue() : file.getSize();
            Integer width = uploadResult.get("width") instanceof Number number ? number.intValue() : null;
            Integer height = uploadResult.get("height") instanceof Number number ? number.intValue() : null;

            return new UploadedImage(
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
