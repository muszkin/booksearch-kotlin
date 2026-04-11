#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"
HEALTH_URL="http://localhost:8080/api/health"
MAX_RETRIES=30
RETRY_INTERVAL=2

cleanup() {
    echo "=== Cleaning up ==="
    docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
}

trap cleanup EXIT

fail() {
    echo "FAIL: $1"
    exit 1
}

pass() {
    echo "PASS: $1"
}

echo "=== Test 1: Docker image builds successfully ==="
docker compose -f "$COMPOSE_FILE" build --no-cache \
    || fail "Docker image build failed"
pass "Docker image builds successfully"

echo ""
echo "=== Test 2: Container exposes port 8080 ==="
EXPOSED_PORT=$(docker inspect --format='{{range $p, $conf := .Config.ExposedPorts}}{{$p}} {{end}}' \
    "$(docker compose -f "$COMPOSE_FILE" images -q backend 2>/dev/null || docker images -q booksearch-v2-backend:latest)" 2>/dev/null \
    | grep -c "8080/tcp" || true)

if [ "$EXPOSED_PORT" -eq 0 ]; then
    EXPOSED_PORT=$(docker image inspect --format='{{range $p, $conf := .Config.ExposedPorts}}{{$p}} {{end}}' \
        booksearch-v2-backend:latest 2>/dev/null | grep -c "8080/tcp" || true)
fi

[ "$EXPOSED_PORT" -ge 1 ] || fail "Port 8080 is not exposed in the Docker image"
pass "Container exposes port 8080"

echo ""
echo "=== Test 3: Health endpoint responds ==="
docker compose -f "$COMPOSE_FILE" up -d

retries=0
while [ $retries -lt $MAX_RETRIES ]; do
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
    if [ "$HTTP_STATUS" = "200" ]; then
        break
    fi
    retries=$((retries + 1))
    sleep $RETRY_INTERVAL
done

[ "$HTTP_STATUS" = "200" ] || fail "Health endpoint did not return 200 (got: ${HTTP_STATUS}) after ${MAX_RETRIES} retries"
pass "Health endpoint responds with HTTP 200"

echo ""
echo "=== All Docker tests passed ==="
