package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;
import org.apache.commons.lang3.EnumUtils;
import org.bitbucket.openkilda.floodlight.switchmanager.OutputVlanType;

/**
 * Class represents egress flow installation info.
 *
 * Transit vlan id is used in matching.
 * Output action depends on flow input and output vlan presence, but should at least contain transit vlan stripping.
 * Output vlan id is optional, because flow could be untagged on outgoing side.
 *
 * Created by jonv on 23/3/17.
 */
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
        "output_vlan_type"
})
public class InstallEgressFlow extends InstallTransitFlow {

    /** Output action on the vlan tag. It is a mandatory parameter. */
    protected String outputVlanType;

    /** Output vlan id value. It is an optional parameter. */
    protected Number outputVlanId;

    /** Default constructor. */
    public InstallEgressFlow() {}

    /**
     * Constructs an egress flow installation command.
     *
     * @param flowName        Name of the flow
     * @param switchId        Switch ID for flow installation
     * @param inputPort       Input port of the flow
     * @param outputPort      Output port of the flow
     * @param transitVlanId   Transit vlan id value
     * @param outputVlanId    Output vlan id value
     * @param outputVlanType  Output vlan tag action
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public InstallEgressFlow(@JsonProperty("flow_name") String flowName,
                             @JsonProperty("switch_id") String switchId,
                             @JsonProperty("input_port") Number inputPort,
                             @JsonProperty("output_port") Number outputPort,
                             @JsonProperty("transit_vlan_id") Number transitVlanId,
                             @JsonProperty("output_vlan_id") Number outputVlanId,
                             @JsonProperty("output_vlan_type") String outputVlanType) {
        super(flowName, switchId, inputPort, outputPort, transitVlanId);

        setOutputVlanId(outputVlanId);
        setOutputVlanType(outputVlanType);
    }

    /**
     * Returns output action on the vlan tag.
     *
     * @return Output action on the vlan tag
     */
    @JsonProperty("output_vlan_type")
    public String getOutputVlanType() {
        return outputVlanType;
    }

    /**
     * Returns output vlan id value.
     *
     * @return Output vlan id value
     */
    @JsonProperty("output_vlan_id")
    public Number getOutputVlanId() {
        return outputVlanId;
    }

    /**
     * Sets output action on the vlan tag.
     *
     * @param outputVlanType action on the vlan tag
     */
    @JsonProperty("output_vlan_type")
    public void setOutputVlanType(String outputVlanType) {
        if (outputVlanType == null) {
            throw new IllegalArgumentException("need to set output_vlan_type");
        } else if (!EnumUtils.isValidEnum(OutputVlanType.class, outputVlanType)) {
            throw new IllegalArgumentException("need to set valid value for output_vlan_type");
        } else if (!Utils.checkOutputVlanType(outputVlanId, outputVlanType)) {
            throw new IllegalArgumentException("need to set valid values for output_vlan_id and output_vlan_type");
        } else {
            this.outputVlanType = outputVlanType;
        }
    }

    /**
     * Sets output vlan id value.
     *
     * @param outputVlanId output vlan id value
     */
    @JsonProperty("output_vlan_id")
    public void setOutputVlanId(Number outputVlanId) {
        if (outputVlanId == null) {
            this.outputVlanId = 0;
        } else if (Utils.checkVlanRange(outputVlanId.intValue())) {
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
        return Objects.toStringHelper(this)
                .addValue(flowName)
                .addValue(switchId)
                .addValue(inputPort)
                .addValue(outputPort)
                .addValue(transitVlanId)
                .addValue(outputVlanType)
                .addValue(outputVlanId)
                .toString();
    }
}
