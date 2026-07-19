#!/usr/bin/env bash
set -euo pipefail

runtime_env=/run/finwatch/portfolio.env
[[ -f "$runtime_env" ]] || { echo 'Runtime environment is missing.' >&2; exit 1; }

CERTBOT_IMAGE="$(grep '^CERTBOT_IMAGE=' "$runtime_env" | cut -d= -f2-)"
[[ -n "$CERTBOT_IMAGE" ]] || { echo 'CERTBOT_IMAGE is missing.' >&2; exit 1; }
PUBLIC_HOST="$(grep '^PUBLIC_HOST=' "$runtime_env" | cut -d= -f2-)"
[[ -n "$PUBLIC_HOST" ]] || { echo 'PUBLIC_HOST is missing.' >&2; exit 1; }

docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/finwatch/certbot:/var/www/certbot \
  "$CERTBOT_IMAGE" renew --webroot --webroot-path /var/www/certbot --quiet

current=/opt/finwatch/current
if [[ -L "$current" && -f "$current/compose.env" ]]; then
  "$current/scripts/aws/prepare-tls-permissions.sh" "$PUBLIC_HOST"
  docker compose --env-file "$current/compose.env" \
    -f "$current/deploy/compose.portfolio.yml" kill --signal HUP frontend
fi
