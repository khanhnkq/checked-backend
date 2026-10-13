package com.codegym.locketclone.chat.dto.websocket;

import com.codegym.locketclone.chat.document.MessageType;
import com.codegym.locketclone.chat.document.PhotoContextInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class SendChatMessagePayload {

    @NotBlank(message = "conversationId không được để trống")
    private String conversationId;

    @NotBlank(message = "content không được để trống")
    @Size(max = 2000, message = "content tối đa 2000 ký tự")
    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private String replyToMessageId;

    private PhotoContextInfo photoContext;
}
