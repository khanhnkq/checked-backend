package com.codegym.locketclone.admin.dto;

import java.io.Serializable;
import java.util.List;

public record DlqReplayResponse(
        int replayedCount,
        int failedCount,
        long remainingCount,
        List<String> details
) implements Serializable {}
