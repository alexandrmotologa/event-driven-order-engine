import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<150'], // 95% of requests should complete within 150ms
    http_req_failed: ['rate<0.01'],   // Error rate must be less than 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  // Obtain JWT token for testing
  const authPayload = JSON.stringify({
    username: 'benchmark-runner',
    role: 'ADMIN',
  });

  const authRes = http.post(`${BASE_URL}/api/v1/auth/token`, authPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(authRes, {
    'auth token issued successfully': (r) => r.status === 200,
  });

  const token = authRes.json('token');
  return { token };
}

export default function (data) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'X-Tenant-Id': `tenant-${__VU}`,
    },
  };

  // 1. Create an Order
  const createOrderPayload = JSON.stringify({
    customerId: '99999999-0000-0000-0000-000000000001',
    currency: 'USD',
    items: [
      { productSku: 'SKU-BENCHMARK-1', quantity: 2, unitPrice: 49.99 },
      { productSku: 'SKU-BENCHMARK-2', quantity: 1, unitPrice: 19.99 },
    ],
  });

  const createRes = http.post(`${BASE_URL}/api/v1/orders`, createOrderPayload, params);
  check(createRes, {
    'order created status 201 or 200': (r) => r.status === 201 || r.status === 200,
    'order has valid id': (r) => r.json('id') !== undefined,
  });

  const orderId = createRes.json('id');

  // 2. Fetch Order Details
  if (orderId) {
    const getRes = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, params);
    check(getRes, {
      'fetch order status 200': (r) => r.status === 200,
    });
  }

  // 3. Health check
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'actuator health UP': (r) => r.status === 200,
  });

  sleep(0.1);
}
