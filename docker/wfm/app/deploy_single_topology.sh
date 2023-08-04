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

set -eu ${DEBUG:+-x}

STORM="${STORM:-/opt/storm/bin/storm}"

TOPOLOGY_NAME=${1}
PREFIX=${2:-""}
SUFFIX=${3:-""}

TOPOLOGY_CONFIG=${4:-"/app/topology.properties"}
TOPOLOGY_DEFINITION=${5:-"/app/${TOPOLOGY_NAME}-storm-topology/topology-definition.yaml"}

TOPOLOGY_JAR=$(ls -1 /app/${TOPOLOGY_NAME}-storm-topology/libs/ | grep -v "\-original" | head -1)
COMMA_SEPARATED_DEPENDENCY_LIST=""

DEPENDENCY_JAR_DIR="/app/${TOPOLOGY_NAME}-storm-topology/dependency-jars/"
if [ -d "$DEPENDENCY_JAR_DIR" ]
then
    for DEPENDENCY_JAR in $(ls -1 "$DEPENDENCY_JAR_DIR");
    do
        COMMA_SEPARATED_DEPENDENCY_LIST="${COMMA_SEPARATED_DEPENDENCY_LIST:+$COMMA_SEPARATED_DEPENDENCY_LIST,}${DEPENDENCY_JAR}${DEPENDENCY_JAR}"
    done
fi

MAIN_CLASS=$(grep 'Main-Class' /app/${TOPOLOGY_NAME}-storm-topology/build.gradle  | awk -F ':' '{ print $2}' | awk -F "'" '{ print $2 }')

get_zookeeper_node_value () {
    ZOO_LOG4J_PROP="OFF,ROLLINGFILE" /opt/zookeeper/bin/zkCli.sh -server zookeeper get "$1" 2>/dev/null | tail -1
}

is_number () {
    [ "$1" -eq "$1" ] 2>/dev/null
}

get_state () {
    get_zookeeper_node_value "/kilda/$1/blue/state"
}

get_validated_state () {
    state=$(get_state "$1")
    if is_number "$state"
    then
      echo "$state"
    else
      echo "unknown_state"
    fi
}

get_expected_state () {
    get_zookeeper_node_value "/kilda/$1/blue/expected_state"
}

get_validated_expected_state () {
    expected_state=$(get_expected_state "$1")
    if is_number "$expected_state"
    then
      echo "$expected_state"
    else
      echo "unknown_expected_state" # must not be equal to invalid value of get_state_as_number
    fi
}

"${STORM}" \
    jar /app/${TOPOLOGY_NAME}-storm-topology/libs/${TOPOLOGY_JAR} \
    ${MAIN_CLASS} \
    --jars \
    "${COMMA_SEPARATED_DEPENDENCY_LIST}" \
    --name ${PREFIX}${TOPOLOGY_NAME}${SUFFIX} \
    --topology-config ${TOPOLOGY_CONFIG} \
    --topology-definition ${TOPOLOGY_DEFINITION}

# Waiting till topology will be initialized
state=$(get_validated_state "${TOPOLOGY_NAME}")
expected_state=$(get_validated_expected_state "${TOPOLOGY_NAME}")
echo "Current state of topology ${TOPOLOGY_NAME} is '$state'. Expected state is '$expected_state'"

attempt=1
max_attempts=30
while [[ $state != "$expected_state" && $attempt -le $max_attempts ]]
do
  echo "Waiting till ${TOPOLOGY_NAME} topology state '$state' become equal to '$expected_state'. Attempt $attempt/$max_attempts"
  attempt=$(( attempt + 1 ))
  sleep 5
  state=$(get_validated_state "${TOPOLOGY_NAME}")
  expected_state=$(get_validated_expected_state "${TOPOLOGY_NAME}")
done

if [ "$state" == "$expected_state" ]
then
  echo "Topology ${TOPOLOGY_NAME} is in expected state $expected_state"
else
  echo "ERROR: Topology ${TOPOLOGY_NAME} state is $state but expected state is $expected_state"
  exit 1
fi
