package com.challenge.moneytransfer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String referenceId,
        String fromUsername,
        String toUsername,
        BigDecimal amount,
        String status,
        String failureReason,
        Instant createdAt
) {}
