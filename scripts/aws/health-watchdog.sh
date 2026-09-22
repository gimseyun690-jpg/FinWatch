#!/usr/bin/env bash
set -euo pipefail

install_root=/opt/finwatch
current_release="$(readlink -f "$install_root/current" 2>/dev/null || true)"

if [[ -z "$current_release" || "$current_release" != "$install_root"/releases/* ]]; then
  echo 'FinWatch current release is unavailable; watchdog skipped.' >&2
  exit 0
fi

compose_file="$current_release/deploy/compose.portfolio.yml"
compose_env="$current_release/compose.env"
[[ -f "$compose_file" && -f "$compose_env" ]] || exit 0

backend_container="$(docker ps -a \
  --filter label=com.docker.compose.project=finwatch-portfolio \
  --filter label=com.docker.compose.service=backend \
  --format '{{.ID}}' | head -n 1)"

if [[ -z "$backend_container" ]]; then
  docker compose --env-file "$compose_env" -f "$compose_file" up -d backend
  exit 0
fi

running="$(docker inspect --format '{{.State.Running}}' "$backend_container")"
health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "$backend_container")"

if [[ "$running" != true ]]; then
  docker compose --env-file "$compose_env" -f "$compose_file" up -d backend
elif [[ "$health" == unhealthy ]]; then
  docker restart "$backend_container" >/dev/null
fi
