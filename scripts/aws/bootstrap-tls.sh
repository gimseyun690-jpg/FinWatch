#!/usr/bin/env bash
set -euo pipefail

PUBLIC_HOST="${1:?public host is required}"
CERTIFICATE_EMAIL="${2:?certificate email is required}"
CERTBOT_IMAGE="${CERTBOT_IMAGE:?Set CERTBOT_IMAGE to a version or digest-pinned certbot image.}"

mkdir -p /etc/letsencrypt /var/lib/finwatch/certbot

docker run --rm \
  --name finwatch-certbot-bootstrap \
  -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/finwatch/certbot:/var/lib/letsencrypt \
  "$CERTBOT_IMAGE" certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --no-eff-email \
  --email "$CERTIFICATE_EMAIL" \
  --domain "$PUBLIC_HOST"

test -s "/etc/letsencrypt/live/${PUBLIC_HOST}/fullchain.pem"
test -s "/etc/letsencrypt/live/${PUBLIC_HOST}/privkey.pem"
echo "TLS certificate issued for $PUBLIC_HOST. Configure a scheduled renewal and expiry alarm before acceptance."
