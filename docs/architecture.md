# Architecture Specification: Hexagonal / Ports & Adapters

## 1. Overview
The **Event-Driven Order Processing & Workflow Engine** is built strictly adhering to **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)** principles.

The primary goal of this architectural pattern is to isolate business logic (Domain) from technology concerns, frameworks (Spring, Hibernate, JPA), messaging brokers (Kafka), and databases (PostgreSQL).

```
+-----------------------------------------------------------------------------------+
|                               INFRASTRUCTURE                                      |
|                                                                                   |
|   +------------------------------------+  +-----------------------------------+   |
|   |          Driving Adapters          |  |          Driven Adapters          |   |
|   |                                    |  |                                   |   |
|   |  - OrderRestController             |  |  - OrderRepositoryAdapter         |   |
|   |  - OrderSummaryQueryRestController |  |  - OrderSummaryViewRepoAdapter    |   |
|   |  - OrderHistoryQueryRestController |  |  - EventStoreRepositoryAdapter   |   |
|   |  - OrderSseRestController          |  |  - OutboxEventPublisherAdapter    |   |
|   |  - AuthRestController              |  |  - OutboxRelayScheduler           |   |
|   |  - DashboardViewController         |  |  - SagaTimeoutScheduler           |   |
|   |  - JwtAuthenticationFilter         |  |  - ResilientPaymentClient         |   |
|   |  - Kafka Consumers (Projection,    |  |  - KafkaTemplate Producers        |   |
|   |    Saga Replies, Idempotent Events)|  |                                   |   |
|   +-----------------+------------------+  +-----------------^-----------------+   |
|                     |                                       |                     |
|                     | calls                                 | implements          |
|   +-----------------v---------------------------------------+-----------------+   |
|   |                             APPLICATION                                   |   |
|   |                                                                           |   |
|   |   - OrderCommandService                    - OrderQueryService            |   |
|   |   - OrderSummaryQueryService               - OrderReplayService           |   |
|   |   - OrderFulfillmentSagaManager (Saga Orchestration & Compensations)      |   |
|   |   - DTOs & Command Records                                                |   |
|   +-----------------+---------------------------------------+-----------------+   |
|                     |                                       |                     |
|                     | calls                                 | uses                |
|   +-----------------v---------------------------------------+-----------------+   |
|   |                                DOMAIN                                     |   |
|   |                                                                           |   |
|   |   +---------------------+   +---------------------+   +---------------+   |   |
|   |   |    Driving Ports    |   |     Aggregates      |   | Driven Ports  |   |   |
|   |   |  (Inbound / UseCase)|   |  - Order (Root)     |   | (Outbound SPI)|   |   |
|   |   | - CreateOrderUseCase|   |  - OrderItem        |   | - OrderRepo   |   |   |
|   |   | - ProcessPayment    |   |  - Money, OrderId   |   | - EventStore  |   |   |
|   |   | - CancelOrderUseCase|   |  - OrderState       |   | - EventPub    |   |   |
|   |   | - OrderHistoryPort  |   |  - SagaStatus       |   | - Idempotency |   |   |
|   |   +---------------------+   +---------------------+   +---------------+   |   |
|   |                                                                           |   |
|   +---------------------------------------------------------------------------+   |
+-----------------------------------------------------------------------------------+
```

---

## 2. Layer Isolation Rules

### 2.1 Domain Layer (`com.engine.order.domain`)
- **Zero Framework Imports**: No `org.springframework.*`, `jakarta.persistence.*`, `org.hibernate.*`, or any third-party framework classes.
- **Pure Java 21 Features**: Uses `record` for Value Objects, `sealed interface` for domain events and state definitions, and exhaustive `switch` pattern matching.
- **Invariants**: Aggregates guard their own state transitions. Invalid transitions result in `InvalidStateTransitionException`.
- **Domain Events**: Produced as part of state mutation and recorded on the aggregate root until published.

