package com.codegym.locketclone.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Boolean existsByPhoneNumber(String phoneNumber);

    Boolean existsByUsername(String username);

}