package com.codegym.locketclone.storage.appwrite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppwriteFileResponse(
        @JsonProperty("$id") String id,
        @JsonProperty("bucketId") String bucketId,
        @JsonProperty("name") String name,
        @JsonProperty("mimeType") String mimeType,
        @JsonProperty("sizeOriginal") Long sizeOriginal
) {}
