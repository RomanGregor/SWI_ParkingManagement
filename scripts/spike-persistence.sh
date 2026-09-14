#!/usr/bin/env bash
# C01 engineering spike, variant A (persistence).
#
# Starts a real PostgreSQL, runs ReservationPersistenceSpikeIT against it, and writes the
# full output to docs/evidence/spike-a-persistence-run.log. Stops the database afterwards.
#
#   ./scripts/spike-persistence.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="$REPO_ROOT/docs/evidence/spike-a-persistence-run.log"
cd "$REPO_ROOT"

mkdir -p "$(dirname "$EVIDENCE")"

cleanup() {
    ./scripts/db-down.sh || true
}
trap cleanup EXIT

{
    echo "=== C01 spike A (persistence) ==="
    echo "date:  $(date --iso-8601=seconds)"
    echo "java:  $(java -version 2>&1 | head -1)"
    echo

    ./scripts/db-up.sh
    echo

    ./mvnw -B -Pspike test
} 2>&1 | tee "$EVIDENCE"

echo
echo "Evidence written to $EVIDENCE"
