package com.codegym.locketclone.friendship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED")
    List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);

    @Query("SELECT COUNT(f) FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED")
    long countFriends(@Param("userId") UUID userId);
}