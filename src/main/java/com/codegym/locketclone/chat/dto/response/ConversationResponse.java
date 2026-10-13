package com.codegym.locketclone.chat.dto.response;

import com.codegym.locketclone.chat.document.LastMessageInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private String id;
    private FriendSummaryResponse friend;
    private LastMessageInfo lastMessage;
    private long unreadCount;
    private Instant createdAt;
    private Instant updatedAt;
}
