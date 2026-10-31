# Observability (Epic 11: ST-25 .. ST-29)

| Need | Tool | URL | Always on? |
|---|---|---|---|
| Metrics | Prometheus | http://localhost:9090 | yes |
| Dashboards | Grafana (login `admin` / `admin`, change in `.env`) | http://localhost:3000 | yes |
| Distributed traces | Jaeger | http://localhost:16686 | yes |
| Log search | Kibana (Elasticsearch + Logstash) | http://localhost:5601 | `elk` profile |
| Structured logs | JSON lines on every container's stdout | `docker compose logs <service>` | yes |

## ST-25 Structured logging
In Docker every service writes **one JSON object per line** (`logback-spring.xml`, logstash-logback-encoder):
`@timestamp, level, logger_name, message, service, traceId, spanId, correlationId, stack_trace`.
Want readable text instead? Put `TICKETHUB_LOG_APPENDER=CONSOLE` in `.env` and `docker compose up -d`.
Running a service outside Docker prints the normal text format with `[service,traceId,correlationId]`.

## ST-26 Centralised logs with ELK
1. In `.env`: `TICKETHUB_PROFILES=docker,elk`
2. `docker compose --profile elk up -d`   (needs about 3 GB of free RAM)
3. Services ship their JSON logs to Logstash over TCP (port 5000, internal) -> Elasticsearch -> Kibana.
4. Open Kibana -> **Discover**; the data view `tickethub-logs-*` is created automatically by `kibana-setup`.
5. Useful searches: `service:"booking-service" and level:"ERROR"`, `correlationId:"<id from an API response>"`,
   `traceId:"<id from Jaeger>"`.
Without the `elk` profile nothing is sent to Logstash and the services do not depend on it.

## ST-27 Metrics, Prometheus and Grafana
* Every service exposes `/actuator/prometheus`. Prometheus scrapes them every 15 s
  (`observability/prometheus/prometheus.yml`). The gateway exposes actuator on its own unpublished
  port `8089`, so metrics are never reachable through the public port 8080.
* Grafana is provisioned automatically with the datasources Prometheus and Jaeger and the dashboard
  **TicketHub Overview**: services up/down, request rate, p95 latency, 4xx/5xx, bookings created and seat
  conflicts (409), payments by HTTP status, circuit-breaker state, JVM heap, DB connections, Kafka lag.
* Check targets: http://localhost:9090/targets (all five should be `UP`).

## ST-28 Distributed tracing
* Micrometer Tracing + OpenTelemetry export every span to Jaeger (OTLP/HTTP). The trace context travels
  through REST calls (`traceparent` header) **and through Kafka message headers**, so one trace covers
  `gateway -> booking -> event` and `payment -> booking -> Kafka -> notification`.
* Open Jaeger -> choose service `booking-service` -> Find Traces -> open one. The same `traceId` is in
  every log line, so you can jump from a trace to the logs of each hop.
* Sampling is 100 % (`management.tracing.sampling.probability: 1.0` in `config-repo/application.yml`).
  Lower it for a busy environment.

## ST-29 Incident lab
See [INCIDENT_LAB.md](INCIDENT_LAB.md).
