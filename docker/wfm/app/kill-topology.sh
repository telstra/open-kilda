#!/bin/bash

set -e

STORM="${STORM:-/opt/storm/bin/storm}"

topology=$1
if [ -z "${topology}" ]; then
    echo "Usage $0: <topology_name>"
    exit 1
fi

"${STORM}" kill "${topology}"

complete=1
wait_cycles=10
attempts=$(eval echo "{1..${wait_cycles}}")
for idx in ${attempts}; do
    if [ -z "$("${STORM}" list | grep "^${topology} ")" ]; then
        complete=0
        break
    fi
    sleep 1
    echo "[$idx of $wait_cycles] ${topology} still alive..."
done

exit ${complete}
