#!/bin/bash
set -e

FAILED=0

TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-}"
DOMAIN="${TLS_DOMAIN:-spendwiser.me}"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

send_alert() {
    [[ -z "$TELEGRAM_BOT_TOKEN" ]] && return
    curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
        -d "chat_id=${TELEGRAM_CHAT_ID}&text=$1&parse_mode=HTML" || true
}

log "=== Smoke Test Started ==="

# 1. Backend — check via nginx proxy on the exposed port
echo -n "Backend (via nginx): "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 \
    -X POST https://${DOMAIN}/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"smoke@test.com","password":"wrong"}' 2>/dev/null) || HTTP_CODE="DOWN"
if [[ "$HTTP_CODE" == "400" || "$HTTP_CODE" == "401" ]]; then
    echo "✅ $HTTP_CODE"
elif [[ "$HTTP_CODE" == "DOWN" ]]; then
    echo "❌ UNREACHABLE (nginx can't reach backend)"
    BACKEND_STATE=$(docker inspect --format='{{.State.Status}}' expense-backend 2>/dev/null || echo "unknown")
    BACKEND_LOGS=$(docker compose logs backend --tail=20 2>&1 | tr '\n' '|' | cut -c1-1000)
    send_alert "❌ SMOKE TEST FAILED: Backend unreachable | State: $BACKEND_STATE | Logs: $BACKEND_LOGS"
    FAILED=1
else
    echo "⚠️  HTTP $HTTP_CODE (nginx up, backend may be slow)"
fi

# 2. Frontend — check nginx on host port 80 (exposed in docker-compose)
echo -n "Frontend (localhost): "
FRONTEND=$(curl -sfk -o /dev/null -w "%{http_code}" --max-time 10 -L http://localhost/ 2>/dev/null) || FRONTEND="DOWN"
if [[ "$FRONTEND" == "200" ]]; then
    echo "✅ $FRONTEND"
else
    echo "❌ $FRONTEND"
    NGINX_LOGS=$(docker compose logs nginx --tail=10 2>&1 | tr '\n' '|' | cut -c1-500)
    send_alert "❌ SMOKE TEST FAILED: Frontend returned $FRONTEND | Nginx logs: $NGINX_LOGS"
    FAILED=1
fi

# 3. DB connectivity — check backend state
echo -n "Database: "
DB_STATE=$(docker inspect --format='{{.State.Health.Status}}' expense-db 2>/dev/null || echo "unknown")
if [[ "$DB_STATE" == "healthy" ]]; then
    echo "✅ $DB_STATE"
else
    echo "⚠️  DB state: $DB_STATE (may still be initializing)"
fi

log "=== Smoke Test Completed ==="

if [[ $FAILED -eq 0 ]]; then
    exit 0
else
    log "=== SMOKE TESTS FAILED ==="
    exit 1
fi
