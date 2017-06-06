package org.bitbucket.openkilda.messaging.error;

import static com.google.common.base.Objects.toStringHelper;

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
        "error-code",
        "error-message",
        "error-description",
        "error-exception"})
public class ErrorData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

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
     * Error description.
     */
    @JsonProperty("error-description")
    private ErrorType errorDescription;

    /**
     * Error exception.
     */
    @JsonProperty("error-exception")
    private String errorException;

    /**
     * Instance constructor.
     *
     * @param errorCode        error code
     * @param errorMessage     error message
     * @param errorDescription error description
     * @param errorException   error exception
     */
    @JsonCreator
    public ErrorData(@JsonProperty("error-code") final int errorCode,
                     @JsonProperty("error-message") final String errorMessage,
                     @JsonProperty("error-description") final ErrorType errorDescription,
                     @JsonProperty("error-exception") final String errorException) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorDescription = errorDescription;
        this.errorException = errorException;
    }

    /**
     * Returns error code.
     *
     * @return error code
     */
    @JsonProperty("error-code")
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Sets error code.
     *
     * @param errorCode error code
     */
    @JsonProperty("error-code")
    public void setErrorCode(final int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Returns error message.
     *
     * @return error message
     */
    @JsonProperty("error-message")
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage error message
     */
    @JsonProperty("error-message")
    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the description of the error.
     *
     * @return error description
     */
    @JsonProperty("error-description")
    public ErrorType getErrorDescription() {
        return errorDescription;
    }

    /**
     * Sets the error description.
     *
     * @param errorDescription exception description
     */
    @JsonProperty("error-description")
    public void setErrorDescription(final ErrorType errorDescription) {
        this.errorDescription = errorDescription;
    }

    /**
     * Returns error exception.
     *
     * @return error exception
     */
    @JsonProperty("error-exception")
    public String getErrorException() {
        return errorException;
    }

    /**
     * Sets error exception.
     *
     * @param errorException error exception
     */
    @JsonProperty("error-exception")
    public void setErrorException(final String errorException) {
        this.errorException = errorException;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("error-code", errorCode)
                .add("error-message", errorMessage)
                .add("error-description", errorDescription)
                .add("error-exception", errorException)
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
                && Objects.equals(getErrorDescription(), that.getErrorDescription())
                && Objects.equals(getErrorException(), that.getErrorException());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(errorCode, errorException, errorDescription, errorException);
    }
}
