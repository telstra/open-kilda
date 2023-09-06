package org.openkilda.functionaltests.model.stats

enum Direction {
    FORWARD("forward"),
    REVERSE("reverse")

    final String value

    Direction(String value) {
        this.value = value
    }
}
