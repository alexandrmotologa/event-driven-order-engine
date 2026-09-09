package com.engine.order.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId value must not be null");
    }

    public static CustomerId random() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId fromString(String uuidString) {
        Objects.requireNonNull(uuidString, "UUID string must not be null");
        return new CustomerId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
