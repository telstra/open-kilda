package org.bitbucket.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Class represents transit flow installation info.
 * There is no output action for this type of flow.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "flow_name",
        "switch_id",
        "input_port",
        "output_port",
        "transit_vlan_id"})
public class InstallTransitFlowCommandData extends AbstractInstallFlowCommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The transit vlan id value.
     */
    protected Number transitVlanId;

    /**
     * Default constructor.
     */
    public InstallTransitFlowCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param   flowName       name of the flow
     * @param   switchId       switch ID for flow installation
     * @param   inputPort      input port of the flow
     * @param   outputPort     output port of the flow
     * @param   transitVlanId  transit vlan id value
     *
     * @throws  IllegalArgumentException if any of parameters parameters is null
     */
    @JsonCreator
    public InstallTransitFlowCommandData(@JsonProperty("flow_name") String flowName,
                                         @JsonProperty("switch_id") String switchId,
                                         @JsonProperty("input_port") Number inputPort,
                                         @JsonProperty("output_port") Number outputPort,
                                         @JsonProperty("transit_vlan_id") Number transitVlanId) {
        super(flowName, switchId, inputPort, outputPort);
        setTransitVlanId(transitVlanId);
    }

    /**
     * Returns transit vlan id of the flow.
     *
     * @return  transit vlan id of the flow
     */
    @JsonProperty("transit_vlan_id")
    public Number getTransitVlanId() {
        return transitVlanId;
    }

    /**
     * Sets transit vlan id of the flow.
     *
     * @param  transitVlanId  vlan id of the flow
     */
    @JsonProperty("transit_vlan_id")
    public void setTransitVlanId(Number transitVlanId) {
        if (transitVlanId == null) {
            throw new IllegalArgumentException("need to set transit_vlan_id");
        }
        if (!Utils.validateVlanRange(transitVlanId.intValue()) || transitVlanId.intValue() == 0) {
            throw new IllegalArgumentException("need to set valid value for transit_vlan_id");
        }
        this.transitVlanId = transitVlanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .addValue(flowName)
                .addValue(switchId)
                .addValue(inputPort)
                .addValue(outputPort)
                .addValue(transitVlanId)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        InstallTransitFlowCommandData flow = (InstallTransitFlowCommandData) object;
        return Objects.equals(flowName, flow.flowName)
                && Objects.equals(switchId, flow.switchId)
                && Objects.equals(inputPort, flow.inputPort)
                && Objects.equals(outputPort, flow.outputPort)
                && Objects.equals(transitVlanId, flow.transitVlanId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowName, switchId, inputPort, outputPort, transitVlanId);
    }
}
