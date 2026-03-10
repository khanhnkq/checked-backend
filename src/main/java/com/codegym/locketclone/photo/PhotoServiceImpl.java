package com.codegym.locketclone.photo;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import com.codegym.locketclone.common.mapper.PhotoMapper;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final PhotoMapper photoMapper;


    @Override
    public Page<PhotoResponse> getFeedPhotos(UUID userId, Pageable pageable) {
        if (userRepository.findById(userId).isEmpty()){
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return photoRepository.findFeedPhotos(userId, pageable).map(photoMapper::toResponse);
    }

    @Override
    public Page<PhotoResponse> getMyPhotos(UUID userId, Pageable pageable) {
        if (userRepository.findById(userId).isEmpty()){
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return photoRepository.findBySenderId(userId, pageable).map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public PhotoResponse uploadPhoto(MultipartFile file, String caption, UUID senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        try {
            // 2. Upload file lên Cloudinary để lấy link
            log.info("Bắt đầu upload ảnh lên Cloudinary cho user: {}", senderId);
            String imageUrl = cloudinaryService.uploadImage(file);

            // 3. Tạo Entity Photo và lưu Database
            Photo photo = Photo.builder()
                    .sender(sender)
                    .imageUrl(imageUrl)
                    .caption(caption)
                    .build();

            Photo savedPhoto = photoRepository.save(photo);
            log.info("Lưu Photo vào Database thành công, ID: {}", savedPhoto.getId());

            // 4. Trả về DTO (Bạn hãy tự tạo PhotoMapper hoặc convert thủ công)
            return photoMapper.toResponse(savedPhoto);

        } catch (IOException e) {
            log.error("Lỗi khi xử lý file upload: {}", e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
}
