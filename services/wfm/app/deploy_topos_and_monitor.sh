#!/usr/bin/env bash

cd /app/wfm

##
## Add all topologies here .. so that kilda comes up with them.
##
export PATH=${PATH}:/opt/storm/bin

storm list | grep splitter >/dev/null && storm kill splitter && sleep 5
storm list | grep wfm >/dev/null && storm kill wfm && sleep 5
storm list | grep flow >/dev/null && storm kill flow && sleep 5

make deploy-splitter
make deploy-wfm
make deploy-flow

/app/wfm/app/wfm.py $@
