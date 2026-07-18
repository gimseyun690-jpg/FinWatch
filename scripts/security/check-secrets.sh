#!/usr/bin/env bash
set -euo pipefail

high_confidence_pattern='(AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{20,}|AQ\.[0-9A-Za-z_-]{20,}|gh[pousr]_[0-9A-Za-z]{30,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'

matches="$(git grep -IlE "$high_confidence_pattern" -- . ':!docs/evidence/**' || true)"
if [[ -n "$matches" ]]; then
  echo 'Potential committed secret material was found in:' >&2
  printf '%s\n' "$matches" >&2
  exit 1
fi

if [[ -d frontend/dist ]]; then
  bundle_matches="$(grep -RIlE "$high_confidence_pattern" frontend/dist || true)"
  if [[ -n "$bundle_matches" ]]; then
    echo 'Potential secret material was found in the frontend production bundle:' >&2
    printf '%s\n' "$bundle_matches" >&2
    exit 1
  fi
fi

echo 'High-confidence repository and frontend bundle secret scan passed.'
