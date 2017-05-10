package org.bitbucket.openkilda.messaging;

import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents information message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfoMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the information message.
     */
    @JsonProperty("data")
    private InfoData data;

    /**
     * Default constructor.
     */
    public InfoMessage() {
    }

    /**
     * Instance constructor.
     *
     * @param data error info data
     */
    @JsonCreator
    public InfoMessage(@JsonProperty("data") final InfoData data) {
        this.data = data;
    }

    /**
     * Returns data of the information message.
     *
     * @return information message data
     */
    @JsonProperty("data")
    public InfoData getData() {
        return data;
    }

    /**
     * Sets data of the information message.
     *
     * @param data information message data
     */
    @JsonProperty("data")
    public void setData(final InfoData data) {
        this.data = data;
    }
}
