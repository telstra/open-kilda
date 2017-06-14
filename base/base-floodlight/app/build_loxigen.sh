#!/bin/bash

cd /app/loxigen
git checkout bec5ec03459ae60c8da0ef6a4791e9ceeb7c1939
cp /app/nicira_l2 /app/loxigen/openflow_input/nicira_l2
cp /app/legacy_meter /app/loxigen/openflow_input/legacy_meter
patch -p1 < /app/loxigen.diff
make java
cd /app/loxigen/loxi_output/openflowj
MAVEN_OPTS="-Xmx4G" mvn install -DskipTests -Dmaven.javadoc.skip=true
