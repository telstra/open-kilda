package org.bitbucket.openkilda.northbound.model;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * The class represents error response.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse implements Serializable {
    /**
     * The Constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The error status.
     */
    private long status;

    /**
     * The error code.
     */
    private String error;

    /**
     * The error message.
     */
    private String message;

    /**
     * The caused exception.
     */
    private String exception;

    /**
     * The failed request correlation ID.
     */
    @JsonProperty("correlation-id")
    private String correlationId;

    /**
     * The error timestamp.
     */
    private long timestamp;

    /**
     * Constructs the error.
     */
    public ErrorResponse() {
    }

    /**
     * Constructs the error.
     *
     * @param status        the error HttpStatus status
     * @param message       the error reason message
     * @param exception     the caused error exception
     * @param correlationId the failed request correlation ID
     * @param timestamp     the error timestamp
     */
    public ErrorResponse(HttpStatus status, String message, String exception, String correlationId, long timestamp) {
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.message = message;
        this.exception = exception;
        this.correlationId = firstNonNull(correlationId, DEFAULT_CORRELATION_ID);
        this.timestamp = timestamp;
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error code.
     *
     * @param error the error code
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the error message.
     *
     * @param message the error message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the failed request correlation ID.
     *
     * @return the failed request correlation ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the failed request correlation ID.
     *
     * @param correlationId the failed request correlation ID
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * Gets the error status.
     *
     * @return the error status
     */
    public long getStatus() {
        return status;
    }

    /**
     * Sets the error status.
     *
     * @param status the error status
     */
    public void setStatus(long status) {
        this.status = status;
    }

    /**
     * Gets the error timestamp.
     *
     * @return the error timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the error timestamp.
     *
     * @param timestamp the error timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the caused error exception.
     *
     * @return the exception
     */
    public String getException() {
        return exception;
    }

    /**
     * Sets the caused error exception.
     *
     * @param exception the caused error exception
     */
    public void setException(String exception) {
        this.exception = exception;
    }
}
