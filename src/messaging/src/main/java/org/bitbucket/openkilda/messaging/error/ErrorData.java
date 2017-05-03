package org.bitbucket.openkilda.messaging.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the data payload of a Message representing an error.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "error-code",
        "error-type",
        "error-message",
        "error-description"})
public class ErrorData {
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
     * Error type.
     */
    @JsonProperty("error-type")
    private String errorType;

    /**
     * Error message.
     */
    @JsonProperty("error-message")
    private String errorMessage;

    /**
     * Error description.
     */
    @JsonProperty("error-description")
    private String errorDescription;

    /**
     * Default constructor.
     */
    public ErrorData() {
    }

    /**
     * Instance constructor.
     *
     * @param code        - error code
     * @param type        - error type
     * @param message     - error message
     * @param description - error description
     */
    @JsonCreator
    public ErrorData(@JsonProperty("error-code") final int code,
                     @JsonProperty("error-type") final String type,
                     @JsonProperty("error-message") final String message,
                     @JsonProperty("error-description") final String description) {
        this.errorCode = code;
        this.errorType = type;
        this.errorMessage = message;
        this.errorDescription = description;
    }

    /**
     * getErrorCode - Returns error code.
     *
     * @return error code
     */
    @JsonProperty("error-code")
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * setErrorCode - Sets error code.
     *
     * @param errorCode - code to set
     */
    @JsonProperty("error-code")
    public void setErrorCode(final int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * getErrorType - Returns error type.
     *
     * @return error type
     */
    @JsonProperty("error-type")
    public String getErrorType() {
        return errorType;
    }

    /**
     * setErrorType - Sets error type.
     *
     * @param errorType - type to set
     */
    @JsonProperty("error-type")
    public void setErrorType(final String errorType) {
        this.errorType = errorType;
    }

    /**
     * getErrorMessage - Returns error message.
     *
     * @return error message
     */
    @JsonProperty("error-message")
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * setErrorMessage - Sets the error message.
     *
     * @param errorMessage - error message to set
     */
    @JsonProperty("error-message")
    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * getErrorDescription - Returns the description of the error.
     *
     * @return error description
     */
    @JsonProperty("error-description")
    public String getErrorDescription() {
        return errorDescription;
    }

    /**
     * setErrorDescription - Sets the error description.
     *
     * @param errorDescription - description to set
     */
    @JsonProperty("error-description")
    public void setErrorDescription(final String errorDescription) {
        this.errorDescription = errorDescription;
    }
}
