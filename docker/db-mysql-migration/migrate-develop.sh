#!/bin/sh

set -e

cd /liquibase/changelog
rm -f /kilda/flag/migration.*

echo "******\nStart liquibase update using URL: ${KILDA_MYSQL_JDBC_URL}\n******"

if ! liquibase \
       --headless=true --defaultsFile=/liquibase/liquibase.docker.properties \
       --username="${KILDA_MYSQL_USER}" \
       --password="${KILDA_MYSQL_PASSWORD}" \
       --url="${KILDA_MYSQL_JDBC_URL}" \
       update --changelog-file="root.yaml";
then
  echo "******\nmigrate-develop.sh: DB migrations failure.\n******"
  exit 1
fi

echo "******\nmigrate-develop.sh: All migrations have been applied/verified.\n******"
touch /kilda/flag/migration.ok
if [ -z "${NO_SLEEP}"]
then
  echo "Set sleep infinity"
  exec sleep infinity
else
  echo "The migrate script completed"
  exit 0
fi

