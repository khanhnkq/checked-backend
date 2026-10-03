package com.codegym.locketclone.storage.image;

public record ProcessedImage(
        byte[] originalBytes,
        byte[] thumbnailBytes,
        String extension,
        String mimeType,
        int width,
        int height
) {
}
