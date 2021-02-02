#!/bin/bash

set -eu${DEBUG:+x}

set -o pipefail

# Pre-check
/usr/bin/jps | grep Supervisor || exit 1


CONFIG=${1:-/opt/apache-storm-1.2.1/conf/storm.yaml}
LOG_FILE=${2:-/opt/apache-storm-1.2.1/logs/supervisor.log}

PORTS_NUBER_IN_CONFIG=$(cat ${CONFIG}  | sed -n '/supervisor.slots.ports/,/^$/p' | grep -v supervisor.slots.ports | grep -v '^$' | awk -F '-' '{ print $2}' | wc -l )
PORTS_NUMBER_IN_LOGS=$(head  -1000 ${LOG_FILE} | grep 'Starting in state' | wc -l )

D=$(date)
echo "${D} PORTS_NUMBER_IN_LOGS=${PORTS_NUMBER_IN_LOGS} PORTS_NUBER_IN_CONFIG=${PORTS_NUBER_IN_CONFIG}" >> /simple-storm-supervisor-healthcheck.log


if [ "${PORTS_NUMBER_IN_LOGS}" -ge "6"];
then
    exit 0
fi

if [ "${PORTS_NUMBER_IN_LOGS}" -le "${PORTS_NUBER_IN_CONFIG}" ];
then
    exit 2
else
    exit 0
fi
