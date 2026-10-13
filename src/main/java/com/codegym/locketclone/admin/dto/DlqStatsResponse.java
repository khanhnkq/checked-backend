package com.codegym.locketclone.admin.dto;

import java.io.Serializable;

public record DlqStatsResponse(
        String queueName,
        long messageCount,
        int consumerCount,
        String deadLetterExchange,
        String status
) implements Serializable {}
