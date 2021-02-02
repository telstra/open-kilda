#!/bin/bash

set -eu${DEBUG:+x}

set -o pipefail

# Pre-check
/usr/bin/jps | grep Supervisor || exit 1


CONFIG=${1:-/opt/apache-storm-1.2.1/conf/storm.yaml}
LOG_FILE=$(2:-/opt/apache-storm-1.2.1/logs/supervisor.log)

PORTS_NUBER_IN_CONFIG=$(cat ${CONFIG}  | sed -n '/supervisor.slots.ports/,/^$/p' | grep -v supervisor.slots.ports | grep -v '^$' | awk -F '-' '{ print $2}' | wc -l )
PORTS_NUMBER_IN_LOGS=$(head  -1000 ${LOG_FILE} | grep 'Starting in state' | wc -l )



if [ ${PORTS_NUMBER_IN_LOGS} -ge ${PORTS_NUBER_IN_CONFIG} ];
then
    exit 2
else
    exit 0
fi
