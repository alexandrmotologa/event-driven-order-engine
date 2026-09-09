# REST API Specification & Error Contracts

## 1. Base URL & Versioning
All endpoints are versioned under `/api/v1`.

- Base URL: `http://localhost:8080/api/v1`
- OpenAPI Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON Spec: `http://localhost:8080/v3/api-docs`

---

## 2. Endpoints

### 2.1 Create Order
- **Method**: `POST /api/v1/orders`
- **Headers**:
  - `Content-Type: application/json`
  - `Idempotency-Key: <UUID>` *(optional but recommended)*
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
    },
    {
      "id": "b2c3d4e5-f6a1-7890-abcd-ef1234567891",
      "productSku": "PROD-ACC-004",
      "quantity": 1,
      "unitPrice": 15.00,
      "subtotal": 15.00
    }
  ],
  "version": 0,
  "createdAt": "2026-09-09T17:30:00Z",
  "updatedAt": "2026-09-09T17:30:00Z"
}
```

---

### 2.2 Get Order By ID
- **Method**: `GET /api/v1/orders/{id}`
- **Response (200 OK)**: Same payload as `OrderResponseDto`.
- **Response (404 Not Found)**: RFC 7807 Problem Details.

---

### 2.3 Cancel Order
- **Method**: `POST /api/v1/orders/{id}/cancel`
- **Request Body**:
```json
{
  "reason": "Customer requested cancellation before shipment"
}
```
- **Response (200 OK)**: Updated `OrderResponseDto` with `state: "CANCELLED"`.

---

### 2.4 Process Payment
- **Method**: `POST /api/v1/orders/{id}/pay`
- **Request Body**:
```json
{
  "transactionId": "TX-998822331",
  "successful": true
}
```
- **Response (200 OK)**: Updated `OrderResponseDto` with `state: "PAID"`.

---

## 3. RFC 7807 ProblemDetails Error Responses

All error responses strictly follow the RFC 7807 specification using Spring Boot 3's native `ProblemDetail`.

### Example: Invalid State Transition (409 Conflict)
```json
{
  "type": "https://api.engine.order.com/errors/invalid-state-transition",
  "title": "Invalid State Transition",
  "status": 409,
  "detail": "Cannot transition order 7b8f9e6a-b210-4497-b84e-862d665b18aa from COMPLETED to CANCELLED",
  "instance": "/api/v1/orders/7b8f9e6a-b210-4497-b84e-862d665b18aa/cancel",
  "timestamp": "2026-09-09T17:31:00Z"
}
```

### Example: Domain Validation Error (422 Unprocessable Entity)
```json
{
  "type": "https://api.engine.order.com/errors/domain-validation-failed",
  "title": "Domain Validation Failed",
  "status": 422,
  "detail": "Order must contain at least one order item.",
  "instance": "/api/v1/orders",
  "timestamp": "2026-09-09T17:31:00Z"
}
```

### Example: Order Not Found (404 Not Found)
```json
{
  "type": "https://api.engine.order.com/errors/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "Order with ID 7b8f9e6a-b210-4497-b84e-862d665b18aa was not found.",
  "instance": "/api/v1/orders/7b8f9e6a-b210-4497-b84e-862d665b18aa",
  "timestamp": "2026-09-09T17:31:00Z"
}
```
