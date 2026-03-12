package com.codegym.locketclone.expense.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String iconName,
        String colorCode
){
}
