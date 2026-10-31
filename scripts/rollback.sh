#!/usr/bin/env bash
# ST-34: roll back to a previously built release. Usage: scripts/rollback.sh v0.9.0
# The previous images still exist locally (or in your registry), so rollback is a redeploy of that tag.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="${1:?usage: scripts/rollback.sh <previous-version-tag>}"
export TICKETHUB_TAG="$VERSION"
echo "==> Rolling back to $VERSION"
docker compose up -d --no-build
echo "$VERSION (rollback) $(date -u +%FT%TZ)" >> .release-history
scripts/smoke-test.sh
echo "==> Rollback to $VERSION verified."
