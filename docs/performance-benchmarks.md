# Performance Benchmarking & JVM Tuning: Java 21 Virtual Threads & Generational ZGC

## 1. High-Throughput Architecture

The **Event-Driven Order Processing Engine** is optimized for high-concurrency cloud workloads using modern Java 21 features:

### 1.1 Java 21 Virtual Threads (`Project Loom`)
- **Configuration**: `spring.threads.virtual.enabled=true`.
- **Elimination of Thread-Per-Core Saturation**: Traditional Spring Boot servlet applications use a bounded thread pool (typically 200 platform threads). When blocked by database I/O, Kafka acknowledgments, or downstream REST calls, platform threads sleep, causing worker pool exhaustion and context switching overhead.
- **Unbounded Lightweight Concurrency**: Virtual threads decouple Java threads from OS kernel threads. When a virtual thread parks on blocking I/O (e.g., PostgreSQL query or Kafka dispatch), its carrier thread is unmounted and immediately executes other tasks.
- **Result**: Sustained handling of 10,000+ concurrent requests with minimal OS context switching and ultra-low memory overhead (~1 KB per virtual thread vs ~1 MB per platform thread).

### 1.2 Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`)
- **Sub-Millisecond Stop-The-World (STW)**: Generational ZGC separates memory into young and old generations, performing concurrent marking, relocation, and compaction.
- **Max STW Pause Target**: `< 1ms`, regardless of whether heap size is 1 GB or 32 GB.
- **Predictable Latency**: Eliminates latency spikes and tail-latency timeouts that traditionally plagued high-throughput microservices using G1GC or ParallelGC.

---

## 2. Benchmark Suite (k6)

The `benchmarks/` directory contains load testing suites powered by **k6**:

| Test | Script | Profile | Target Metrics |
| :--- | :--- | :--- | :--- |
| **Smoke Test** | `k6-smoke-test.js` | 10 VUs, 30s duration | p95 < 150ms, 0% errors |
| **Spike Test** | `k6-spike-test.js` | 50 -> 1,000 VUs in 10s | p95 < 300ms, < 5% failures |
| **Endurance Test** | `k6-endurance-test.js` | 200 VUs sustained over 5m | p99 < 250ms, heap stable |

---

## 3. Running the Benchmarks

### Via Local k6:
```bash
k6 run --env BASE_URL=http://localhost:8080 benchmarks/k6-smoke-test.js
k6 run --env BASE_URL=http://localhost:8080 benchmarks/k6-spike-test.js
k6 run --env BASE_URL=http://localhost:8080 benchmarks/k6-endurance-test.js
```

### Via Automated Script (uses Docker if k6 is not installed locally):
```powershell
# Windows
.\benchmarks\run-benchmarks.ps1

# Linux / macOS
./benchmarks/run-benchmarks.sh
```
