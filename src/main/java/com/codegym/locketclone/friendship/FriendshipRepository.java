package com.codegym.locketclone.friendship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
            SELECT f
            FROM Friendship f
            JOIN FETCH f.user
            JOIN FETCH f.friend
            WHERE (f.user.id = :userId OR f.friend.id = :userId)
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
            """)
    List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);

    @Query("SELECT COUNT(f) FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED")
    long countFriends(@Param("userId") UUID userId);

    @Query("""
            SELECT DISTINCT CASE
                WHEN f.user.id = :userId THEN f.friend.id
                ELSE f.user.id
            END
            FROM Friendship f
            WHERE (f.user.id = :userId OR f.friend.id = :userId)
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
            """)
    List<UUID> findAcceptedFriendIds(@Param("userId") UUID userId);

    @Query("""
            SELECT f FROM Friendship f
            WHERE (f.user.id = :userA AND f.friend.id = :userB)
               OR (f.user.id = :userB AND f.friend.id = :userA)
            """)
    List<Friendship> findBetweenUsers(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
            SELECT f FROM Friendship f
            JOIN FETCH f.user
            JOIN FETCH f.friend
            WHERE (f.user.id = :userA AND f.friend.id = :userB)
               OR (f.user.id = :userB AND f.friend.id = :userA)
            """)
    Optional<Friendship> findRelationBetweenUsers(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
            SELECT f FROM Friendship f
            WHERE ((f.user.id = :userA AND f.friend.id = :userB)
               OR (f.user.id = :userB AND f.friend.id = :userA))
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
            """)
    Optional<Friendship> findAcceptedBetweenUsers(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
            SELECT f FROM Friendship f
            JOIN FETCH f.user
            WHERE f.friend.id = :userId
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.PENDING
            ORDER BY f.createdAt DESC
            """)
    List<Friendship> findPendingReceivedRequests(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(f) FROM Friendship f
            WHERE f.friend.id = :userId
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.PENDING
            """)
    long countPendingReceivedRequests(@Param("userId") UUID userId);

    @Query("""
            SELECT f FROM Friendship f
            JOIN FETCH f.friend
            WHERE f.user.id = :userId
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.PENDING
            ORDER BY f.createdAt DESC
            """)
    List<Friendship> findPendingSentRequests(@Param("userId") UUID userId);

    @Query("""
            SELECT f FROM Friendship f
            JOIN FETCH f.friend
            WHERE f.user.id = :userId
              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.BLOCKED
            ORDER BY f.createdAt DESC
            """)
    List<Friendship> findBlockedUsers(@Param("userId") UUID userId);
}