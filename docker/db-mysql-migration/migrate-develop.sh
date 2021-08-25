#!/bin/sh

set -e

cd /liquibase/changelog
liquibase \
  --headless=true --defaultsFile=/liquibase/liquibase.docker.properties \
  --username="${KILDA_MYSQL_USER}" \
  --password="${KILDA_MYSQL_PASSWORD}" \
  --url="${KILDA_MYSQL_JDBC_URL}" \
  update --changelog-file="root.yaml"

echo "All migrations have been applied/verified"
touch /kilda/flag/migration.ok
exec sleep infinity
