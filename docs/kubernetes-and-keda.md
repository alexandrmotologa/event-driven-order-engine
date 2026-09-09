# Cloud-Native Kubernetes Deployment & KEDA Kafka Lag Autoscaler

## 1. Executive Summary & Architecture Overview

The **Event-Driven Order Processing & Workflow Engine** is packaged as a cloud-native, production-ready Kubernetes application managed via Helm 3. It utilizes **KEDA (Kubernetes Event-driven Autoscaling)** to dynamically scale engine pod replicas based directly on Apache Kafka consumer group lag rather than traditional CPU/Memory utilization.

```
                                  [ Internet / Clients ]
                                             │
                                             ▼
                                     [ Ingress NGINX ]
                                             │
                                             ▼
                                  [ Service: order-engine ]
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
             [ Pod 1: order-engine ]                     [ Pod N: order-engine ]
             ┌─────────────────────┐                     ┌─────────────────────┐
             │ Java 21 (Virtual Th)│                     │ Java 21 (Virtual Th)│
             │ Generational ZGC    │                     │ Generational ZGC    │
             │ Liveness / Readiness│                     │ Liveness / Readiness│
             └──────────┬──────────┘                     └──────────┬──────────┘
                        │                                           │
         ┌──────────────┴──────────────┐             ┌──────────────┴──────────────┐
         ▼                             ▼             ▼                             ▼
  [ PostgreSQL DB ]            [ Apache Kafka Cluster (order.events topic) ]
         ▲                                     ▲
         │                                     │
         │                         [ KEDA Operator / Metrics Adapter ]
         │                                     │ (Polls Consumer Group Lag)
         └─────────────────────────────────────┴─ Scales Deployment (2 to 10 Replicas)
```

---

## 2. Why KEDA for Event-Driven Systems?

In standard Kubernetes deployments, the **Horizontal Pod Autoscaler (HPA)** monitors metrics such as CPU or Memory usage:
- **The Bottleneck**: With Java 21 **Virtual Threads (`fibers`)**, I/O operations (like waiting for database responses or Kafka acknowledgments) do not pin OS threads or burn CPU cycles.
- **The Failure Mode**: A backlog of 500,000 unconsumed orders in Kafka might cause almost zero CPU load while messages sit unconsumed, leaving the system under-provisioned.
- **The KEDA Solution**: KEDA connects directly to Kafka broker metadata via consumer group metrics (`order-processing-group` on `order.events`). If total consumer lag exceeds `lagThreshold: 10`, KEDA immediately triggers horizontal scale-up from 2 to up to 10 pods within seconds.

---

## 3. Helm Chart Specification (`deploy/helm/order-engine/`)

### File Structure
```
deploy/helm/order-engine/
├── Chart.yaml                     # Chart metadata (v1.0.0)
├── values.yaml                    # Default deployment parameters & thresholds
└── templates/
    ├── _helpers.tpl               # Template naming helpers
    ├── deployment.yaml            # Zero-downtime rolling update deployment
    ├── service.yaml               # ClusterIP internal service (port 8080)
    ├── configmap.yaml             # Non-sensitive environment configuration
    ├── secret.yaml                # Database passwords & security tokens
    ├── keda-scaledobject.yaml     # KEDA Kafka Lag ScaledObject
    ├── hpa.yaml                   # CPU/Memory fallback HPA
    └── ingress.yaml               # Ingress routing rules
```

### Key Configuration Parameters (`values.yaml`)

| Parameter | Default Value | Description |
| :--- | :--- | :--- |
| `replicaCount` | `2` | Baseline pod count when idle. |
| `image.repository` | `ghcr.io/alexandrmotologa/event-driven-order-engine` | Container image repository. |
| `image.tag` | `1.0.0` | Container image tag. |
| `jvm.opts` | `-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0` | JVM flags for sub-millisecond GC pauses. |
| `resources.limits.cpu` | `2000m` | Pod CPU limit (2 cores). |
| `resources.limits.memory` | `2Gi` | Pod Memory limit (2 GB). |
| `keda.enabled` | `true` | Enables KEDA Kafka consumer lag autoscaling. |
| `keda.minReplicaCount` | `2` | Minimum pods under zero or low lag. |
| `keda.maxReplicaCount` | `10` | Maximum pods under burst/spike traffic. |
| `keda.triggers[0].metadata.lagThreshold` | `"10"` | Messages of lag per pod before triggering scale-up. |
| `keda.triggers[0].metadata.consumerGroup` | `order-processing-group` | Target consumer group being monitored. |

---

## 4. Zero-Downtime Deployments & Graceful Shutdown

The Helm deployment configures enterprise lifecycle management to prevent dropped HTTP requests or interrupted Kafka batches:

### Rolling Update Strategy
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1          # Spin up 1 new pod before killing an old one
    maxUnavailable: 0    # Ensure zero capacity degradation during deployment
```

### PreStop Hook & Graceful Shutdown
```yaml
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 10"]
```
1. When a pod is scheduled for termination, Kubernetes sends `SIGTERM`.
2. The `preStop` hook delays termination by 10 seconds, allowing Kubernetes Endpoint routing and Ingress controllers to remove the pod from active traffic.
3. Spring Boot's `server.shutdown=graceful` finishes executing in-flight transactions and closes database connections cleanly.

### Actuator Health & Readiness Probes
- **Liveness Probe**: `GET /actuator/health/liveness` checks if the JVM is alive.
- **Readiness Probe**: `GET /actuator/health/readiness` checks if Kafka brokers and PostgreSQL are reachable. If database connections drop, traffic is instantly stopped to that pod.

---

## 5. Deployment Instructions

### Prerequisites
- Kubernetes cluster 1.28+ (Minikube, Kind, EKS, GKE, or AKS).
- Helm 3.12+.
- KEDA installed in the cluster:
  ```bash
  helm repo add kedacore https://kedacore.github.io/charts
  helm repo update
  helm install keda kedacore/keda --namespace keda --create-namespace
  ```

### Step 1: Install or Upgrade via Helm
```bash
# Create target namespace
kubectl create namespace order-engine

# Dry run and lint template
helm template order-engine ./deploy/helm/order-engine --namespace order-engine

# Install chart
helm install order-engine ./deploy/helm/order-engine \
  --namespace order-engine \
  --set env.datasource.url="jdbc:postgresql://postgres.database.svc.cluster.local:5432/order_db" \
  --set env.kafka.bootstrapServers="kafka-bootstrap.kafka.svc.cluster.local:9092"
```

### Step 2: Verify Resources
```bash
# Check running pods
kubectl get pods -n order-engine -l app.kubernetes.io/name=order-engine

# Check KEDA ScaledObject status
kubectl get scaledobject -n order-engine

# Watch HPA managed by KEDA
kubectl get hpa -n order-engine -w
```

### Step 3: Simulate Kafka Lag & Observe Autoscaling
Inject 5,000 orders using the k6 spike test:
```bash
k6 run --env BASE_URL=http://<INGRESS_IP> benchmarks/k6-spike-test.js
```
Watch KEDA detect the lag increase and scale pods from 2 to 10:
```bash
kubectl get pods -n order-engine -w
```
Once the consumer backlog drains to 0, KEDA observes the 300s cooldown period and safely scales the cluster back down to 2 pods.
