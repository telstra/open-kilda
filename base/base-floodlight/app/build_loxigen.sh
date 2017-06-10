#!/bin/bash

cp /app/nicira_l2 /app/loxigen/openflow_input/nicira_l2
cp /app/legacy_meter /app/loxigen/openflow_input/legacy_meter
cd /app/loxigen
patch -p1 < /app/loxigen.diff
make java
cd /app/loxigen/loxi_output/openflowj
MAVEN_OPTS="-Xmx4G" mvn install -DskipTests -Dmaven.javadoc.skip=true
