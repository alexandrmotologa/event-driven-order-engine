# Event-Driven Order Processing & Workflow Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Architecture](https://img.shields.io/badge/Architecture-Hexagonal%20%2F%20Clean-blue.svg)](docs/architecture.md)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-Enforced-purple.svg)](https://www.archunit.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

An enterprise-grade, high-throughput, fault-tolerant **Event-Driven Order Processing & Workflow Engine** engineered in **Java 21 LTS** and **Spring Boot 3.3+**. 

The system solves mission-critical distributed systems challenges: strict transactional consistency, zero dual-write message loss (Transactional Outbox Pattern), distributed transaction compensation (Saga Orchestration), and end-to-end observability.

---

## 🏛 Architectural Blueprint

Built with strict **Hexagonal Architecture (Ports and Adapters)** and **Domain-Driven Design (DDD)**:

```
com.engine.order/
├── domain/                         # Pure Java 21: ZERO framework/JPA/Spring imports
│   ├── model/                      # Aggregate Root (Order), Entities, Value Objects
│   ├── event/                      # Sealed Domain Events
│   ├── exception/                  # Domain-specific Exceptions
│   └── port/                       # Driving (in) & Driven (out) Interfaces
├── application/                    # Application Services & DTOs
│   ├── service/                    # OrderCommandService, OrderQueryService
│   └── dto/                        # Command & Response Records
└── infrastructure/                 # Concrete Adapters & Configuration
    ├── adapter/
    │   ├── in/rest/                # REST Controllers, RFC 7807 Exception Handlers
    │   └── out/persistence/        # JPA Entities, Spring Data, Mappers, Adapters
    ├── config/                     # OpenAPI, Domain Beans
    └── security/                   # Idempotency Filter
```

---

## 🔄 Deterministic Domain State Machine

The order lifecycle transitions through well-defined, immutable states enforced by domain invariants:

```mermaid
stateDiagram-v2
    [*] --> CREATED: create()
    CREATED --> VALIDATED: validate()
    VALIDATED --> PAYMENT_PENDING: initiatePayment()
    PAYMENT_PENDING --> PAID: markPaid()
    PAID --> INVENTORY_ALLOCATED: allocateInventory()
    INVENTORY_ALLOCATED --> COMPLETED: complete()
    
    CREATED --> CANCELLED: cancel()
    VALIDATED --> CANCELLED: cancel()
    PAYMENT_PENDING --> CANCELLED: cancel()
    PAID --> REFUNDED: refund()
    
    COMPLETED --> [*]
    CANCELLED --> [*]
    REFUNDED --> [*]
```

---

## 🚀 Features by Level

- [x] **Level 1: Pure Hexagonal Core & Deterministic State Machine**
  - Pure Java 21 domain aggregate with zero third-party dependencies.
  - Invariant validation (non-empty items, monetary consistency, currency matching).
  - Driving Ports (`CreateOrderUseCase`, `ProcessPaymentUseCase`, `CancelOrderUseCase`, `GetOrderQuery`).
  - Driven Ports (`OrderRepositoryPort`, `EventPublisherPort`, `IdempotencyStorePort`).
  - PostgreSQL schema with Flyway database migration (`V1__init_order_schema.sql`).
  - JPA persistence entities with Optimistic Locking (`@Version`).
  - REST API (`/api/v1/orders`) with RFC 7807 `ProblemDetail` errors and `Idempotency-Key` filter.
  - Quality Gate: Comprehensive JUnit 5 + AssertJ unit tests and strict ArchUnit architectural enforcement.
- [x] **Level 2: Asynchronous Event-Driven & Transactional Outbox Pattern**
  - Elimination of dual-write loss via PostgreSQL `outbox_messages` table and Flyway `V2__init_outbox_and_idempotent_consumer.sql`.
  - Atomically writes domain events in the same database transaction as the aggregate state mutation.
  - `OutboxRelayScheduler` worker polling pending events with `SELECT ... FOR UPDATE SKIP LOCKED` (safe for multi-instance scaling).
  - Idempotent Apache Kafka producer (`acks=all`, `enable.idempotence=true`, `retries=3`) publishing to `order.events`.
  - Idempotent Consumer Pattern deduplicating incoming events via `consumed_messages` table.
  - Complete `docker-compose.yml` with PostgreSQL 16, Apache Kafka (KRaft mode), and Kafdrop.
  - Quality Gate: Integration tests with embedded Kafka verifying atomic outbox insert, relay dispatch, and consumer deduplication.
- [ ] **Level 3: Distributed Transactions & The Saga Pattern** *(Planned)*
- [ ] **Level 4: Enterprise Production-Ready (Observability, CQRS & Resilience)** *(Planned)*

---

## 🛠 Tech Stack

- **Language**: Java 21 LTS (Virtual Threads, Records, Sealed Interfaces, Pattern Matching)
- **Framework**: Spring Boot 3.3+
- **Database**: PostgreSQL 16+ (with Flyway migrations)
- **Architecture Enforcement**: ArchUnit 1.3.0
- **Documentation**: SpringDoc OpenAPI 2.6.0 / Swagger UI
- **Testing**: JUnit 5, AssertJ, Mockito, Testcontainers

---

## ⚡ Getting Started

### Prerequisites
- JDK 21+ installed and configured on `PATH`
- Maven 3.9+
- PostgreSQL 14+ (or Docker)

### Build and Run Tests
```bash
mvn clean test
```

### Run the Application
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI Spec: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 📚 Documentation
- [Master Project Plan](docs/master_project_plan_java_event_driven.md)
- [Hexagonal Architecture & Boundaries](docs/architecture.md)
- [Domain State Machine Specification](docs/state-machine.md)
- [REST API Specification](docs/api-spec.md)
