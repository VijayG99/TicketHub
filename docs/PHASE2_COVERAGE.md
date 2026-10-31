# Phase 2 backlog coverage (EPIC-14 to EPIC-21)

"Built" = code/config/scripts exist in this repo. **Nothing is run-verified** (no Maven/Docker where it was written): Java parses, YAML/JSON/shell are syntax-checked, unit tests are written but not executed.
"Doc only" = explained in a document, not automated.

| Story | Status | Where / how to see it | Limits |
|---|---|---|---|
| ST-35 Saga + compensation | Built | `PaymentEventHandler`, `BookingService`, `PaymentRefundService`; `phase2-lab.sh s4-booking-down`, `s5-late-payment` | choreography only |
| ST-63 Choreography vs orchestration | Doc only (comparison + decision) | [SAGA_DECISION.md](SAGA_DECISION.md); saga state/log + `GET /bookings/{id}/saga` give orchestration's visibility | orchestrator not built (guide allows a prototype/comparison) |
| ST-64 Reconciliation | Built | `ReconciliationService/Job/Controller`; `s8-reconcile` | confirmed-booking-without-payment is flagged, not auto-fixed |
| ST-36 Transactional outbox | Built (Booking, Payment) | `OutboxService`, `OutboxRelay`; `s3-outbox` | polling relay; no CDC |
| ST-37 Retry + dead-letter | Built (Notification, Booking, Payment) | `@RetryableTopic`; `s1-poison` | only Notification has the DB table + replay API |
| ST-65 Per-booking ordering | Built | key = bookingId, `aggregate_version`; `s6-order` | |
| ST-66 Replay | Built | profile `replay`; `s12-replay`, [runbook](PHASE2_RUNBOOKS.md) | shares retry topics with the main service |
| ST-67 Consumer lag | Built | `alerts.yml`, Grafana dashboard `tickethub-phase2`; `s11-lag` | |
| ST-68 Event versioning | Built | v1/v2 `BookingConfirmedEvent`, contract test; `s13-contract` | no Schema Registry (explained) |
| ST-38 Inbox + recovery exercise | Built | inbox tables, `s2-duplicate`, `s15-crash` | |
| ST-69 Out-of-order / late webhooks | Built | `WebhookService`, `PaymentStatus`; `s9-webhooks` | Stripe's own webhook signature format not implemented |
| ST-70 Unknown payment after timeout | Built | `UNKNOWN`, `ProviderInvoker.lookup`, reconciliation; `s9-webhooks` | simulated provider's memory is in-process |
| ST-71 Crash after payment | Built (drill) | `phase2-lab.sh s15-crash` (kill Payment after paying, kill Booking mid-consume, reconcile) | a payment left INITIATED by a crash is repaired only if the provider can look it up (Stripe yes, simulator only until restart) |
| ST-72 Refund of cancelled confirmed booking | Built | `BookingService.cancel`, `PaymentRefundService`; e2e section 8, `s5-late-payment` | |
| ST-73 Email retry without duplicates | Built | inbox + retry topics | tiny window: crash between SMTP accept and saving SENT |
| ST-74 Email outage + rate limits | Built | `EmailDeliveryGuard` (breaker + limiter); `s10-mail-outage` | |
| ST-75 Recovery lab | Built | `phase2-lab.sh` (s0..s15), [INCIDENT_TEMPLATE.md](INCIDENT_TEMPLATE.md) | |
| ST-76 Advanced IAM patterns | Built (service-to-service key) | `InternalApiKeyFilter`, `INTERNAL_API_KEY` in `.env` (off by default) | Keycloak client-credentials per-service tokens described, not built |
| ST-77 Extended observability | Built | business metrics (`tickethub_*`), dashboard `tickethub-phase2.json`, extra alerts | no new tracing/log pipeline work |
| ST-78 Harden S3 and RDS | Partly built, rest doc | SSE + cache headers + MinIO versioning/lifecycle + Hikari limits + `db-restore-drill.sh`; [HARDENING.md](HARDENING.md) | IAM, Multi-AZ, PITR, force-SSL, bucket policies are AWS console/Terraform steps |
| ST-79 Second notification channel | Built (PUSH, simulated provider) | `PushNotificationSender`, V3 migration, per-channel rows/breakers; e2e checks both | no real push provider; recipient is the customer id |

New in the final round: ST-71 drill, ST-76, ST-77, ST-78, ST-79. Everything else was in the first Phase 2 delivery.
