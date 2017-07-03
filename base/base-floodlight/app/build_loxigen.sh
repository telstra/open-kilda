#!/bin/bash

cd /app/loxigen
git checkout bec5ec03459ae60c8da0ef6a4791e9ceeb7c1939
patch -p1 < /app/loxigen.diff
make java

cd /app/loxigen/loxi_output/openflowj
patch -p1 < /app/openflowj.diff
MAVEN_OPTS="-Xmx4G" mvn install -DskipTests -Dmaven.javadoc.skip=true
