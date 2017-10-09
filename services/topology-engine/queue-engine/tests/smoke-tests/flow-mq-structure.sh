#!/bin/bash
# Copyright 2017 Telstra Open Source
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


KAFKA_PRODUCER=~/bin/KafkaProducer.py
BOOTSTRAP_SERVER=localhost:9092
TOPIC=kilda-speaker

FLOWNAME=ABCDEF01
INPUT_VLAN=0
TRANSIT_VLAN_FORWARD=123
TRANSIT_VLAN_REVERSE=124
BANDWIDTH=10000

INGRESS_SWITCH=00:00:00:00:00:00:00:01
INGRESS_SWITCH_INPUT=1
INGRESS_SWITCH_OUTPUT=2
TRANSIT_SWITCH=00:00:00:00:00:00:00:02
TRANSIT_SWITCH_INPUT=2
TRANSIT_SWITCH_OUTPUT=3
EGRESS_SWITCH=00:00:00:00:00:00:00:03
EGRESS_SWITCH_INPUT=2
EGRESS_SWITCH_OUTPUT=1


INGRESS_DATA="{\"command\": \"install_ingress_flow\",\
\"destination\": \"CONTROLLER\",\
\"flow_name\":\"$FLOWNAME\",\
\"switch_id\": \"$INGRESS_SWITCH\",\
\"input_port\": $INGRESS_SWITCH_INPUT,\
\"output_port\": $INGRESS_SWITCH_OUTPUT,\
\"input_vlan_id\": $INPUT_VLAN,\
\"transit_vlan_id\": $TRANSIT_VLAN_FORWARD,
\"bandwidth\": $BANDWIDTH}"

echo $INGRESS_DATA
#$KAFKA_PRODUCER $BOOTSTRAP_SERVER $TOPIC "{\"type\": \"COMMAND\",\"timestamp\": 1490228616,\"data\":$INGRESS_DATA}"

EGRESS_DATA="{\"command\": \"install_egress_flow\",\
\"destination\": \"CONTROLLER\",\
\"flow_name\":\"$FLOWNAME\",\
\"switch_id\": \"$EGRESS_SWITCH\",\
\"input_port\": $EGRESS_SWITCH_INPUT,\
\"output_port\": $EGRESS_SWITCH_OUTPUT,\
\"transit_vlan_id\": $TRANSIT_VLAN_FORWARD}"

echo $EGRESS_DATA
#$KAFKA_PRODUCER $BOOTSTRAP_SERVER $TOPIC "{\"type\": \"COMMAND\",\"timestamp\": 1490228616,\"data\":$EGRESS_DATA}"

TRANSIT_DATA="{\"command\": \"install_transit_flow\",\
\"destination\": \"CONTROLLER\",\
\"flow_name\":\"$FLOWNAME\",\
\"switch_id\": \"$TRANSIT_SWITCH\",\
\"input_port\": $TRANSIT_SWITCH_INPUT,\
\"output_port\": $TRANSIT_SWITCH_OUTPUT,\
\"transit_vlan_id\": $TRANSIT_VLAN_FORWARD}"

echo $TRANSIT_DATA
#$KAFKA_PRODUCER $BOOTSTRAP_SERVER $TOPIC "{\"type\": \"COMMAND\",\"timestamp\": 1490228616,\"data\":$TRANSIT_DATA}"

#RETURN PATH
INGRESS_DATA="{\"command\": \"install_ingress_flow\",\
\"destination\": \"CONTROLLER\",\
\"flow_name\":\"$FLOWNAME\",\
\"switch_id\": \"$EGRESS_SWITCH\",\
\"input_port\": $EGRESS_SWITCH_OUTPUT,\
\"output_port\": $EGRESS_SWITCH_INPUT,\
\"input_vlan_id\": $INPUT_VLAN,\
\"transit_vlan_id\": $TRANSIT_VLAN_REVERSE,
\"bandwidth\": $BANDWIDTH}"

echo $INGRESS_DATA
#$KAFKA_PRODUCER $BOOTSTRAP_SERVER $TOPIC "{\"type\": \"COMMAND\",\"timestamp\": 1490228616,\"data\":$INGRESS_DATA}"

EGRESS_DATA="{\"command\": \"install_egress_flow\",\
\"destination\": \"CONTROLLER\",\
\"flow_name\":\"$FLOWNAME\",\
\"switch_id\": \"$INGRESS_SWITCH\",\
\"input_port\": $INGRESS_SWITCH_OUTPUT,\
\"output_port\": $INGRESS_SWITCH_INPUT,\
\"transit_vlan_id\": $TRANSIT_VLAN_REVERSE}"

echo $EGRESS_DATA
#$KAFKA_PRODUCER $BOOTSTRAP_SERVER $TOPIC "{\"type\": \"COMMAND\",\"timestamp\": 1490228616,\"data\":$EGRESS_DATA}"

TRANSIT_DATA="{\"command\": \"install_transit_flow\",\
\"destination\": \"CONTROLLER\",\
\"flow_name\":\"$FLOWNAME\",\
\"switch_id\": \"$TRANSIT_SWITCH\",\
\"input_port\": $TRANSIT_SWITCH_OUTPUT,\
\"output_port\": $TRANSIT_SWITCH_INPUT,\
\"transit_vlan_id\": $TRANSIT_VLAN_REVERSE}"

echo $TRANSIT_DATA
#$KAFKA_PRODUCER $BOOTSTRAP_SERVER $TOPIC "{\"type\": \"COMMAND\",\"timestamp\": 1490228616,\"data\":$TRANSIT_DATA}"
