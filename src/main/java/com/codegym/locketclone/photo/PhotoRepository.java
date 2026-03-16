package com.codegym.locketclone.photo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    @Query(
            value = """
                    SELECT p
                    FROM PhotoRecipient pr
                    JOIN pr.photo p
                    JOIN FETCH p.sender
                    LEFT JOIN FETCH p.category
                    WHERE pr.recipient.id = :userId
                      AND p.status <> :deletedStatus
                    ORDER BY p.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(pr.id)
                    FROM PhotoRecipient pr
                    JOIN pr.photo p
                    WHERE pr.recipient.id = :userId
                      AND p.status <> :deletedStatus
                    """
    )
    Page<Photo> findFeedPhotos(@Param("userId") UUID userId,
                               @Param("deletedStatus") PhotoStatus deletedStatus,
                               Pageable pageable);

    @Query(
            value = """
                    SELECT p
                    FROM Photo p
                    JOIN FETCH p.sender
                    LEFT JOIN FETCH p.category
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                    ORDER BY p.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Photo p
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                    """
    )
    Page<Photo> findMyPhotos(@Param("senderId") UUID senderId,
                             @Param("deletedStatus") PhotoStatus deletedStatus,
                             Pageable pageable);

    @Query("""
            SELECT DISTINCT p
            FROM Photo p
            JOIN FETCH p.sender
            LEFT JOIN FETCH p.category
            LEFT JOIN PhotoRecipient pr ON pr.photo = p AND pr.recipient.id = :userId
            WHERE p.id = :photoId
              AND p.status <> :deletedStatus
              AND (p.sender.id = :userId OR pr.id IS NOT NULL)
            """)
    Optional<Photo> findAccessiblePhotoById(@Param("photoId") UUID photoId,
                                            @Param("userId") UUID userId,
                                            @Param("deletedStatus") PhotoStatus deletedStatus);

    @Query(
            value = """
                    SELECT p
                    FROM Photo p
                    LEFT JOIN FETCH p.category
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND p.amount IS NOT NULL
                      AND p.amount > 0
                      AND COALESCE(p.takenAt, p.createdAt) >= :fromDate
                      AND COALESCE(p.takenAt, p.createdAt) < :toDate
                    ORDER BY COALESCE(p.takenAt, p.createdAt) DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Photo p
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND p.amount IS NOT NULL
                      AND p.amount > 0
                      AND COALESCE(p.takenAt, p.createdAt) >= :fromDate
                      AND COALESCE(p.takenAt, p.createdAt) < :toDate
                    """
    )
    Page<Photo> findExpensePhotosBySenderAndMonth(@Param("senderId") UUID senderId,
                                                   @Param("deletedStatus") PhotoStatus deletedStatus,
                                                   @Param("fromDate") LocalDateTime fromDate,
                                                   @Param("toDate") LocalDateTime toDate,
                                                   Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Photo p
            WHERE p.sender.id = :senderId
              AND p.status <> :deletedStatus
              AND p.amount IS NOT NULL
              AND p.amount > 0
              AND COALESCE(p.takenAt, p.createdAt) >= :fromDate
              AND COALESCE(p.takenAt, p.createdAt) < :toDate
            """)
    BigDecimal sumExpenseAmountBySenderAndMonth(@Param("senderId") UUID senderId,
                                                @Param("deletedStatus") PhotoStatus deletedStatus,
                                                @Param("fromDate") LocalDateTime fromDate,
                                                @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT p.category.id, COALESCE(p.category.name, 'Uncategorized'), COALESCE(SUM(p.amount), 0)
            FROM Photo p
            WHERE p.sender.id = :senderId
              AND p.status <> :deletedStatus
              AND p.amount IS NOT NULL
              AND p.amount > 0
              AND COALESCE(p.takenAt, p.createdAt) >= :fromDate
              AND COALESCE(p.takenAt, p.createdAt) < :toDate
            GROUP BY p.category.id, p.category.name
            ORDER BY COALESCE(SUM(p.amount), 0) DESC
            """)
    List<Object[]> summarizeExpenseByCategory(@Param("senderId") UUID senderId,
                                              @Param("deletedStatus") PhotoStatus deletedStatus,
                                              @Param("fromDate") LocalDateTime fromDate,
                                              @Param("toDate") LocalDateTime toDate);

    long countBySenderId(UUID senderId);
}