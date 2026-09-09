#!/usr/bin/env pwsh

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " Event-Driven Order Processing: Benchmark Runner  " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$baseUrl = $env:BASE_URL
if (-not $baseUrl) {
    $baseUrl = "http://localhost:8080"
}

Write-Host "Target Base URL: $baseUrl" -ForegroundColor Yellow

if (Get-Command k6 -ErrorAction SilentlyContinue) {
    Write-Host "Running Smoke Test via local k6..." -ForegroundColor Green
    k6 run --env BASE_URL=$baseUrl benchmarks/k6-smoke-test.js
} else {
    Write-Host "k6 binary not found locally on PATH. Running via Docker container grafana/k6..." -ForegroundColor Yellow
    docker run --rm -i --network="host" -v "${PWD}/benchmarks:/benchmarks" -e BASE_URL=$baseUrl grafana/k6 run /benchmarks/k6-smoke-test.js
}
