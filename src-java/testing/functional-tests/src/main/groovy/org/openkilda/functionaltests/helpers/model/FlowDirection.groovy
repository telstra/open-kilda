package org.openkilda.functionaltests.helpers.model

enum FlowDirection {
    FORWARD("forward"),
    REVERSE("reverse"),
    PROTECTED_FORWARD("protected_forward"),
    PROTECTED_REVERSE("protected_reverse")

    private String direction

    FlowDirection(String direction) {
        this.direction = direction
    }

    String getDirection() {
        return direction
    }

    static FlowDirection getByDirection(String direction) {
        for (FlowDirection flowDirection : values()) {
            if (flowDirection.getDirection() == direction) {
                return flowDirection
            }
        }
        throw new IllegalArgumentException("Invalid value for encapsulation type")
    }
}
