package com.codegym.locketclone.common.mapper;

import com.codegym.locketclone.photo.Photo;
import com.codegym.locketclone.photo.dto.PhotoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PhotoMapper {

    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderDisplayName", source = "sender.displayName")
    @Mapping(target = "senderAvatarUrl", source = "sender.avatarUrl")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    PhotoResponse toResponse(Photo photo);
}
