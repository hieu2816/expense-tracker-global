#!/bin/bash
# bootstrap-tls.sh — Idempotent TLS certificate bootstrap via Certbot + Webroot
#
# Run once on a fresh server, safely skips if a valid cert already exists.
# Called by deploy.yml before "docker compose up -d".
#
# Required env vars (set in .env or CI secrets):
#   LETSENCRYPT_EMAIL  — contact email for Let's Encrypt
#   TLS_DOMAIN         — primary domain (default: spendwiser.me)
#
# Optional env vars:
#   CERTBOT_STAGING=true — use Let's Encrypt staging API (no rate limits, for testing)

set -euo pipefail

# --- STEP 0: Load .env FIRST, then validate required vars ---
# Must source before any :? checks so values from .env are available
if [[ -f .env ]]; then
    set -a
    # shellcheck source=/dev/null
    source .env
    set +a
fi

DOMAIN="${TLS_DOMAIN:-spendwiser.me}"
EMAIL="${LETSENCRYPT_EMAIL:?Error: LETSENCRYPT_EMAIL is required. Set it in .env or CI secrets.}"

# Staging flag — use CERTBOT_STAGING=true to test without hitting rate limits
if [[ "${CERTBOT_STAGING:-false}" == "true" ]]; then
    STAGING_FLAG="--staging"
    echo "⚠️  CERTBOT_STAGING=true — using Let's Encrypt staging API (cert will not be trusted by browsers)"
else
    STAGING_FLAG=""
fi

DATA_PATH="/etc/letsencrypt"
LIVE_DIR="${DATA_PATH}/live/${DOMAIN}"
RENEWAL_CONF="${DATA_PATH}/renewal/${DOMAIN}.conf"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

# --- STEP 1: Idempotency check — skip if a valid, non-expiring cert already exists ---
# Checks both: renewal config exists AND cert is valid for at least 30 more days
if docker compose run --rm --entrypoint sh certbot -c "
    test -f '${RENEWAL_CONF}' && \
    openssl x509 -checkend 2592000 -noout -in '${LIVE_DIR}/fullchain.pem' 2>/dev/null
" 2>/dev/null; then
    log "✅ Valid Let's Encrypt certificate already exists for ${DOMAIN} (>30 days remaining). Skipping bootstrap."
    exit 0
fi

log "⚠️  No valid certificate found. Starting TLS bootstrap for ${DOMAIN}..."

# --- STEP 2: Create dummy self-signed cert so Nginx can start ---
ensure_dummy_certs() {
    log "=> Creating dummy self-signed certificate..."
    docker compose run --rm --entrypoint sh certbot -c "
        set -e
        command -v openssl >/dev/null 2>&1 || apk add --no-cache openssl >/dev/null
        mkdir -p '${LIVE_DIR}'
        openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
            -keyout '${LIVE_DIR}/privkey.pem' \
            -out '${LIVE_DIR}/fullchain.pem' \
            -subj '/CN=${DOMAIN}'
    "
    log "=> Dummy certificate created."
}

# --- STEP 3: Remove dummy cert and issue real cert ---
issue_real_certs() {
    log "=> Removing dummy certificate to prevent Certbot symlink conflict..."
    docker compose run --rm --entrypoint sh certbot -c "
        rm -rf '${DATA_PATH}/live/${DOMAIN}' '${DATA_PATH}/archive/${DOMAIN}'
    "

    log "=> Requesting real Let's Encrypt certificate (${STAGING_FLAG:-production})..."
    # --non-interactive is mandatory for CI/CD — prevents interactive prompts that hang pipelines
    docker compose run --rm certbot certonly \
        --webroot \
        --webroot-path /var/www/certbot \
        --email "${EMAIL}" \
        --agree-tos \
        --no-eff-email \
        --non-interactive \
        ${STAGING_FLAG} \
        -d "${DOMAIN}"
}

# --- STEP 4: Wait for Nginx to be healthy (replaces fragile sleep) ---
wait_for_nginx() {
    log "=> Waiting for Nginx to be ready on port 80..."
    local max_attempts=15
    local attempt=1
    until curl -sf http://localhost/health > /dev/null 2>&1; do
        if (( attempt >= max_attempts )); then
            log "❌ Nginx did not become ready after $((max_attempts * 2)) seconds. Aborting."
            docker compose logs nginx --tail=20
            exit 1
        fi
        log "  ...attempt ${attempt}/${max_attempts}, retrying in 2s"
        (( attempt++ ))
        sleep 2
    done
    log "=> Nginx is ready ✅"
}

# --- EXECUTION FLOW ---
ensure_dummy_certs

log "=> Starting Nginx in background..."
docker compose up -d nginx

wait_for_nginx

issue_real_certs

log "=> Reloading Nginx to apply real certificates..."
docker compose exec nginx nginx -s reload

log "🎉 TLS Bootstrap completed successfully for ${DOMAIN}!"
