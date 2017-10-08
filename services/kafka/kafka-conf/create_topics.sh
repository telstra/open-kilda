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


STD_OPTS="--create --partition 1 --replication-factor 1 "
CREATE_SIMPLE="/opt/kafka/bin/kafka-topics.sh --zookeeper zookeeper.pendev:2181 ${STD_OPTS} --topic"

${CREATE_SIMPLE} kilda.speaker
${CREATE_SIMPLE} kilda-test
${CREATE_SIMPLE} speaker.command
${CREATE_SIMPLE} speaker.other
${CREATE_SIMPLE} speaker.info
${CREATE_SIMPLE} speaker.info.switch
${CREATE_SIMPLE} speaker.info.switch.updown
${CREATE_SIMPLE} speaker.info.switch.other
${CREATE_SIMPLE} speaker.info.port
${CREATE_SIMPLE} speaker.info.port.updown
${CREATE_SIMPLE} speaker.info.port.other
${CREATE_SIMPLE} speaker.info.isl
${CREATE_SIMPLE} speaker.info.isl.updown
${CREATE_SIMPLE} speaker.info.isl.other
${CREATE_SIMPLE} speaker.info.other
${CREATE_SIMPLE} kilda.wfm.topo.updown
