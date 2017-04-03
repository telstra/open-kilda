#!/bin/bash

STD_OPTS="--create --partition 1 --replication-factor 1 "
CREATE_SIMPLE="kafka-topics --zookeeper localhost ${STD_OPTS} --topic"

${CREATE_SIMPLE} kilda.speaker
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
