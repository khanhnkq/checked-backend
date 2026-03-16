package com.codegym.locketclone.expense.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String icon,
        String color,
        Boolean isDefault,
        Boolean isActive
){
}
