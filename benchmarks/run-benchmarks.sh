#!/usr/bin/env bash
set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=================================================="
echo " Event-Driven Order Processing: Benchmark Runner  "
echo " Target Base URL: $BASE_URL                       "
echo "=================================================="

if command -v k6 &> /dev/null; then
    echo "Running Smoke Test via local k6..."
    k6 run --env BASE_URL="$BASE_URL" benchmarks/k6-smoke-test.js
else
    echo "k6 not found on PATH. Executing via Docker (grafana/k6)..."
    docker run --rm -i --network="host" -v "$(pwd)/benchmarks:/benchmarks" -e BASE_URL="$BASE_URL" grafana/k6 run /benchmarks/k6-smoke-test.js
fi
