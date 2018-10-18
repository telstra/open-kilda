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

set -e

export SELF_CONTAINER_ID=$(head -1 /proc/self/cgroup | cut -d/ -f3)

if [ "$1" = 'api' ]; then
    cd /app/labapi
    exec python3 /app/labapi/api.py
elif [ "$1" = 'service' ]; then
    export PATH=$PATH:/usr/share/openvswitch/scripts \
    && ovs-ctl start \
    && ovs-vsctl init \
    && sleep 1 \
    && exec python3 /app/labservice/main.py
fi

exec "$@"
