package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents abstract flow installation info.
 *
 * Every flow installation command should contain these class properties.
 *
 * Created by atopilin on 07/04/2017.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "cookie",
        "switch_id",
        "input_port",
        "output_port"
})
public class AbstractInstallFlow extends AbstractFlow {

    /** Input port flow matching. It is a mandatory parameter. */
    protected Number inputPort;

    /** Output port for flow action. It is a mandatory parameter. */
    protected Number outputPort;

    /** Default constructor. */
    public AbstractInstallFlow() {}

    /**
     * Constructs an abstract flow installation command.
     *
     * @param cookie        Flow cookie
     * @param switchId      Switch id for flow installation
     * @param inputPort     Input port of the flow
     * @param outputPort    Output port of the flow
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public AbstractInstallFlow(@JsonProperty("cookie") String cookie,
                               @JsonProperty("switch_id") String switchId,
                               @JsonProperty("input_port") Number inputPort,
                               @JsonProperty("output_port") Number outputPort) {
        super(cookie, switchId);
        setInputPort(inputPort);
        setOutputPort(outputPort);
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
}
