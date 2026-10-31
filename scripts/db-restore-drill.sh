#!/usr/bin/env bash
# ST-78: backup and RESTORE drill for the local Postgres containers. A backup you never restored is not a backup.
# Takes a dump of booking_db, restores it into a scratch database, compares row counts, then drops the scratch DB.
# Usage: bash scripts/db-restore-drill.sh [service-db-container] [database]      default: booking-db booking_db
# (On AWS the same drill is: restore an RDS snapshot / point-in-time to a NEW instance and compare, see docs/HARDENING.md.)
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { set -a; . ./.env; set +a; }
SVC="${1:-booking-db}"; DB="${2:-booking_db}"; USER_="${POSTGRES_USER:-tickethub}"; TMPDB="${DB}_restore_drill"
p() { docker compose exec -T "$SVC" psql -U "$USER_" -d postgres -At -c "$1"; }
echo "1. dump $DB"
docker compose exec -T "$SVC" pg_dump -U "$USER_" -Fc "$DB" > "/tmp/${DB}.dump"
echo "   dump size: $(wc -c < "/tmp/${DB}.dump") bytes"
echo "2. restore into scratch database $TMPDB"
p "drop database if exists $TMPDB" >/dev/null
p "create database $TMPDB" >/dev/null
docker compose exec -T "$SVC" pg_restore -U "$USER_" -d "$TMPDB" --no-owner < "/tmp/${DB}.dump"
echo "3. compare row counts of every table"
TABLES=$(docker compose exec -T "$SVC" psql -U "$USER_" -d "$DB" -At -c "select tablename from pg_tables where schemaname='public' order by 1")
BAD=0
for t in $TABLES; do
  a=$(docker compose exec -T "$SVC" psql -U "$USER_" -d "$DB" -At -c "select count(*) from \"$t\"")
  b=$(docker compose exec -T "$SVC" psql -U "$USER_" -d "$TMPDB" -At -c "select count(*) from \"$t\"")
  [ "$a" = "$b" ] && echo "   ok   $t: $a rows" || { echo "   DIFF $t: original=$a restored=$b"; BAD=1; }
done
p "drop database $TMPDB" >/dev/null; rm -f "/tmp/${DB}.dump"
[ "$BAD" = 0 ] && echo "RESTORE DRILL PASSED" || { echo "RESTORE DRILL FAILED"; exit 1; }
