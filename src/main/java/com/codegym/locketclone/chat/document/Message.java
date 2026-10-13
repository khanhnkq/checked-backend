package com.codegym.locketclone.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_messages_conversation_sent_at", def = "{'conversationId': 1, 'sentAt': -1}"),
    @CompoundIndex(name = "idx_messages_conversation_sender_read", def = "{'conversationId': 1, 'senderId': 1, 'readBy': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;

    private String conversationId;

    private UUID senderId;

    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private ReplyToInfo replyTo;

    private PhotoContextInfo photoContext;

    @Builder.Default
    private Set<UUID> readBy = new HashSet<>();

    @Builder.Default
    private Set<UUID> deletedFor = new HashSet<>();

    @CreatedDate
    private Instant sentAt;
}
