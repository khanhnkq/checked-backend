package com.codegym.locketclone.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorPayload {

    @NotBlank(message = "conversationId không được để trống")
    private String conversationId;

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
