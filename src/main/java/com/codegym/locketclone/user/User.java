package com.codegym.locketclone.user;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

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

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Builder.Default
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "otp_code", length = 6)
    private String otpCode;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @Builder.Default
    @Column(name = "is_gold_member", nullable = false)
    private Boolean isGoldMember = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    public Boolean getProfileCompleted() {
        return StringUtils.hasText(username) && StringUtils.hasText(firstName) && StringUtils.hasText(lastName);
    }

    @Transient
    public String getDisplayName() {
        boolean hasFirstName = StringUtils.hasText(firstName);
        boolean hasLastName = StringUtils.hasText(lastName);

        if (hasFirstName && hasLastName) {
            return firstName.trim() + " " + lastName.trim();
        }
        if (hasFirstName) {
            return firstName.trim();
        }
        if (hasLastName) {
            return lastName.trim();
        }
        return username;
    }
}
