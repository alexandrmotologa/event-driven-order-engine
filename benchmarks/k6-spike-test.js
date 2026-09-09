import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 50 },    // Warm-up to 50 VUs
    { duration: '10s', target: 500 },   // Massive spike to 500 VUs
    { duration: '20s', target: 1000 },  // Sustained spike with 1,000 concurrent Virtual Users
    { duration: '10s', target: 50 },    // Quick scale down
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],  // p95 under 300ms even under 1k concurrency
    http_req_failed: ['rate<0.05'],    // Under 5% failures during 1000 VU burst
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  const authPayload = JSON.stringify({
    username: 'spike-tester',
    role: 'ADMIN',
  });

  const authRes = http.post(`${BASE_URL}/api/v1/auth/token`, authPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  return { token: authRes.json('token') };
}

export default function (data) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
      'X-Tenant-Id': `tenant-spike-${__VU % 50}`, // distribute load across 50 tenant buckets
    },
  };

  const payload = JSON.stringify({
    customerId: '88888888-0000-0000-0000-000000000002',
    currency: 'USD',
    items: [
      { productSku: 'SPIKE-SKU-1', quantity: 1, unitPrice: 25.00 },
    ],
  });

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, params);

  check(res, {
    'request handled (200, 201, or 429)': (r) =>
      r.status === 201 || r.status === 200 || r.status === 429,
  });

  sleep(0.05);
}
