package com.codegym.locketclone.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("""
            SELECT c
            FROM Category c
            WHERE c.isActive = true
              AND (c.user IS NULL OR c.user.id = :userId)
            ORDER BY c.isDefault DESC, c.name ASC
            """)
    List<Category> findVisibleActiveCategories(@Param("userId") UUID userId);

    @Query("""
            SELECT c
            FROM Category c
            WHERE c.id = :id
              AND c.isActive = true
              AND (c.user IS NULL OR c.user.id = :userId)
            """)
    Optional<Category> findActiveVisibleById(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByUser_IdAndNameIgnoreCase(UUID userId, String name);
}

