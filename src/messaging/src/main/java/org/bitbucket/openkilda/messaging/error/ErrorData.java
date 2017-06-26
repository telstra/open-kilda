package org.bitbucket.openkilda.messaging.error;

import static com.google.common.base.MoreObjects.toStringHelper;

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
        "error-type",
        "error-message",
        "error-description"})
public class ErrorData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Error type.
     */
    @JsonProperty("error-type")
    protected ErrorType errorType;

    /**
     * Error message.
     */
    @JsonProperty("error-message")
    protected String errorMessage;

    /**
     * Error description.
     */
    @JsonProperty("error-description")
    protected String errorDescription;

    /**
     * Instance constructor.
     *
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error exception
     */
    @JsonCreator
    public ErrorData(@JsonProperty("error-type") final ErrorType errorType,
                     @JsonProperty("error-message") final String errorMessage,
                     @JsonProperty("error-description") final String errorDescription) {
        this.errorType = errorType;
        this.errorMessage = errorMessage;
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("error-type", errorType)
                .add("error-message", errorMessage)
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
        return Objects.equals(getErrorType(), that.getErrorType())
                && Objects.equals(getErrorMessage(), that.getErrorMessage())
                && Objects.equals(getErrorDescription(), that.getErrorDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(errorType, errorMessage, errorDescription);
    }
}
