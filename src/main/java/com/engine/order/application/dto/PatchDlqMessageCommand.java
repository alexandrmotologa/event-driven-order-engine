package com.engine.order.application.dto;

import jakarta.validation.constraints.NotBlank;

public record PatchDlqMessageCommand(
        @NotBlank(message = "Updated payload cannot be empty")
        String payload
) {
}
