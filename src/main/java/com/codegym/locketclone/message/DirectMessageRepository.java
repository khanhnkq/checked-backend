package com.codegym.locketclone.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
    @Query("SELECT dm FROM DirectMessage dm WHERE (dm.sender.id = :userId AND dm.receiver.id = :friendId) OR (dm.sender.id = :friendId AND dm.receiver.id = :userId) ORDER BY dm.createdAt ASC")
    List<DirectMessage> findConversation(@Param("userId") UUID userId, @Param("friendId") UUID friendId);
}

