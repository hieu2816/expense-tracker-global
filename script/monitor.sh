#!bin/bash
set -e

# [KEY FEATURES]
# 1. check_backend_health: Checks Java's "heartbeat" (port 8080).
# If it crashes or loses DB connection -> Instantly sends the last 30 error log lines to Telegram.
# 2. check_sync_health: Queries PostgreSQL directly.
# Counts FAILED GoCardless transactions in the last 15 minutes -> Reports error codes to Telegram.
# 3. check_ssl_expiry: Reads the HTTPS certificate.
# Sends renewal reminders before the site is marked "Not Secure" (alerts at 30 days and 7 days prior).
# 4. cleanup_disk: Auto-cleans garbage. Deletes old Docker images (>72 hours) and truncates
# oversized log files (>100MB) down to 50MB to save the server from crashing due to a full disk.
# 5. main: The master function. Parses arguments passed from the Cronjob to determine
# whether to run the 5-minute monitor, the daily SSL check, or the weekly disk cleanup.

# --- Config ---
LOG_FILE="/var/log/monitor.log"
RESTART_LOCK_FILE="/tmp/backend_restart.lock"
MAX_RESTARTS=3
RESTART_WINDOW=300          # 5-minute window
TELEGRAM_BOT_TOKEN="YOUR_TOKEN"
TELEGRAM_CHAT_ID="YOUR_CHAT_ID"
ALERT_THRESHOLD_DISK=85      # % disk usage to alert
CLEANUP_THRESHOLD_DISK=95    # % disk usage to auto-prune

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"; }

alert() {
    local severity="$1"
    local message="$2"
    local logs="${3:-}"   # optional log tail to include

    local full_message="<b>[$severity]</b> $message"
    [[ -n "$logs" ]] && full_message="$full_message"$'\n'"<code>$logs</code>"

    curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
        -d "chat_id=${TELEGRAM_CHAT_ID}&text=${full_message}&parse_mode=HTML"
}

# --- Restart guard: prevents restart loops ---
check_restart_loop() {
    local now=$(date +%s)
    local last_restart=0
    local restart_count=0

    if [[ -f "$RESTART_LOCK_FILE" ]]; then
        last_restart=$(stat -c %Y "$RESTART_LOCK_FILE" 2>/dev/null || echo 0)
        restart_count=$(grep -s "count=" "$RESTART_LOCK_FILE" | cut -d= -f2 || echo 0)
    fi

    # If last restart was > RESTART_WINDOW seconds ago, reset counter
    if (( now - last_restart > RESTART_WINDOW )); then
        restart_count=0
    fi

    (( restart_count >= MAX_RESTARTS )) && {
        log "CRITICAL: Restart loop detected ($restart_count restarts in ${RESTART_WINDOW}s)"
        alert "CRITICAL" "Backend restart loop detected! $restart_count restarts in ${RESTART_WINDOW}s. Manual intervention required. Disabling auto-restart."
        return 1   # Signal: do NOT restart — escalate instead
    }

    return 0   # OK to restart
}

record_restart() {
    local count=$(($(grep "count=" "$RESTART_LOCK_FILE" 2>/dev/null | cut -d= -f2 || echo 0) + 1))
    echo "count=$count" > "$RESTART_LOCK_FILE"
    touch "$RESTART_LOCK_FILE"
}

# --- Disk space monitoring ---
check_disk() {
    local disk_usage=$(df / | awk 'NR==2 {print $5}' | tr -d '%')
    log "Disk usage: ${disk_usage}%"

    if (( disk_usage >= CLEANUP_THRESHOLD_DISK )); then
        log "CRITICAL: Disk at ${disk_usage}% — running cleanup"
        docker system prune -af --volumes 2>/dev/null || true
        find /var/log -name "*.log" -size +100M -exec truncate -s 50M {} \; 2>/dev/null || true
        alert "WARNING" "Disk at ${disk_usage}% — auto-pruned Docker images and logs"
    elif (( disk_usage >= ALERT_THRESHOLD_DISK )); then
        alert "WARNING" "Disk usage at ${disk_usage}% — consider cleanup"
    fi
}

