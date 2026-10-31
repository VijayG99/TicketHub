# What changed compared with the project you sent

Everything you already had still works the same way: default `docker compose up -d --build`, the dev token endpoint,
the simulated payment provider and all existing API paths are unchanged. Each new feature is additive.

## Behaviour changes you will notice
* Container logs in Docker are now **JSON lines** (set `TICKETHUB_LOG_APPENDER=CONSOLE` in `.env` for text).
* The gateway's actuator moved to the unpublished port **8089** (Docker health check and Prometheus use it).
  `GET /actuator/health` on port 8080 now returns 401.
* `scripts/smoke-test.sh` waits on `GET /events` instead of the gateway health URL; `scripts/e2e.sh` also uploads a poster.
* Dockerfiles build with `-Dmaven.test.skip=true` (tests run in CI, not in the image build).
* `docker compose up` now also starts MinIO, Prometheus, Grafana and Jaeger (Keycloak and ELK only with profiles).

## Modified files
* `.env.example`
* `.github/workflows/ci.yml`
* `README.md`
* `apigate-service/Dockerfile`
* `apigate-service/pom.xml`
* `apigate-service/src/main/java/com/tickethub/gateway/config/SecurityProperties.java`
* `apigate-service/src/main/java/com/tickethub/gateway/filter/GatewayAuthFilter.java`
* `apigate-service/src/main/resources/application.yml`
* `booking-service/Dockerfile`
* `booking-service/pom.xml`
* `booking-service/src/main/java/com/tickethub/booking/config/RestClientConfig.java`
* `booking-service/src/main/resources/application.yml`
* `config-server/Dockerfile`
* `config-server/src/main/resources/config-repo/apigate-service.yml`
* `config-server/src/main/resources/config-repo/application-docker.yml`
* `config-server/src/main/resources/config-repo/application.yml`
* `config-server/src/main/resources/config-repo/event-service-docker.yml`
* `config-server/src/main/resources/config-repo/event-service.yml`
* `docker-compose.yml`
* `eureka-server/Dockerfile`
* `event-service/Dockerfile`
* `event-service/pom.xml`
* `event-service/src/main/java/com/tickethub/event/common/error/GlobalExceptionHandler.java`
* `event-service/src/main/java/com/tickethub/event/config/RestClientConfig.java`
* `event-service/src/main/java/com/tickethub/event/controller/EventController.java`
* `event-service/src/main/java/com/tickethub/event/service/EventService.java`
* `event-service/src/main/resources/application.yml`
* `notification-service/Dockerfile`
* `notification-service/pom.xml`
* `notification-service/src/main/resources/application.yml`
* `payment-service/Dockerfile`
* `payment-service/pom.xml`
* `payment-service/src/main/java/com/tickethub/payment/config/RestClientConfig.java`
* `payment-service/src/main/java/com/tickethub/payment/enums/PaymentProviderName.java`
* `payment-service/src/main/resources/application.yml`
* `scripts/e2e.sh`
* `scripts/smoke-test.sh`

## New files
* `.gitattributes`
* `apigate-service/src/main/java/com/tickethub/gateway/security/KeycloakTokenVerifier.java`
* `apigate-service/src/main/java/com/tickethub/gateway/security/TokenVerifier.java`
* `apigate-service/src/main/resources/logback-spring.xml`
* `apigate-service/src/test/java/com/tickethub/gateway/security/TokenVerifierTest.java`
* `booking-service/src/main/resources/logback-spring.xml`
* `config-server/src/main/resources/config-repo/booking-service-aws.yml`
* `config-server/src/main/resources/config-repo/event-service-aws.yml`
* `config-server/src/main/resources/config-repo/notification-service-aws.yml`
* `config-server/src/main/resources/config-repo/payment-service-aws.yml`
* `config-server/src/main/resources/config-repo/payment-service-stripe.yml`
* `docker-compose.aws.yml`
* `docs/AWS_RDS_AND_SECRETS.md`
* `docs/INCIDENT_LAB.md`
* `docs/KEYCLOAK.md`
* `docs/OBSERVABILITY.md`
* `event-service/src/main/java/com/tickethub/event/config/MediaProperties.java`
* `event-service/src/main/java/com/tickethub/event/service/MediaStorageService.java`
* `event-service/src/main/java/com/tickethub/event/service/PosterService.java`
* `event-service/src/main/resources/logback-spring.xml`
* `event-service/src/test/java/com/tickethub/event/service/MediaStorageServiceTest.java`
* `keycloak/realm/tickethub-realm.json`
* `notification-service/src/main/resources/logback-spring.xml`
* `observability/grafana/dashboards/tickethub-overview.json`
* `observability/grafana/provisioning/dashboards/dashboards.yml`
* `observability/grafana/provisioning/datasources/datasources.yml`
* `observability/logstash/pipeline/logstash.conf`
* `observability/prometheus/prometheus.yml`
* `payment-service/src/main/java/com/tickethub/payment/provider/StripeSandboxPaymentProvider.java`
* `payment-service/src/main/resources/logback-spring.xml`
* `payment-service/src/test/java/com/tickethub/payment/provider/StripeSandboxPaymentProviderTest.java`
* `scripts/concurrency-test.sh`
* `scripts/incident-lab.sh`


