#!/usr/bin/env bash
# ST-62: production-style end-to-end flow through the gateway with JWTs.
# Requires: docker compose stack running, curl, jq, openssl. Reads secrets from .env.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { set -a; . ./.env; set +a; }
BASE="${BASE:-http://localhost:8080}"
MAILHOG="${MAILHOG:-http://localhost:8025}"
: "${DEV_ADMIN_KEY:?DEV_ADMIN_KEY missing (.env)}"
: "${PAYMENT_WEBHOOK_SECRET:?PAYMENT_WEBHOOK_SECRET missing (.env)}"

STATUS=""; BODY=""
call() {   # call METHOD PATH TOKEN [BODY] [extra headers...]
  local method="$1" path="$2" token="$3" body="${4:-}"
  local extra=("${@:5}")
  local args=(-sS -X "$method" "$BASE$path" -o /tmp/th_body -w '%{http_code}' -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  for h in "${extra[@]:-}"; do [ -n "$h" ] && args+=(-H "$h"); done
  STATUS=$(curl "${args[@]}"); BODY=$(cat /tmp/th_body)
}
expect() { [ "$STATUS" = "$1" ] || { echo "FAIL ($2): expected HTTP $1, got $STATUS: $BODY"; exit 1; }; echo "  ok [$STATUS] $2"; }
field() { echo "$BODY" | jq -r "$1"; }
# Phase 2: confirmation is now EVENT-driven (Payment -> Kafka -> Booking), so it is eventually
# consistent. Poll instead of assuming the booking is CONFIRMED the instant payment returns.
wait_booking() {   # wait_booking BOOKING_ID TOKEN EXPECTED_STATUS [seconds]
  local i
  for i in $(seq 1 "${4:-40}"); do
    call GET "/bookings/$1" "$2"
    [ "$(field .status)" = "$3" ] && return 0
    sleep 1
  done
  echo "FAIL: booking $1 did not reach $3 in time (last status: $(field .status))"; exit 1
}
wait_saga() {      # wait_saga BOOKING_ID TOKEN EXPECTED_SAGA_STATE [seconds]
  local i
  for i in $(seq 1 "${4:-40}"); do
    call GET "/bookings/$1/saga" "$2"
    [ "$(field .sagaState)" = "$3" ] && return 0
    sleep 1
  done
  echo "FAIL: booking $1 saga did not reach $3 in time (last: $(field .sagaState))"; exit 1
}
iso() { date -u -d "$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v"${2}" +%Y-%m-%dT%H:%M:%SZ; }

echo "== 1. tokens (AUTH_MODE=${AUTH_MODE:-dev}: dev = gateway dev endpoint, keycloak = real Keycloak login)"
if [ "${AUTH_MODE:-dev}" = "keycloak" ]; then
  KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
  kc_token() {   # kc_token USER PASSWORD -> access token from Keycloak (password grant, dev realm only)
    curl -sS -X POST "$KEYCLOAK_URL/realms/tickethub/protocol/openid-connect/token" \
      -d grant_type=password -d client_id=tickethub-web -d "username=$1" -d "password=$2" | jq -r '.access_token // empty'
  }
  ADMIN=$(kc_token admin "${KC_ADMIN_PASSWORD:-Admin@12345}"); [ -n "$ADMIN" ] || { echo "FAIL: no Keycloak token for admin (is the iam profile up and KEYCLOAK_ENABLED=true?)"; exit 1; }; echo "  ok keycloak admin token"
  ALICE=$(kc_token alice "${KC_ALICE_PASSWORD:-Alice@12345}"); [ -n "$ALICE" ] || { echo "FAIL: no Keycloak token for alice"; exit 1; }; echo "  ok keycloak alice token"
  BOB=$(kc_token bob "${KC_BOB_PASSWORD:-Bob@12345}"); [ -n "$BOB" ] || { echo "FAIL: no Keycloak token for bob"; exit 1; }; echo "  ok keycloak bob token"
else
  call POST /auth/token "" "{\"userId\":\"admin-1\",\"email\":\"admin@tickethub.local\",\"roles\":[\"ADMIN\"],\"adminKey\":\"$DEV_ADMIN_KEY\"}"; expect 200 "admin token"; ADMIN=$(field .token)
  call POST /auth/token "" '{"userId":"alice","email":"alice@example.com"}'; expect 200 "alice token"; ALICE=$(field .token)
  call POST /auth/token "" '{"userId":"bob","email":"bob@example.com"}'; expect 200 "bob token"; BOB=$(field .token)
fi

echo "== 2. admin builds the catalogue"
call POST /events "$ADMIN" '{"title":"Space Odyssey Premiere","description":"Opening night","type":"MOVIE"}'; expect 201 "create event (DRAFT)"; EVENT=$(field .id)
call PATCH "/events/$EVENT/status" "$ADMIN" '{"status":"PUBLISHED"}'; expect 200 "publish event"
if [ "${CHECK_POSTER:-1}" = "1" ]; then
  printf '\x89PNG\r\n\x1a\n' > /tmp/th_poster.png
  STATUS=$(curl -sS -X POST "$BASE/events/$EVENT/poster" -H "Authorization: Bearer $ADMIN" -F "file=@/tmp/th_poster.png;type=image/png" -o /tmp/th_body -w '%{http_code}'); BODY=$(cat /tmp/th_body)
  expect 200 "ST-30 poster uploaded to object storage"
  POSTER=$(field .posterUrl); [ -n "$POSTER" ] && [ "$POSTER" != null ] || { echo "FAIL: no posterUrl returned"; exit 1; }
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$POSTER")" = 200 ] || { echo "FAIL: poster URL not publicly readable: $POSTER"; exit 1; }
  echo "  ok poster is publicly readable: $POSTER"
fi
call POST /venues "$ADMIN" '{"name":"Grand Cinema","city":"Bengaluru","address":"MG Road"}'; expect 201 "create venue"; VENUE=$(field .id)
call POST "/venues/$VENUE/seats" "$ADMIN" '{"rows":[{"rowLabel":"A","seatCount":6,"category":"STANDARD"}]}'; expect 201 "seat layout"
START=$(iso '+7 days' '+7d'); END=$(iso '+7 days 3 hours' '+7d -v+3H')
call POST /shows "$ADMIN" "{\"eventId\":$EVENT,\"venueId\":$VENUE,\"startTime\":\"$START\",\"endTime\":\"$END\",\"ticketPrice\":250.00,\"currency\":\"INR\"}"; expect 201 "create show"; SHOW=$(field .id)
call PATCH "/shows/$SHOW/status" "$ADMIN" '{"status":"OPEN"}'; expect 200 "open show"

echo "== 3. customer browses (public) and sees seats"
call GET /events "" ; expect 200 "browse events (no token)"
call GET "/shows/$SHOW/seats" ""; expect 200 "seat availability"
[ "$(field '[.seats[]|select(.state=="AVAILABLE")]|length')" = 6 ] || { echo "FAIL: expected 6 available seats"; exit 1; }
S1=$(field '.seats[0].seatId'); S2=$(field '.seats[1].seatId'); S3=$(field '.seats[2].seatId'); S4=$(field '.seats[3].seatId')

echo "== 4. SC-01 happy path: reserve -> pay -> confirmed -> email"
KEY="e2e-$(date +%s)"
call POST /bookings "$ALICE" "{\"showId\":$SHOW,\"seatIds\":[$S1,$S2]}" "Idempotency-Key: $KEY"; expect 201 "alice books 2 seats (PENDING_PAYMENT)"
BOOKING=$(field .bookingId); [ "$(field .status)" = PENDING_PAYMENT ] || exit 1
[ "$(field '.amount == 500')" = true ] || { echo "FAIL amount (expected 2 x 250): $(field .amount)"; exit 1; }
call POST /bookings "$ALICE" "{\"showId\":$SHOW,\"seatIds\":[$S1,$S2]}" "Idempotency-Key: $KEY"; expect 200 "SC-13 retry with same key returns original"
[ "$(field .bookingId)" = "$BOOKING" ] || { echo "FAIL: retry created a different booking"; exit 1; }
call POST /bookings "$BOB" "{\"showId\":$SHOW,\"seatIds\":[$S2]}"; expect 409 "SC-02/03 bob cannot take a reserved seat"
[ "$(field .code)" = SEAT_UNAVAILABLE ] || exit 1
call POST /payments "$ALICE" "{\"bookingId\":\"$BOOKING\"}"; expect 201 "alice pays (simulated provider)"
[ "$(field .status)" = SUCCESS ] || { echo "FAIL: payment not SUCCESS"; exit 1; }
PAYMENT=$(field .paymentId)
call POST /payments "$ALICE" "{\"bookingId\":\"$BOOKING\"}"; expect 200 "repeated initiation returns the same payment"
wait_booking "$BOOKING" "$ALICE" CONFIRMED; expect 200 "booking became CONFIRMED (via PaymentSucceeded event)"
call GET "/bookings/$BOOKING" "$BOB"; expect 404 "bob cannot see alice's booking"
call GET "/payments/$PAYMENT" "$BOB"; expect 404 "bob cannot see alice's payment"
call GET "/shows/$SHOW/seats" ""; [ "$(field '[.seats[]|select(.state=="BOOKED")]|length')" = 2 ] || { echo "FAIL: expected 2 BOOKED seats"; exit 1; }
echo "  waiting for Kafka -> Notification -> MailHog email ..."
for i in $(seq 1 30); do
  TOTAL=$(curl -s "$MAILHOG/api/v2/messages" | jq -r '.total' 2>/dev/null || echo 0)
  [ "${TOTAL:-0}" -ge 1 ] && break; sleep 2
done
[ "${TOTAL:-0}" -ge 1 ] || { echo "FAIL: no email arrived in MailHog ($MAILHOG)"; exit 1; }
echo "  ok confirmation email delivered ($TOTAL message(s) in MailHog)"
call GET "/notifications?bookingId=$BOOKING" "$ADMIN"; expect 200 "admin sees notification record"; [ "$(field '([.content[]|select(.channel=="EMAIL")][0].status)')" = SENT ] || { echo "FAIL: email notification not SENT"; exit 1; }
[ "$(field '([.content[]|select(.channel=="PUSH")][0].status)')" = SENT ] || { echo "FAIL: ST-79 push notification not SENT"; exit 1; }
echo "  ok ST-79 both channels delivered (EMAIL via SMTP, PUSH simulated)"

echo "== 5. SC-04 payment fails -> seats released"
call POST /bookings "$BOB" "{\"showId\":$SHOW,\"seatIds\":[$S3]}"; expect 201 "bob books seat"; B2=$(field .bookingId)
call POST /payments "$BOB" "{\"bookingId\":\"$B2\",\"simulatedOutcome\":\"FAILURE\"}"; expect 201 "payment attempt"; [ "$(field .status)" = FAILED ] || exit 1
wait_booking "$B2" "$BOB" PAYMENT_FAILED; expect 200 "booking became PAYMENT_FAILED (via PaymentFailed event)"
call POST /bookings "$ALICE" "{\"showId\":$SHOW,\"seatIds\":[$S3]}"; expect 201 "seat is bookable again after failed payment"
call POST "/bookings/$(field .bookingId)/cancel" "$ALICE"; expect 200 "cancel pending booking releases seat"

echo "== 6. SC-10 webhook: signed, idempotent, rejects forgeries"
call POST /bookings "$BOB" "{\"showId\":$SHOW,\"seatIds\":[$S4]}"; expect 201 "bob books seat"; B3=$(field .bookingId)
call POST /payments "$BOB" "{\"bookingId\":\"$B3\",\"simulatedOutcome\":\"PENDING_WEBHOOK\"}"; expect 201 "payment pending webhook"; [ "$(field .status)" = PENDING ] || exit 1
REF=$(field .providerReference)
PAYLOAD="{\"eventId\":\"evt-$(date +%s%N)\",\"providerReference\":\"$REF\",\"status\":\"SUCCEEDED\"}"
SIG=$(printf '%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$PAYMENT_WEBHOOK_SECRET" -hex | sed 's/^.* //')
call POST /payments/webhook "" "$PAYLOAD" "X-Signature: sha256=bad$SIG"; expect 401 "forged signature rejected"
call POST /payments/webhook "" "$PAYLOAD" "X-Signature: sha256=$SIG"; expect 200 "valid webhook applied"; [ "$(field .outcome)" = APPLIED ] || exit 1
call POST /payments/webhook "" "$PAYLOAD" "X-Signature: sha256=$SIG"; expect 200 "duplicate webhook"; [ "$(field .outcome)" = DUPLICATE_IGNORED ] || exit 1
wait_booking "$B3" "$BOB" CONFIRMED; expect 200 "webhook confirmed the booking (event-driven)"

echo "== 7. negative / security checks"
call POST /bookings "" "{\"showId\":$SHOW,\"seatIds\":[$S1]}"; expect 401 "no token -> 401"
call POST /events "$ALICE" '{"title":"x","type":"MOVIE"}'; expect 403 "customer cannot create events -> 403"
call POST /events "$ADMIN" '{"title":"","type":"MOVIE"}'; expect 400 "validation error -> 400 with fieldErrors"; [ "$(field .code)" = VALIDATION_ERROR ] || exit 1
call GET /events/999999 ""; expect 404 "unknown id -> 404"
call GET /internal/shows/1 ""; expect 404 "/internal not exposed"
call POST /bookings "$ALICE" "{\"showId\":$SHOW,\"seatIds\":[999999]}"; expect 422 "seat not in show -> business error"

echo "== 8. Phase 2: saga view, cancel a CONFIRMED booking -> refund (compensation)"
call GET "/bookings/$BOOKING/saga" "$ALICE"; expect 200 "S7 saga view answers 'where is my booking?'"
[ "$(field .sagaState)" = CONFIRMED ] || { echo "FAIL: expected saga CONFIRMED, got $(field .sagaState)"; exit 1; }
call POST "/bookings/$BOOKING/cancel" "$ALICE"; expect 200 "S5 customer cancels the CONFIRMED booking"
[ "$(field .status)" = CANCELLED ] || { echo "FAIL: booking not CANCELLED"; exit 1; }
wait_saga "$BOOKING" "$ALICE" COMPENSATED 60; echo "  ok refund completed, saga COMPENSATED"
call GET "/payments/$PAYMENT" "$ALICE"; [ "$(field .status)" = REFUNDED ] || { echo "FAIL: payment not REFUNDED (is $(field .status))"; exit 1; }
echo "  ok payment REFUNDED"
call GET "/shows/$SHOW/seats" ""; [ "$(field '[.seats[]|select(.state=="BOOKED")]|length')" = 1 ] || { echo "FAIL: seats of the cancelled booking were not freed"; exit 1; }
echo "  ok seats of the cancelled booking are free again"

echo
echo "E2E PASSED. Explore: Eureka http://localhost:8761  MailHog $MAILHOG  Kafka UI http://localhost:8090"
