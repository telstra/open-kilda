#!/bin/bash

set -e

topology=$1
if [ -z "${topology}" ]; then
    echo "Usage $0: <topology_name>"
    exit 1
fi

storm kill "${topology}"

complete=1
wait_cycles=10
attempts=$(eval echo "{1..${wait_cycles}}")
for idx in ${attempts}; do
    if [ -z "$(storm list | grep "^${topology} ")" ]; then
        complete=0
        break
    fi
    sleep 1
    echo "[$idx of $wait_cycles] ${topology} still alive..."
done

exit ${complete}
