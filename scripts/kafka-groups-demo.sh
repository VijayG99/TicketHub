#!/usr/bin/env bash
# ST-60: topics, partitions and consumer groups, hands-on. Run while the stack is up.
# Terminal A: two consumers in ONE group share the partitions.
# Terminal B: a consumer in a SECOND group receives every message.
set -euo pipefail
KC="docker compose exec -T kafka /opt/kafka/bin"
TOPIC=booking-confirmed
echo "==> topic description (3 partitions)"
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic "$TOPIC"
echo
echo "Start these in separate terminals, then run scripts/e2e.sh (or create bookings) and watch:"
echo "  # two members of group 'demo-group-a' -> partitions are split between them"
echo "  docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $TOPIC --group demo-group-a --property print.partition=true"
echo "  docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $TOPIC --group demo-group-a --property print.partition=true"
echo "  # one member of a different group -> receives ALL messages independently"
echo "  docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $TOPIC --group demo-group-b --from-beginning --property print.partition=true"
echo
echo "==> group membership / lag (notification-service-group is the real consumer)"
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service-group || true
