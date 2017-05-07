package org.bitbucket.openkilda.messaging.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The entity exception type enum.
 */
public enum ErrorType {
    /**
     * The error message for internal service error.
     */
    INTERNAL_ERROR("Internal service error"),

    /**
     * The error message for object not found.
     */
    NOT_FOUND("Object was not found"),

    /**
     * The error message for object already exists.
     */
    ALREADY_EXISTS("Object already exists"),

    /**
     * The error message for invalid request data.
     */
    DATA_INVALID("Invalid request data"),

    /**
     * The error message for invalid request parameters.
     */
    PARAMETERS_INVALID("Invalid request parameters"),

    /**
     * The error message for operation timeout.
     */
    OPERATION_TIMED_OUT("Operation has timed out"),

    /**
     * The error message for invalid request credentials.
     */
    AUTH_FAILED("Invalid credentials");

    /**
     * The text type value.
     */
    @JsonProperty("type")
    private final String type;

    /**
     * Constructs instance by type value.
     *
     * @param type the type value
     */
    @JsonCreator
    ErrorType(@JsonProperty("type") final String type) {
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return type;
    }
}
