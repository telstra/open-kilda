#!/bin/sh
#
# Copyright 2020 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

DB_NAME="${KILDA_ORIENTDB_DB_NAME:-kilda}"
DB_HOST="${KILDA_ORIENTDB_HOST:-odb1.pendev}"

TMP=$(mktemp -d)
if ! curl -i --user "${KILDA_ORIENTDB_ROOT_USER:-root}":"${KILDA_ORIENTDB_ROOT_PASSWORD:-root}" -X POST http://"${DB_HOST}":2480/database/"${DB_NAME}"/plocal > "$TMP/db-create.out" 2>&1; then
  cat "$TMP/db-create.out"
  exit 1
fi

set -e
cd /liquibase

echo "Apply access management migrations set"
./liquibase --username="${KILDA_ORIENTDB_ROOT_USER:-root}" --password="${KILDA_ORIENTDB_ROOT_PASSWORD:-root}" --url=jdbc:orient:remote:"${DB_HOST}/${DB_NAME}" \
  --driver=com.orientechnologies.orient.jdbc.OrientJdbcDriver \
  --changeLogFile=migrations/initial-access-management.yaml update

echo "Apply prehistory migrations set"
./liquibase --username="${KILDA_ORIENTDB_USER:-kilda}" --password="${KILDA_ORIENTDB_PASSWORD:-kilda}" \
  --url=jdbc:orient:remote:"${DB_HOST}/${DB_NAME}" \
  --driver=com.orientechnologies.orient.jdbc.OrientJdbcDriver --changeLogFile=migrations/prehistory.yaml update
