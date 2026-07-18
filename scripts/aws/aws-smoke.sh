#!/usr/bin/env bash
set -euo pipefail

PUBLIC_BASE_URL="${1:?canonical HTTPS base URL is required}"
HTTP_BASE_URL="${PUBLIC_BASE_URL/https:\/\//http://}"
temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$HTTP_BASE_URL/")"
[[ "$status" == "308" || "$status" == "301" ]] || { echo "HTTP redirect failed: $status" >&2; exit 1; }

curl --fail --silent --show-error "$PUBLIC_BASE_URL/" >"$temporary_directory/index.html"
grep -q '<div id="root"></div>' "$temporary_directory/index.html"

curl --fail --silent --show-error "$PUBLIC_BASE_URL/stocks/KRX/000660/technical" \
  >"$temporary_directory/direct-route.html"
grep -q '<div id="root"></div>' "$temporary_directory/direct-route.html"

health="$(curl --fail --silent --show-error "$PUBLIC_BASE_URL/api/v1/health")"
jq -e '.status == "UP"' <<<"$health" >/dev/null

curl --fail --silent --show-error "$PUBLIC_BASE_URL/manifest.webmanifest" \
  | jq -e '.name | length > 0' >/dev/null

service_worker_headers="$(curl --silent --show-error --dump-header - --output /dev/null "$PUBLIC_BASE_URL/sw.js")"
grep -Eqi '^cache-control:.*no-(cache|store)' <<<"$service_worker_headers"

actuator_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  "$PUBLIC_BASE_URL/actuator/health")"
[[ "$actuator_status" == "404" ]] || { echo "Public actuator must be 404, got $actuator_status" >&2; exit 1; }

kakao_status="$(curl --fail --silent --show-error "$PUBLIC_BASE_URL/api/v1/auth/kakao/status")"
jq -e '.data.enabled == true' <<<"$kakao_status" >/dev/null \
  || { echo 'Kakao login is not enabled in the public environment.' >&2; exit 1; }

websocket_status="$(curl --http1.1 --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --header 'Connection: Upgrade' \
  --header 'Upgrade: websocket' \
  --header 'Sec-WebSocket-Version: 13' \
  --header 'Sec-WebSocket-Key: Zmlud2F0Y2gtc21va2Uta2V5IQ==' \
  "$PUBLIC_BASE_URL/ws/quotes")"
[[ "$websocket_status" == "401" ]] \
  || { echo "WebSocket proxy/auth boundary expected 401 without a session, got $websocket_status" >&2; exit 1; }

echo 'AWS public smoke passed: TLS, redirect, SPA, PWA, API, actuator boundary, Kakao status and WebSocket proxy.'
