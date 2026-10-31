#!/usr/bin/env bash
# ST-33: smoke test run after every deployment. Verifies the platform is up and routing works.
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
EUREKA="${EUREKA:-http://localhost:8761}"
fail() { echo "SMOKE FAIL: $1"; exit 1; }

echo "==> waiting for the gateway (public catalogue route answers)"
# The gateway's actuator now lives on an unpublished management port, so readiness is checked
# through a public business route instead.
for i in $(seq 1 60); do
  curl -fs "$BASE/events" >/dev/null 2>&1 && break
  [ "$i" = 60 ] && fail "gateway not healthy"
  sleep 3
done

echo "==> all business services registered in Eureka"
for svc in EVENT-SERVICE BOOKING-SERVICE PAYMENT-SERVICE NOTIFICATION-SERVICE APIGATE-SERVICE; do
  for i in $(seq 1 30); do
    curl -fs -H 'Accept: application/json' "$EUREKA/eureka/apps/$svc" >/dev/null 2>&1 && break
    [ "$i" = 30 ] && fail "$svc not registered"
    sleep 2
  done
  echo "   $svc registered"
done

echo "==> public browse route works through the gateway"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/events")
[ "$code" = 200 ] || fail "GET /events returned $code"

echo "==> protected route rejects anonymous callers"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/bookings" -H 'Content-Type: application/json' -d '{}')
[ "$code" = 401 ] || fail "POST /bookings without token returned $code (expected 401)"

echo "==> internal APIs are not reachable through the gateway"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/internal/shows/1")
[ "$code" = 404 ] || fail "/internal route returned $code (expected 404)"

echo "SMOKE TEST PASSED"
