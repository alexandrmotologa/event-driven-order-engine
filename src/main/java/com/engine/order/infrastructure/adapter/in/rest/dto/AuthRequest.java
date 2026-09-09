package com.engine.order.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Role is required (CUSTOMER or ADMIN)")
        String role
) {
}
