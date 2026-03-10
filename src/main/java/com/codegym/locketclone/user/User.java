package com.codegym.locketclone.user;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "users")
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String password;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // Chỉ ghi lúc mới tạo (INSERT), không bị sửa khi (UPDATE)

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private String fcmToken;
}
