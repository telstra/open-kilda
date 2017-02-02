#!/bin/bash

cd /app/floodlight
ant
mvn install
mkdir /var/lib/floodlight
chmod 777 /var/lib/floodlight
