#!/usr/bin/env bash
set -euo pipefail

required=(PGHOST MASTER_USERNAME MASTER_PASSWORD APP_USERNAME APP_PASSWORD)
for key in "${required[@]}"; do
  [[ -n "${!key:-}" ]] || { echo "$key is required in the secure operator session." >&2; exit 1; }
done

export PGPASSWORD="$MASTER_PASSWORD"
export PGSSLMODE=require

psql --host "$PGHOST" --username "$MASTER_USERNAME" --dbname postgres \
  --set ON_ERROR_STOP=1 \
  --set app_user="$APP_USERNAME" \
  --set app_password="$APP_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'app_user', :'app_password') \gexec
SELECT format('GRANT CONNECT ON DATABASE finwatch TO %I', :'app_user') \gexec
SQL

psql --host "$PGHOST" --username "$MASTER_USERNAME" --dbname finwatch \
  --set ON_ERROR_STOP=1 \
  --set app_user="$APP_USERNAME" <<'SQL'
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I', :'app_user') \gexec
SQL

unset PGPASSWORD MASTER_PASSWORD APP_PASSWORD
echo 'Dedicated RDS application user is ready. Remove any temporary master-secret access now.'
