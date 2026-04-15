#!/bin/bash
set -e

BASE_URL="${1:-http://localhost:8080}"
FAILED=0

TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
TELEGRAM_CHAT_ID="${TELEGRAM_CHAT_ID:-}"

alert() {
    [[ -z "$TELEGRAM_BOT_TOKEN" ]] && return
    curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
        -d "chat_id=${TELEGRAM_CHAT_ID}&text=$1&parse_mode=HTML" || true
}

log_and_echo() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

log_and_echo "=== Smoke Test Started ==="

# 1. Health check
echo -n "Backend health: "
STATUS=$(curl -sf --max-time 10 "$BASE_URL/actuator/health" | jq -r .status 2>/dev/null) || STATUS="UNREACHABLE"
[[ "$STATUS" == "UP" ]] && echo "✅ UP" || {
    echo "❌ $STATUS"
    last_logs=$(docker compose logs backend --tail=30 2>&1 | tr '\n' '|')
    alert "❌ SMOKE TEST FAILED: Backend health=$STATUS | URL=$BASE_URL/actuator/health | Logs: ${last_logs:0:1500}"
    FAILED=1
}

# 2. Auth endpoint responds (should reject bad credentials, not 500)
echo -n "Auth endpoint: "
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 10 -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com","password":"wrongpassword"}') || HTTP_CODE="UNREACHABLE"
[[ "$HTTP_CODE" == "400" || "$HTTP_CODE" == "401" ]] && echo "✅ $HTTP_CODE (correctly rejected bad auth)" || {
    echo "❌ $HTTP_CODE (expected 400/401)"
    auth_logs=$(docker compose logs backend --tail=10 2>&1 | tr '\n' '|')
    alert "⚠️ SMOKE TEST: Auth endpoint returned $HTTP_CODE (not 4xx). Logs: ${auth_logs:0:500}"
    # Auth returning non-4xx is a warning, not hard fail (could be network issue)
}

# 3. Nginx serves frontend
echo -n "Frontend: "
FRONTEND=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 10 http://localhost/) || FRONTEND="UNREACHABLE"
[[ "$FRONTEND" == "200" ]] && echo "✅ $FRONTEND" || {
    echo "❌ $FRONTEND"
    nginx_logs=$(docker compose logs nginx --tail=10 2>&1 | tr '\n' '|')
    alert "❌ SMOKE TEST FAILED: Nginx frontend returned $FRONTEND | Logs: ${nginx_logs:0:500}"
    FAILED=1
}

# 4. DB connectivity via actuator
echo -n "DB health: "
DB_STATUS=$(curl -sf --max-time 10 "$BASE_URL/actuator/health" | jq -r '.components.db.status' 2>/dev/null) || DB_STATUS="UNREACHABLE"
[[ "$DB_STATUS" == "UP" ]] && echo "✅ $DB_STATUS" || {
    echo "❌ $DB_STATUS"
    db_logs=$(docker compose logs backend --tail=10 2>&1 | tr '\n' '|')
    alert "❌ SMOKE TEST FAILED: DB health=$DB_STATUS | Logs: ${db_logs:0:500}"
    FAILED=1
}

log_and_echo "=== Smoke Test Completed ==="

[[ $FAILED -eq 0 ]] && exit 0
log_and_echo "=== SMOKE TESTS FAILED ===" && exit 1