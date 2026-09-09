# REST API Specification & Error Contracts

## 1. Base URL, Documentation & Interactive UI
All business API endpoints are versioned under `/api/v1`.

- **Base URL**: `http://localhost:8080/api/v1`
- **Interactive Live Dashboard**: `http://localhost:8080/dashboard`
- **OpenAPI Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Spec**: `http://localhost:8080/v3/api-docs`
- **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
- **Health Probes**: `http://localhost:8080/actuator/health`

---

## 2. Authentication & Authorization (RBAC)

Secured endpoints require a JWT Bearer token in the `Authorization` header:
```http
Authorization: Bearer <jwt_token>
```

### 2.1 Generate Token
- **Method**: `POST /api/v1/auth/token`
- **Public**: Yes
- **Request Body**:
```json
{
  "username": "alice",
  "role": "CUSTOMER"
}
```
*(Valid roles: `CUSTOMER` or `ADMIN`)*

- **Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "username": "alice",
  "role": "CUSTOMER",
  "expiresInSeconds": 86400
}
```

---

## 3. Order Management Endpoints

### 3.1 Create Order
- **Method**: `POST /api/v1/orders`
- **Role Required**: `ROLE_CUSTOMER` or `ROLE_ADMIN`
- **Headers**:
  - `Content-Type: application/json`
  - `Idempotency-Key: <UUID>` *(optional, prevents duplicate execution)*
- **Request Body**:
```json
{
  "customerId": "d3b07384-d113-4e08-bc8a-d1e5d76d4982",
  "currency": "USD",
  "items": [
    {
      "productSku": "PROD-TECH-001",
      "quantity": 2,
      "unitPrice": 49.99
    },
    {
      "productSku": "PROD-ACC-004",
      "quantity": 1,
      "unitPrice": 15.00
    }
  ]
}
```
- **Response (201 Created)**:
```json
{
  "id": "7b8f9e6a-b210-4497-b84e-862d665b18aa",
  "customerId": "d3b07384-d113-4e08-bc8a-d1e5d76d4982",
  "state": "CREATED",
  "currency": "USD",
  "totalAmount": 114.98,
  "items": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "productSku": "PROD-TECH-001",
      "quantity": 2,
      "unitPrice": 49.99,
      "subtotal": 99.98
    }
  ],
  "version": 0,
  "createdAt": "2026-09-10T00:00:00Z",
  "updatedAt": "2026-09-10T00:00:00Z"
}
```

---

### 3.2 Get Order By ID
- **Method**: `GET /api/v1/orders/{id}`
- **Role Required**: `ROLE_CUSTOMER` or `ROLE_ADMIN`
- **Response (200 OK)**: Full `OrderResponseDto` aggregate.
- **Response (404 Not Found)**: RFC 7807 Problem Details.

---

### 3.3 Cancel Order
- **Method**: `POST /api/v1/orders/{id}/cancel`
- **Role Required**: `ROLE_CUSTOMER` or `ROLE_ADMIN`
- **Request Body**:
```json
{
  "reason": "Customer requested cancellation before shipment"
}
```
- **Response (200 OK)**: Updated `OrderResponseDto` with `state: "CANCELLED"`.

---

### 3.4 Process Payment
- **Method**: `POST /api/v1/orders/{id}/pay`
- **Role Required**: `ROLE_CUSTOMER` or `ROLE_ADMIN`
- **Request Body**:
```json
{
  "transactionId": "TX-998822331",
  "successful": true
}
```
- **Response (200 OK)**: Updated `OrderResponseDto` with `state: "PAID"`.

---

## 4. CQRS Read Projections

### 4.1 Query Order Summaries
- **Method**: `GET /api/v1/orders/summary`
- **Role Required**: `ROLE_ADMIN`
- **Query Parameters**:
  - `customerId` *(optional)*: Filter by customer UUID
  - `status` *(optional)*: Filter by status (`CREATED`, `PAID`, `COMPLETED`, etc.)
- **Response (200 OK)**:
```json
[
  {
    "orderId": "7b8f9e6a-b210-4497-b84e-862d665b18aa",
    "customerId": "d3b07384-d113-4e08-bc8a-d1e5d76d4982",
    "status": "COMPLETED",
    "totalAmount": 114.98,
    "currency": "USD",
    "itemCount": 3,
    "createdAt": "2026-09-10T00:00:00Z",
    "updatedAt": "2026-09-10T00:00:05Z"
  }
]
```

### 4.2 Get Order Summary by ID
- **Method**: `GET /api/v1/orders/summary/{orderId}`
- **Role Required**: `ROLE_ADMIN`
- **Response (200 OK)**: Single `OrderSummaryDto`.

---

## 5. Event Sourcing & Audit History

### 5.1 Get Order Audit Trail
- **Method**: `GET /api/v1/orders/{orderId}/history`
- **Role Required**: `ROLE_ADMIN`
- **Response (200 OK)**: Immutable sequence of domain events.
```json
[
  {
    "eventId": "11111111-2222-3333-4444-555555555555",
    "orderId": "7b8f9e6a-b210-4497-b84e-862d665b18aa",
    "sequenceNumber": 1,
    "eventType": "OrderCreatedEvent",
    "payload": "{\"eventId\":\"...\",\"orderId\":{\"value\":\"...\"}}",
    "metadata": null,
    "createdAt": "2026-09-10T00:00:00Z"
  },
  {
    "eventId": "22222222-3333-4444-5555-666666666666",
    "orderId": "7b8f9e6a-b210-4497-b84e-862d665b18aa",
    "sequenceNumber": 2,
    "eventType": "OrderValidatedEvent",
    "payload": "{\"eventId\":\"...\"}",
    "metadata": null,
    "createdAt": "2026-09-10T00:00:01Z"
  }
]
```

### 5.2 Time-Travel Aggregate Replay
- **Method**: `GET /api/v1/orders/{orderId}/replay?targetVersion={version}`
- **Role Required**: `ROLE_ADMIN`
- **Description**: Reconstitutes the aggregate state by folding events up to the specified sequence version number. Automatically leverages the closest snapshot to achieve amortized $O(1)$ replay performance.
- **Response (200 OK)**: Reconstructed `OrderResponseDto` at that historical point in time.

### 5.3 Get Order Snapshots
- **Method**: `GET /api/v1/orders/{orderId}/snapshots`
- **Role Required**: `ROLE_ADMIN`
- **Description**: Returns all historical aggregate state snapshots recorded for the given order.
- **Response (200 OK)**: List of `OrderSnapshotRecord` objects.

### 5.4 Trigger Aggregate Snapshot
- **Method**: `POST /api/v1/orders/{orderId}/snapshots`
- **Role Required**: `ROLE_ADMIN`
- **Description**: Captures and persists an immediate aggregate snapshot at the current latest sequence version.
- **Response (201 Created)**: Created `OrderSnapshotRecord`.

---

## 6. Server-Sent Events (SSE) Live Streams

### 6.1 Stream Events for Specific Order
- **Method**: `GET /api/v1/orders/{orderId}/live`
- **Public / Token Query Support**: Yes
- **Format**: `text/event-stream`
- **Events Emitted**: `ORDER_CREATED`, `ORDER_VALIDATED`, `PAYMENT_PENDING`, `ORDER_PAID`, `ORDER_COMPLETED`, `ORDER_CANCELLED`.

### 6.2 Stream Global Event Feed
- **Method**: `GET /api/v1/orders/live`
- **Public / Token Query Support**: Yes
- **Format**: `text/event-stream`

---

## 7. RFC 7807 Error Responses

All error responses adhere to RFC 7807 `ProblemDetail` with standardized types:

### 401 Unauthorized (Missing or Invalid Token)
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Full authentication is required to access this resource",
  "instance": "/api/v1/orders"
}
```

