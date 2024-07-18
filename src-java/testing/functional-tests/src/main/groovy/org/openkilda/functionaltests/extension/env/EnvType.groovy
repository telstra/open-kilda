package org.openkilda.functionaltests.extension.env

enum EnvType {
    VIRTUAL_ENV("virtual"),
    HARDWARE_ENV("hardware")

    String value

    EnvType(String environmentType) {
        this.value = environmentType
    }
}