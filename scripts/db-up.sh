#!/usr/bin/env bash
# Starts the PostgreSQL instance the application and the persistence spike use.
# Works with either podman or docker.
set -euo pipefail

CONTAINER_NAME="${PARK_DB_CONTAINER:-park-db}"
PORT="${PARK_DB_PORT:-55432}"
IMAGE="${PARK_DB_IMAGE:-docker.io/library/postgres:16-alpine}"

if command -v podman >/dev/null 2>&1; then
    RUNTIME=podman
elif command -v docker >/dev/null 2>&1; then
    RUNTIME=docker
else
    echo "ERROR: neither podman nor docker is installed." >&2
    exit 1
fi

if $RUNTIME ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    echo "Removing previous container $CONTAINER_NAME"
    $RUNTIME rm -f "$CONTAINER_NAME" >/dev/null
fi

echo "Starting PostgreSQL ($RUNTIME) as $CONTAINER_NAME on port $PORT"
$RUNTIME run -d --name "$CONTAINER_NAME" \
    -e POSTGRES_DB=parkdb \
    -e POSTGRES_USER=park \
    -e POSTGRES_PASSWORD=park \
    -p "${PORT}:5432" \
    "$IMAGE" >/dev/null

echo -n "Waiting for the database to accept connections"
for _ in $(seq 1 60); do
    if $RUNTIME exec "$CONTAINER_NAME" pg_isready -U park -d parkdb >/dev/null 2>&1; then
        echo " - ready"
        exit 0
    fi
    echo -n "."
    sleep 1
done

echo
echo "ERROR: database did not become ready in 60s" >&2
$RUNTIME logs "$CONTAINER_NAME" >&2
exit 1