### 2.2 Application Layer (`com.engine.order.application`)
- Contains Application Services coordinating use cases:
  - `OrderCommandService`: Command execution (create, pay, cancel).
  - `OrderQueryService`: Direct aggregate lookup.
  - `OrderSummaryQueryService`: High-performance CQRS denormalized views.
  - `OrderReplayService`: Event folding and historical aggregate reconstruction.
  - `OrderFulfillmentSagaManager`: Distributed transaction coordinator dispatching steps and emergency compensation.
- Orchestrates transaction boundaries, interacts strictly with domain and outbound ports.
- Does NOT depend on any infrastructure classes.

### 2.3 Infrastructure Layer (`com.engine.order.infrastructure`)
- **Driving Adapters (Inbound)**:
  - REST Controllers (`OrderRestController`, `OrderSummaryQueryRestController`, `OrderHistoryQueryRestController`, `OrderSseRestController`, `AuthRestController`).
  - RFC 7807 Exception Handlers (`GlobalExceptionHandler`).
  - Security & Idempotency Filters (`JwtAuthenticationFilter`, `IdempotencyFilter`).
  - Kafka Event Consumers (`OrderEventsConsumer`, `OrderSummaryProjectionConsumer`, `InventoryReplyConsumer`, `PaymentReplyConsumer`).
- **Driven Adapters (Outbound)**:
  - Persistence via Spring Data JPA (`OrderRepositoryAdapter`, `OrderSummaryViewRepositoryAdapter`, `EventStoreRepositoryAdapter`, `SagaInstanceJpaRepository`).
  - Outbox Relay Worker (`OutboxRelayScheduler`) with `SELECT ... FOR UPDATE SKIP LOCKED`.
  - Saga Timeout Worker (`SagaTimeoutScheduler`) for detecting stuck distributed transactions.
  - Kafka Event Publishers (`OutboxEventPublisherAdapter`, `KafkaProducerAdapter`).
  - Resilience4j Fault Tolerant Clients (`ResilientPaymentClient`).

---

## 3. Distributed Patterns Implemented

1. **Transactional Outbox Pattern**:
   - Aggregate mutations and domain events are written atomically to PostgreSQL in a single database transaction.
   - Eliminates dual-write data loss between database and Kafka.
2. **Idempotent Consumer Pattern**:
   - Consumer tracks processed message IDs in the `consumed_messages` table to ensure exact-once processing semantics over at-least-once Kafka delivery.
3. **Saga Pattern (Orchestration Mode)**:
   - Centralized coordinator (`OrderFulfillmentSagaManager`) directs distributed steps across inventory and payment.
   - Detects failures and triggers backward recovery (compensating transactions such as `ReleaseInventoryCommand`).
   - Dead-Man Switch handles downstream microservice silence via timeout worker.
4. **Command Query Responsibility Segregation (CQRS)**:
   - Write model (`orders`, `order_items`) optimized for transactional integrity and ACID guarantees.
   - Read model (`order_summary_view`) optimized for high-throughput queries, updated asynchronously via Kafka projections.
5. **Event Sourcing & Audit Trail**:
   - Append-only event store (`order_event_stream`) capturing every state change with monotonic sequence numbers.
   - Replay service reconstructs aggregate state at any historical version.
6. **Server-Sent Events (SSE)**:
   - Reactive real-time notification push to frontend clients and monitoring dashboards.
7. **Debezium CDC (Change Data Capture)**:
   - Logical decoding blueprint streaming outbox records directly to Kafka with sub-millisecond latency.

---

## 4. Architectural Enforcement via ArchUnit
Architecture rules are continuously verified and enforced via **ArchUnit** test suites (`HexagonalArchitectureArchUnitTest`):
- `domainMustNotDependOnFrameworks`
- `domainMustNotDependOnOuterLayers`
- `applicationMustNotDependOnInfrastructure`
- `layeredHexagonalArchitectureRules`
- `restControllersMustResideInRestPackage`
- `repositoryAdaptersMustResideInPersistencePackage`
- `kafkaConsumersMustResideInKafkaPackage`

All 7 rules pass on every build with 0 violations.
