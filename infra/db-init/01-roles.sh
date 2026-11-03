#!/bin/sh
# Runs once on an empty data directory. Migrations run as tutorbridge_migrator, the API as tutorbridge_app.
set -eu

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" \
  -v migrator_password="$TB_MIGRATOR_PASSWORD" \
  -v app_password="$TB_APP_PASSWORD" <<'SQL'
CREATE ROLE tutorbridge_migrator LOGIN PASSWORD :'migrator_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE tutorbridge_app LOGIN PASSWORD :'app_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE;
ALTER DATABASE :"db" OWNER TO tutorbridge_migrator;
-- vector is not a trusted extension, so the owner cannot create it. V6 then finds it installed.
CREATE EXTENSION IF NOT EXISTS vector;
SQL
