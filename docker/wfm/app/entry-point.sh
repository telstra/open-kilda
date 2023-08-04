#!/usr/bin/env bash
# Copyright 2018 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

set -eu ${DEBUG:+-x}

PATH=${PATH}:/opt/storm/bin
CPU_CORES=$(grep -c 'processor' /proc/cpuinfo)
THREAD_COUNT=$(( CPU_CORES * 3 / 4 )) # use 75 % of all CPUS to do not have high system load
THREAD_COUNT=$(( THREAD_COUNT > 0 ? THREAD_COUNT : 1 ))

cd /app

case ${WFM_TOPOLOGIES_MODE:-} in

  blue)
    make -j"$THREAD_COUNT" kill-all-blue
    exec make -j"$THREAD_COUNT" deploy-all-blue
    ;;

  green)
    make -j"$THREAD_COUNT" kill-all-green
    exec make -j"$THREAD_COUNT" deploy-all-green
    ;;

  *)
    make -j"$THREAD_COUNT" kill-all
    exec make -j"$THREAD_COUNT" deploy-all
    ;;
esac
