#!/bin/sh

if [ -n "${OK_TESTS}" ]; then
    # enable debug for neo4j in case future lock issues (only on local/devel environment)

    dbms_jvm_additional=""
    dbms_jvm_additional="${dbms_jvm_additional} -Dcom.sun.management.jmxremote"
    dbms_jvm_additional="${dbms_jvm_additional} -Dcom.sun.management.jmxremote.port=16010"
    dbms_jvm_additional="${dbms_jvm_additional} -Dcom.sun.management.jmxremote.local.only=false"
    dbms_jvm_additional="${dbms_jvm_additional} -Dcom.sun.management.jmxremote.authenticate=false"
    dbms_jvm_additional="${dbms_jvm_additional} -Dcom.sun.management.jmxremote.ssl=false"
    dbms_jvm_additional="${dbms_jvm_additional} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=16011"

    export dbms_jvm_additional
fi

neo4j console &
# Wait for neo4j to be and execute queries.
/app/wait-for-it.sh -t 120 -h localhost -p 7473 -- cycli -f /app/neo4j-queries.cql
sleep infinity
