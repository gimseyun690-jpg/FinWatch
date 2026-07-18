#!/usr/bin/env bash
set -euo pipefail

RELEASE_ID="${1:?release SHA is required}"
DEPLOYMENT_BUCKET="${2:?deployment bucket is required}"
AWS_REGION="${3:?AWS region is required}"
BACKEND_IMAGE="${4:?backend image is required}"
FRONTEND_IMAGE="${5:?frontend image is required}"
REDIS_IMAGE="${6:?digest-pinned Redis image is required}"
PROJECT_NAME="${PROJECT_NAME:-finwatch}"
ENVIRONMENT_NAME="${ENVIRONMENT_NAME:-portfolio}"

install_root=/opt/finwatch
release_root="$install_root/releases/$RELEASE_ID"
bundle="/tmp/finwatch-${RELEASE_ID}.tgz"
runtime_env=/run/finwatch/portfolio.env

mkdir -p "$release_root" /run/finwatch
umask 077

aws s3 cp "s3://${DEPLOYMENT_BUCKET}/releases/${RELEASE_ID}/deploy.tgz" "$bundle" \
  --region "$AWS_REGION" --only-show-errors
tar -xzf "$bundle" -C "$release_root"
rm -f "$bundle"

"$release_root/scripts/aws/render-runtime-env.sh" \
  "$PROJECT_NAME" "$ENVIRONMENT_NAME" "$AWS_REGION" "$RELEASE_ID" "$runtime_env"

PUBLIC_HOST="$(grep '^PUBLIC_HOST=' "$runtime_env" | cut -d= -f2-)"
BACKEND_LOG_GROUP="$(grep '^BACKEND_LOG_GROUP=' "$runtime_env" | cut -d= -f2-)"
NGINX_LOG_GROUP="$(grep '^NGINX_LOG_GROUP=' "$runtime_env" | cut -d= -f2-)"
REDIS_LOG_GROUP="$(grep '^REDIS_LOG_GROUP=' "$runtime_env" | cut -d= -f2-)"

for certificate_file in \
  "/etc/letsencrypt/live/${PUBLIC_HOST}/fullchain.pem" \
  "/etc/letsencrypt/live/${PUBLIC_HOST}/privkey.pem"; do
  [[ -s "$certificate_file" ]] || { echo "Missing TLS file: $certificate_file" >&2; exit 1; }
done

compose_env="$release_root/compose.env"
cat >"$compose_env" <<EOF
AWS_REGION=$AWS_REGION
PUBLIC_HOST=$PUBLIC_HOST
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
REDIS_IMAGE=$REDIS_IMAGE
FINWATCH_RUNTIME_ENV_FILE=$runtime_env
BACKEND_LOG_GROUP=$BACKEND_LOG_GROUP
NGINX_LOG_GROUP=$NGINX_LOG_GROUP
REDIS_LOG_GROUP=$REDIS_LOG_GROUP
EOF
chmod 0600 "$compose_env"

registry="${BACKEND_IMAGE%%/*}"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$registry" >/dev/null

previous_release=''
if [[ -L "$install_root/current" ]]; then
  previous_release="$(readlink -f "$install_root/current")"
fi

compose_file="$release_root/deploy/compose.portfolio.yml"
docker compose --env-file "$compose_env" -f "$compose_file" config --quiet
docker compose --env-file "$compose_env" -f "$compose_file" pull --quiet

if ! docker compose --env-file "$compose_env" -f "$compose_file" up -d --remove-orphans --wait --wait-timeout 180; then
  echo 'New release failed readiness; attempting application rollback.' >&2
  if [[ -n "$previous_release" && -f "$previous_release/compose.env" ]]; then
    docker compose --env-file "$previous_release/compose.env" \
      -f "$previous_release/deploy/compose.portfolio.yml" up -d --remove-orphans --wait --wait-timeout 180
  fi
  exit 1
fi

"$release_root/scripts/aws/aws-smoke.sh" "https://${PUBLIC_HOST}"
ln -sfn "$release_root" "$install_root/current"

install -m 0644 "$release_root/deploy/systemd/finwatch-cert-renew.service" /etc/systemd/system/
install -m 0644 "$release_root/deploy/systemd/finwatch-cert-renew.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now finwatch-cert-renew.timer

backend_digest="$(docker image inspect "$BACKEND_IMAGE" --format '{{index .RepoDigests 0}}')"
frontend_digest="$(docker image inspect "$FRONTEND_IMAGE" --format '{{index .RepoDigests 0}}')"
cat >"$release_root/deployment-evidence.json" <<EOF
{"release":"$RELEASE_ID","deployedAt":"$(date -u +%FT%TZ)","backendDigest":"$backend_digest","frontendDigest":"$frontend_digest"}
EOF
chmod 0640 "$release_root/deployment-evidence.json"
echo "FinWatch release $RELEASE_ID deployed and verified."
