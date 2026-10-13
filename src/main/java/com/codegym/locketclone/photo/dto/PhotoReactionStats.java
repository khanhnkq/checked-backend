package com.codegym.locketclone.photo.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PhotoReactionStats(
        UUID photoId,
        long totalCount,
        Map<String, Long> countsByType,
        List<PhotoReactorResponse> reactors
) {
}
