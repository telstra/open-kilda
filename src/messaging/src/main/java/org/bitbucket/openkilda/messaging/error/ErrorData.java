package org.bitbucket.openkilda.messaging.error;

import static com.google.common.base.Objects.toStringHelper;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.MessageData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload of a Message representing an error.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "destination",
        "error-code",
        "error-message",
        "error-type",
        "error-description"})
public class ErrorData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message destination.
     */
    @JsonProperty("destination")
    private Destination destination;

    /**
     * Error code.
     */
    @JsonProperty("error-code")
    private int errorCode;

    /**
     * Error message.
     */
    @JsonProperty("error-message")
    private String errorMessage;

    /**
     * Error type.
     */
    @JsonProperty("error-type")
    private ErrorType errorType;

    /**
     * Error description.
     */
    @JsonProperty("error-description")
    private String errorDescription;

    /**
     * Instance constructor.
     *
     * @param errorType        error type
     * @param errorDescription error exception
     */
    public ErrorData(@JsonProperty("error-type") final ErrorType errorType,
                     @JsonProperty("error-description") final String errorDescription) {
        this.errorType = errorType;
        this.errorDescription = errorDescription;
        this.destination = Destination.WFM_TRANSACTION;
    }

    /**
     * Instance constructor.
     *
     * @param errorCode        error code
     * @param errorMessage     error message
     * @param errorType        error type
     * @param errorDescription error exception
     */
    @JsonCreator
    public ErrorData(@JsonProperty("error-code") final int errorCode,
                     @JsonProperty("error-message") final String errorMessage,
                     @JsonProperty("error-type") final ErrorType errorType,
                     @JsonProperty("error-description") final String errorDescription) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
        this.errorDescription = errorDescription;
        this.destination = Destination.WFM_TRANSACTION;
    }

    /**
     * Gets message destination.
     *
     * @return message destination
     */
    public Destination getDestination() {
        return destination;
    }

    /**
     * Sets message destination.
     *
     * @param destination message destination
     */
    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    /**
     * Returns error code.
     *
     * @return error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Sets error code.
     *
     * @param errorCode error code
     */
    public void setErrorCode(final int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Returns error message.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets error message.
     *
     * @param errorMessage error message
     */
    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns description of the error.
     *
     * @return error description
     */
    public String getErrorDescription() {
        return errorDescription;
    }

    /**
     * Sets error description.
     *
     * @param errorDescription exception description
     */
    public void setErrorDescription(final String errorDescription) {
        this.errorDescription = errorDescription;
    }

    /**
     * Returns error type.
     *
     * @return error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Sets error type.
     *
     * @param errorType error type
     */
    public void setErrorType(final ErrorType errorType) {
        this.errorType = errorType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("error-code", errorCode)
                .add("error-message", errorMessage)
                .add("error-type", errorType)
                .add("error-description", errorDescription)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !(obj instanceof ErrorData)) {
            return false;
        }

        ErrorData that = (ErrorData) obj;
        return Objects.equals(getErrorCode(), that.getErrorCode())
                && Objects.equals(getErrorMessage(), that.getErrorMessage())
                && Objects.equals(getErrorType(), that.getErrorType())
                && Objects.equals(getErrorDescription(), that.getErrorDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(errorCode, errorMessage, errorType, errorDescription);
    }
}
