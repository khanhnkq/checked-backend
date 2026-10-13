package com.codegym.locketclone.chat.repository;

import com.codegym.locketclone.chat.document.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByPairKey(String pairKey);

    Optional<Conversation> findByParticipantIds(List<UUID> participantIds);

    List<Conversation> findByParticipantIdsContainingOrderByUpdatedAtDesc(UUID userId);
}
