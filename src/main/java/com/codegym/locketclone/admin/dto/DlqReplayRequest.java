package com.codegym.locketclone.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DlqReplayRequest(
        @Min(1) @Max(1000)
        Integer limit,
        String targetExchange
) {}
