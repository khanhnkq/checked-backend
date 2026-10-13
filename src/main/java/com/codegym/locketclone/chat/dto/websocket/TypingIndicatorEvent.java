package com.codegym.locketclone.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorEvent {

    private String conversationId;
    private UUID userId;

    @JsonProperty("isTyping")
    private boolean isTyping;

    @JsonProperty("isTyping")
    public boolean isTyping() {
        return isTyping;
    }

    @JsonProperty("isTyping")
    public void setTyping(boolean isTyping) {
        this.isTyping = isTyping;
    }
}
