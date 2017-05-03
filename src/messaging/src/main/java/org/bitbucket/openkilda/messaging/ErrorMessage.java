package org.bitbucket.openkilda.messaging;

import org.bitbucket.openkilda.messaging.error.ErrorData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents error message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the error message.
     */
    @JsonProperty("data")
    private ErrorData data;

    /**
     * Default constructor.
     */
    public ErrorMessage() {
    }

    /**
     * Instance constructor.
     *
     * @param data error message data
     */
    @JsonCreator
    public ErrorMessage(@JsonProperty("data") final ErrorData data) {
        this.data = data;
    }

    /**
     * Returns data of the error message.
     *
     * @return error message data
     */
    @JsonProperty("data")
    public ErrorData getData() {
        return data;
    }

    /**
     * Sets data of the error message.
     *
     * @param data error message data
     */
    @JsonProperty("data")
    public void setData(final ErrorData data) {
        this.data = data;
    }
}
