package com.codegym.locketclone.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "idx_conversations_user_updated", def = "{'participantIds': 1, 'updatedAt': -1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private String id;

    /**
     * Khóa duy nhất xác định cặp bạn bè (dạng: minUUID_maxUUID).
     * Dùng trường vô hướng String thay cho unique index trên mảng participantIds để tránh lỗi
     * E11000 duplicate key của Multikey Unique Index trong MongoDB khi 1 user chat với nhiều bạn.
     */
    @Indexed(name = "idx_conversations_pair_key_unique", unique = true)
    private String pairKey;

    @Builder.Default
    private List<UUID> participantIds = new ArrayList<>();

    private LastMessageInfo lastMessage;

    @Builder.Default
    private Map<String, Instant> readStatus = new HashMap<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
