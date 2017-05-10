package org.bitbucket.openkilda.messaging;

import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents command message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the command message.
     */
    @JsonProperty("data")
    private CommandData data;

    /**
     * Default constructor.
     */
    public CommandMessage() {
    }

    /**
     * Instance constructor.
     *
     * @param data command message data
     */
    @JsonCreator
    public CommandMessage(@JsonProperty("data") final CommandData data) {
        this.data = data;
    }

    /**
     * Returns data of the command message.
     *
     * @return command message data
     */
    @JsonProperty("data")
    public CommandData getData() {
        return data;
    }

    /**
     * Sets data of the command message.
     *
     * @param data command message data
     */
    @JsonProperty("data")
    public void setData(final CommandData data) {
        this.data = data;
    }
}
