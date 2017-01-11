#!/bin/bash

mvn package
java -cp /Users/jonv/Documents/kilda/floodlight/target/floodlight.jar:/Users/jonv/Documents/kilda/kilda-controller/src/pathverification/target/pathverification-0.0.1-SNAPSH.jar net.floodlightcontroller.core.Main -cf /Users/jonv/Documents/kilda/kilda-controller/src/pathverification/src/main/resources/floodlightkilda.properties
