package com.codegym.locketclone.photo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    @Query("SELECT p FROM Photo p WHERE p.sender.id = :userId " +
            "OR p.sender.id IN (SELECT f.friend.id FROM Friendship f WHERE f.user.id = :userId AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED) " +
            "OR p.sender.id IN (SELECT f.user.id FROM Friendship f WHERE f.friend.id = :userId AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED) " +
            "ORDER BY p.createdAt DESC")
    Page<Photo> findFeedPhotos(@Param("userId") UUID userId, Pageable pageable);

    Page<Photo> findBySenderId(UUID senderId, Pageable pageable);

    long countBySenderId(UUID senderId);
}