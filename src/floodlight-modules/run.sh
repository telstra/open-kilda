#!/bin/bash

cd ../../../floodlight
mvn install

cd ../floodlight-test
mvn install

cd ../kilda-controller/src/floodlight-modules
mvn verify
java -Dlogback.configurationFile=src/test/resources/logback.xml -cp ../../../floodlight/target/floodlight.jar:target/floodlight-modules-0.0.1-SNAPSHOT.jar net.floodlightcontroller.core.Main -cf src/main/resources/floodlightkilda.properties
