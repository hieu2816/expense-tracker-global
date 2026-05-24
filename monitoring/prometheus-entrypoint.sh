#!/bin/sh
set -eu

TEMPLATE_FILE="/etc/prometheus/prometheus.yml.template"
CONFIG_FILE="/tmp/prometheus.yml"

: "${GRAFANA_PROM_URL:?GRAFANA_PROM_URL is required}"
: "${GRAFANA_PROM_USER:?GRAFANA_PROM_USER is required}"
: "${GRAFANA_PROM_PASS:?GRAFANA_PROM_PASS is required}"

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\/&|]/\\&/g'
}

GRAFANA_PROM_URL_ESCAPED="$(escape_sed_replacement "$GRAFANA_PROM_URL")"
GRAFANA_PROM_USER_ESCAPED="$(escape_sed_replacement "$GRAFANA_PROM_USER")"
GRAFANA_PROM_PASS_ESCAPED="$(escape_sed_replacement "$GRAFANA_PROM_PASS")"

sed \
  -e "s|\${GRAFANA_PROM_URL}|${GRAFANA_PROM_URL_ESCAPED}|g" \
  -e "s|\${GRAFANA_PROM_USER}|${GRAFANA_PROM_USER_ESCAPED}|g" \
  -e "s|\${GRAFANA_PROM_PASS}|${GRAFANA_PROM_PASS_ESCAPED}|g" \
  "$TEMPLATE_FILE" > "$CONFIG_FILE"

exec /bin/prometheus --config.file="$CONFIG_FILE" "$@"
