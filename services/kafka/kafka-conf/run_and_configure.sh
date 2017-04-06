#!/bin/bash

nohup /opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties &
sleep 10
/opt/kafka/bin/create_topics.sh