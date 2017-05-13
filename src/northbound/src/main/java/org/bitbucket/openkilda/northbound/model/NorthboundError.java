package org.bitbucket.openkilda.northbound.model;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.MoreObjects.toStringHelper;
import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.northbound.utils.Constants.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * The class represents error response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NorthboundError implements Serializable {
    /**
     * The Constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The failed request correlation ID.
     */
    @JsonProperty(CORRELATION_ID)
    private String correlationId;

    /**
     * The error timestamp.
     */
    @JsonProperty(TIMESTAMP)
    private long timestamp;

    /**
     * The error code.
     */
    @JsonProperty("error-code")
    private long code;

    /**
     * The error message.
     */
    @JsonProperty("error-message")
    private String message;

    /**
     * The error description.
     */
    @JsonProperty("error-description")
    private String description;

    /**
     * The caused exception.
     */
    @JsonProperty("error-exception")
    private String exception;

    /**
     * Constructs the error.
     */
    public NorthboundError() {
    }

    /**
     * Constructs the error.
     *
     * @param correlationId the failed request correlation id
     * @param timestamp     the error timestamp
     * @param status        the error HttpStatus
     * @param description   the error description
     * @param exception     the caused error exception name
     */
    public NorthboundError(final String correlationId,
                           final long timestamp,
                           final HttpStatus status,
                           final String description,
                           final String exception) {
        this.correlationId = firstNonNull(correlationId, DEFAULT_CORRELATION_ID);
        this.timestamp = timestamp;
        this.code = status.value();
        this.message = status.getReasonPhrase();
        this.description = description;
        this.exception = exception;
    }

    /**
     * Constructs the error.
     *
     * @param correlationId the failed request correlation id
     * @param timestamp     the error timestamp
     * @param code          the error HttpStatus code
     * @param message       the error HttpStatus reason phrase
     * @param description   the error description
     * @param exception     the caused error exception name
     */
    @JsonCreator
    public NorthboundError(@JsonProperty(CORRELATION_ID) final String correlationId,
                           @JsonProperty(TIMESTAMP) final long timestamp,
                           @JsonProperty("error-code") final long code,
                           @JsonProperty("error-message") final String message,
                           @JsonProperty("error-description") final String description,
                           @JsonProperty("error-exception") final String exception) {
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.code = code;
        this.message = message;
        this.description = description;
        this.exception = exception;
    }

    /**
     * Gets the error description.
     *
     * @return the error description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the error description.
     *
     * @param error the error description
     */
    public void setDescription(String error) {
        this.description = error;
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
     * Gets the error code.
     *
     * @return the error code
     */
    public long getCode() {
        return code;
    }

    /**
     * Sets the error code.
     *
     * @param status the error code
     */
    public void setCode(long status) {
        this.code = code;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(CORRELATION_ID, correlationId)
                .add(TIMESTAMP, timestamp)
                .add("error-code", code)
                .add("error-message", message)
                .add("error-description", description)
                .add("error-exception", exception)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(correlationId, timestamp, code, message, description, exception);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        NorthboundError that = (NorthboundError) object;
        return Objects.equals(getCorrelationId(), that.getCorrelationId())
                && Objects.equals(getTimestamp(), that.getTimestamp())
                && Objects.equals(getCode(), that.getCode())
                && Objects.equals(getMessage(), that.getMessage())
                && Objects.equals(getDescription(), that.getDescription())
                && Objects.equals(getException(), that.getException());
    }
}
