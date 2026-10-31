#!/usr/bin/env bash
# ST-34: tagged release. Usage: scripts/release.sh v1.0.0
# Builds images tagged with the version, deploys them, then verifies with the smoke test.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="${1:?usage: scripts/release.sh <version-tag, e.g. v1.0.0>}"

git tag -a "$VERSION" -m "TicketHub $VERSION" 2>/dev/null || echo "(git tag $VERSION already exists or git unavailable)"
export TICKETHUB_TAG="$VERSION"
echo "==> Building and deploying release $VERSION"
docker compose build
docker compose up -d
echo "$VERSION $(date -u +%FT%TZ)" >> .release-history
scripts/smoke-test.sh
echo "==> Release $VERSION deployed and verified. History:"; cat .release-history
