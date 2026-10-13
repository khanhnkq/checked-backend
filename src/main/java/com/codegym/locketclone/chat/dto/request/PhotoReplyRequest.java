package com.codegym.locketclone.chat.dto.request;

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
public class PhotoReplyRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @Size(max = 2000, message = "Nội dung phản hồi tối đa 2000 ký tự")
    private String content;

    private String replyToMessageId;
}
