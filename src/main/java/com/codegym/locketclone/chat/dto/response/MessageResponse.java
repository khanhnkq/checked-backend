package com.codegym.locketclone.chat.dto.response;

import com.codegym.locketclone.chat.document.MessageType;
import com.codegym.locketclone.chat.document.PhotoContextInfo;
import com.codegym.locketclone.chat.document.ReplyToInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class MessageResponse {
    private String id;
    private String conversationId;
    private UUID senderId;
    private String content;
    private MessageType type;
    private ReplyToInfo replyTo;
    private PhotoContextInfo photoContext;

    @JsonProperty("isRead")
    private boolean isRead;

    private Instant sentAt;

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }

    @JsonProperty("isRead")
    public void setRead(boolean isRead) {
        this.isRead = isRead;
    }
}
