# Architecture Specification: Hexagonal / Ports & Adapters

## 1. Overview
The **Event-Driven Order Processing & Workflow Engine** is built strictly adhering to **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)** principles.

The primary goal of this architectural pattern is to isolate business logic (Domain) from technology concerns, frameworks (Spring, Hibernate, JPA), messaging brokers (Kafka), and databases (PostgreSQL).

```
+-----------------------------------------------------------------------------------+
|                               INFRASTRUCTURE                                      |
|                                                                                   |
|   +-----------------------+                    +------------------------------+   |
|   |   Driving Adapters    |                    |       Driven Adapters        |   |
|   |                       |                    |                              |   |
|   |  - OrderRestController|                    |  - OrderRepositoryAdapter    |   |
|   |  - IdempotencyFilter  |                    |    (Spring Data JPA)         |   |
|   |  - Kafka Consumers    |                    |  - KafkaProducerAdapter      |   |
|   +-----------+-----------+                    |  - OutboxRelayScheduler      |   |
|               |                                +--------------^---------------+   |
|               | calls                                         | implements        |
|   +-----------v-----------------------------------------------+---------------+   |
|   |                             APPLICATION                                   |   |
|   |                                                                           |   |
|   |   - OrderCommandService                    - OrderQueryService            |   |
|   |   - DTOs (CreateOrderCommand, OrderResponseDto)                           |   |
|   +-----------+-----------------------------------------------+---------------+   |
|               |                                               |                   |
|               | calls                                         | uses              |
|   +-----------v-----------------------------------------------+---------------+   |
|   |                                DOMAIN                                     |   |
|   |                                                                           |   |
|   |   +---------------------+   +---------------------+   +---------------+   |   |
|   |   |    Driving Ports    |   |     Aggregates      |   | Driven Ports  |   |   |
|   |   |  (Inbound / UseCase)|   |  - Order (Root)     |   | (Outbound SPI)|   |   |
|   |   | - CreateOrderUseCase|   |  - OrderItem        |   | - OrderRepo   |   |   |
|   |   | - ProcessPayment    |   |  - Money, OrderId   |   | - EventPub    |   |   |
|   |   | - CancelOrderUseCase|   |  - OrderState       |   | - Idempotency |   |   |
|   |   +---------------------+   +---------------------+   +---------------+   |   |
|   |                                                                           |   |
|   +---------------------------------------------------------------------------+   |
+-----------------------------------------------------------------------------------+
```

## 2. Layer Isolation Rules

### 2.1 Domain Layer (`com.engine.order.domain`)
- **Zero Framework Imports**: No `org.springframework.*`, `jakarta.persistence.*`, `org.hibernate.*`, or any third-party framework classes.
- **Java 21 Features**: Uses `record` for Value Objects, `sealed interface` for domain events and state definitions, and exhaustive `switch` pattern matching.
- **Invariants**: Aggregates guard their own state transitions. Invalid transitions result in `InvalidStateTransitionException`.
- **Domain Events**: Produced as part of state mutation and recorded on the aggregate root until published.

### 2.2 Application Layer (`com.engine.order.application`)
- Contains Application Services coordinating use cases: `OrderCommandService` and `OrderQueryService`.
- Orchestrates transaction boundaries, calls inbound use cases, interacts with driven ports (`OrderRepositoryPort`, `EventPublisherPort`), and maps domain aggregates to DTOs.
- Does NOT depend on any infrastructure classes.

### 2.3 Infrastructure Layer (`com.engine.order.infrastructure`)
- Adapters in: REST controllers (`OrderRestController`), RFC 7807 Exception Handlers (`GlobalExceptionHandler`), Servlet filters (`IdempotencyFilter`).
- Adapters out: Persistence via Spring Data JPA (`OrderRepositoryAdapter`, `SpringDataOrderRepository`), Outbox Relay workers, Kafka Producers.
- Configuration: OpenAPI Swagger docs, Spring bean factories.

## 3. Enforcement
Architecture rules are continuously verified and enforced via **ArchUnit** test suites (`HexagonalArchitectureArchUnitTest`). Any violation causes a build failure.