### 403 Forbidden (Insufficient Role)
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied"
}
```

### 409 Conflict (Invalid State Transition)
```json
{
  "type": "https://api.engine.order.com/errors/invalid-state-transition",
  "title": "Invalid State Transition",
  "status": 409,
  "detail": "Order [7b8f9e6a-b210-4497-b84e-862d665b18aa] cannot transition from COMPLETED to CANCELLED",
  "instance": "/api/v1/orders/7b8f9e6a-b210-4497-b84e-862d665b18aa/cancel",
  "orderId": "7b8f9e6a-b210-4497-b84e-862d665b18aa",
  "fromState": "COMPLETED",
  "toState": "CANCELLED"
}
```

### 429 Too Many Requests (Rate Limit Exceeded)
Returned when a tenant exhausts their token bucket allowance:
- **Header**: `Retry-After: 1`
```json
{
  "type": "https://api.engine.order.com/errors/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded for tenant [TENANT-DEFAULT]. Standard tier allows 60 req/min.",
  "instance": "/api/v1/orders"
}
```

---

## 8. Dead Letter Queue (DLQ) Management Endpoints

### 8.1 List DLQ Messages
- **Method**: `GET /api/v1/dlq/messages?status={PENDING|REPLAYED|DISCARDED}`
- **Role Required**: `ROLE_ADMIN`
- **Response (200 OK)**:
```json
[
  {
    "id": "c1f72a4d-88b1-4cfa-a7e8-316279f104d5",
    "originalTopic": "order.events",
    "partition": 0,
    "offsetNum": 42,
    "messageKey": "7b8f9e6a-b210-4497-b84e-862d665b18aa",
    "payload": "{\"orderId\":\"7b8f9e6a-b210-4497-b84e-862d665b18aa\",\"eventType\":\"OrderCreatedEvent\"}",
    "exceptionClass": "org.springframework.dao.DataIntegrityViolationException",
    "errorMessage": "Simulated unhandled processing failure",
    "status": "PENDING",
    "retryCount": 0,
    "createdAt": "2026-09-10T00:00:00Z",
    "updatedAt": "2026-09-10T00:00:00Z"
  }
]
```

### 8.2 Redrive Message
- **Method**: `POST /api/v1/dlq/{id}/redrive`
- **Role Required**: `ROLE_ADMIN`
- **Description**: Re-publishes the poisoned message to its original Kafka topic and transitions status to `REPLAYED`.

### 8.3 Patch & Redrive Message
- **Method**: `POST /api/v1/dlq/{id}/patch-and-retry`
- **Role Required**: `ROLE_ADMIN`
- **Request Body**:
```json
{
  "patchedPayload": "{\"orderId\":\"...\",\"correctedField\":\"valid\"}"
}
```

### 8.4 Discard Message
- **Method**: `DELETE /api/v1/dlq/{id}`
- **Role Required**: `ROLE_ADMIN`
- **Description**: Marks the message as `DISCARDED`.

---

## 9. Chaos Engineering & Fault Injection Endpoints

### 9.1 Configure Fault Parameters
- **Method**: `POST /api/v1/chaos/configure`
- **Role Required**: `ROLE_ADMIN`
- **Request Body**:
```json
{
  "latencyMs": 500,
  "paymentFailureRate": 0.25,
  "simulatePaymentOutage": false
}
```

### 9.2 Retrieve Active Chaos Status
- **Method**: `GET /api/v1/chaos/status`
- **Role Required**: `ROLE_ADMIN`

### 9.3 Reset Chaos to Healthy
- **Method**: `POST /api/v1/chaos/reset`
- **Role Required**: `ROLE_ADMIN`

---

## 10. Multi-Tenancy Context
Clients may pass the `X-Tenant-Id` header to identify the calling tenant:
```http
X-Tenant-Id: TENANT-ENTERPRISE-001
```
Available tiers:
- `TENANT-DEFAULT` / Standard: 60 requests / minute
- `TENANT-ENTERPRISE-*` / Enterprise: 600 requests / minute

