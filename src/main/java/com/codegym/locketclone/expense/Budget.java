package com.codegym.locketclone.expense;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {
    private UUID id;
    private UUID userId;
    private Integer month;
    private Integer year;
    private BigDecimal limitAmount;
}