#!/bin/bash

set -eu${DEBUG:+x}

set -o pipefail

CONFIG=${1:-/opt/apache-storm-1.2.1/conf/storm.yaml}

for PORT in $(cat ${CONFIG}  | sed -n '/supervisor.slots.ports/,/^$/p' | grep -v supervisor.slots.ports | grep -v '^$' | awk -F '-' '{ print $2}');
do
    echo "Checking Port ${PORT}"; 
    netstat -ntpl | \
        grep java | \
        grep LISTEN | \
        awk '{ print $4}'| \
        grep "0.0.0.0:${PORT}" || exit 1
done
