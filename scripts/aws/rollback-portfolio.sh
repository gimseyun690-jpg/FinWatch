#!/usr/bin/env bash
set -euo pipefail

RELEASE_ID="${1:?previous release SHA is required}"
release_root="/opt/finwatch/releases/$RELEASE_ID"

[[ -f "$release_root/compose.env" ]] || { echo "Unknown release: $RELEASE_ID" >&2; exit 1; }
[[ -f "$release_root/deploy/compose.portfolio.yml" ]] || { echo 'Missing release Compose file.' >&2; exit 1; }

docker compose --env-file "$release_root/compose.env" \
  -f "$release_root/deploy/compose.portfolio.yml" up -d --remove-orphans --wait --wait-timeout 180

PUBLIC_HOST="$(grep '^PUBLIC_HOST=' "$release_root/compose.env" | cut -d= -f2-)"
"$release_root/scripts/aws/aws-smoke.sh" "https://${PUBLIC_HOST}"
ln -sfn "$release_root" /opt/finwatch/current
echo "Rolled back application images to $RELEASE_ID. Database state was not reversed."
