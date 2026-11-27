# Monitoring with Prometheus and Grafana

This project exposes Spring Boot Actuator metrics in Prometheus format at `/actuator/prometheus` and ships an optional Docker Compose `monitoring` profile with Prometheus and Grafana pre-provisioned from repository files.

## Configuration and Secrets

- The default stack (`db` + `app`) **does not require** any monitoring variables. When `METRICS_USERNAME` or `METRICS_PASSWORD` is unset or blank, `/actuator/prometheus` is closed (`401` for all requests).
- To enable the monitoring profile, copy `.env.example` to `.env` and set strong values for:
  - `METRICS_USERNAME` — HTTP Basic username expected on `/actuator/prometheus`
  - `METRICS_PASSWORD` — HTTP Basic password passed to Prometheus via the Compose secret `/run/secrets/metrics_password` (`password_file`)
  - `GRAFANA_ADMIN_USER` — Grafana admin login username
  - `GRAFANA_ADMIN_PASSWORD` — Grafana admin login password

## Commands

Start **only** the default application stack (Prometheus and Grafana are not started):

```bash
docker compose up --build
```

Start the application stack **with** Prometheus and Grafana:

```bash
docker compose --profile monitoring up --build
```

Verify `/actuator/prometheus` authentication manually:

```bash
# Anonymous request must return 401
curl -i http://localhost:8080/actuator/prometheus

# Authenticated scrape returns 200 with Prometheus metrics
curl -u "$METRICS_USERNAME:$METRICS_PASSWORD" http://localhost:8080/actuator/prometheus | head -n 20
```

Stop everything (add `-v` to also remove `postgres_data`, `prometheus_data`, and `grafana_data`):

```bash
docker compose --profile monitoring down
```

## URLs

| Service | URL | Auth |
|---|---|---|
| Spring Boot Prometheus endpoint | `http://localhost:8080/actuator/prometheus` | HTTP Basic (`METRICS_USERNAME` / `METRICS_PASSWORD`) |
| Prometheus UI & Targets | `http://localhost:9090` (`/targets`) | None (local Compose network) |
| Grafana UI | `http://localhost:3000` | `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` |

Provisioned Grafana dashboard path: **Dashboards -> School Management -> School Management System** (`uid: school-management-overview`).

## Dashboard Panels

| Panel | PromQL Expression | What It Means |
|---|---|---|
| **Service Up** | `up{job="school-management"}` | `1` (`UP`) when Prometheus successfully scrapes `/actuator/prometheus` every 15s; `0` (`DOWN`) if the app is unreachable or scrape credentials fail. |
| **HTTP Request Rate** | `sum(rate(http_server_requests_seconds_count{job="school-management"}[1m]))` | Requests per second handled by the embedded Tomcat server across all endpoints. Sudden drops indicate traffic loss or outages; spikes show load surges. |
| **HTTP Error Rate (%)** | `100 * (sum(rate(http_server_requests_seconds_count{job="school-management",status=~"5..\|4.."}[1m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{job="school-management"}[1m])), 1e-9)` | Percentage of HTTP responses returning `5xx` (server errors) or `4xx` (client/auth errors). Any sustained `5xx` rise above `0%` indicates unhandled failures or downstream DB issues. |
| **HTTP p95 Latency** | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="school-management"}[2m]))) * 1000` | 95th-percentile response time in milliseconds computed from the `http.server.requests` histogram buckets. Shows tail latency experienced by the slowest 5% of requests. |
| **JVM Heap Memory** | `jvm_memory_used_bytes`, `jvm_memory_committed_bytes`, `jvm_memory_max_bytes` (`area="heap"`) | Current heap used, OS-committed heap, and configured max heap (`-XX:MaxRAMPercentage=75.0`). A steady upward trend approaching `Heap max` without dropping after GC signals a memory leak or undersized container limit. |
| **CPU Usage** | `process_cpu_usage * 100`, `system_cpu_usage * 100` | CPU percentage consumed by the Java process vs. total host/container CPU. Sustained high `Process CPU` alongside rising p95 latency points to CPU saturation (for example, heavy BCrypt logins or slow queries). |
| **HikariCP Connections** | `hikaricp_connections_active`, `_idle`, `_pending`, `_max` | PostgreSQL connection pool usage (`maximum-pool-size=10`). `Pending > 0` means requests are waiting for a database connection because active queries are exhausting the pool. |
