#!/usr/bin/env bash
# Runs every integration test against a real PostgreSQL: the API walking-skeleton tests
# and the C01 persistence spike.
#
#   ./scripts/integration-tests.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

cleanup() {
    ./scripts/db-down.sh || true
}
trap cleanup EXIT

./scripts/db-up.sh
./mvnw -B -Pit test
