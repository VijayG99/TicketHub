# ST-29 - End-to-end production incident lab

Goal: practise finding the cause of a failure with **logs, metrics and traces**, repair it, and prove the
system recovered without losing money, seats or emails.

Prerequisite: the stack is running (`docker compose up -d`) and `DEV_TOKEN_ENABLED=true`.
Start Kibana too if you want log search: `TICKETHUB_PROFILES=docker,elk` and `docker compose --profile elk up -d`.

## Scenario A - `payment-sync` (SC-06): customer paid, booking not confirmed
```bash
bash scripts/incident-lab.sh payment-sync
```
The script books a seat, starts a payment that waits for the provider webhook, **stops booking-service**, then
delivers a valid signed `SUCCEEDED` webhook.

What you should discover
1. **Symptom** - the payment is `SUCCESS` but the booking cannot be confirmed.
2. **Logs** - `payment-service`: `Could not deliver payment result to booking ... will retry`.
3. **Metrics** - Grafana: circuit-breaker `bookingService` opens, 5xx rate rises.
4. **Trace** - Jaeger: the `POST /payments/webhook` trace ends in a failed call to booking-service.
5. **Root cause** - booking-service was down; nothing is wrong with the payment.
6. **Repair** - the script restarts booking-service; Booking consumes the `PaymentSucceeded` event that waited in Kafka and
   the booking becomes `CONFIRMED` (idempotent endpoint, so no double confirmation).
7. **Prevention** - alert when the breaker is open > 1 min; Phase 2 already replaced the sync job with an outbox/saga (see PHASE2_FLOW.md).

## Scenario B - `mail-outage` (SC-07): booking confirmed, email provider down
```bash
bash scripts/incident-lab.sh mail-outage
```
The script **stops MailHog**, pays for a booking and shows that the booking stays `CONFIRMED` while the
notification is `FAILED`; then restarts MailHog and `NotificationRetryJob` (every 60 s) delivers it.
Investigate with `docker compose logs notification-service | grep FAILED`, the notification record
(`GET /notifications?bookingId=...` as admin) and the Jaeger trace that crosses Kafka.

## Write-up template (hand in with the lab)
| Field | Your answer |
|---|---|
| Timeline (UTC) | first symptom, detection, mitigation, recovery |
| Customer impact | who, how many, what they saw |
| Evidence | Grafana panel, Jaeger traceId, Kibana/log lines, correlationId |
| Root cause | |
| Fix | |
| What prevented data loss | |
| Follow-up actions | alerting, runbook, tests |
