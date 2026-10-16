package com.codegym.locketclone.storage.appwrite;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "storage.appwrite")
public class AppwriteStorageProperties {

    private String endpoint = "https://fra.cloud.appwrite.io/v1";
    private String projectId = "backend-storage-hub";
    private String bucketId = "checked-storage";
    private String apiKey = "";

    public String buildViewUrl(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return "";
        }
        return getNormalizedEndpoint() + "/storage/buckets/" + bucketId + "/files/" + fileId + "/view?project=" + projectId;
    }

    public String buildPreviewUrl(String fileId, int width, int height) {
        if (fileId == null || fileId.isBlank()) {
            return "";
        }
        return getNormalizedEndpoint() + "/storage/buckets/" + bucketId + "/files/" + fileId
                + "/preview?project=" + projectId
                + "&width=" + width
                + "&height=" + height
                + "&output=webp";
    }

    public String buildPhotoUrl(String fileId) {
        return buildPreviewUrl(fileId, 1080, 1080);
    }

    public String buildThumbnailUrl(String fileId) {
        return buildPreviewUrl(fileId, 320, 320);
    }

    public String buildAvatarUrl(String fileId) {
        return buildPreviewUrl(fileId, 500, 500);
    }

    private String getNormalizedEndpoint() {
        return endpoint != null ? endpoint.replaceAll("/+$", "") : "";
    }
}
