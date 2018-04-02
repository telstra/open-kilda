#!/usr/bin/env bash

HOST=${HOST:-http://localhost:8088}
ROUTE=${ROUTE:-/api/v1/flows/c3none/validate}
CID=${CID:=kilroy_likes_tests}

echo "Calling: ${HOST}${ROUTE}"
U_P=`echo -n kilda:kilda | base64`
AUTH="Authorization: Basic ${U_P}"
CORR="correlation_id: ${CID}"
CONT="Content-Type: application/json"

curl -H "${AUTH}" -H "${CORR}" -H "${CONT}" -X GET ${HOST}${ROUTE}
echo ""

