package com.codegym.locketclone.storage.image;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class ImageProcessingService {

    private static final int MAX_PHOTO_DIMENSION = 1920;
    private static final int PHOTO_THUMB_SIZE = 320;
    private static final int AVATAR_SIZE = 500;
    private static final int AVATAR_THUMB_SIZE = 150;
    private static final float JPEG_QUALITY_HIGH = 0.85f;
    private static final float JPEG_QUALITY_THUMB = 0.80f;

    public ProcessedImage processPhoto(MultipartFile file) throws IOException {
        validateFile(file);

        // 1. Process main photo: max 1920x1920 maintaining aspect ratio
        ByteArrayOutputStream mainBaos = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(MAX_PHOTO_DIMENSION, MAX_PHOTO_DIMENSION)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY_HIGH)
                .toOutputStream(mainBaos);

        byte[] mainBytes = mainBaos.toByteArray();
        BufferedImage mainImage = ImageIO.read(new ByteArrayInputStream(mainBytes));
        int width = mainImage != null ? mainImage.getWidth() : MAX_PHOTO_DIMENSION;
        int height = mainImage != null ? mainImage.getHeight() : MAX_PHOTO_DIMENSION;

        // 2. Process thumbnail: 320x320 square crop center
        ByteArrayOutputStream thumbBaos = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(mainBytes))
                .size(PHOTO_THUMB_SIZE, PHOTO_THUMB_SIZE)
                .crop(Positions.CENTER)
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

        // 1. Process avatar: 500x500 square crop center
        ByteArrayOutputStream mainBaos = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(AVATAR_SIZE, AVATAR_SIZE)
                .crop(Positions.CENTER)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY_HIGH)
                .toOutputStream(mainBaos);

        byte[] mainBytes = mainBaos.toByteArray();
        BufferedImage mainImage = ImageIO.read(new ByteArrayInputStream(mainBytes));
        int width = mainImage != null ? mainImage.getWidth() : AVATAR_SIZE;
        int height = mainImage != null ? mainImage.getHeight() : AVATAR_SIZE;

        // 2. Process avatar thumbnail: 150x150 square
        ByteArrayOutputStream thumbBaos = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(mainBytes))
                .size(AVATAR_THUMB_SIZE, AVATAR_THUMB_SIZE)
                .crop(Positions.CENTER)
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
    }
}
