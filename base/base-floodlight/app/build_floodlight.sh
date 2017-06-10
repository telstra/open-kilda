#!/bin/bash

cd /app/floodlight
#ant
patch -p1 < /app/floodlight.diff
mvn install -DskipTests
mkdir /var/lib/floodlight
chmod 777 /var/lib/floodlight
