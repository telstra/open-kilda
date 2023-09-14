package org.openkilda.functionaltests.model.stats

enum Status {
    ERROR("error"),
    SUCCESS("success")

    final String value

    Status(String value) {
        this.value = value
    }
}
