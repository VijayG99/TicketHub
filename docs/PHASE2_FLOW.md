# Phase 2: the flow, explained

Read this first. It explains **what happens, in order**, and where each piece lives in the code.
Every Java class that belongs to Phase 2 has a long comment at the top that repeats the idea in the same words.

> **What is verified.** The author had no Maven, no Docker and no Kafka where this was written. Every Java file was
> checked to *parse* (javac syntax pass), YAML files were parsed, shell scripts were `bash -n` checked. **Nothing was
> compiled against the real libraries or run.** Expect to fix a few small compile or startup errors the first time
> (typical: a missing import, a Flyway/Hibernate column mismatch). Send the error text and it can be fixed quickly.
> The unit tests (`mvn verify`) are the first thing to run.

---

## 1. The big picture

```
                          booking-confirmed                      
  Booking ───────────────────────────────────────────────▶ Notification ──▶ email (MailHog)
     │  ▲                                                     │  retry-0/1/2 ▶ -dlt ▶ dead_letters table
     │  │ payment-events                                      │  inbox_events (no duplicates)
     │  │ (PaymentSucceeded, PaymentFailed, PaymentRefunded)
     │  │
     │  └───────────────────────── Payment ◀──── provider webhook / simulator / Stripe
     │         booking-events                       │ outbox_events, inbox_events
     └────────▶(BookingRejected, BookingCancelled)──┘ reconciliation job
```

Three Kafka topics, **every message keyed by bookingId** (same booking => same partition => in order):

| Topic | Producer | Consumer | Events |
|---|---|---|---|
| `booking-confirmed` | Booking | Notification | `BookingConfirmed` |
| `payment-events` | Payment | Booking | `PaymentSucceeded`, `PaymentFailed`, `PaymentRefunded` |
| `booking-events` | Booking | Payment | `BookingRejected`, `BookingCancelled` |

Nobody calls anybody over REST for the result of a payment any more. A service writes **its own database
and an outbox row in one transaction**; a relay publishes the outbox row; the other service reads it with an
**inbox** so a duplicate does nothing.

## 2. The five building blocks (learn these, everything else is a combination)

| Block | One sentence | Code |
|---|---|---|
| **Outbox** | Write the event into a DB table *in the same transaction* as the state change; publish it later. Never "save, then call Kafka". | `OutboxService` (write), `OutboxRelay` (publish, every 1 s) |
| **Inbox** | Remember every event id you handled; a primary key on the id rejects the second copy, even from two instances at once. | `InboxService`, `InboxRepository` |
| **Retry topics + DLT** | A failing message moves to `-retry-0`, `-retry-1`, `-retry-2` (waiting 3 s, 6 s, 12 s) without blocking others; then to `-dlt`. Bad data skips the retries. | `@RetryableTopic` on the three consumers |
| **Version number** | Each event carries `aggregateVersion` (1, 2, 3 ...). A consumer ignores an event older than the newest it applied. | `aggregate_version`, `last_*_event_version` columns |
| **Compensation** | A saga cannot roll back across two databases; instead it runs a new step that *undoes* the old one. Here: a refund. | `PaymentRefundService` |

## 3. Walk-through A: the happy path (pay and get confirmed)

1. `POST /bookings` -> Booking row `PENDING_PAYMENT`, saga `PAYMENT_PENDING`. (`BookingService.createPending`)
2. `POST /payments` -> Payment asks Booking (REST, read-only) for the amount, calls the provider.
3. Provider says OK -> in **one transaction** Payment sets `SUCCESS` and writes `PaymentSucceeded` to **its** outbox. (`PaymentStateService.transition`)
4. Payment's relay publishes it on `payment-events`.
5. Booking's `PaymentEventsConsumer` receives it -> `PaymentEventHandler`, **one transaction**:
   inbox row, version check, `BookingService.applyPaymentResult` -> status `CONFIRMED`, saga `CONFIRMED`,
   seats `BOOKED`, `BookingConfirmed` written to Booking's outbox.
6. Booking's relay publishes `BookingConfirmed` on `booking-confirmed`.
7. Notification's `BookingConfirmedConsumer` -> `NotificationService.handle` -> inbox + notification row (one transaction) -> email through the circuit breaker -> `SENT`.

Because step 4-6 go through Kafka, **confirmation is eventually consistent**: `scripts/e2e.sh` polls instead of expecting `CONFIRMED` instantly.

## 4. Walk-through B: what happens when things go wrong

| Failure | What the system does | Scenario |
|---|---|---|
| Booking is down while the customer pays | `PaymentSucceeded` waits in Kafka; Booking applies it when it starts | S4 |
| Kafka is down | events pile up in the outbox tables (metric `tickethub_outbox_unpublished`); relay sends them when Kafka returns | S3 |
| Same event delivered twice | inbox primary key rejects the copy | S2 |
| Email server down | notification `RETRYING`; message moves through retry topics; after the last one -> `-dlt` -> `dead_letters` table, status `DEAD`; operator replays | S10, S1 |
| Poison message (bad JSON, no email) | no retries, straight to `-dlt`; stream continues | S1 |
| Payment arrives after the booking expired/was cancelled | Booking sends `BookingRejected`; Payment refunds; `PaymentRefunded` -> saga `COMPENSATED` | S5 |
| Customer cancels a CONFIRMED booking | seats freed, `BookingCancelled` -> refund -> `COMPENSATED` | S5 |
| Events arrive out of order | lower `aggregateVersion` is ignored | S6 |
| Provider call times out | payment `UNKNOWN`; reconciliation asks the provider, then moves it to `SUCCESS`/`FAILED` | S9 |
| Provider repeats / reorders webhooks | processed-event table + forward-only status machine | S9 |
| Everything above leaked something | reconciliation compares Payment and Booking and repairs or flags | S8 |

