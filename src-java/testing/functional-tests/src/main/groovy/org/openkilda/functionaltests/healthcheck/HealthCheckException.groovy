package org.openkilda.functionaltests.healthcheck

class HealthCheckException extends RuntimeException {
    Object id;

    HealthCheckException(String message, Object id) {
        super(message)
        this.id = id
    }

    Object getId() {
        return id
    }
}
