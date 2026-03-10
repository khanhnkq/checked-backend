package com.codegym.locketclone.common.mapper;

import com.codegym.locketclone.user.dto.UserRequest;
import com.codegym.locketclone.user.dto.UserResponse;
import com.codegym.locketclone.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    // Tự động map các trường trùng tên như id, username, displayName
    UserResponse toResponse(User user);
    User toEntity(UserRequest request);
}