## 5. State diagrams

Booking **status** (what the customer sees) and **saga state** (what the system is doing about money):

```
 status:  PENDING_PAYMENT ──paid──────────▶ CONFIRMED ──customer cancels──▶ CANCELLED
              │  │  └─payment failed──▶ PAYMENT_FAILED
              │  └──expires──▶ EXPIRED
              └──customer cancels──▶ CANCELLED

 saga:    PAYMENT_PENDING ──PaymentSucceeded──▶ CONFIRMED ──cancel──▶ COMPENSATING ──PaymentRefunded──▶ COMPENSATED
              │                                                           ▲
              ├── PaymentSucceeded but booking already expired/cancelled ──┘ (BookingRejected sent)
              └── failed / expired / cancelled before paying ──▶ FAILED   (no money moved)
```
Payment status (only forward moves allowed):
```
 INITIATED ─▶ PENDING ─▶ SUCCESS ─▶ REFUNDED
     │          │  ╲        ▲
     │          └──▶ UNKNOWN ┘ ─▶ FAILED / CANCELLED
     └─▶ FAILED / CANCELLED
```

## 6. Scenario-by-scenario map

| S | Title | Where to read the code | Try it |
|---|---|---|---|
| 0 | See inside Kafka | `docker-compose.yml` (`kafka-ui`) | `phase2-lab.sh s0-inspect`, http://localhost:8090 |
| 1 | Bad message blocks consumer | `BookingConfirmedConsumer`, `DeadLetterService`, `DeadLetterController` | `s1-poison` |
| 2 | Same event twice | `InboxRepository`, `NotificationRecorder.accept`, `NotificationService.handle` | `s2-duplicate` |
| 3 | Lost between commit and publish | `OutboxService`, `OutboxRelay` (Booking and Payment) | `s3-outbox` |
| 4 | Money taken, booking not confirmed | `PaymentStateService.transition`, `PaymentEventsConsumer`, `PaymentEventHandler` | `s4-booking-down` |
| 5 | Late payment / cancel | `BookingService` (`rejectLatePayment`, `cancel`, `recordRefund`), `PaymentRefundService`, `BookingEventsConsumer` | `s5-late-payment` |
| 6 | Out of order | version columns in `V2__*.sql`; `PaymentEventHandler`, `PaymentRefundService` | `s6-order` |
| 7 | Where is it stuck | `SagaLog`, `BookingService.sagaView`, [SAGA_DECISION.md](SAGA_DECISION.md) | `s7-saga-view` |
| 8 | Disagreement | `ReconciliationService` | `s8-reconcile` |
| 9 | Provider callbacks | `WebhookService` (unchanged idempotency), `PaymentStatus`, `markUnknown`, `ProviderInvoker.lookup` | `s9-webhooks` |
| 10 | Email provider down | `EmailDeliveryGuard`, `@RetryableTopic` | `s10-mail-outage` |
| 11 | Consumer lag | `alerts.yml`, `simulated-delay-ms` | `s11-lag` |
| 12 | Replay | `notification-service-replay.yml`, compose profile `replay` | `s12-replay` |
| 13 | Contract change | `BookingConfirmedEvent` in Booking and in Notification | `s13-contract` |
| 14 | Everything together | `phase2-lab.sh s14-chaos`, [INCIDENT_TEMPLATE.md](INCIDENT_TEMPLATE.md) | `s14-chaos` |

## 7. Database changes (Flyway V2 in each service)

| Service | New tables | New columns |
|---|---|---|
| Booking | `outbox_events`, `inbox_events`, `saga_log` | `bookings.aggregate_version`, `last_payment_event_version`, `saga_state` |
| Payment | `outbox_events`, `inbox_events` | `payments.refund_reference`, `last_booking_event_version`; status `UNKNOWN`; unique live-payment index now includes `UNKNOWN` |
| Notification | `inbox_events`, `dead_letters` | statuses `RETRYING`, `DEAD`, `REPLAYED` (text, no schema change) |

`ddl-auto: validate` is on, so an entity that disagrees with these migrations stops the service at start-up with a clear message.

## 8. Honest limits and decisions

* **Razorpay** is not implemented (simulated + Stripe test mode are). Refund/lookup exist for both; add a Razorpay class implementing `PaymentProviderGateway`.
* **Topic names** keep the Phase 1 dash style; the guide's `booking.confirmed.DLT` is `booking-confirmed-dlt` here.
* **Kafka down now delays confirmation.** In Phase 1 confirmation did not need Kafka; since S4 it does. The outbox guarantees nothing is lost, but a booking cannot confirm during the outage, and if the outage outlasts the 10-minute reservation the payment is refunded (S5).
* **At-least-once.** One tiny window remains: the mail server accepted the email but the service crashed before saving `SENT`; the retry sends a second mail. Closing it needs an idempotency key at the email provider.
* **Dead-lettered Booking/Payment events** are only logged (Notification has the DB table and replay API). Reconciliation (S8) is the repair path for them.
* **Replay (S12)** runs against its own database and consumer group but shares the retry topics with the main service; run it when the topic holds no poison messages.
* **Retention (S12):** Kafka keeps messages 7 days by default (`log.retention.hours=168`); replay can only go back that far.
* The old REST endpoint `POST /internal/bookings/{id}/payment-result` and `BookingClient.sendPaymentResult` are kept but unused (handy for debugging).
