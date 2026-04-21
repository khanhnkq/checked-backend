package com.codegym.locketclone.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoReactionRepository extends JpaRepository<PhotoReaction, UUID> {
    Optional<PhotoReaction> findByPhotoIdAndUserId(UUID photoId, UUID userId);

    long countByPhotoId(UUID photoId);

    void deleteByPhotoIdAndUserId(UUID photoId, UUID userId);

    @Query("""
            SELECT pr.reactionType, COUNT(pr)
            FROM PhotoReaction pr
            WHERE pr.photo.id = :photoId
            GROUP BY pr.reactionType
            """)
    List<Object[]> summarizeByPhotoId(@Param("photoId") UUID photoId);
}

