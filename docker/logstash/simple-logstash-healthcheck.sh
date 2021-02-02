#!/bin/bash


set -eu${DEBUG:+x}

set -o pipefail


for PORT in $(seq 5000 5006);
do
    nc -zv localhost  ${PORT} 2>&1 | tee -a /simple-logstash-healthcheck.log
done
