#!/bin/bash
# ============================================================
# PostgreSQL initialisation script — postgres-main container
#
# Runs once on first boot (when the data volume is empty).
# Creates the keycloak_db database and keycloak_user alongside
# the cba_db / cba_user that Docker creates from POSTGRES_DB
# and POSTGRES_USER environment variables.
#
# The KEYCLOAK_DB_PASSWORD env var is read at runtime.
# Default value matches the docker-compose.yml dev default.
# ============================================================
set -e

KEYCLOAK_PASS="${KEYCLOAK_DB_PASSWORD:-keycloak_dev_password}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname   "$POSTGRES_DB" <<-EOSQL

    -- Keycloak user and database
    CREATE USER keycloak_user WITH PASSWORD '${KEYCLOAK_PASS}';
    CREATE DATABASE keycloak_db
        ENCODING    'UTF8'
        LC_COLLATE  'en_US.utf8'
        LC_CTYPE    'en_US.utf8'
        TEMPLATE    template0
        OWNER       keycloak_user;
    GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO keycloak_user;

EOSQL

echo "✓ keycloak_db and keycloak_user created"