# --- Container health ---
check_containers() {
    local failed=($(docker compose ps --format json 2>/dev/null | \
        jq -r 'select(.Service!="certbot" and .State!="Up") | .Service' 2>/dev/null || true))

    if [[ ${#failed[@]} -gt 0 ]]; then
        log "ERROR: Containers not running: ${failed[*]}"

        if [[ " ${failed[*]} " =~ " backend " ]]; then
            if check_restart_loop; then
                local last_logs=$(docker compose logs backend --tail=20 2>&1 | tr '\n' '|')
                log "Restarting backend..."
                docker compose restart backend
                record_restart
                sleep 15
                # Verify it stayed up
                if curl -sf http://localhost:8080/actuator/health > /dev/null; then
                    alert "WARNING" "Backend restarted successfully" "${last_logs:0:1000}"
                else
                    alert "CRITICAL" "Backend restarted but still unhealthy" "${last_logs:0:1000}"
                fi
            fi
        else
            docker compose restart "${failed[*]}"
            alert "WARNING" "Containers restarted: ${failed[*]}"
        fi
    fi
}

# --- Backend health endpoint ---
check_backend_health() {
    local health_response
    health_response=$(curl -sf --max-time 10 http://localhost:8080/actuator/health 2>&1) || {
        local last_logs=$(docker compose logs backend --tail=30 2>&1 | tr '\n' '|')
        log "ERROR: Backend health check failed: $health_response"
        alert "CRITICAL" "Backend is DOWN and unreachable at http://localhost:8080/actuator/health" "${last_logs:0:1500}"
        return 1
    }

    local status=$(echo "$health_response" | jq -r .status 2>/dev/null)
    if [[ "$status" != "UP" ]]; then
        local db_status=$(echo "$health_response" | jq -r '.components.db.status' 2>/dev/null)
        local disk_status=$(echo "$health_response" | jq -r '.components.diskSpace.status' 2>/dev/null)
        local last_logs=$(docker compose logs backend --tail=20 2>&1 | tr '\n' '|')
        log "WARNING: Backend status=$status, db=$db_status, disk=$disk_status"
        alert "WARNING" "Backend health: $status | DB: $db_status | Disk: $disk_status" "${last_logs:0:1000}"
    else
        log "Backend health: UP ✅"
    fi
}

# --- SyncLog business logic check ---
check_sync_health() {
    local failed_count
    failed_count=$(docker compose exec -T database psql -U postgres -d postgres -t -c \
        "SELECT COUNT(*) FROM sync_logs WHERE status='FAILED' AND synced_at > NOW() - INTERVAL '15 minutes';" 2>/dev/null | tr -d ' ' || echo "0")

    if (( failed_count > 0 )); then
        local recent_failures=$(docker compose exec -T database psql -U postgres -d postgres -t -c \
            "SELECT bank_config_id, error_message, synced_at FROM sync_logs WHERE status='FAILED' AND synced_at > NOW() - INTERVAL '15 minutes' ORDER BY synced_at DESC LIMIT 3;" 2>/dev/null | tr '\n' '|' || echo "query_failed")
        log "WARNING: $failed_count failed syncs in last 15 minutes"
        alert "WARNING" "$failed_count failed GoCardless syncs in last 15 min" "${recent_failures:0:1000}"
    fi
}

# --- SSL certificate expiry ---
check_ssl_expiry() {
    local cert_file="/etc/letsencrypt/live/spendwiser.me/fullchain.pem"
    if [[ -f "$cert_file" ]]; then
        local expiry_epoch
        expiry_epoch=$(openssl x509 -in "$cert_file" -noout -enddate 2>/dev/null | cut -d= -f2 | xargs -I{} date +%s -d{})
        local now_epoch
        now_epoch=$(date +%s)
        local days_left=$(( (expiry_epoch - now_epoch) / 86400 ))

        if (( days_left < 0 )); then
            alert "CRITICAL" "SSL certificate has EXPIRED! Site may show security warnings."
        elif (( days_left < 7 )); then
            alert "CRITICAL" "SSL cert expires in ${days_left} days — renew immediately"
        elif (( days_left < 30 )); then
            alert "WARNING" "SSL cert expires in ${days_left} days"
        else
            log "SSL cert valid for $days_left more days"
        fi
    fi
}

# --- Disk cleanup (scheduled) ---
cleanup_disk() {
    log "Running disk cleanup..."
    docker system prune -af --filter "until=72h"  # Only prune images older than 72h
    docker container prune -f
    # Truncate large log files (>100MB)
    find /var/log -name "*.log" -size +100M -exec truncate -s 50M {} \; 2>/dev/null || true
    log "Disk cleanup done. Current usage: $(df -h / | awk 'NR==2 {print $5}')"
}

# --- Entry point ---
main() {
    log "=== Monitor run started ==="
    check_disk
    check_containers
    check_backend_health
    check_sync_health

    # SSL check — run daily only (use --ssl-check argument)
    if [[ "${1:-}" == "--ssl-check" ]]; then
        check_ssl_expiry
    fi

    # Disk cleanup — run weekly only (use --cleanup argument)
    if [[ "${1:-}" == "--cleanup" ]]; then
        cleanup_disk
    fi

    log "=== Monitor run completed ==="
}

main "$@"