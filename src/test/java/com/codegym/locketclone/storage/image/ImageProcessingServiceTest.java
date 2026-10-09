package com.codegym.locketclone.storage.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessingServiceTest {

    private ImageProcessingService imageProcessingService;

    @BeforeEach
    void setUp() {
        imageProcessingService = new ImageProcessingService();
    }

    private byte[] createTestImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    void processPhoto_resizesAndGeneratesThumbnail() throws IOException {
        byte[] imageBytes = createTestImage(2400, 1600);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", imageBytes);

        ProcessedImage result = imageProcessingService.processPhoto(file);

        assertNotNull(result);
        assertNotNull(result.originalBytes());
        assertNotNull(result.thumbnailBytes());
        assertTrue(result.originalBytes().length > 0);
        assertTrue(result.thumbnailBytes().length > 0);
        assertEquals("jpg", result.extension());
        assertEquals("image/jpeg", result.mimeType());
        assertTrue(result.width() <= 1920);
        assertTrue(result.height() <= 1920);
    }

    @Test
    void processAvatar_cropsAndGeneratesThumbnail() throws IOException {
        byte[] imageBytes = createTestImage(800, 600);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", imageBytes);

        ProcessedImage result = imageProcessingService.processAvatar(file);

        assertNotNull(result);
        assertEquals(500, result.width());
        assertEquals(500, result.height());
        assertEquals("jpg", result.extension());
        assertEquals("image/jpeg", result.mimeType());
    }

    @Test
    void processPhoto_throwsWhenFileEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThrows(IOException.class, () -> imageProcessingService.processPhoto(emptyFile));
    }

    @Test
    void processPhoto_throwsWhenImageExceedsMaxDimension() throws IOException {
        byte[] largeDimensionBytes = createTestImage(8193, 100);
        MockMultipartFile file = new MockMultipartFile("file", "huge.jpg", "image/jpeg", largeDimensionBytes);

        com.codegym.locketclone.common.exception.AppException ex = assertThrows(
                com.codegym.locketclone.common.exception.AppException.class,
                () -> imageProcessingService.processPhoto(file)
        );
        assertEquals(com.codegym.locketclone.common.exception.ErrorCode.INVALID_PHOTO_FILE, ex.getErrorCode());
    }

    @Test
    void processPhoto_throwsWhenImageContentCorrupted() {
        MockMultipartFile corruptFile = new MockMultipartFile("file", "corrupt.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5});

        com.codegym.locketclone.common.exception.AppException ex = assertThrows(
                com.codegym.locketclone.common.exception.AppException.class,
                () -> imageProcessingService.processPhoto(corruptFile)
        );
        assertEquals(com.codegym.locketclone.common.exception.ErrorCode.INVALID_PHOTO_FILE, ex.getErrorCode());
    }
}
