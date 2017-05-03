package org.bitbucket.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Represents abstract flow installation info.
 * Every flow installation command should contain these class properties.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "flow_name",
        "switch_id",
        "input_port",
        "output_port"})
public class AbstractInstallFlow extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The name of the flow. It is a mandatory parameter.
     */
    protected String flowName;

    /**
     * The switch id to install flow on. It is a mandatory parameter.
     */
    protected String switchId;

    /**
     * Input port flow matching. It is a mandatory parameter.
     */
    protected Number inputPort;

    /**
     * Output port for flow action. It is a mandatory parameter.
     */
    protected Number outputPort;

    /**
     * Default constructor.
     */
    public AbstractInstallFlow() {
    }

    /**
     * Constructs an abstract flow installation command.
     *
     * @param flowId   Name of the flow
     * @param switchId Switch id for flow installation
     * @param inPort   Input port of the flow
     * @param outPort  Output port of the flow
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public AbstractInstallFlow(@JsonProperty("flow_name") final String flowId,
                               @JsonProperty("switch_id") final String switchId,
                               @JsonProperty("input_port") final Number inPort,
                               @JsonProperty("output_port") final Number outPort) {
        setFlowName(flowId);
        setSwitchId(switchId);
        setInputPort(inPort);
        setOutputPort(outPort);
    }

    /**
     * Returns name of the flow.
     *
     * @return Name of the flow
     */
    @JsonProperty("flow_name")
    public String getFlowName() {
        return flowName;
    }

    /**
     * Returns id of the switch.
     *
     * @return ID of the switch
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Returns input port of the flow.
     *
     * @return Inout port of the flow
     */
    @JsonProperty("input_port")
    public Number getInputPort() {
        return inputPort;
    }

    /**
     * Returns output port of the flow.
     *
     * @return Output port of the flow
     */
    @JsonProperty("output_port")
    public Number getOutputPort() {
        return outputPort;
    }

    /**
     * Sets name of the flow.
     *
     * @param flowName of the flow
     */
    @JsonProperty("flow_name")
    public void setFlowName(String flowName) {
        if (flowName == null || flowName.isEmpty()) {
            throw new IllegalArgumentException("need to set a flow_name");
        }
        this.flowName = flowName;
    }

    /**
     * Sets id of the switch.
     *
     * @param switchId of the switch
     */
    @JsonProperty("switch_id")
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }

    /**
     * Sets input port of the flow.
     *
     * @param inputPort port of the flow
     */
    @JsonProperty("input_port")
    public void setInputPort(Number inputPort) {
        if (inputPort == null) {
            throw new IllegalArgumentException("need to set input_port");
        } else if (inputPort.intValue() < 0) {
            throw new IllegalArgumentException("need to set positive value for input_port");
        }
        this.inputPort = inputPort;
    }

    /**
     * Sets output port of the flow.
     *
     * @param outputPort port of the flow
     */
    @JsonProperty("output_port")
    public void setOutputPort(Number outputPort) {
        if (outputPort == null) {
            throw new IllegalArgumentException("need to set output_port");
        } else if (outputPort.intValue() < 0) {
            throw new IllegalArgumentException("need to set positive value for output_port");
        }
        this.outputPort = outputPort;
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
                .toString();
    }
}
