package com.codegym.locketclone.storage.image;

import com.codegym.locketclone.common.exception.AppException;
import com.codegym.locketclone.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
@Slf4j
public class ImageProcessingService {

    public static final int MAX_INPUT_DIMENSION = 8192;
    private static final int PHOTO_SIZE = 1080;
    private static final int PHOTO_THUMB_SIZE = 320;
    private static final int AVATAR_SIZE = 500;
    private static final int AVATAR_THUMB_SIZE = 150;
    private static final float JPEG_QUALITY_HIGH = 0.85f;
    private static final float JPEG_QUALITY_THUMB = 0.80f;

    public ProcessedImage processPhoto(MultipartFile file) throws IOException {
        validateFile(file);

        // 1. Center crop 1:1 square & resize to 1080x1080 in memory with EXIF orientation
        BufferedImage squareImage = Thumbnails.of(file.getInputStream())
                .useExifOrientation(true)
                .crop(Positions.CENTER)
                .size(PHOTO_SIZE, PHOTO_SIZE)
                .asBufferedImage();

        int width = squareImage.getWidth();
        int height = squareImage.getHeight();

        // 2. Encode main photo bytes (1080x1080, high quality)
        ByteArrayOutputStream mainBaos = new ByteArrayOutputStream();
        Thumbnails.of(squareImage)
                .size(PHOTO_SIZE, PHOTO_SIZE)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY_HIGH)
                .toOutputStream(mainBaos);
        byte[] mainBytes = mainBaos.toByteArray();

        // 3. Encode thumbnail bytes directly from squareImage in RAM (downscaled to 320x320)
        ByteArrayOutputStream thumbBaos = new ByteArrayOutputStream();
        Thumbnails.of(squareImage)
                .size(PHOTO_THUMB_SIZE, PHOTO_THUMB_SIZE)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY_THUMB)
                .toOutputStream(thumbBaos);
        byte[] thumbBytes = thumbBaos.toByteArray();

        return new ProcessedImage(
                mainBytes,
                thumbBytes,
                "jpg",
                "image/jpeg",
                width,
                height
        );
    }

    public ProcessedImage processAvatar(MultipartFile file) throws IOException {
        validateFile(file);

        // 1. Center crop 1:1 square & resize to 500x500 in memory with EXIF orientation
        BufferedImage squareAvatar = Thumbnails.of(file.getInputStream())
                .useExifOrientation(true)
                .crop(Positions.CENTER)
                .size(AVATAR_SIZE, AVATAR_SIZE)
                .asBufferedImage();

        int width = squareAvatar.getWidth();
        int height = squareAvatar.getHeight();

        // 2. Encode main avatar bytes (500x500, high quality)
        ByteArrayOutputStream mainBaos = new ByteArrayOutputStream();
        Thumbnails.of(squareAvatar)
                .size(AVATAR_SIZE, AVATAR_SIZE)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY_HIGH)
                .toOutputStream(mainBaos);
        byte[] mainBytes = mainBaos.toByteArray();

        // 3. Encode avatar thumbnail bytes directly from squareAvatar in RAM (downscaled to 150x150)
        ByteArrayOutputStream thumbBaos = new ByteArrayOutputStream();
        Thumbnails.of(squareAvatar)
                .size(AVATAR_THUMB_SIZE, AVATAR_THUMB_SIZE)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY_THUMB)
                .toOutputStream(thumbBaos);
        byte[] thumbBytes = thumbBaos.toByteArray();

        return new ProcessedImage(
                mainBytes,
                thumbBytes,
                "jpg",
                "image/jpeg",
                width,
                height
        );
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Tệp hình ảnh không được để trống.");
        }
        validateImageDimensions(file);
    }

    private void validateImageDimensions(MultipartFile file) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.getInputStream())) {
            if (iis == null) {
                throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_INPUT_DIMENSION || height > MAX_INPUT_DIMENSION) {
                    log.warn("Kích thước ảnh vượt quá giới hạn an toàn: {}x{} (tối đa {}px)", width, height, MAX_INPUT_DIMENSION);
                    throw new AppException(ErrorCode.INVALID_PHOTO_FILE);
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
