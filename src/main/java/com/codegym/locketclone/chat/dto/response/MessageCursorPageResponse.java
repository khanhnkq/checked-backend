package com.codegym.locketclone.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCursorPageResponse {
    private List<MessageResponse> messages;
    private Instant nextCursor;

    @JsonProperty("hasMore")
    private boolean hasMore;
}
