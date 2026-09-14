#!/usr/bin/env bash
# Stops and removes the PostgreSQL container started by db-up.sh.
set -euo pipefail

CONTAINER_NAME="${PARK_DB_CONTAINER:-park-db}"

if command -v podman >/dev/null 2>&1; then
    RUNTIME=podman
elif command -v docker >/dev/null 2>&1; then
    RUNTIME=docker
else
    echo "ERROR: neither podman nor docker is installed." >&2
    exit 1
fi

$RUNTIME rm -f "$CONTAINER_NAME" >/dev/null 2>&1 && echo "Removed $CONTAINER_NAME" || echo "Nothing to remove"
