package com.codegym.locketclone.chat.repository;

import com.codegym.locketclone.chat.document.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);

    List<Message> findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
            String conversationId, Instant sentAt, Pageable pageable);

    long countByConversationIdAndSenderIdNotAndReadByNotContaining(
            String conversationId, UUID currentUserId, UUID currentUserIdCheck);
}
