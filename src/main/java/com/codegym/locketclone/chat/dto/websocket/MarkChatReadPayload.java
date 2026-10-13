package com.codegym.locketclone.chat.dto.websocket;

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
public class MarkChatReadPayload {

    @NotBlank(message = "conversationId không được để trống")
    private String conversationId;
}
