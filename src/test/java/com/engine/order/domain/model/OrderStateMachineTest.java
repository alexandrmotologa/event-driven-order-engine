package com.engine.order.domain.model;

import com.engine.order.domain.event.*;
import com.engine.order.domain.exception.InvalidOrderException;
import com.engine.order.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private OrderId orderId;
    private CustomerId customerId;
    private OrderItem item1;
    private OrderItem item2;

    @BeforeEach
    void setUp() {
        orderId = OrderId.random();
        customerId = CustomerId.random();
        item1 = OrderItem.of("SKU-LAPTOP", 1, Money.of(1200.00, "USD"));
        item2 = OrderItem.of("SKU-MOUSE", 2, Money.of(25.00, "USD"));
    }

    private Order createDefaultOrder() {
        return Order.create(orderId, customerId, List.of(item1, item2));
    }

    @Nested
    @DisplayName("Creation & Invariant Validation")
    class CreationTests {

        @Test
        @DisplayName("Order is created successfully in CREATED state with correct total and domain event")
        void shouldCreateOrderSuccessfully() {
            Order order = createDefaultOrder();

            assertThat(order.getId()).isEqualTo(orderId);
            assertThat(order.getCustomerId()).isEqualTo(customerId);
            assertThat(order.getState()).isEqualTo(OrderState.CREATED);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(Money.of(1250.00, "USD"));
            assertThat(order.getVersion()).isEqualTo(0L);
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.getUpdatedAt()).isNotNull();
            assertThat(order.getCancellationReason()).isEmpty();

            assertThat(order.getDomainEvents()).hasSize(1);
            DomainEvent firstEvent = order.getDomainEvents().getFirst();
            assertThat(firstEvent).isInstanceOf(OrderCreatedEvent.class);
            OrderCreatedEvent createdEvent = (OrderCreatedEvent) firstEvent;
            assertThat(createdEvent.orderId()).isEqualTo(orderId);
            assertThat(createdEvent.customerId()).isEqualTo(customerId);
            assertThat(createdEvent.totalAmount()).isEqualTo(Money.of(1250.00, "USD"));
        }

        @Test
        @DisplayName("Order creation fails when items list is null or empty")
        void shouldFailWhenItemsEmpty() {
            assertThatThrownBy(() -> Order.create(orderId, customerId, null))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("at least one order item");

            assertThatThrownBy(() -> Order.create(orderId, customerId, List.of()))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("at least one order item");
        }

        @Test
        @DisplayName("Order creation fails when items have mismatched currencies")
        void shouldFailWhenMismatchedCurrencies() {
            OrderItem eurItem = OrderItem.of("SKU-KEYBOARD", 1, Money.of(50.00, "EUR"));

            assertThatThrownBy(() -> Order.create(orderId, customerId, List.of(item1, eurItem)))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("same currency");
        }

        @Test
        @DisplayName("OrderItem creation fails with invalid quantities or SKUs")
        void shouldFailOrderItemWithInvalidInputs() {
            assertThatThrownBy(() -> OrderItem.of("", 1, Money.of(10.00, "USD")))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("Product SKU");

            assertThatThrownBy(() -> OrderItem.of("SKU-1", 0, Money.of(10.00, "USD")))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("quantity must be positive");

            assertThatThrownBy(() -> OrderItem.of("SKU-1", -3, Money.of(10.00, "USD")))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("quantity must be positive");
        }
    }

    @Nested
    @DisplayName("Happy Path Lifecycle Transitions")
    class HappyPathTests {

        @Test
        @DisplayName("Order completes full lifecycle: CREATED -> VALIDATED -> PAYMENT_PENDING -> PAID -> INVENTORY_ALLOCATED -> COMPLETED")
        void shouldCompleteFullLifecycleSuccessfully() {
            Order order = createDefaultOrder();
            assertThat(order.getState()).isEqualTo(OrderState.CREATED);

            // Step 1: Validate
            Order validated = order.validate();
            assertThat(validated.getState()).isEqualTo(OrderState.VALIDATED);
            assertThat(validated.getDomainEvents()).hasSize(2);
            assertThat(validated.getDomainEvents().get(1)).isInstanceOf(OrderValidatedEvent.class);

            // Step 2: Initiate Payment
            Order paymentPending = validated.initiatePayment();
            assertThat(paymentPending.getState()).isEqualTo(OrderState.PAYMENT_PENDING);
            assertThat(paymentPending.getDomainEvents()).hasSize(3);
            assertThat(paymentPending.getDomainEvents().get(2)).isInstanceOf(OrderPaymentPendingEvent.class);

            // Step 3: Mark Paid
            String txId = "TX-100293";
            Order paid = paymentPending.markPaid(txId);
            assertThat(paid.getState()).isEqualTo(OrderState.PAID);
            assertThat(paid.getDomainEvents()).hasSize(4);
            assertThat(paid.getDomainEvents().get(3)).isInstanceOf(OrderPaidEvent.class);
            assertThat(((OrderPaidEvent) paid.getDomainEvents().get(3)).transactionId()).isEqualTo(txId);

            // Step 4: Allocate Inventory
            Order allocated = paid.allocateInventory();
            assertThat(allocated.getState()).isEqualTo(OrderState.INVENTORY_ALLOCATED);
            assertThat(allocated.getDomainEvents()).hasSize(5);
            assertThat(allocated.getDomainEvents().get(4)).isInstanceOf(OrderInventoryAllocatedEvent.class);

            // Step 5: Complete Order
            Order completed = allocated.complete();
            assertThat(completed.getState()).isEqualTo(OrderState.COMPLETED);
            assertThat(completed.getDomainEvents()).hasSize(6);
            assertThat(completed.getDomainEvents().get(5)).isInstanceOf(OrderCompletedEvent.class);

            // Step 6: Clear Events
            Order cleared = completed.clearDomainEvents();
            assertThat(cleared.getDomainEvents()).isEmpty();
            assertThat(cleared.getState()).isEqualTo(OrderState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Cancellation & Refund Transitions")
    class CancellationAndRefundTests {

        @Test
        @DisplayName("Order can be cancelled from CREATED state")
        void shouldCancelFromCreated() {
            Order order = createDefaultOrder();
            Order cancelled = order.cancel("Customer changed mind");

            assertThat(cancelled.getState()).isEqualTo(OrderState.CANCELLED);
            assertThat(cancelled.getCancellationReason()).contains("Customer changed mind");
            assertThat(cancelled.getDomainEvents().getLast()).isInstanceOf(OrderCancelledEvent.class);
        }

        @Test
        @DisplayName("Order can be cancelled from VALIDATED state")
        void shouldCancelFromValidated() {
            Order order = createDefaultOrder().validate();
            Order cancelled = order.cancel("Out of stock check failure");

            assertThat(cancelled.getState()).isEqualTo(OrderState.CANCELLED);
            assertThat(cancelled.getCancellationReason()).contains("Out of stock check failure");
            assertThat(cancelled.getDomainEvents().getLast()).isInstanceOf(OrderCancelledEvent.class);
        }

        @Test
        @DisplayName("Order can be cancelled from PAYMENT_PENDING state")
        void shouldCancelFromPaymentPending() {
            Order order = createDefaultOrder().validate().initiatePayment();
            Order cancelled = order.cancel("Payment gateway timeout");

            assertThat(cancelled.getState()).isEqualTo(OrderState.CANCELLED);
            assertThat(cancelled.getCancellationReason()).contains("Payment gateway timeout");
            assertThat(cancelled.getDomainEvents().getLast()).isInstanceOf(OrderCancelledEvent.class);
        }

        @Test
        @DisplayName("Order in PAID state can be refunded")
        void shouldRefundFromPaid() {
            Order order = createDefaultOrder().validate().initiatePayment().markPaid("TX-999");
            Order refunded = order.refund("Customer returned product");

            assertThat(refunded.getState()).isEqualTo(OrderState.REFUNDED);
            assertThat(refunded.getCancellationReason()).contains("Customer returned product");
            assertThat(refunded.getDomainEvents().getLast()).isInstanceOf(OrderRefundedEvent.class);
        }

        @Test
        @DisplayName("Cancelling with null or blank reason throws InvalidOrderException")
        void shouldFailCancellationWithBlankReason() {
            Order order = createDefaultOrder();

            assertThatThrownBy(() -> order.cancel(null))
                    .isInstanceOf(InvalidOrderException.class);

            assertThatThrownBy(() -> order.cancel("   "))
                    .isInstanceOf(InvalidOrderException.class);
        }

        @Test
        @DisplayName("Refunding with null or blank reason throws InvalidOrderException")
        void shouldFailRefundWithBlankReason() {
            Order order = createDefaultOrder().validate().initiatePayment().markPaid("TX-123");

            assertThatThrownBy(() -> order.refund(null))
                    .isInstanceOf(InvalidOrderException.class);

            assertThatThrownBy(() -> order.refund(""))
                    .isInstanceOf(InvalidOrderException.class);
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions (Negative Testing)")
    class InvalidTransitionTests {

        @Test
        @DisplayName("CREATED cannot transition directly to illegal states")
        void shouldRejectInvalidJumpsFromCreated() {
            Order order = createDefaultOrder();

            assertThatThrownBy(order::initiatePayment)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.markPaid("TX-1"))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::allocateInventory)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.refund("Reason"))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("VALIDATED cannot transition directly to illegal states")
        void shouldRejectInvalidJumpsFromValidated() {
            Order order = createDefaultOrder().validate();

            assertThatThrownBy(order::validate)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.markPaid("TX-1"))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::allocateInventory)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.refund("Reason"))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("PAYMENT_PENDING cannot transition directly to illegal states")
        void shouldRejectInvalidJumpsFromPaymentPending() {
            Order order = createDefaultOrder().validate().initiatePayment();

            assertThatThrownBy(order::validate)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::initiatePayment)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::allocateInventory)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.refund("Reason"))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("PAID cannot be cancelled directly without refund")
        void shouldRejectCancelFromPaid() {
            Order order = createDefaultOrder().validate().initiatePayment().markPaid("TX-1");

            assertThatThrownBy(() -> order.cancel("Cancel"))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::validate)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::initiatePayment)
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete)
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("markPaid fails with empty transactionId")
        void shouldFailMarkPaidWithEmptyTxId() {
            Order order = createDefaultOrder().validate().initiatePayment();

            assertThatThrownBy(() -> order.markPaid(null))
                    .isInstanceOf(InvalidOrderException.class);
            assertThatThrownBy(() -> order.markPaid("  "))
                    .isInstanceOf(InvalidOrderException.class);
        }

        @Test
        @DisplayName("COMPLETED order cannot transition to any other state")
        void shouldRejectTransitionsFromCompleted() {
            Order order = createDefaultOrder()
                    .validate()
                    .initiatePayment()
                    .markPaid("TX-1")
                    .allocateInventory()
                    .complete();

            assertThatThrownBy(order::validate).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::initiatePayment).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.markPaid("TX-2")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::allocateInventory).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.cancel("Cancel")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.refund("Refund")).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("CANCELLED order cannot transition to any other state")
        void shouldRejectTransitionsFromCancelled() {
            Order order = createDefaultOrder().cancel("Cancelled");

            assertThatThrownBy(order::validate).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::initiatePayment).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.markPaid("TX-1")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::allocateInventory).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.cancel("Again")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.refund("Refund")).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("REFUNDED order cannot transition to any other state")
        void shouldRejectTransitionsFromRefunded() {
            Order order = createDefaultOrder().validate().initiatePayment().markPaid("TX-1").refund("Refunded");

            assertThatThrownBy(order::validate).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::initiatePayment).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.markPaid("TX-2")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::allocateInventory).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(order::complete).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.cancel("Cancel")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> order.refund("Again")).isInstanceOf(InvalidStateTransitionException.class);
        }
    }
}
