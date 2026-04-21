package com.codegym.locketclone.friendship.invite;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendInviteLinkRepository extends JpaRepository<FriendInviteLink, UUID> {

    Optional<FriendInviteLink> findFirstByOwner_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID ownerUserId,
            LocalDateTime now
    );

    List<FriendInviteLink> findByOwner_IdAndRevokedAtIsNullAndExpiresAtAfter(UUID ownerUserId, LocalDateTime now);

    boolean existsByToken(String token);

    Optional<FriendInviteLink> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FriendInviteLink> findByTokenAndRevokedAtIsNull(String token);
}




