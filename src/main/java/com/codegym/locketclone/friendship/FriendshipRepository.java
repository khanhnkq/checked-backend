package com.codegym.locketclone.friendship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {

    // Tìm tất cả mối quan hệ bạn bè của một User (cả khi là id1 hoặc id2)
    @Query("SELECT f FROM Friendship f WHERE (f.userId1 = :userId OR f.userId2 = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAllAcceptedFriends(@Param("userId") UUID userId);

    // Đếm số lượng bạn bè để kiểm tra giới hạn 20 người
    @Query("SELECT COUNT(f) FROM Friendship f WHERE (f.userId1 = :userId OR f.userId2 = :userId) AND f.status = 'ACCEPTED'")
    long countFriends(@Param("userId") UUID userId);
}