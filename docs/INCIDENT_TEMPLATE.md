# Incident note (copy one per failure drill, S14)

| Field | |
|---|---|
| Drill | e.g. "Kafka stopped while a booking was paid" |
| Date / who | |
| **Symptom** | What did a customer or an operator *see*? (exact status, message, missing email ...) |
| **Detected by** | Grafana panel / alert / log line / Kafka UI / database query (name it) |
| **Cause** | The real reason, in one or two sentences. Which guarantee was missing or tested? |
| **Fix / recovery** | Exact commands or API calls; how you verified money, seats and emails are consistent |
| **Prevention** | What stops it next time: code, config, alert, runbook |
| Data lost? duplicated? | Money: ... Seats: ... Emails: ... |

## Worked example: Kafka down during payment (from `phase2-lab.sh s3-outbox`)
* **Symptom:** payment `SUCCESS`, booking still `PENDING_PAYMENT`, no email.
* **Detected by:** Prometheus alert `OutboxBacklog` (`tickethub_outbox_unpublished > 20`) or `select count(*) from outbox_events where published_at is null` in payment-db.
* **Cause:** Kafka unreachable; the relay could not publish `PaymentSucceeded`. The event was safe in the outbox.
* **Fix:** start Kafka. The relay publishes; Booking confirms; Notification sends the email.
* **Prevention:** alert on backlog; keep outages shorter than the reservation time; reconciliation (S8) as the safety net.
