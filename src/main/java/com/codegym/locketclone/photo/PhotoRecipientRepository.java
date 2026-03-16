package com.codegym.locketclone.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PhotoRecipientRepository extends JpaRepository<PhotoRecipient, UUID> {
}

