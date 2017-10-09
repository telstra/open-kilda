#!/usr/bin/env bash

cd /app/wfm

##
## Add all topologies here .. so that kilda comes up with them.
##
export PATH=${PATH}:/opt/storm/bin

storm list | grep splitter >/dev/null && storm kill splitter && sleep 5
storm list | grep wfm >/dev/null && storm kill wfm && sleep 5
storm list | grep flow >/dev/null && storm kill flow && sleep 5
storm list | grep stats >/dev/null && storm kill stats && sleep 5
storm list | grep cache >/dev/null && storm kill cache && sleep 5
storm list | grep islstats >/dev/null && storm kill islstats && sleep 5

config_file=$1

make deploy-splitter config=$config_file
make deploy-wfm config=$config_file
make deploy-flow config=$config_file
make deploy-stats config=$config_file
make deploy-cache config=$config_file
make deploy-islstats config=$config_file

