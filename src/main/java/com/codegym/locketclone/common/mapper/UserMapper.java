package com.codegym.locketclone.common.mapper;

import com.codegym.locketclone.user.User;
import com.codegym.locketclone.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    UserResponse toResponse(User user);
}