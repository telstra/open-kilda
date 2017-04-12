package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;
import org.apache.commons.lang3.EnumUtils;
import org.bitbucket.openkilda.floodlight.switchmanager.OutputVlanType;

/**
 * Class represents flow through one switch installation info.
 *
 * Input and output vlan ids are optional, because flow could be untagged on ingoing or outgoing side.
 * Output action depends on flow input and output vlan presence.
 * Bandwidth and two meter ids are used for flow throughput limitation.
 *
 * Created by atopilin on 05/04/2017.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "flow_name",
        "switch_id",
        "input_port",
        "output_port",
        "input_vlan_id",
        "output_vlan_id",
        "output_vlan_type",
        "bandwidth",
        "input_meter_id",
        "output_meter_id"
})
public class InstallOneSwitchFlow extends AbstractInstallFlow {

    /** Output action on the vlan tag. */
    private String outputVlanType;

    /** Flow bandwidth value. */
    private Number bandwidth;

    /** Allocated meter id for forward traffic. */
    private Number inputMeterId;

    /** Allocated meter id for reverse traffic. */
    private Number outputMeterId;

    /** Optional input vlan id value. */
    private Number inputVlanId;

    /** Optional output vlan id value. */
    private Number outputVlanId;

    /** Default constructor. */
    public InstallOneSwitchFlow() {}

    /**
     * Constructs a flow through one switch installation command.
     *
     * @param flowName        Name of the flow
     * @param switchId        Switch ID for flow installation
     * @param inputPort       Input port of the flow
     * @param outputPort      Output port of the flow
     * @param inputVlanId     Input vlan id value
     * @param outputVlanId    Output vlan id value
     * @param outputVlanType  Output vlan tag action
     * @param bandwidth       Flow bandwidth
     * @param inputMeterId    Allocated meter id
     * @param outputMeterId   Allocated meter id
     * @throws IllegalArgumentException if any of arguments is null
     */
    @JsonCreator
    public InstallOneSwitchFlow(@JsonProperty("flow_name") String flowName,
                                @JsonProperty("switch_id") String switchId,
                                @JsonProperty("input_port") Number inputPort,
                                @JsonProperty("output_port") Number outputPort,
                                @JsonProperty("input_vlan_id") Number inputVlanId,
                                @JsonProperty("output_vlan_id") Number outputVlanId,
                                @JsonProperty("output_vlan_type") String outputVlanType,
                                @JsonProperty("bandwidth") Number bandwidth,
                                @JsonProperty("input_meter_id") Number inputMeterId,
                                @JsonProperty("output_meter_id") Number outputMeterId) {
        super(flowName, switchId, inputPort, outputPort);

        setInputVlanId(inputVlanId);
        setOutputVlanId(outputVlanId);
        setOutputVlanType(outputVlanType);
        setBandwidth(bandwidth);
        setInputMeterId(inputMeterId);
        setOutputMeterId(outputMeterId);
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
     * Returns flow bandwidth value.
     *
     * @return Flow bandwidth value
     */
    @JsonProperty("bandwidth")
    public Number getBandwidth() {
        return bandwidth;
    }

    /**
     * Returns input meter id for the flow.
     *
     * @return Input meter id for the flow
     */
    @JsonProperty("input_meter_id")
    public Number getInputMeterId() {
        return inputMeterId;
    }

    /**
     * Returns output meter id for the flow.
     *
     * @return Output meter id for the flow
     */
    @JsonProperty("output_meter_id")
    public Number getOutputMeterId() {
        return outputMeterId;
    }

    /**
     * Returns input vlan id value.
     *
     * @return Input vlan id value
     */
    @JsonProperty("input_vlan_id")
    public Number getInputVlanId() {
        return inputVlanId;
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
     * Sets flow bandwidth value.
     *
     * @param bandwidth bandwidth value
     */
    @JsonProperty("bandwidth")
    public void setBandwidth(Number bandwidth) {
        if (bandwidth == null) {
            throw new IllegalArgumentException("need to set bandwidth");
        } else if (bandwidth.intValue() < 0) {
            throw new IllegalArgumentException("need to set non negative bandwidth");
        }
        this.bandwidth = bandwidth;
    }

    /**
     * Sets input meter id for the flow.
     *
     * @param inputMeterId meter id for the flow
     */
    @JsonProperty("input_meter_id")
    public void setInputMeterId(Number inputMeterId) {
        if (inputMeterId == null) {
            throw new IllegalArgumentException("need to set input_meter_id");
        } else if (inputMeterId.intValue() < 0) {
            throw new IllegalArgumentException("need to set non negative input_meter_id");
        }
        this.inputMeterId = inputMeterId;
    }

    /**
     * Ses output meter id for the flow.
     *
     * @param outputMeterId meter id for the flow
     */
    @JsonProperty("output_meter_id")
    public void setOutputMeterId(Number outputMeterId) {
        if (outputMeterId == null) {
            throw new IllegalArgumentException("need to set output_meter_id");
        } else if (outputMeterId.intValue() < 0) {
            throw new IllegalArgumentException("need to set non negative output_meter_id");
        } else if (outputMeterId.equals(inputMeterId)) {
            throw new IllegalArgumentException("need to set different input_meter_id and output_meter_id");
        }
        this.outputMeterId = outputMeterId;
    }

    /**
     * Sets input vlan id value.
     *
     * @param inputVlanId input vlan id value
     */
    @JsonProperty("input_vlan_id")
    public void setInputVlanId(Number inputVlanId) {
        if (inputVlanId == null) {
            this.inputVlanId = 0;
        } else if (Utils.checkVlanRange(inputVlanId.intValue())) {
            this.inputVlanId = inputVlanId;
        } else {
            throw new IllegalArgumentException("need to set valid value for input_vlan_id");
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
                .addValue(inputVlanId)
                .addValue(outputVlanId)
                .addValue(outputVlanType)
                .addValue(bandwidth)
                .addValue(inputMeterId)
                .addValue(outputMeterId)
                .toString();
    }
}
