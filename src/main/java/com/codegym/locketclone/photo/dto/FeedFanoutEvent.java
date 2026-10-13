package com.codegym.locketclone.photo.dto;

import com.codegym.locketclone.photo.RecipientScope;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record FeedFanoutEvent(
        UUID photoId,
        UUID senderId,
        RecipientScope recipientScope,
        List<UUID> recipientIds
) implements Serializable {}
