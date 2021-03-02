#!/usr/bin/env bash
# Copyright 2020 Telstra Open Source
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

set -e ${DEBUG:+-x}

STORM="${STORM:-/opt/storm/bin/storm}"

TOPOLOGY_NAME="${1}"
EXPIRATION_TIME="${2}"

set -u

if [ -z "${TOPOLOGY_NAME}" ]; then
  echo "Usage $0: <topology-name> [expiration-time]"
  exit 1
fi

"${STORM}" set_log_level -l org.openkilda=TRACE"${EXPIRATION_TIME:+:EXPIRATION_TIME}" "${TOPOLOGY_NAME}"
