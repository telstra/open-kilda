package org.bitbucket.openkilda.floodlight.message.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Created by jonv on 3/4/17.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "error_code",
        "error_message",
        "error_description"
})

/**
 * Defines the data payload of a Message representing an error.
 */

public class ErrorData {
    @JsonProperty("error_code")
    private int errorCode;
    @JsonProperty("error_message")
    private String errorMessage;
    @JsonProperty("error_descripiton")
    private String errorDescription;

    /**
     * getErrorCode - Returns error code.
     *
     * @return int
     */
    @JsonProperty("error_code")
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * setErrorCode - Sets error code.
     *
     * @param errorCode - code to set
     */
    @JsonProperty("error_code")
    public void setErrorCode(final int errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorData withErrorCode(int errorCode) {
        setErrorCode(errorCode);
        return this;
    }

    /**
     * getErrorMessage - Returns error message.
     *
     * @return String
     */
    @JsonProperty("error_message")
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * setErrorMessage - Sets the error message.
     *
     * @param errorMessage - error message to set
     */
    @JsonProperty("error_message")
    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ErrorData withErrorMessage(String errorMessage) {
        setErrorMessage(errorMessage);
        return this;
    }

    /**
     * getErrorDescription - Returns the description of the error.
     *
     * @return String
     */
    @JsonProperty("error_descripiton")
    public String getErrorDescription() {
        return errorDescription;
    }

    /**
     * setErrorDescription - Sets the error description.
     *
     * @param errorDescription - description of the error
     */
    @JsonProperty("error_descripiton")
    public void setErrorDescription(final String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public ErrorData withErrorDescription(String errorDescription) {
        setErrorDescription(errorDescription);
        return this;
    }
}
