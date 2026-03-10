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

    public String uploadImage(MultipartFile file) throws IOException {
        try {
            // Upload trực tiếp từ byte array để không phải tạo file rác trên server
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

            // Lấy link ảnh an toàn (https) trả về từ Cloudinary
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            log.error("Lỗi khi upload ảnh lên Cloudinary: ", e);
            throw new IOException("Không thể tải ảnh lên, vui lòng thử lại sau.");
        }
    }
}
