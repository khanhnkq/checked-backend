package com.codegym.locketclone.calorie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalorieGoalRepository extends JpaRepository<CalorieGoal, UUID> {
    Optional<CalorieGoal> findByUserId(UUID userId);
}
