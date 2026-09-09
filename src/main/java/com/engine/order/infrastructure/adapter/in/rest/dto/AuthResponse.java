package com.engine.order.infrastructure.adapter.in.rest.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String username,
        String role,
        long expiresInSeconds
) {
}
