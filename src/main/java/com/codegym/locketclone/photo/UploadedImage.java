package com.codegym.locketclone.photo;

public record UploadedImage(
        String secureUrl,
        String thumbnailUrl,
        String publicId,
        String mimeType,
        Long bytes,
        Integer width,
        Integer height
) {
}

