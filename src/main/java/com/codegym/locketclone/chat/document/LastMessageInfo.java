package com.codegym.locketclone.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageInfo {
    private String messageId;
    private UUID senderId;
    private String content;
    private MessageType type;
    private Instant sentAt;
}
