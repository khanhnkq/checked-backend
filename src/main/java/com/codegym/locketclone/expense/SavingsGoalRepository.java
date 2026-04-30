package com.codegym.locketclone.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {
    Optional<SavingsGoal> findByUser_IdAndMonthKey(UUID userId, String monthKey);
}

