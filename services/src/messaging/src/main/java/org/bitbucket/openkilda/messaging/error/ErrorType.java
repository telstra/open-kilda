package org.bitbucket.openkilda.messaging.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The entity exception type enum.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum ErrorType {
    /**
     * The error message for internal service error.
     */
    INTERNAL_ERROR("Internal service error"),

    /**
     * The error message for flow creation failure.
     */
    CREATION_FAILURE("Flow creation error"),

    /**
     * The error message for flow update failure.
     */
    UPDATE_FAILURE("Flow update error"),

    /**
     * The error message for flow deletion failure.
     */
    DELETION_FAILURE("Flow deletion error"),

    /**
     * The error message for not implemented error.
     */
    NOT_IMPLEMENTED("Feature not implemented"),

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
     * The error message for invalid request.
     */
    REQUEST_INVALID("Invalid request"),

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
    @JsonProperty("error-type")
    private final String errorType;

    /**
     * Instance constructor.
     *
     * @param errorType the type value
     */
    @JsonCreator
    ErrorType(@JsonProperty("error-type") final String errorType) {
        this.errorType = errorType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return errorType;
    }
}
