#!/bin/bash

set -eu${DEBUG:+x}

set -o pipefail

# Pre-check
/usr/bin/jps | grep --silent core || exit 1


CONFIG=${1:-/opt/apache-storm-1.2.1/conf/storm.yaml}

PORTS_NUBER_IN_CONFIG=$(cat ${CONFIG}  | sed -n '/supervisor.slots.ports/,/^$/p' | grep -v supervisor.slots.ports | grep -v '^$' | awk -F '-' '{ print $2}' | wc -l )
ACTUAL_SLOTS=$(curl  -q 127.0.0.1:8080/api/v1/cluster/summary 2>/dev/null | jq -r  '.slotsTotal')

D=$(date)
echo "${D} ACTUAL_SLOTS=${ACTUAL_SLOTS} PORTS_NUBER_IN_CONFIG=${PORTS_NUBER_IN_CONFIG}" >> /simple-storm-ui-healthcheck.log

if [ "${ACTUAL_SLOTS}" -ge "${PORTS_NUBER_IN_CONFIG}" ];
then
    exit 0
else
    exit 2
fi
