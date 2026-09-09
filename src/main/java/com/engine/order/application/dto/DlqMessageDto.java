package com.engine.order.application.dto;

import java.time.Instant;

public record DlqMessageDto(
        String id,
        String originalTopic,
        int partitionNum,
        long offsetNum,
        String messageKey,
        String payload,
        String exceptionClass,
        String errorMessage,
        String status,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {
}
