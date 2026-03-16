package com.codegym.locketclone.common.mapper;


import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import org.springframework.stereotype.Component;

@Component
public class PhotoMapper {
    public PhotoResponse toResponse(Photo photo) {
        return PhotoResponse.builder()
                .id(photo.getId())
                .senderId(photo.getSender().getId())
                .senderDisplayName(photo.getSender().getDisplayName())
                .senderAvatarUrl(photo.getSender().getAvatarUrl())
                .imageUrl(photo.getImageUrl())
                .thumbnailUrl(photo.getThumbnailUrl())
                .caption(photo.getCaption())
                .note(photo.getNote())
                .amount(photo.getAmount())
                .categoryId(photo.getCategory() != null ? photo.getCategory().getId() : null)
                .categoryName(photo.getCategory() != null ? photo.getCategory().getName() : null)
                .recipientScope(photo.getRecipientScope())
                .recipientCount(photo.getRecipientCount())
                .status(photo.getStatus())
                .mimeType(photo.getMimeType())
                .fileSize(photo.getFileSize())
                .width(photo.getWidth())
                .height(photo.getHeight())
                .takenAt(photo.getTakenAt())
                .createdAt(photo.getCreatedAt())
                .build();
    }
}
