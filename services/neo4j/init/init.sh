#!/bin/bash

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

# set the neo4j initial password only if you run the database server
if [ "${NEO4J_AUTH:-}" == "none" ]; then
    NEO4J_dbms_security_auth__enabled=false
elif [[ "${NEO4J_AUTH:-}" == neo4j/* ]]; then
    password="${NEO4J_AUTH#neo4j/}"
    if [ "${password}" == "neo4j" ]; then
        echo >&2 "Invalid value for password. It cannot be 'neo4j', which is the default."
        exit 1
    fi
    # Will exit with error if users already exist (and print a message explaining that)
    /usr/bin/neo4j-admin set-initial-password "${password}" || true
elif [ -n "${NEO4J_AUTH:-}" ]; then
    echo >&2 "Invalid value for NEO4J_AUTH: '${NEO4J_AUTH}'"
    exit 1
fi

# list env variables with prefix NEO4J_ and create settings from them
unset NEO4J_AUTH NEO4J_SHA256 NEO4J_TARBALL
for i in $( set | grep ^NEO4J_ | awk -F'=' '{print $1}' | sort -rn ); do
    setting=$(echo ${i} | sed 's|^NEO4J_||' | sed 's|_|.|g' | sed 's|\.\.|_|g')
    value=$(echo ${!i})
    # Don't allow settings with no value or settings that start with a number (neo4j converts settings to env variables and you cannot have an env variable that starts with a number)
    if [[ -n ${value} ]]; then
        if [[ ! "${setting}" =~ ^[0-9]+.*$ ]]; then
            if grep -q -F "${setting}=" /etc/neo4j/neo4j.conf; then
                # Remove any lines containing the setting already
                sed --in-place "/${setting}=.*/d" /etc/neo4j/neo4j.conf
            fi
            # Then always append setting to file
            echo "${setting}=${value}" >> /etc/neo4j/neo4j.conf
        else
            echo >&2 "WARNING: ${setting} not written to conf file because settings that start with a number are not permitted"
        fi
    fi
done

neo4j console &
# Wait for neo4j to be and execute queries.
/app/wait-for-it.sh -t 120 -h localhost -p 7473 -- cycli -f /app/neo4j-queries.cql
sleep infinity
