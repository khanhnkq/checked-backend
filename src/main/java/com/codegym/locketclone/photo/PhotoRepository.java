package com.codegym.locketclone.photo;

import com.codegym.locketclone.expense.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
                    SELECT DISTINCT p
                    FROM Photo p
                    JOIN FETCH p.sender
                    LEFT JOIN FETCH p.category
                    WHERE p.status <> :deletedStatus
                      AND (
                          p.sender.id = :userId
                          OR (
                              p.recipientScope = com.codegym.locketclone.photo.RecipientScope.SELECTED_FRIENDS
                              AND EXISTS (
                                  SELECT 1
                                  FROM PhotoRecipient pr
                                  WHERE pr.photo = p
                                    AND pr.recipient.id = :userId
                              )
                          )
                          OR (
                              p.recipientScope = com.codegym.locketclone.photo.RecipientScope.ALL_FRIENDS
                              AND EXISTS (
                                  SELECT 1
                                  FROM Friendship f
                                  WHERE ((f.user.id = p.sender.id AND f.friend.id = :userId)
                                      OR (f.user.id = :userId AND f.friend.id = p.sender.id))
                                    AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
                              )
                          )
                      )
                    ORDER BY p.createdAt DESC
                    """
    )
    Slice<Photo> findFeedPhotos(@Param("userId") UUID userId,
                                @Param("deletedStatus") PhotoStatus deletedStatus,
                                Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM Photo p
                    JOIN FETCH p.sender
                    LEFT JOIN FETCH p.category
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND (
                          p.sender.id = :recipientId
                          OR (
                              p.recipientScope = com.codegym.locketclone.photo.RecipientScope.SELECTED_FRIENDS
                              AND EXISTS (
                                  SELECT 1
                                  FROM PhotoRecipient pr
                                  WHERE pr.photo = p
                                    AND pr.recipient.id = :recipientId
                              )
                          )
                          OR (
                              p.recipientScope = com.codegym.locketclone.photo.RecipientScope.ALL_FRIENDS
                              AND EXISTS (
                                  SELECT 1
                                  FROM Friendship f
                                  WHERE ((f.user.id = p.sender.id AND f.friend.id = :recipientId)
                                      OR (f.user.id = :recipientId AND f.friend.id = p.sender.id))
                                    AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
                              )
                          )
                      )
                    ORDER BY p.createdAt DESC
                    """
    )
    Slice<Photo> findFeedPhotosBySender(@Param("recipientId") UUID recipientId,
                                        @Param("senderId") UUID senderId,
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
            WHERE p.id = :photoId
              AND p.status <> :deletedStatus
              AND (
                    p.sender.id = :userId
                    OR (
                        p.recipientScope = com.codegym.locketclone.photo.RecipientScope.SELECTED_FRIENDS
                        AND EXISTS (
                            SELECT 1
                            FROM PhotoRecipient pr
                            WHERE pr.photo = p
                              AND pr.recipient.id = :userId
                        )
                    )
                    OR (
                        p.recipientScope = com.codegym.locketclone.photo.RecipientScope.ALL_FRIENDS
                        AND EXISTS (
                            SELECT 1
                            FROM Friendship f
                            WHERE ((f.user.id = p.sender.id AND f.friend.id = :userId)
                                OR (f.user.id = :userId AND f.friend.id = p.sender.id))
                              AND f.status = com.codegym.locketclone.friendship.FriendshipStatus.ACCEPTED
                        )
                    )
              )
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

    // Income/Cashflow support
    @Query(
            value = """
                    SELECT p
                    FROM Photo p
                    LEFT JOIN FETCH p.category
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND p.amount IS NOT NULL
                      AND p.amount > 0
                      AND p.transactionType = :transactionType
                      AND p.occurredAt >= :fromDate
                      AND p.occurredAt < :toDate
                    ORDER BY p.occurredAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Photo p
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND p.amount IS NOT NULL
                      AND p.amount > 0
                      AND p.transactionType = :transactionType
                      AND p.occurredAt >= :fromDate
                      AND p.occurredAt < :toDate
                    """
    )
    Page<Photo> findTransactionPhotosBySenderAndMonth(@Param("senderId") UUID senderId,
                                                      @Param("deletedStatus") PhotoStatus deletedStatus,
                                                      @Param("transactionType") TransactionType transactionType,
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
              AND p.transactionType = :transactionType
              AND p.occurredAt >= :fromDate
              AND p.occurredAt < :toDate
            """)
    BigDecimal sumTransactionAmountBySenderAndMonth(@Param("senderId") UUID senderId,
                                                    @Param("deletedStatus") PhotoStatus deletedStatus,
                                                    @Param("transactionType") TransactionType transactionType,
                                                    @Param("fromDate") LocalDateTime fromDate,
                                                    @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT p.transactionType, COALESCE(SUM(p.amount), 0)
            FROM Photo p
            WHERE p.sender.id = :senderId
              AND p.status <> :deletedStatus
              AND p.amount IS NOT NULL
              AND p.amount > 0
              AND p.occurredAt >= :fromDate
              AND p.occurredAt < :toDate
            GROUP BY p.transactionType
            """)
    List<Object[]> summarizeTransactionTotalsByTypeAndMonth(@Param("senderId") UUID senderId,
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
              AND p.transactionType = :transactionType
              AND p.occurredAt >= :fromDate
              AND p.occurredAt < :toDate
            GROUP BY p.category.id, p.category.name
            ORDER BY COALESCE(SUM(p.amount), 0) DESC
            """)
    List<Object[]> summarizeTransactionByCategory(@Param("senderId") UUID senderId,
                                                  @Param("deletedStatus") PhotoStatus deletedStatus,
                                                  @Param("transactionType") TransactionType transactionType,
                                                  @Param("fromDate") LocalDateTime fromDate,
                                                  @Param("toDate") LocalDateTime toDate);

    @Query(
            value = """
                    SELECT p
                    FROM Photo p
                    LEFT JOIN FETCH p.category
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND p.amount IS NOT NULL
                      AND p.amount > 0
                      AND (:transactionType IS NULL OR p.transactionType = :transactionType)
                      AND p.occurredAt >= :fromDate
                      AND p.occurredAt < :toDate
                    ORDER BY p.occurredAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Photo p
                    WHERE p.sender.id = :senderId
                      AND p.status <> :deletedStatus
                      AND p.amount IS NOT NULL
                      AND p.amount > 0
                      AND (:transactionType IS NULL OR p.transactionType = :transactionType)
                      AND p.occurredAt >= :fromDate
                      AND p.occurredAt < :toDate
                    """
    )
    Page<Photo> findTransactionPhotosBySenderAndRange(@Param("senderId") UUID senderId,
                                                       @Param("deletedStatus") PhotoStatus deletedStatus,
                                                       @Param("transactionType") TransactionType transactionType,
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
              AND (:transactionType IS NULL OR p.transactionType = :transactionType)
              AND p.occurredAt >= :fromDate
              AND p.occurredAt < :toDate
            """)
    BigDecimal sumTransactionAmountBySenderInRange(@Param("senderId") UUID senderId,
                                                   @Param("deletedStatus") PhotoStatus deletedStatus,
                                                   @Param("transactionType") TransactionType transactionType,
                                                   @Param("fromDate") LocalDateTime fromDate,
                                                   @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT p.category.id, COALESCE(p.category.name, 'Uncategorized'), COALESCE(SUM(p.amount), 0)
            FROM Photo p
            WHERE p.sender.id = :senderId
              AND p.status <> :deletedStatus
              AND p.amount IS NOT NULL
              AND p.amount > 0
              AND (:transactionType IS NULL OR p.transactionType = :transactionType)
              AND p.occurredAt >= :fromDate
              AND p.occurredAt < :toDate
            GROUP BY p.category.id, p.category.name
            ORDER BY COALESCE(SUM(p.amount), 0) DESC
            """)
    List<Object[]> summarizeTransactionByCategoryInRange(@Param("senderId") UUID senderId,
                                                         @Param("deletedStatus") PhotoStatus deletedStatus,
                                                         @Param("transactionType") TransactionType transactionType,
                                                         @Param("fromDate") LocalDateTime fromDate,
                                                         @Param("toDate") LocalDateTime toDate);

    @Query("""
            SELECT 
                EXTRACT(MONTH FROM p.occurredAt),
                p.transactionType,
                COALESCE(SUM(p.amount), 0)
            FROM Photo p
            WHERE p.sender.id = :senderId
              AND p.status <> :deletedStatus
              AND p.amount IS NOT NULL
              AND p.amount > 0
              AND p.occurredAt >= :fromDate
              AND p.occurredAt < :toDate
            GROUP BY EXTRACT(MONTH FROM p.occurredAt), p.transactionType
            """)
    List<Object[]> summarizeMonthlyTransactionsForYear(@Param("senderId") UUID senderId,
                                                      @Param("deletedStatus") PhotoStatus deletedStatus,
                                                      @Param("fromDate") LocalDateTime fromDate,
                                                      @Param("toDate") LocalDateTime toDate);

    long countBySenderId(UUID senderId);
}