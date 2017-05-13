package org.bitbucket.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

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
public abstract class AbstractInstallFlowCommandData extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The name of the flow. It is a mandatory parameter.
     */
    protected String flowName;

    /**
     * The switch id to install flow on.
     */
    protected String switchId;

    /**
     * Input port flow matching.
     */
    protected Number inputPort;

    /**
     * Output port for flow action.
     */
    protected Number outputPort;

    /**
     * Default constructor.
     */
    public AbstractInstallFlowCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param   flowId    name of the flow
     * @param   switchId  switch id for flow installation
     * @param   inPort    input port of the flow
     * @param   outPort   output port of the flow
     * @throws  IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public AbstractInstallFlowCommandData(@JsonProperty("flow_name") final String flowId,
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
     * @return  name of the flow
     */
    @JsonProperty("flow_name")
    public String getFlowName() {
        return flowName;
    }

    /**
     * Returns id of the switch.
     *
     * @return  id of the switch
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Returns input port of the flow.
     *
     * @return  inout port of the flow
     */
    @JsonProperty("input_port")
    public Number getInputPort() {
        return inputPort;
    }

    /**
     * Returns output port of the flow.
     *
     * @return  output port of the flow
     */
    @JsonProperty("output_port")
    public Number getOutputPort() {
        return outputPort;
    }

    /**
     * Sets name of the flow.
     *
     * @param   flowName  name of the flow
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
     * @param   switchId  id of the switch
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
     * @param   inputPort  input port of the flow
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
     * @param   outputPort  output port of the flow
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

        AbstractInstallFlowCommandData that = (AbstractInstallFlowCommandData) object;
        return Objects.equals(getFlowName(), that.getFlowName()) &&
                Objects.equals(getSwitchId(), that.getSwitchId()) &&
                Objects.equals(getInputPort(), that.getInputPort()) &&
                Objects.equals(getOutputPort(), that.getOutputPort());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowName, switchId, inputPort, outputPort);
    }
}
