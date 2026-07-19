#!/usr/bin/env bash
set -euo pipefail

PUBLIC_HOST="${1:?public host is required}"
TLS_READ_GROUP_ID="${TLS_READ_GROUP_ID:-20001}"
LETSENCRYPT_ROOT=/etc/letsencrypt
LIVE_DIR="$LETSENCRYPT_ROOT/live/$PUBLIC_HOST"
ARCHIVE_DIR="$LETSENCRYPT_ROOT/archive/$PUBLIC_HOST"

[[ "$TLS_READ_GROUP_ID" =~ ^[0-9]+$ ]] || { echo 'TLS_READ_GROUP_ID must be numeric.' >&2; exit 1; }
[[ -d "$LIVE_DIR" && -d "$ARCHIVE_DIR" ]] || { echo "TLS certificate directories are missing for $PUBLIC_HOST." >&2; exit 1; }

for directory in \
  "$LETSENCRYPT_ROOT" \
  "$LETSENCRYPT_ROOT/live" \
  "$LETSENCRYPT_ROOT/archive" \
  "$LIVE_DIR" \
  "$ARCHIVE_DIR"; do
  chown "root:$TLS_READ_GROUP_ID" "$directory"
  chmod 0750 "$directory"
done

find "$ARCHIVE_DIR" -maxdepth 1 -type f -name '*.pem' \
  -exec chown "root:$TLS_READ_GROUP_ID" {} + \
  -exec chmod 0640 {} +

test -r "$LIVE_DIR/fullchain.pem"
test -r "$LIVE_DIR/privkey.pem"
echo "TLS certificate permissions prepared for the Nginx read group."
