package org.bitbucket.openkilda.messaging.command;

import org.bitbucket.openkilda.messaging.MessageData;
import org.bitbucket.openkilda.messaging.command.flow.InstallEgressFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallIngressFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.bitbucket.openkilda.messaging.command.flow.InstallTransitFlow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the data payload of a Message representing an command.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "command")
@JsonSubTypes({
        @Type(value = DefaultFlowsCommandData.class, name = "install_default_flows"),
        @Type(value = InstallIngressFlow.class, name = "install_ingress_flow"),
        @Type(value = InstallEgressFlow.class, name = "install_egress_flow"),
        @Type(value = InstallTransitFlow.class, name = "install_transit_flow"),
        @Type(value = InstallOneSwitchFlow.class, name = "install_one_switch_flow"),
        @Type(value = DiscoverIslCommandData.class, name = "discover_isl"),
        @Type(value = DiscoverPathCommandData.class, name = "discover_path")})
public abstract class CommandData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message destination.
     */
    @JsonProperty("destination")
    private CommandDestinationType destination;

    /**
     * Message command.
     */
    @JsonProperty("command")
    private String command;

    /**
     * Default constructor.
     */
    public CommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param destination message destination
     * @param command     message command
     */
    @JsonCreator
    public CommandData(@JsonProperty("destination") final CommandDestinationType destination,
                       @JsonProperty("command") final String command) {
        this.destination = destination;
        this.command = command;
    }

    /**
     * Returns message destination.
     *
     * @return message destination
     */
    @JsonProperty("destination")
    public CommandDestinationType getDestination() {
        return destination;
    }

    /**
     * Sets message destination.
     *
     * @param destination message destination to set
     */
    @JsonProperty("destination")
    public void setDestination(final CommandDestinationType destination) {
        this.destination = destination;
    }

    /**
     * Returns message command.
     *
     * @return message command
     */
    @JsonProperty("command")
    public String getCommand() {
        return command;
    }

    /**
     * Sets message command.
     *
     * @param command message command to set
     */
    @JsonProperty("command")
    public void setCommand(final String command) {
        this.command = command;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s -> %s", command, destination);
    }
}
