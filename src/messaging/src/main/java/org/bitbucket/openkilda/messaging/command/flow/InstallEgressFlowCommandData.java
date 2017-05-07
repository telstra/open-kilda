package org.bitbucket.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.payload.response.OutputVlanType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Class represents egress flow installation info.
 * Transit vlan id is used in matching.
 * Output action depends on flow input and output vlan presence, but should at least contain transit vlan stripping.
 * Output vlan id is optional, because flow could be untagged on outgoing side.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "command",
        "destination",
        "flow_name",
        "switch_id",
        "input_port",
        "output_port",
        "transit_vlan_id",
        "output_vlan_id",
        "output_vlan_type"})
public class InstallEgressFlowCommandData extends InstallTransitFlowCommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Output action on the vlan tag.
     */
    protected OutputVlanType outputVlanType;

    /**
     * Output vlan id value.
     */
    protected Number outputVlanId;

    /**
     * Default constructor.
     */
    public InstallEgressFlowCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param   flowName        name of the flow
     * @param   switchId        switch ID for flow installation
     * @param   inputPort       input port of the flow
     * @param   outputPort      output port of the flow
     * @param   transitVlanId   transit vlan id value
     * @param   outputVlanId    output vlan id value
     * @param   outputVlanType  output vlan tag action
     *
     * @throws  IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public InstallEgressFlowCommandData(@JsonProperty("flow_name") String flowName,
                                        @JsonProperty("switch_id") String switchId,
                                        @JsonProperty("input_port") Number inputPort,
                                        @JsonProperty("output_port") Number outputPort,
                                        @JsonProperty("transit_vlan_id") Number transitVlanId,
                                        @JsonProperty("output_vlan_id") Number outputVlanId,
                                        @JsonProperty("output_vlan_type") OutputVlanType outputVlanType) {
        super(flowName, switchId, inputPort, outputPort, transitVlanId);
        setOutputVlanId(outputVlanId);
        setOutputVlanType(outputVlanType);
    }

    /**
     * Returns output action on the vlan tag.
     *
     * @return  output action on the vlan tag
     */
    @JsonProperty("output_vlan_type")
    public OutputVlanType getOutputVlanType() {
        return outputVlanType;
    }

    /**
     * Returns output vlan id value.
     *
     * @return  output vlan id value
     */
    @JsonProperty("output_vlan_id")
    public Number getOutputVlanId() {
        return outputVlanId;
    }

    /**
     * Sets output action on the vlan tag.
     *
     * @param   outputVlanType  action on the vlan tag
     */
    @JsonProperty("output_vlan_type")
    public void setOutputVlanType(OutputVlanType outputVlanType) {
        if (outputVlanType == null) {
            throw new IllegalArgumentException("need to set output_vlan_type");
        } else if (!Utils.validateOutputVlanType(outputVlanId, outputVlanType)) {
            throw new IllegalArgumentException("need to set valid values for output_vlan_id and output_vlan_type");
        } else {
            this.outputVlanType = outputVlanType;
        }
    }

    /**
     * Sets output vlan id value.
     *
     * @param   outputVlanId  output vlan id value
     */
    @JsonProperty("output_vlan_id")
    public void setOutputVlanId(Number outputVlanId) {
        if (outputVlanId == null) {
            this.outputVlanId = 0;
        } else if (Utils.validateVlanRange(outputVlanId.intValue())) {
            this.outputVlanId = outputVlanId;
        } else {
            throw new IllegalArgumentException("need to set valid value for output_vlan_id");
        }
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
                .addValue(outputVlanType)
                .addValue(outputVlanId)
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

        InstallEgressFlowCommandData flow = (InstallEgressFlowCommandData) object;
        return Objects.equals(flowName, flow.flowName)
                && Objects.equals(switchId, flow.switchId)
                && Objects.equals(inputPort, flow.inputPort)
                && Objects.equals(outputPort, flow.outputPort)
                && Objects.equals(transitVlanId, flow.transitVlanId)
                && Objects.equals(outputVlanType, flow.outputVlanType)
                && Objects.equals(outputVlanId, flow.outputVlanId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowName, switchId, inputPort, outputPort,
                transitVlanId, outputVlanType, outputVlanId);
    }
}
