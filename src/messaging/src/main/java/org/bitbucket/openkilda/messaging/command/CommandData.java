package org.bitbucket.openkilda.messaging.command;

import org.bitbucket.openkilda.messaging.DestinationType;
import org.bitbucket.openkilda.messaging.MessageData;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.bitbucket.openkilda.messaging.command.flow.DefaultFlowsCommandData;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.InstallEgressFlowCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallIngressFlowCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallOneSwitchFlowCommandData;
import org.bitbucket.openkilda.messaging.command.flow.InstallTransitFlowCommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload of a Message representing an command.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "command",
        visible = true)
@JsonSubTypes({
        @Type(value = FlowsStatusRequest.class, name = "flows_status"),
        @Type(value = FlowStatusRequest.class, name = "flow_status"),
        @Type(value = FlowCreateRequest.class, name = "flow_create"),
        @Type(value = FlowUpdateRequest.class, name = "flow_update"),
        @Type(value = FlowDeleteRequest.class, name = "flow_delete"),
        @Type(value = FlowsGetRequest.class, name = "flows_get"),
        @Type(value = FlowGetRequest.class, name = "flow_get"),
        @Type(value = FlowPathRequest.class, name = "flow_path"),
        @Type(value = DefaultFlowsCommandData.class, name = "install_default_flows"),
        @Type(value = InstallIngressFlowCommandData.class, name = "install_ingress_flow"),
        @Type(value = InstallEgressFlowCommandData.class, name = "install_egress_flow"),
        @Type(value = InstallTransitFlowCommandData.class, name = "install_transit_flow"),
        @Type(value = InstallOneSwitchFlowCommandData.class, name = "install_one_switch_flow"),
        @Type(value = DiscoverIslCommandData.class, name = "discover_isl"),
        @Type(value = DiscoverPathCommandData.class, name = "discover_path")})
public abstract class CommandData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message command.
     */
    @JsonProperty("command")
    private String command;

    /**
     * Message destination.
     */
    @JsonProperty("destination")
    private DestinationType destination;

    /**
     * Default constructor.
     */
    public CommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param   command      message command
     * @param   destination  message destination
     */
    @JsonCreator
    public CommandData(@JsonProperty("command") final String command,
                       @JsonProperty("destination") final DestinationType destination) {
        setCommand(command);
        setDestination(destination);
    }

    /**
     * Returns message command.
     *
     * @return  message command
     */
    @JsonProperty("command")
    public String getCommand() {
        return command;
    }

    /**
     * Sets message command.
     *
     * @param   command  message command
     */
    @JsonProperty("command")
    public void setCommand(final String command) {
        this.command = command;
    }

    /**
     * Returns message destination.
     *
     * @return  message destination
     */
    @JsonProperty("destination")
    public DestinationType getDestination() {
        return destination;
    }

    /**
     * Sets message destination.
     *
     * @param   destination  message destination
     */
    @JsonProperty("destination")
    public void setDestination(final DestinationType destination) {
        this.destination = destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s -> %s", command, destination);
    }
}
