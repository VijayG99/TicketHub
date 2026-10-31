# Phase 2 runbooks

All commands run from the project root in Git Bash (Windows) or any Linux/macOS shell.

## S0: Inspecting Kafka
* Kafka UI: http://localhost:8090 (always on now). Look at topics `booking-confirmed`, `payment-events`, `booking-events`,
  and the generated `*-retry-0/1/2` and `*-dlt` topics. Under *Consumers* find `notification-service-group`, `booking-service-group`, `payment-service-group`.
* Words: **partition** = one ordered log inside a topic; **offset** = position of a message in a partition;
  **consumer group** = services sharing the work of a topic; **lag** = latest offset minus the group's committed offset.
* CLI: `bash scripts/phase2-lab.sh s0-inspect`.

## Outbox troubleshooting (S3)
```
docker compose exec -T booking-db  psql -U tickethub -d booking_db  -c "select event_type, attempts, created_at from outbox_events where published_at is null order by seq"
docker compose exec -T payment-db  psql -U tickethub -d payment_db  -c "select event_type, attempts, created_at from outbox_events where published_at is null order by seq"
```
Rows that stay unpublished = Kafka unreachable or the relay failing (`docker compose logs booking-service | grep -i outbox`). Prometheus metric: `tickethub_outbox_unpublished`.

## Dead letters (S1)
```
GET  /notifications/dead-letters?status=PARKED          (ADMIN token)
POST /notifications/dead-letters/{id}/replay            body optional: {"payload":"<corrected JSON as a string>"}
```
Order of work: read `error`, fix the data (or the code), replay. Replay publishes to `booking-confirmed` again; the inbox makes it safe.
Booking and Payment dead letters are logged only (`DEAD LETTER` in their logs); use reconciliation (S8) or republish by hand with `produce` in `phase2-lab.sh`.

## Consumer lag (S11)
1. Burst: `N=200 bash scripts/phase2-lab.sh s11-lag`.
2. Make the consumer slow: in `config-server/src/main/resources/config-repo/notification-service.yml` set `notification.simulated-delay-ms: 1000`, then
   `docker compose up -d --build config-server notification-service`.
3. Watch lag: Kafka UI -> Consumers, or `kafka-consumer-groups.sh --describe --group notification-service-group`, or Prometheus `kafka_consumer_fetch_manager_records_lag_max`
   (Prometheus http://localhost:9090/alerts shows `ConsumerLagHigh`).
4. Scale: `docker compose up -d --scale notification-service=3` and watch lag drain faster.
5. **Why a 4th consumer does nothing:** the topic has 3 partitions and one partition goes to at most one consumer of a group, so the 4th instance sits idle. More partitions are needed to scale further (and increasing partitions changes key-to-partition mapping for new messages).
6. Reset `simulated-delay-ms` to 0 afterwards.

## Replay (S12): safe procedure
1. **Check retention first.** Kafka keeps data for `log.retention.hours` (default 168 = 7 days). Older events cannot be replayed.
   `docker compose exec kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9092 --describe --entity-type topics --entity-name booking-confirmed --all | grep retention`
2. Start the replay instance: `docker compose --profile replay up -d --build notification-replay-db notification-replay`.
   It uses **a new consumer group** (`notification-replay-group`, reads from the earliest offset), **its own empty database**, is **not registered in Eureka**, and has `notification.replay-mode=true` so records are written as `REPLAYED` and **no email is sent**.
3. Verify: `select status, count(*) from notifications group by status` in `notification-replay-db` shows only `REPLAYED`; MailHog count unchanged.
4. To *restore* the real service from a replay, copy the rebuilt rows over (or point the real service at the rebuilt database) and mark them as already `SENT` so they are not re-sent. Never start the real consumer group from offset 0 without replay mode.
5. Stop it: `docker compose --profile replay stop notification-replay notification-replay-db`.
Caveat: the replay instance shares the `-retry-N` topics with the main service; run it only when no poison messages are in flight.

## Contract change (S13): safe steps
1. **Expand:** producer adds the new field and keeps the old one (done: `totalAmount`, `seatCategory` added; `amount` kept). Consumers ignore unknown fields.
2. **Migrate:** upgrade every consumer to read the new field (done in Notification via `effectiveAmount()`).
3. **Contract:** only when no consumer needs the old field and the topic no longer holds old events (after retention), stop sending `amount`.
A *rename* in one step breaks old consumers and old events still in the topic. In industry a Schema Registry with Avro/Protobuf enforces these compatibility rules automatically; here the tests (`BookingConfirmedEventContractTest`) play that role.

## Reconciliation (S8)
Runs every 60 s (`payment.reconciliation.*` in `payment-service.yml`). On demand: `POST /payments/reconciliation/run` (ADMIN) and read `GET /payments/reconciliation/report`.
It repairs: payments stuck `UNKNOWN`/`PENDING` (asks the provider), captured payments whose booking never confirmed (re-publishes `PaymentSucceeded`) or is expired/cancelled (refunds).
It only flags: a CONFIRMED booking with no captured payment.
