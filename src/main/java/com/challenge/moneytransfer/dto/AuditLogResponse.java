package com.challenge.moneytransfer.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String actorUsername,
        String action,
        String details,
        String ipAddress,
        Instant createdAt
) {}
