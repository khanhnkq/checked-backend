package com.codegym.locketclone.photo.dto;

import java.util.Map;
import java.util.UUID;

public record PhotoReactionSummaryResponse(
        UUID photoId,
        long totalCount,
        String myReaction,
        Map<String, Long> countsByType
) {
}

