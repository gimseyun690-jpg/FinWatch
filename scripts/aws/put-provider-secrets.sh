#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="${1:-finwatch}"
ENVIRONMENT_NAME="${2:-portfolio}"
AWS_REGION="${3:?AWS region is required}"
SECRET_ID="/${PROJECT_NAME}/${ENVIRONMENT_NAME}/providers"

required=(
  GEMINI_API_KEY KIS_APP_KEY KIS_APP_SECRET KIS_HTS_ID
  NAVER_API_HUB_CLIENT_ID NAVER_API_HUB_CLIENT_SECRET
  FINNHUB_API_KEY OPENDART_API_KEY ARTICLE_USER_AGENT SEC_EDGAR_USER_AGENT
)
for key in "${required[@]}"; do
  [[ -n "${!key:-}" ]] || { echo "Set $key in the operator environment before running this script." >&2; exit 1; }
done

kakao_enabled=false
if [[ -n "${KAKAO_REST_API_KEY:-}" || -n "${KAKAO_CLIENT_SECRET:-}" ]]; then
  [[ -n "${KAKAO_REST_API_KEY:-}" && -n "${KAKAO_CLIENT_SECRET:-}" ]] \
    || { echo 'Set both KAKAO_REST_API_KEY and KAKAO_CLIENT_SECRET.' >&2; exit 1; }
  kakao_enabled=true
fi

secret_json="$(jq -cn \
  --arg gemini "$GEMINI_API_KEY" \
  --arg kisKey "$KIS_APP_KEY" \
  --arg kisSecret "$KIS_APP_SECRET" \
  --arg kisHts "$KIS_HTS_ID" \
  --arg naverId "$NAVER_API_HUB_CLIENT_ID" \
  --arg naverSecret "$NAVER_API_HUB_CLIENT_SECRET" \
  --arg finnhub "$FINNHUB_API_KEY" \
  --arg dart "$OPENDART_API_KEY" \
  --arg articleAgent "$ARTICLE_USER_AGENT" \
  --arg secAgent "$SEC_EDGAR_USER_AGENT" \
  --arg kakaoKey "${KAKAO_REST_API_KEY:-}" \
  --arg kakaoSecret "${KAKAO_CLIENT_SECRET:-}" \
  --argjson kakaoEnabled "$kakao_enabled" '
  {
    GEMINI_API_KEY: $gemini,
    KIS_APP_KEY: $kisKey,
    KIS_APP_SECRET: $kisSecret,
    KIS_HTS_ID: $kisHts,
    NAVER_API_HUB_CLIENT_ID: $naverId,
    NAVER_API_HUB_CLIENT_SECRET: $naverSecret,
    FINNHUB_API_KEY: $finnhub,
    OPENDART_API_KEY: $dart,
    ARTICLE_USER_AGENT: $articleAgent,
    SEC_EDGAR_USER_AGENT: $secAgent,
    KAKAO_LOGIN_ENABLED: ($kakaoEnabled | tostring)
  }
  + (if $kakaoEnabled then {
      KAKAO_REST_API_KEY: $kakaoKey,
      KAKAO_CLIENT_SECRET: $kakaoSecret
    } else {} end)
')"

aws secretsmanager put-secret-value \
  --region "$AWS_REGION" \
  --secret-id "$SECRET_ID" \
  --secret-string "$secret_json" >/dev/null

echo "Updated $SECRET_ID without printing secret values."
