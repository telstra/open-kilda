#!/bin/sh

set -e

cd /kilda
./apply-prehistory-migrations.sh

rm -f flag/migration.*
if ! ./migrate.sh; then
  echo "DB migrations failure"
  exit 1
fi

echo "All migrations have been applied/verified"
touch flag/migration.ok
exec sleep infinity
