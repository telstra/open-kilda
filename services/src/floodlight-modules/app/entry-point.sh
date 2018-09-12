#!/bin/bash

config_file="/app/floodlightkilda.properties"
extra_args=( )

get_testing_mode_status() {
    sed -nE -e '/^org.openkilda.floodlight.kafka.KafkaMessageCollector.testing-mode[[:space:]]*=/ {s:^.*=::; s:[[:space:]]+$::; p}' \
        "${config_file}"
}

if [[ "$(get_testing_mode_status)" == "YES" ]]; then
    extra_args=(
        "${extra_args[@]}"
        -Dcom.sun.management.jmxremote
        -Dcom.sun.management.jmxremote.port=11011
        -Dcom.sun.management.jmxremote.local.only=false
        -Dcom.sun.management.jmxremote.authenticate=false
        -Dcom.sun.management.jmxremote.ssl=false
    )
fi

exec java \
    -XX:+PrintFlagsFinal \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseCGroupMemoryLimitForHeap \
    "${extra_args[@]}" \
    -Dlogback.configurationFile=/app/logback.xml \
    -cp /app/floodlight.jar:/app/floodlight-modules.jar \
    net.floodlightcontroller.core.Main -cf "${config_file}"
