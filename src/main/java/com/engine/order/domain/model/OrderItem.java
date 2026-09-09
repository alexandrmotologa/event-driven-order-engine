package com.engine.order.domain.model;

import com.engine.order.domain.exception.InvalidOrderException;

import java.util.Objects;
import java.util.UUID;

public final class OrderItem {

    private final UUID id;
    private final String productSku;
    private final int quantity;
    private final Money unitPrice;
    private final Money subtotal;

    public OrderItem(UUID id, String productSku, int quantity, Money unitPrice) {
        this.id = Objects.requireNonNull(id, "OrderItem ID must not be null");
        if (productSku == null || productSku.isBlank()) {
            throw new InvalidOrderException("Product SKU must not be null or blank");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("Order item quantity must be positive, got: " + quantity);
        }
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price must not be null");
        this.productSku = productSku.trim();
        this.quantity = quantity;
        this.subtotal = unitPrice.multiply(quantity);
    }

    public static OrderItem of(String productSku, int quantity, Money unitPrice) {
        return new OrderItem(UUID.randomUUID(), productSku, quantity, unitPrice);
    }

    public UUID getId() {
        return id;
    }

    public String getProductSku() {
        return productSku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Money getSubtotal() {
        return subtotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem orderItem)) return false;
        return Objects.equals(id, orderItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "id=" + id +
                ", productSku='" + productSku + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", subtotal=" + subtotal +
                '}';
    }
}
