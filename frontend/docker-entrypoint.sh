#!/bin/sh
# Substitute ${TLS_DOMAIN} in the nginx config template before nginx starts.
# Single quotes around '${TLS_DOMAIN}' prevent the shell from expanding it here —
# envsubst receives the literal string "${TLS_DOMAIN}" and knows which variable to replace.
envsubst '${TLS_DOMAIN}' < /etc/nginx/templates/nginx.conf.template > /etc/nginx/nginx.conf
exec nginx -g "daemon off;"
