#!/bin/sh

set -e

cd /liquibase

if [ $# -eq 0 ]; then
  action="update"
  rollback_tag=""
elif [ $# -eq 1 ]; then
  action="rollback"
  rollback_tag="$1"
else
  echo "Invalid number of arguments - accept up to 1 arguments. Do update" \
       "in case of 0 arguments, do rollback to tag in case 1 argument (use" \
       "argument value as a database tag)"
  exit 1
fi

DB_NAME="${KILDA_ORIENTDB_DB_NAME:-kilda}"
DB_HOST="${KILDA_ORIENTDB_HOST:-odb1.pendev}"

echo "Apply main migrations set (root.yaml)"
./liquibase --username="${KILDA_ORIENTDB_USER:-kilda}" --password="${KILDA_ORIENTDB_PASSWORD:-kilda}" \
  --url=jdbc:orient:remote:"${DB_HOST}/${DB_NAME}" \
  --driver=com.orientechnologies.orient.jdbc.OrientJdbcDriver --changeLogFile=migrations/root.yaml \
  "${action}" ${rollback_tag:+"$rollback_tag"}

exit 0
