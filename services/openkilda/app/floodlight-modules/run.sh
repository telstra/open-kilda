#!/bin/bash

ant -buildfile ../../../floodlight

mvn -f ../../../floodlight-test install

mvn package
java -Dlogback.configurationFile=src/test/resources/logback.xml -cp ../../../floodlight/target/floodlight.jar:target/floodlight-modules-0.0.1-SNAPSHOT.jar net.floodlightcontroller.core.Main -cf src/main/resources/floodlightkilda.properties
