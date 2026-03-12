package com.codegym.locketclone.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoReactionRepository extends JpaRepository<PhotoReaction, UUID> {
    Optional<PhotoReaction> findByPhotoIdAndUserId(UUID photoId, UUID userId);

    long countByPhotoId(UUID photoId);
}

