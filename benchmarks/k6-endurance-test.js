import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 }, // ramp up
    { duration: '4m', target: 200 },  // 4 minutes sustained 200 VUs
    { duration: '30s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<250'], // 99% of requests below 250ms
    http_req_failed: ['rate<0.01'],   // less than 1% failure rate
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  const authPayload = JSON.stringify({
    username: 'endurance-runner',
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
      'X-Tenant-Id': `tenant-endurance-${__VU % 20}`,
    },
  };

  const payload = JSON.stringify({
    customerId: '77777777-0000-0000-0000-000000000003',
    currency: 'USD',
    items: [
      { productSku: 'ENDURANCE-SKU-99', quantity: 3, unitPrice: 12.50 },
    ],
  });

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, params);

  check(res, {
    'request successful': (r) => r.status === 201 || r.status === 200,
  });

  sleep(0.1);
}
