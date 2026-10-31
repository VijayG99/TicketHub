#!/usr/bin/env bash
# ST-29: production-incident lab. Injects a real failure, lets you investigate it with logs, metrics and
# traces, then repairs it and checks the system self-healed.
#
# Usage: bash scripts/incident-lab.sh payment-sync     # booking-service down while a payment succeeds (SC-06)
#        bash scripts/incident-lab.sh mail-outage      # email provider down while a booking is confirmed (SC-07)
# Requires: running stack, DEV_TOKEN_ENABLED=true, docker, curl, jq, openssl. Reads secrets from .env.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { set -a; . ./.env; set +a; }
BASE="${BASE:-http://localhost:8080}"
MAILHOG="${MAILHOG:-http://localhost:8025}"
SCENARIO="${1:-}"
: "${DEV_ADMIN_KEY:?DEV_ADMIN_KEY missing (.env)}"
: "${PAYMENT_WEBHOOK_SECRET:?PAYMENT_WEBHOOK_SECRET missing (.env)}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

api() { local m="$1" p="$2" t="$3" b="${4:-}"; local a=(-sS -X "$m" "$BASE$p" -H 'Content-Type: application/json'); [ -n "$t" ] && a+=(-H "Authorization: Bearer $t"); [ -n "$b" ] && a+=(-d "$b"); curl "${a[@]}"; }
iso() { date -u -d "$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v"${2}" +%Y-%m-%dT%H:%M:%SZ; }
say() { echo; echo "### $*"; }

setup() {
  ADMIN=$(api POST /auth/token "" "{\"userId\":\"admin-1\",\"email\":\"admin@tickethub.local\",\"roles\":[\"ADMIN\"],\"adminKey\":\"$DEV_ADMIN_KEY\"}" | jq -r .token)
  USER=$(api POST /auth/token "" '{"userId":"incident-user","email":"incident@example.com"}' | jq -r .token)
  EVENT=$(api POST /events "$ADMIN" '{"title":"Incident Lab Show","type":"THEATRE"}' | jq -r .id)
  api PATCH "/events/$EVENT/status" "$ADMIN" '{"status":"PUBLISHED"}' >/dev/null
  VENUE=$(api POST /venues "$ADMIN" '{"name":"Lab Hall","city":"Chennai","address":"Anna Salai"}' | jq -r .id)
  api POST "/venues/$VENUE/seats" "$ADMIN" '{"rows":[{"rowLabel":"A","seatCount":4,"category":"STANDARD"}]}' >/dev/null
  SHOW=$(api POST /shows "$ADMIN" "{\"eventId\":$EVENT,\"venueId\":$VENUE,\"startTime\":\"$(iso '+7 days' '+7d')\",\"endTime\":\"$(iso '+7 days 3 hours' '+7d -v+3H')\",\"ticketPrice\":150.00,\"currency\":\"INR\"}" | jq -r .id)
  api PATCH "/shows/$SHOW/status" "$ADMIN" '{"status":"OPEN"}' >/dev/null
  SEATS=$(api GET "/shows/$SHOW/seats" "" | jq -r '[.seats[].seatId]|map(tostring)|join(",")')
}

wait_for() {  # wait_for "description" seconds command...
  local what="$1" secs="$2"; shift 2
  for _ in $(seq 1 "$secs"); do "$@" && { echo "   OK: $what"; return 0; }; sleep 1; done
  echo "   TIMEOUT waiting for: $what"; return 1
}

case "$SCENARIO" in
payment-sync)
  say "Scenario: booking-service is DOWN while a payment succeeds"
  setup
  S1=${SEATS%%,*}
  BOOKING=$(api POST /bookings "$USER" "{\"showId\":$SHOW,\"seatIds\":[$S1]}" | jq -r .bookingId)
  PAY=$(api POST /payments "$USER" "{\"bookingId\":\"$BOOKING\",\"simulatedOutcome\":\"PENDING_WEBHOOK\"}")
  REF=$(echo "$PAY" | jq -r .providerReference)
  echo "   booking=$BOOKING  payment pending, providerReference=$REF"

  say "INJECT: stopping booking-service"
  docker compose stop booking-service >/dev/null

  PAYLOAD="{\"eventId\":\"lab-$(date +%s%N)\",\"providerReference\":\"$REF\",\"status\":\"SUCCEEDED\"}"
  SIG=$(printf '%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$PAYMENT_WEBHOOK_SECRET" -hex | sed 's/^.* //')
  echo "   provider webhook says SUCCEEDED -> payment-service publishes PaymentSucceeded to Kafka (booking-service is down, the event waits)"
  api POST /payments/webhook "" "$PAYLOAD" >/dev/null 2>&1 || true   # unsigned attempt is rejected (401)
  curl -sS -X POST "$BASE/payments/webhook" -H 'Content-Type: application/json' -H "X-Signature: sha256=$SIG" -d "$PAYLOAD"; echo

  say "SYMPTOM: customer paid, but the booking is not confirmed"
  echo "   (booking-service is down, so you cannot read it. The payment itself is safe:)"
  PID=$(echo "$PAY" | jq -r .paymentId)
  echo "   payment status: $(api GET "/payments/$PID" "$USER" | jq -r .status)"
  cat <<'TXT'

   INVESTIGATE NOW (the payment event is waiting safely in Kafka):
   - Logs     : docker compose logs payment-service | grep -i "outbox"
                (Kibana, if you run the elk profile: service:"payment-service" AND message:"Outbox")
   - Metrics  : Grafana http://localhost:3000 -> "Circuit breaker state" and "5xx error rate"
   - Traces   : Jaeger http://localhost:16686 -> service payment-service -> the POST /payments/webhook trace
   - Question : is any money or any seat lost? (no: payment is SUCCESS, the seat is still reserved)
TXT
  say "REPAIR: starting booking-service again"
  docker compose start booking-service >/dev/null
  echo "   waiting for booking-service to become healthy and consume the waiting PaymentSucceeded event ..."
  check() { [ "$(api GET "/bookings/$BOOKING" "$USER" 2>/dev/null | jq -r .status 2>/dev/null)" = CONFIRMED ]; }
  wait_for "booking became CONFIRMED from the event that waited in Kafka" 240 check && echo "INCIDENT RESOLVED: the system self-healed, nothing was lost." || { echo "NOT RESOLVED: check docker compose logs payment-service booking-service"; exit 1; }
  ;;

mail-outage)
  say "Scenario: the email provider (MailHog) is DOWN while a booking is confirmed"
  setup
  S1=${SEATS%%,*}
  say "INJECT: stopping mailhog"
  docker compose stop mailhog >/dev/null
  BOOKING=$(api POST /bookings "$USER" "{\"showId\":$SHOW,\"seatIds\":[$S1]}" | jq -r .bookingId)
  api POST /payments "$USER" "{\"bookingId\":\"$BOOKING\"}" >/dev/null
  echo "   booking status: $(api GET "/bookings/$BOOKING" "$USER" | jq -r .status)   (must still be CONFIRMED: email is not allowed to break a booking)"
  sleep 8
  say "SYMPTOM: no confirmation email"
  echo "   notification record (RETRYING or DEAD): $(api GET "/notifications?bookingId=$BOOKING" "$ADMIN" | jq -r '([.content[]|select(.channel=="EMAIL")][0].status) + " attempts=" + (([.content[]|select(.channel=="EMAIL")][0].attempts|tostring)')"
  cat <<'TXT'

   INVESTIGATE:
   - Logs   : docker compose logs notification-service | grep -i "FAILED"
   - Traces : Jaeger -> trace that starts at POST /payments and continues through Kafka into notification-service
   - Admin  : GET /notifications?bookingId=<id> shows FAILED + failureReason
TXT
  say "REPAIR: starting mailhog; the Kafka retry topics redeliver (3s, 6s, 12s), then the message waits in the dead-letter topic; replay it from the Dead Letters API"
  docker compose start mailhog >/dev/null
  check() {
    local st; st=$(api GET "/notifications?bookingId=$BOOKING" "$ADMIN" 2>/dev/null | jq -r '([.content[]|select(.channel=="EMAIL")][0].status)' 2>/dev/null)
    if [ "$st" = DEAD ]; then   # all automatic retries were used while the mail server was down: replay from the dead-letter list
      echo "   status DEAD -> replaying parked dead letters"
      for id in $(api GET "/notifications/dead-letters?status=PARKED" "$ADMIN" | jq -r '.content[].id'); do
        api POST "/notifications/dead-letters/$id/replay" "$ADMIN" >/dev/null
      done
    fi
    [ "$st" = SENT ]
  }
  wait_for "notification became SENT after retry" 240 check && echo "INCIDENT RESOLVED: email delivered after the outage." || { echo "NOT RESOLVED: check docker compose logs notification-service"; exit 1; }
  ;;

*)
  echo "usage: bash scripts/incident-lab.sh payment-sync | mail-outage"; exit 2 ;;
esac
