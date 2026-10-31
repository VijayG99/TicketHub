#!/usr/bin/env bash
# ST-08: N customers try to reserve the SAME seat at the same moment. Exactly one may win.
# Usage: bash scripts/concurrency-test.sh [number-of-customers, default 20]
# Requires: running stack with DEV_TOKEN_ENABLED=true, curl, jq. Reads secrets from .env.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { set -a; . ./.env; set +a; }
BASE="${BASE:-http://localhost:8080}"
N="${1:-20}"
: "${DEV_ADMIN_KEY:?DEV_ADMIN_KEY missing (.env)}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

api() {   # api METHOD PATH TOKEN [BODY] -> prints body, sets $TMP/status
  local m="$1" p="$2" t="$3" b="${4:-}"
  local args=(-sS -X "$m" "$BASE$p" -o "$TMP/body" -w '%{http_code}' -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$b" ] && args+=(-d "$b")
  curl "${args[@]}" > "$TMP/status"; cat "$TMP/body"
}
iso() { date -u -d "$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v"${2}" +%Y-%m-%dT%H:%M:%SZ; }

echo "== setup: admin, event, venue with 3 seats, open show"
ADMIN=$(api POST /auth/token "" "{\"userId\":\"admin-1\",\"email\":\"admin@tickethub.local\",\"roles\":[\"ADMIN\"],\"adminKey\":\"$DEV_ADMIN_KEY\"}" | jq -r .token)
EVENT=$(api POST /events "$ADMIN" '{"title":"Concurrency Night","type":"CONCERT"}' | jq -r .id)
api PATCH "/events/$EVENT/status" "$ADMIN" '{"status":"PUBLISHED"}' >/dev/null
VENUE=$(api POST /venues "$ADMIN" '{"name":"Race Hall","city":"Pune","address":"FC Road"}' | jq -r .id)
api POST "/venues/$VENUE/seats" "$ADMIN" '{"rows":[{"rowLabel":"A","seatCount":3,"category":"STANDARD"}]}' >/dev/null
START=$(iso '+7 days' '+7d'); END=$(iso '+7 days 3 hours' '+7d -v+3H')
SHOW=$(api POST /shows "$ADMIN" "{\"eventId\":$EVENT,\"venueId\":$VENUE,\"startTime\":\"$START\",\"endTime\":\"$END\",\"ticketPrice\":100.00,\"currency\":\"INR\"}" | jq -r .id)
api PATCH "/shows/$SHOW/status" "$ADMIN" '{"status":"OPEN"}' >/dev/null
SEAT=$(api GET "/shows/$SHOW/seats" "" | jq -r '.seats[0].seatId')
echo "   show=$SHOW contested seat=$SEAT customers=$N"

echo "== creating $N customer tokens"
for i in $(seq 1 "$N"); do
  api POST /auth/token "" "{\"userId\":\"racer-$i\",\"email\":\"racer$i@example.com\"}" | jq -r .token > "$TMP/token_$i"
done

echo "== firing $N simultaneous booking requests for seat $SEAT"
for i in $(seq 1 "$N"); do
  (
    curl -sS -o "$TMP/out_$i" -w '%{http_code}' -X POST "$BASE/bookings" \
      -H 'Content-Type: application/json' -H "Authorization: Bearer $(cat "$TMP/token_$i")" \
      -d "{\"showId\":$SHOW,\"seatIds\":[$SEAT]}" > "$TMP/code_$i"
  ) &
done
wait

WON=0; LOST=0; OTHER=0
for i in $(seq 1 "$N"); do
  case "$(cat "$TMP/code_$i")" in
    201) WON=$((WON+1)) ;;
    409) LOST=$((LOST+1)) ;;
    *)   OTHER=$((OTHER+1)); echo "   unexpected response for racer-$i: $(cat "$TMP/code_$i") $(cat "$TMP/out_$i")" ;;
  esac
done
echo "   201 (reserved): $WON    409 (seat unavailable): $LOST    other: $OTHER"

AVAILABLE=$(api GET "/shows/$SHOW/seats" "" | jq '[.seats[]|select(.state=="AVAILABLE")]|length')
echo "   seats still available: $AVAILABLE of 3 (expected 2)"

if [ "$WON" = 1 ] && [ "$LOST" = "$((N-1))" ] && [ "$OTHER" = 0 ] && [ "$AVAILABLE" = 2 ]; then
  echo "CONCURRENCY TEST PASSED: exactly one customer got the seat."
else
  echo "CONCURRENCY TEST FAILED"; exit 1
fi
