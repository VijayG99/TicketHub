#!/usr/bin/env bash
# =============================================================================
# Phase 2 failure lab. One command per scenario of the Phase 2 guide (S0 ... S14).
#
#   bash scripts/phase2-lab.sh s1-poison        # ...and so on. Run with no argument to list them.
#
# Needs: running stack (docker compose up -d --build), DEV_TOKEN_ENABLED=true, docker, curl, jq, openssl.
# Secrets come from .env. NOTHING in this file was run by the author (no Docker where it was written);
# if a step fails, read the message, check `docker compose logs <service>` and tell your mentor / me.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { set -a; . ./.env; set +a; }
BASE="${BASE:-http://localhost:8080}"
MAILHOG="${MAILHOG:-http://localhost:8025}"
: "${DEV_ADMIN_KEY:?DEV_ADMIN_KEY missing (.env)}"
: "${PAYMENT_WEBHOOK_SECRET:?PAYMENT_WEBHOOK_SECRET missing (.env)}"
KAFKA_BIN="/opt/kafka/bin"
PG_USER="${POSTGRES_USER:-tickethub}"

# ------------------------------------------------------------------ helpers
api() { local m="$1" p="$2" t="$3" b="${4:-}"; local a=(-sS -X "$m" "$BASE$p" -H 'Content-Type: application/json'); [ -n "$t" ] && a+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && a+=(-d "$b"); curl "${a[@]}"; }
iso()  { date -u -d "$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v"${2}" +%Y-%m-%dT%H:%M:%SZ; }
say()  { echo; echo "### $*"; }
ok()   { echo "   OK: $*"; }
die()  { echo "   FAIL: $*"; exit 1; }
uuid() { local h; h=$(openssl rand -hex 16); echo "${h:0:8}-${h:8:4}-4${h:13:3}-a${h:17:3}-${h:20:12}"; }
wait_for() { local what="$1" secs="$2"; shift 2; for _ in $(seq 1 "$secs"); do "$@" && { ok "$what"; return 0; }; sleep 1; done; die "timeout waiting for: $what"; }
psql_in() { local svc="$1" db="$2" sql="$3"; docker compose exec -T "$svc" psql -U "$PG_USER" -d "$db" -At -c "$sql"; }
produce() {  # produce TOPIC KEY JSON   (publishes ONE message with a key, straight into Kafka, bypassing every service)
  printf '%s|%s\n' "$2" "$3" | docker compose exec -T kafka "$KAFKA_BIN/kafka-console-producer.sh" \
    --bootstrap-server kafka:9092 --topic "$1" --property parse.key=true --property 'key.separator=|' >/dev/null
}
confirmed_event() {  # confirmed_event EVENT_ID BOOKING_ID EMAIL  -> a valid BookingConfirmed v2 JSON
  printf '{"eventId":"%s","eventType":"BookingConfirmed","eventVersion":2,"aggregateVersion":1,"occurredAt":"%s","bookingId":"%s","customerId":"lab","customerEmail":"%s","showId":1,"eventTitle":"Lab Show","venueName":"Lab Hall","showStartTime":"2030-01-01T18:30:00Z","seatIds":[1],"seatLabels":["A1"],"amount":100.00,"totalAmount":100.00,"seatCategory":"STANDARD","currency":"INR"}' \
    "$1" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$2" "$3"
}
mail_count() { curl -s "$MAILHOG/api/v2/search?kind=to&query=$1" | jq -r '.total' 2>/dev/null || echo 0; }

setup() {   # a fresh event + venue + 4 seats + OPEN show, plus tokens
  ADMIN=$(api POST /auth/token "" "{\"userId\":\"admin-1\",\"email\":\"admin@tickethub.local\",\"roles\":[\"ADMIN\"],\"adminKey\":\"$DEV_ADMIN_KEY\"}" | jq -r .token)
  USER=$(api POST /auth/token "" '{"userId":"lab-user","email":"lab-user@example.com"}' | jq -r .token)
  EVENT=$(api POST /events "$ADMIN" '{"title":"Phase2 Lab Show","type":"THEATRE"}' | jq -r .id)
  api PATCH "/events/$EVENT/status" "$ADMIN" '{"status":"PUBLISHED"}' >/dev/null
  VENUE=$(api POST /venues "$ADMIN" '{"name":"Lab Hall","city":"Chennai","address":"Anna Salai"}' | jq -r .id)
  api POST "/venues/$VENUE/seats" "$ADMIN" '{"rows":[{"rowLabel":"A","seatCount":4,"category":"STANDARD"}]}' >/dev/null
  SHOW=$(api POST /shows "$ADMIN" "{\"eventId\":$EVENT,\"venueId\":$VENUE,\"startTime\":\"$(iso '+7 days' '+7d')\",\"endTime\":\"$(iso '+7 days 3 hours' '+7d -v+3H')\",\"ticketPrice\":150.00,\"currency\":\"INR\"}" | jq -r .id)
  api PATCH "/shows/$SHOW/status" "$ADMIN" '{"status":"OPEN"}' >/dev/null
  SEATS=$(api GET "/shows/$SHOW/seats" "" | jq -r '[.seats[].seatId]|map(tostring)|join(",")')
  IFS=',' read -r SE1 SE2 SE3 SE4 <<< "$SEATS"
}
new_booking() { api POST /bookings "$USER" "{\"showId\":$SHOW,\"seatIds\":[$1]}" | jq -r .bookingId; }
booking_status() { api GET "/bookings/$1" "$USER" | jq -r .status; }
saga_state() { api GET "/bookings/$1/saga" "$USER" | jq -r .sagaState; }
send_webhook() {  # send_webhook EVENT_ID PROVIDER_REF STATUS
  local payload="{\"eventId\":\"$1\",\"providerReference\":\"$2\",\"status\":\"$3\"}"
  local sig; sig=$(printf '%s' "$payload" | openssl dgst -sha256 -hmac "$PAYMENT_WEBHOOK_SECRET" -hex | sed 's/^.* //')
  curl -sS -X POST "$BASE/payments/webhook" -H 'Content-Type: application/json' -H "X-Signature: sha256=$sig" -d "$payload"; echo
}

# ------------------------------------------------------------------ scenarios
s0_inspect() {
  say "S0: look inside Kafka"
  echo "Kafka UI: http://localhost:8090  (topics, messages, consumer groups, lag)"
  docker compose exec -T kafka "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server kafka:9092 --list
  docker compose exec -T kafka "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server kafka:9092 --describe --topic booking-confirmed
  docker compose exec -T kafka "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server kafka:9092 --describe --group notification-service-group || true
  say "publish and read one test message with the console tools"
  docker compose exec -T kafka "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server kafka:9092 --create --if-not-exists --topic lab-test --partitions 1 --replication-factor 1 >/dev/null
  produce lab-test k1 '{"hello":"kafka"}'
  docker compose exec -T kafka "$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server kafka:9092 --topic lab-test --from-beginning --max-messages 1 --timeout-ms 10000 --property print.key=true --property print.offset=true --property print.partition=true
}

s1_poison() {
  say "S1: a poison message must not block the stream"
  setup
  local bad_id; bad_id=$(uuid)
  echo "   publishing (a) broken JSON and (b) a BookingConfirmed WITHOUT an email"
  produce booking-confirmed "$bad_id" 'this is not json {{{'
  produce booking-confirmed "$bad_id" "$(confirmed_event "$(uuid)" "$bad_id" "")"
  echo "   then a real booking behind them: it must still get its email"
  local b; b=$(new_booking "$SE1"); api POST /payments "$USER" "{\"bookingId\":\"$b\"}" >/dev/null
  chk() { [ "$(api GET "/notifications?bookingId=$b" "$ADMIN" | jq -r '([.content[]|select(.channel=="EMAIL")][0].status)')" = SENT ]; }
  wait_for "good booking's email was SENT (stream not blocked)" 60 chk
  chk2() { [ "$(api GET "/notifications/dead-letters?status=PARKED" "$ADMIN" | jq -r '.totalElements // (.content|length)')" -ge 2 ]; }
  wait_for "both bad messages are parked as dead letters" 60 chk2
  api GET "/notifications/dead-letters?status=PARKED" "$ADMIN" | jq '.content[] | {id, error, payload}'
  say "FIX + REPLAY the one with the missing email (send corrected JSON)"
  local id; id=$(api GET "/notifications/dead-letters?status=PARKED" "$ADMIN" | jq -r '[.content[]|select(.payload|contains("customerEmail"))][0].id')
  local fixed; fixed=$(confirmed_event "$(uuid)" "$bad_id" "fixed-$RANDOM@example.com")
  api POST "/notifications/dead-letters/$id/replay" "$ADMIN" "$(jq -n --arg p "$fixed" '{payload:$p}')" | jq '{id,status}'
  chk3() { [ "$(api GET "/notifications?bookingId=$bad_id" "$ADMIN" | jq -r '([.content[]|select(.channel=="EMAIL")][0].status)')" = SENT ]; }
  wait_for "the replayed (fixed) message produced an email" 60 chk3
  echo "   Also look at topics booking-confirmed-retry-0/1/2 and booking-confirmed-dlt in Kafka UI."
}

s2_duplicate() {
  say "S2: the same event five times must produce ONE email"
  local b e mail; b=$(uuid); e=$(uuid); mail="dup-$RANDOM@example.com"
  for _ in 1 2 3 4 5; do produce booking-confirmed "$b" "$(confirmed_event "$e" "$b" "$mail")"; done
  chk() { [ "$(mail_count "$mail")" -ge 1 ]; }
  wait_for "an email arrived in MailHog" 60 chk
  sleep 8
  echo "   emails for $mail in MailHog: $(mail_count "$mail")  (must be exactly 1)"
  [ "$(mail_count "$mail")" = 1 ] || die "duplicates produced more than one email"
  psql_in notification-db notification_db "select count(*) as inbox_rows from inbox_events where event_id='$e'"
  ok "one email, one inbox row"
}

s3_outbox() {
  say "S3: Kafka down. Events must wait in the outbox, not be lost"
  setup
  local b; b=$(new_booking "$SE1")
  local pay; pay=$(api POST /payments "$USER" "{\"bookingId\":\"$b\"}")
  echo "   payment status before the outage: $(echo "$pay" | jq -r .status)"
  chk0() { [ "$(booking_status "$b")" = CONFIRMED ]; }
  wait_for "booking confirmed (normal flow)" 40 chk0
  say "INJECT: stop Kafka, then run a second booking"
  docker compose stop kafka >/dev/null
  local b2; b2=$(new_booking "$SE2")
  api POST /payments "$USER" "{\"bookingId\":\"$b2\"}" | jq -r '"   payment: " + .status'
  sleep 5
  echo "   booking status (Kafka is down, so Booking cannot hear PaymentSucceeded): $(booking_status "$b2")"
  echo "   unpublished outbox rows in payment-db: $(psql_in payment-db payment_db 'select count(*) from outbox_events where published_at is null')  (must be >= 1: the event is SAFE in the database)"
  say "REPAIR: start Kafka"
  docker compose start kafka >/dev/null
  chk() { [ "$(booking_status "$b2")" = CONFIRMED ]; }
  wait_for "the waiting event was relayed, booking became CONFIRMED" 180 chk
  chk2() { [ "$(api GET "/notifications?bookingId=$b2" "$ADMIN" | jq -r '([.content[]|select(.channel=="EMAIL")][0].status)')" = SENT ]; }
  wait_for "confirmation email was sent after Kafka came back" 120 chk2
  echo "NOTE: while Kafka is down a booking cannot be CONFIRMED (confirmation now travels over Kafka). Keep the outage"
  echo "      shorter than the reservation time (10 min) or the booking expires and the late payment is refunded (S5)."
}

s4_booking_down() { say "S4: money taken while Booking is down (same drill as the incident lab)"; bash scripts/incident-lab.sh payment-sync; }

s5_late_payment() {
  say "S5a: payment arrives after the booking was cancelled -> automatic refund"
  setup
  local b; b=$(new_booking "$SE1")
  local pay ref pid; pay=$(api POST /payments "$USER" "{\"bookingId\":\"$b\",\"simulatedOutcome\":\"PENDING_WEBHOOK\"}")
  ref=$(echo "$pay" | jq -r .providerReference); pid=$(echo "$pay" | jq -r .paymentId)
  api POST "/bookings/$b/cancel" "$USER" | jq -r '"   booking now: " + .status'
  echo "   the provider now reports SUCCESS for a booking that no longer exists:"
  send_webhook "late-$(uuid)" "$ref" SUCCEEDED
  chk() { [ "$(saga_state "$b")" = COMPENSATED ]; }
  wait_for "saga reached COMPENSATED (BookingRejected -> refund -> PaymentRefunded)" 90 chk
  echo "   payment status: $(api GET "/payments/$pid" "$USER" | jq -r .status)  booking status: $(booking_status "$b")"
  api GET "/bookings/$b/saga" "$USER" | jq '{status, sagaState, explanation, timeline: [.timeline[]|.step]}'
  say "S5b: customer cancels a CONFIRMED booking -> refund"
  local b2; b2=$(new_booking "$SE2"); api POST /payments "$USER" "{\"bookingId\":\"$b2\"}" >/dev/null
  chk2() { [ "$(booking_status "$b2")" = CONFIRMED ]; }
  wait_for "confirmed" 40 chk2
  api POST "/bookings/$b2/cancel" "$USER" | jq -r '"   cancel -> " + .status'
  chk3() { [ "$(saga_state "$b2")" = COMPENSATED ]; }
  wait_for "refund completed (COMPENSATED)" 90 chk3
}

s6_order() {
  say "S6: events arriving in the WRONG order must not corrupt state"
  setup
  local b; b=$(new_booking "$SE1")
  local pid; pid=$(uuid)
  echo "   injecting PaymentRefunded (version 2) FIRST, then PaymentSucceeded (version 1) for booking $b"
  produce payment-events "$b" "{\"eventId\":\"$(uuid)\",\"eventType\":\"PaymentRefunded\",\"eventVersion\":1,\"aggregateVersion\":2,\"bookingId\":\"$b\",\"paymentId\":\"$pid\"}"
  sleep 4
  produce payment-events "$b" "{\"eventId\":\"$(uuid)\",\"eventType\":\"PaymentSucceeded\",\"eventVersion\":1,\"aggregateVersion\":1,\"bookingId\":\"$b\",\"paymentId\":\"$pid\"}"
  sleep 6
  echo "   booking status: $(booking_status "$b")   (must NOT be CONFIRMED: the older event is stale and was ignored)"
  [ "$(booking_status "$b")" != CONFIRMED ] || die "stale event was applied"
  docker compose logs --tail=50 booking-service 2>/dev/null | grep -i "STALE" | tail -2 || true
  ok "stale event ignored"
}

s7_saga_view() {
  say "S7: 'where is booking X and why?'"
  setup
  local b; b=$(new_booking "$SE1")
  echo "-- after creation"; api GET "/bookings/$b/saga" "$USER" | jq '{status, sagaState, explanation}'
  api POST /payments "$USER" "{\"bookingId\":\"$b\"}" >/dev/null
  chk() { [ "$(saga_state "$b")" = CONFIRMED ]; }
  wait_for "saga CONFIRMED" 40 chk
  echo "-- after payment"; api GET "/bookings/$b/saga" "$USER" | jq '{status, sagaState, explanation, timeline}'
}

s8_reconcile() {
  say "S8: seed mismatches by hand, then let reconciliation find and fix them"
  setup
  local b; b=$(new_booking "$SE1")
  local pay pid; pay=$(api POST /payments "$USER" "{\"bookingId\":\"$b\",\"simulatedOutcome\":\"PENDING_WEBHOOK\"}"); pid=$(echo "$pay" | jq -r .paymentId)
  echo "   mismatch 1: payment captured (SUCCESS) but NO event was sent and booking is still $(booking_status "$b")"
  psql_in payment-db payment_db "update payments set status='SUCCESS', updated_at = now() - interval '10 minutes' where id='$pid'" >/dev/null
  local b2; b2=$(new_booking "$SE2")
  echo "   mismatch 2: booking CONFIRMED by hand with NO payment"
  psql_in booking-db booking_db "update bookings set status='CONFIRMED', saga_state='CONFIRMED', confirmed_at=now() where id='$b2'" >/dev/null
  say "run reconciliation"
  api POST /payments/reconciliation/run "$ADMIN" | jq '{paymentsChecked, bookingsChecked, fixed, flagged}'
  chk() { [ "$(booking_status "$b")" = CONFIRMED ]; }
  wait_for "mismatch 1 repaired: booking $b is now CONFIRMED" 60 chk
  echo "   mismatch 2 appears under 'flagged' above (it cannot be repaired automatically)."
}

s9_webhooks() {
  say "S9a: repeated, conflicting and late webhooks leave ONE correct final state"
  setup
  local b; b=$(new_booking "$SE1")
  local pay ref pid; pay=$(api POST /payments "$USER" "{\"bookingId\":\"$b\",\"simulatedOutcome\":\"PENDING_WEBHOOK\"}")
  ref=$(echo "$pay" | jq -r .providerReference); pid=$(echo "$pay" | jq -r .paymentId)
  local e1; e1="evt-$(uuid)"
  echo -n "   SUCCEEDED         -> "; send_webhook "$e1" "$ref" SUCCEEDED
  echo -n "   SUCCEEDED again   -> "; send_webhook "$e1" "$ref" SUCCEEDED
  echo -n "   FAILED (late)     -> "; send_webhook "evt-$(uuid)" "$ref" FAILED
  echo -n "   PENDING (older)   -> "; send_webhook "evt-$(uuid)" "$ref" PENDING
  echo "   payment status: $(api GET "/payments/$pid" "$USER" | jq -r .status)  (must be SUCCESS)"
  say "S9b: provider timeout -> UNKNOWN -> resolved by asking the provider"
  local b2; b2=$(new_booking "$SE2")
  echo "   paying with a provider that hangs (customer IS charged, but we time out)..."
  api POST /payments "$USER" "{\"bookingId\":\"$b2\",\"simulatedOutcome\":\"TIMEOUT\"}" | jq -c '{code, message}' || true
  local p2; p2=$(psql_in payment-db payment_db "select id from payments where booking_id='$b2'")
  echo "   payment status in DB: $(psql_in payment-db payment_db "select status from payments where id='$p2'")  (UNKNOWN)"
  psql_in payment-db payment_db "update payments set updated_at = now() - interval '10 minutes' where id='$p2'" >/dev/null
  api POST /payments/reconciliation/run "$ADMIN" | jq '{fixed, flagged}'
  chk() { [ "$(booking_status "$b2")" = CONFIRMED ]; }
  wait_for "UNKNOWN payment resolved to SUCCESS and booking CONFIRMED" 60 chk
}

s15_crash() {
  say "ST-71: services CRASH around a payment (docker kill = no graceful shutdown). Nothing may be lost or doubled."
  setup
  say "Drill A: kill payment-service right after the payment, before its relay may have published"
  local b; b=$(new_booking "$SE1")
  api POST /payments "$USER" "{\"bookingId\":\"$b\"}" | jq -r '"   payment: " + .status'
  docker compose kill payment-service >/dev/null
  echo "   payment-service killed. Its outbox row is in payment-db: $(psql_in payment-db payment_db "select count(*) from outbox_events where event_type='PaymentSucceeded' and aggregate_id='$b'") row(s); published? $(psql_in payment-db payment_db "select coalesce(published_at::text,'NOT YET') from outbox_events where aggregate_id='$b'")"
  docker compose start payment-service >/dev/null
  chkA() { [ "$(booking_status "$b")" = CONFIRMED ]; }
  wait_for "after restart the relay delivered the event: booking CONFIRMED" 180 chkA
  echo "   PaymentSucceeded rows for this booking (must be 1): $(psql_in payment-db payment_db "select count(*) from outbox_events where event_type='PaymentSucceeded' and aggregate_id='$b'")"

  say "Drill B: kill booking-service while it is consuming PaymentSucceeded (webhook path, so Booking can be down first)"
  local b2 pay ref; b2=$(new_booking "$SE2")
  pay=$(api POST /payments "$USER" "{\"bookingId\":\"$b2\",\"simulatedOutcome\":\"PENDING_WEBHOOK\"}"); ref=$(echo "$pay" | jq -r .providerReference)
  send_webhook "crash-$(uuid)" "$ref" SUCCEEDED >/dev/null
  docker compose kill booking-service >/dev/null
  echo "   booking-service killed right after the event was published; the event waits in Kafka (offset not committed)"
  docker compose start booking-service >/dev/null
  chkB() { [ "$(booking_status "$b2")" = CONFIRMED ]; }
  wait_for "booking-service restarted, re-read the event, booking CONFIRMED" 180 chkB
  echo "   BookingConfirmed events for it in booking-db outbox (must be 1): $(psql_in booking-db booking_db "select count(*) from outbox_events where event_type='BookingConfirmed' and aggregate_id='$b2'")"
  echo "   inbox rows in booking-db for PaymentSucceeded (duplicates would show as >1 only if ids differ): $(psql_in booking-db booking_db "select count(*) from inbox_events where event_type='PaymentSucceeded'")"

  say "Drill C: any payment left INITIATED/UNKNOWN by a crash is picked up by reconciliation"
  api POST /payments/reconciliation/run "$ADMIN" | jq '{fixed, flagged}'
  ok "reconciliation ran (see 'fixed' / 'flagged' above)"
  echo "NOTE: with the SIMULATED provider, lookup() only knows payments made since its last start (in-memory),"
  echo "      so a payment stuck INITIATED by a crash is FLAGGED, not repaired. With Stripe test mode, lookup() asks Stripe and repairs it."
}

s10_mail_outage() { say "S10: email provider down"; bash scripts/incident-lab.sh mail-outage; }

s11_lag() {
  say "S11: consumer lag"
  local n="${N:-100}"
  echo "   publishing $n events straight into booking-confirmed ..."
  for i in $(seq 1 "$n"); do local b; b=$(uuid); produce booking-confirmed "$b" "$(confirmed_event "$(uuid)" "$b" "burst-$i@example.com")"; done
  echo "   lag per partition (watch it fall; add consumers with: docker compose up -d --scale notification-service=3)"
  for _ in 1 2 3 4 5 6; do
    docker compose exec -T kafka "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server kafka:9092 --describe --group notification-service-group 2>/dev/null | awk 'NR==1 || /booking-confirmed/' | head -6
    echo ---; sleep 5
  done
  echo "   Slow the consumer on purpose: set notification.simulated-delay-ms: 1000 in config-repo/notification-service.yml,"
  echo "   then: docker compose up -d --build config-server notification-service   and run this again."
  echo "   Grafana: metric kafka_consumer_fetch_manager_records_lag_max (see docs/PHASE2_RUNBOOKS.md)."
}

s12_replay() {
  say "S12: rebuild notification records from Kafka history with a NEW consumer group (no emails)"
  local before; before=$(curl -s "$MAILHOG/api/v2/messages" | jq -r .total)
  docker compose --profile replay up -d --build notification-replay-db notification-replay
  echo "   emails in MailHog before: $before"
  chk() { [ "$(psql_in notification-replay-db notification_db 'select count(*) from notifications' 2>/dev/null || echo 0)" -ge 1 ]; }
  wait_for "replay consumer rebuilt notification rows in ITS OWN database" 180 chk
  sleep 10
  psql_in notification-replay-db notification_db "select status, count(*) from notifications group by status"
  local after; after=$(curl -s "$MAILHOG/api/v2/messages" | jq -r .total)
  echo "   emails in MailHog after: $after"
  [ "$before" = "$after" ] || die "the replay sent emails"
  ok "records rebuilt, zero emails sent"
  docker compose exec -T kafka "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server kafka:9092 --describe --group notification-replay-group | head -8
}

s13_contract() {
  say "S13: v1 and v2 BookingConfirmed side by side, plus an unknown field"
  local mail1="v1-$RANDOM@example.com" mail2="v2-$RANDOM@example.com" b1 b2; b1=$(uuid); b2=$(uuid)
  produce booking-confirmed "$b1" "{\"eventId\":\"$(uuid)\",\"eventType\":\"BookingConfirmed\",\"bookingId\":\"$b1\",\"customerEmail\":\"$mail1\",\"eventTitle\":\"Old\",\"venueName\":\"V\",\"seatLabels\":[\"A1\"],\"amount\":100.00,\"currency\":\"INR\"}"
  produce booking-confirmed "$b2" "$(confirmed_event "$(uuid)" "$b2" "$mail2" | sed 's/}$/,"somethingNew":"ignored"}/')"
  chk() { [ "$(mail_count "$mail1")" = 1 ] && [ "$(mail_count "$mail2")" = 1 ]; }
  wait_for "both versions were processed (v2 with an unknown extra field)" 60 chk
}

s14_chaos() {
  say "S14: everything breaks together. Run each drill and write an incident note (docs/INCIDENT_TEMPLATE.md)."
  s1_poison; s2_duplicate; s3_outbox; s5_late_payment; s6_order; s8_reconcile; s9_webhooks; s13_contract; s15_crash
  echo "Manual drills still to do: kill Booking mid-confirmation (docker compose kill booking-service), stop Notification AND the mail server,"
  echo "then s12_replay. For each: diagnose from Grafana, logs, Kafka UI and the database, recover, write the note."
}

case "${1:-}" in
  s0-inspect) s0_inspect ;; s1-poison) s1_poison ;; s2-duplicate) s2_duplicate ;; s3-outbox) s3_outbox ;;
  s4-booking-down) s4_booking_down ;; s5-late-payment) s5_late_payment ;; s6-order) s6_order ;;
  s7-saga-view) s7_saga_view ;; s8-reconcile) s8_reconcile ;; s9-webhooks) s9_webhooks ;;
  s10-mail-outage) s10_mail_outage ;; s11-lag) s11_lag ;; s12-replay) s12_replay ;;
  s13-contract) s13_contract ;; s14-chaos) s14_chaos ;; s15-crash) s15_crash ;;
  *) echo "usage: bash scripts/phase2-lab.sh <scenario>"
     echo "  s0-inspect s1-poison s2-duplicate s3-outbox s4-booking-down s5-late-payment s6-order s7-saga-view"
     echo "  s8-reconcile s9-webhooks s10-mail-outage s11-lag s12-replay s13-contract s14-chaos s15-crash"; exit 2 ;;
esac
