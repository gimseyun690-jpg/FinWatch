#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="${1:?project name is required}"
ENVIRONMENT_NAME="${2:?environment name is required}"
AWS_REGION="${3:?AWS region is required}"
APP_VERSION="${4:?application version is required}"
OUTPUT_FILE="${5:-/run/finwatch/portfolio.env}"
PARAMETER_PATH="/${PROJECT_NAME}/${ENVIRONMENT_NAME}/"

command -v aws >/dev/null
command -v jq >/dev/null
umask 077

parameters_json="$(aws ssm get-parameters-by-path \
  --region "$AWS_REGION" \
  --path "$PARAMETER_PATH" \
  --recursive \
  --with-decryption \
  --output json)"

flat_parameters="$(jq -c '
  reduce .Parameters[] as $item ({};
    . + {($item.Name | split("/") | last): $item.Value})
' <<<"$parameters_json")"

runtime_defaults="$(jq -er '.RUNTIME_DEFAULTS | fromjson' <<<"$flat_parameters")"
plain_parameters="$(jq -c 'del(.RUNTIME_DEFAULTS)' <<<"$flat_parameters")"

database_secret="$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "/${PROJECT_NAME}/${ENVIRONMENT_NAME}/database-application" \
  --query SecretString --output text)"
jwt_secret="$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "/${PROJECT_NAME}/${ENVIRONMENT_NAME}/jwt" \
  --query SecretString --output text)"
redis_secret="$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "/${PROJECT_NAME}/${ENVIRONMENT_NAME}/redis" \
  --query SecretString --output text)"
provider_secret="$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "/${PROJECT_NAME}/${ENVIRONMENT_NAME}/providers" \
  --query SecretString --output text)"

combined="$(jq -cn \
  --argjson defaults "$runtime_defaults" \
  --argjson parameters "$plain_parameters" \
  --argjson database "$database_secret" \
  --argjson jwt "$jwt_secret" \
  --argjson redis "$redis_secret" \
  --argjson providers "$provider_secret" \
  --arg appVersion "$APP_VERSION" '
    $defaults * $parameters
    * {DB_USERNAME: $database.username, DB_PASSWORD: $database.password}
    * $jwt * $redis * $providers
    * {APP_VERSION: $appVersion}
  ')"

required_keys=(
  SPRING_PROFILES_ACTIVE APP_VERSION APP_PUBLIC_BASE_URL FRONTEND_BASE_URL
  CORS_ALLOWED_ORIGINS DB_URL DB_USERNAME DB_PASSWORD REDIS_HOST REDIS_PASSWORD
  JWT_SECRET DATA_MODE AI_PROVIDER SESSION_COOKIE_SECURE DEMO_USERS_ENABLED
  AWS_REGION PUBLIC_HOST BACKEND_LOG_GROUP NGINX_LOG_GROUP REDIS_LOG_GROUP
  REDIS_IMAGE CERTBOT_IMAGE
)

for key in "${required_keys[@]}"; do
  jq -e --arg key "$key" '.[$key] | type == "string" and length > 0' <<<"$combined" >/dev/null \
    || { echo "Missing required runtime setting: $key" >&2; exit 1; }
done

if [[ "$(jq -r '.AI_PROVIDER' <<<"$combined")" == "gemini" ]]; then
  jq -e '.GEMINI_API_KEY | type == "string" and length >= 16' <<<"$combined" >/dev/null \
    || { echo 'GEMINI_API_KEY is required for the portfolio Gemini provider.' >&2; exit 1; }
fi

if [[ "$(jq -r '.KAKAO_LOGIN_ENABLED // "false"' <<<"$combined")" == "true" ]]; then
  for key in KAKAO_REST_API_KEY KAKAO_CLIENT_SECRET KAKAO_REDIRECT_URI; do
    jq -e --arg key "$key" '.[$key] | type == "string" and length >= 8' <<<"$combined" >/dev/null \
      || { echo "Missing enabled Kakao setting: $key" >&2; exit 1; }
  done
fi

jq -e 'all(.[]; (type != "string") or (test("[\\r\\n]") | not))' <<<"$combined" >/dev/null \
  || { echo 'Runtime values may not contain line breaks.' >&2; exit 1; }

mkdir -p "$(dirname "$OUTPUT_FILE")"
temporary_file="$(mktemp "${OUTPUT_FILE}.XXXXXX")"
jq -r 'to_entries | sort_by(.key)[] | "\(.key)=\(.value | tostring)"' <<<"$combined" >"$temporary_file"
chmod 0600 "$temporary_file"
mv -f "$temporary_file" "$OUTPUT_FILE"
echo "Runtime environment rendered to $OUTPUT_FILE without printing secret values."
