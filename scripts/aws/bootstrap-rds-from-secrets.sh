#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="${1:?AWS region is required}"
MASTER_SECRET_ID="${2:?RDS master secret ID is required}"
APPLICATION_SECRET_ID="${3:?application secret ID is required}"
DB_ENDPOINT="${4:?RDS endpoint is required}"
BOOTSTRAP_SCRIPT="${5:?bootstrap script path is required}"

cleanup() {
  unset master_json application_json
  unset PGHOST MASTER_USERNAME MASTER_PASSWORD APP_USERNAME APP_PASSWORD
}
trap cleanup EXIT

master_json="$(aws secretsmanager get-secret-value \
  --region "$AWS_REGION" \
  --secret-id "$MASTER_SECRET_ID" \
  --query SecretString \
  --output text)"
application_json="$(aws secretsmanager get-secret-value \
  --region "$AWS_REGION" \
  --secret-id "$APPLICATION_SECRET_ID" \
  --query SecretString \
  --output text)"

export PGHOST="$DB_ENDPOINT"
export MASTER_USERNAME="$(jq -er '.username' <<<"$master_json")"
export MASTER_PASSWORD="$(jq -er '.password' <<<"$master_json")"
export APP_USERNAME="$(jq -er '.username' <<<"$application_json")"
export APP_PASSWORD="$(jq -er '.password' <<<"$application_json")"

"$BOOTSTRAP_SCRIPT"
