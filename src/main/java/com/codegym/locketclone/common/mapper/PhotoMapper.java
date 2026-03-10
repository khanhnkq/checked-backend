package com.codegym.locketclone.common.mapper;

import com.codegym.locketclone.photo.dto.PhotoResponse;
import com.codegym.locketclone.photo.Photo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PhotoMapper {
    @Mapping(source = "sender.id", target = "senderId")
    PhotoResponse toResponse(Photo photo);

    Photo toEntity(PhotoResponse response);
}
