package com.codegym.locketclone.expense;

import com.codegym.locketclone.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(name = "uk_budgets_user_month", columnNames = {"user_id", "month_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "month_key", nullable = false, length = 6)
    private String monthKey;

    @Column(name = "amount_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountLimit;

    @Builder.Default
    @Column(name = "alert_threshold_pct", nullable = false)
    private Integer alertThresholdPct = 80;
}