---
# Phase 2 changes (Kafka reliability and sagas)

**Nothing here was compiled or run by the author. Java files were syntax-checked (parse only); YAML and shell were parsed.**
Run `mvn verify` first; see docs/PHASE2_FLOW.md for the design and docs/PHASE2_RUNBOOKS.md for operations.

## Removed
* `BookingEventPublisher` (publish-after-commit), `PaymentResultSyncJob`, `NotificationRetryJob`, Notification's blocking `DefaultErrorHandler`.
* Payment no longer calls Booking's `/internal/bookings/{id}/payment-result` (endpoint kept, unused).

## Added / changed per service
* **Booking:** Flyway V2 (`outbox_events`, `inbox_events`, `saga_log`, 3 columns); `outbox/` (OutboxService, OutboxRelay, InboxService); `saga/SagaLog`;
  `consumer/PaymentEvent*`; events `BookingConfirmedEvent` (v2), `BookingSagaEvent`, `PaymentEvent`; `BookingService` (outbox instead of publisher, cancel of CONFIRMED,
  rejectLatePayment, recordRefund, sagaView, confirmedIdsSince); `GET /bookings/{id}/saga`; `GET /internal/bookings/confirmed-ids`; `SagaState`; topics config.
* **Payment:** spring-kafka; Flyway V2; outbox/inbox; `PaymentStatus.UNKNOWN`; events; `PaymentStateService` (emit events, markUnknown, completeRefund, republishSucceeded);
  `PaymentRefundService`; `consumer/BookingEvent*`; provider `lookup()` and `refund()` (simulated + Stripe); `reconciliation/` + `/payments/reconciliation/*`.
* **Notification:** spring-aop + resilience4j; Flyway V2 (`inbox_events`, `dead_letters`); `@RetryableTopic` consumer with DLT handler; inbox-based `NotificationRecorder`;
  `EmailDeliveryGuard` (circuit breaker + rate limiter); dead-letter service/controller; versioned `BookingConfirmedEvent`; statuses RETRYING/DEAD/REPLAYED; replay mode.
* **Config repo:** consumer/producer settings for Booking and Payment, topics, retry, reconciliation, resilience4j for email, `notification-service-replay.yml`.
* **Compose:** Kafka UI always on, `replay` profile (`notification-replay-db`, `notification-replay`), Payment waits for Kafka, Prometheus alert rules.
* **Scripts:** `scripts/phase2-lab.sh` (all 15 scenarios), `e2e.sh` polls for eventual consistency and tests cancel+refund, `incident-lab.sh` updated.
* **Docs:** PHASE2_FLOW, PHASE2_RUNBOOKS, SAGA_DECISION, INCIDENT_TEMPLATE.
* **Tests:** NotificationServiceTest, BookingConfirmedEventContractTest, PaymentEventHandlerTest, PaymentRefundServiceTest, SimulatedPaymentProviderTest, extended PaymentStatusTest.

## Behaviour changes to be aware of
* A booking is **eventually** confirmed (a few seconds after payment), not synchronously.
* While Kafka is down bookings cannot confirm (payments are safe in the outbox).
* A CONFIRMED booking can now be cancelled (refund). The Phase 1 rule "cannot cancel CONFIRMED" is gone.

## Phase 2, round 2 (ST-71, ST-76, ST-77, ST-78, ST-79)
* **ST-79:** `NotificationChannel.PUSH`, `PushNotificationSender` (simulated), Flyway V3 in Notification (unique constraints now include the channel),
  one notification row per channel, per-channel circuit breakers; failure of one channel never blocks the other.
  `NotificationRecorder.Accepted` now carries a list. Config: `notification.push.enabled`.
* **ST-71:** `phase2-lab.sh s15-crash`; reconciliation now also examines payments stuck `INITIATED`.
* **ST-76:** `InternalApiKeyFilter` (booking, event) + header added by the three REST clients. Off unless `INTERNAL_API_KEY` is set.
* **ST-77:** counters `tickethub_dead_letters_parked_total`, `tickethub_notifications_total{channel,result}`, `tickethub_refunds_total`,
  `tickethub_reconciliation_{fixed,flagged}_total`, `tickethub_saga_compensations_total{type}`; dashboard `tickethub-phase2.json`; three more alert rules.
* **ST-78:** SSE + cache-control on poster upload, MinIO versioning/lifecycle, Hikari pool limits in the shared config, `scripts/db-restore-drill.sh`, docs/HARDENING.md.
* **Docs:** docs/PHASE2_COVERAGE.md. **Scripts:** e2e/phase2-lab/incident-lab now pick the EMAIL row explicitly.
