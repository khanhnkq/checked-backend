package com.codegym.locketclone.storage;

public record UploadedFile(
        String secureUrl,
        String thumbnailUrl,
        String key,
        String mimeType,
        Long bytes,
        Integer width,
        Integer height
) {
    /**
     * Backward-compatibility alias for S3 object key or Cloudinary public_id.
     */
    public String publicId() {
        return key;
    }
}
